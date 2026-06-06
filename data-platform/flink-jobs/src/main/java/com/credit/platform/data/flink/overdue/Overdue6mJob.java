package com.credit.platform.data.flink.overdue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import com.credit.platform.data.flink.common.HBaseFeatureSink;
import com.credit.platform.data.flink.common.RedisFeatureSink;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

/**
 * FLINK_002: 近6月逾期次数统计作业。
 *
 * <p>处理流程:
 * <ol>
 *   <li>Kafka Source 消费 {@code cdc_overdue_event} Topic</li>
 *   <li>JSON 解析为 OverdueEvent POJO</li>
 *   <li>按 customerId 分组</li>
 *   <li>滑动窗口: 180 天窗口，1 天滑动</li>
 *   <li>聚合统计每个客户的逾期次数和逾期总金额</li>
 *   <li>输出结果（当前为 print，后续接入 Redis/HBase Sink）</li>
 * </ol>
 *
 * <p>输入 JSON 格式:
 * <pre>{@code
 * {
 *   "customerId": "C001",
 *   "loanId": "L001",
 *   "overdueDays": 5,
 *   "overdueAmount": 1000.00,
 *   "eventTime": "2026-06-01T10:00:00"
 * }
 * }</pre>
 */
public class Overdue6mJob {

    private static final String JOB_NAME = "Overdue6mJob";
    private static final String DEFAULT_BOOTSTRAP_SERVERS = "localhost:9092";
    private static final String DEFAULT_GROUP_ID = "flink-overdue-6m";
    private static final String TOPIC = "cdc_overdue_event";

    public static void main(String[] args) throws Exception {
        // ---- 配置参数：优先环境变量，缺省使用默认值 ----
        String bootstrapServers = getEnvOrDefault("KAFKA_BOOTSTRAP_SERVERS", DEFAULT_BOOTSTRAP_SERVERS);
        String groupId = getEnvOrDefault("KAFKA_GROUP_ID", DEFAULT_GROUP_ID);
        int parallelism = Integer.parseInt(getEnvOrDefault("FLINK_PARALLELISM", "1"));

        // ---- 执行环境 ----
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(parallelism);

        // ---- Kafka Source ----
        KafkaSource<String> kafkaSource = KafkaSource.<String>builder()
                .setBootstrapServers(bootstrapServers)
                .setGroupId(groupId)
                .setTopics(TOPIC)
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        // ---- 数据流处理 ----
        DataStream<String> rawStream = env
                .fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), "kafka-" + TOPIC);

        DataStream<OverdueResult> resultStream = rawStream
                // 1. JSON 解析
                .process(new JsonParseFunction())
                // 2. 分配时间戳和水位线
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<OverdueEvent>forMonotonousTimestamps()
                                .withTimestampAssigner((event, ts) -> event.eventTime.toEpochSecond(ZoneOffset.ofHours(8)) * 1000)
                )
                // 3. 按客户 ID 分组
                .keyBy(event -> event.customerId)
                // 4. 滑动窗口: 180 天，每 1 天滑动
                .window(SlidingEventTimeWindows.of(Time.days(180), Time.days(1)))
                // 5. 聚合: 统计逾期次数和逾期总金额
                .aggregate(new OverdueCountAggregator(), new OverdueCountWindowFunction());

        // 6. 转换为 Map 并写入 Redis + HBase + print 调试
        DataStream<Map<String, Object>> featureStream = resultStream.map(result -> {
            Map<String, Object> map = new HashMap<>();
            map.put("customerId", result.customerId);
            map.put("overdue_count_6m", result.overdueCount);
            map.put("total_overdue_amount_6m", result.totalOverdueAmount);
            map.put("windowStart", result.windowStart);
            map.put("windowEnd", result.windowEnd);
            return map;
        });

        String redisUri = getEnvOrDefault("REDIS_URI", "redis://localhost:6379");
        String zkQuorum = getEnvOrDefault("HBASE_ZK_QUORUM", "localhost");
        String zkPort = getEnvOrDefault("HBASE_ZK_PORT", "2181");
        featureStream.addSink(new RedisFeatureSink(redisUri, "overdue_6m"));
        featureStream.addSink(new HBaseFeatureSink(zkQuorum, zkPort, "overdue_6m"));
        featureStream.print();

        env.execute(JOB_NAME);
    }

    // ===================== JSON 解析 =====================

    /**
     * JSON 解析 ProcessFunction — 将原始 JSON 字符串解析为 OverdueEvent。
     *
     * <p>解析失败时跳过该条记录并打印警告日志，保证作业容错运行。
     */
    private static class JsonParseFunction extends ProcessFunction<String, OverdueEvent> {

        private static final long serialVersionUID = 1L;
        private transient ObjectMapper mapper;

        @Override
        public void open(org.apache.flink.configuration.Configuration parameters) {
            mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
        }

        @Override
        public void processElement(String value, ProcessFunction<String, OverdueEvent>.Context ctx,
                                   Collector<OverdueEvent> out) throws Exception {
            try {
                OverdueEvent event = mapper.readValue(value, OverdueEvent.class);
                out.collect(event);
            } catch (Exception e) {
                // 解析失败，跳过脏数据
                System.err.println("[" + JOB_NAME + "] JSON 解析失败: " + value + ", 原因: " + e.getMessage());
            }
        }
    }

    // ===================== 聚合函数 =====================

    /**
     * 逾期聚合累加器 — 记录窗口内逾期次数和逾期总金额。
     */
    private static class OverdueAccumulator {
        long count;
        double totalOverdueAmount;
    }

    /**
     * 窗口内聚合函数 — 累加统计逾期次数和逾期总金额。
     */
    private static class OverdueCountAggregator
            implements AggregateFunction<OverdueEvent, OverdueAccumulator, OverdueAccumulator> {

        private static final long serialVersionUID = 1L;

        @Override
        public OverdueAccumulator createAccumulator() {
            OverdueAccumulator acc = new OverdueAccumulator();
            acc.count = 0L;
            acc.totalOverdueAmount = 0.0;
            return acc;
        }

        @Override
        public OverdueAccumulator add(OverdueEvent value, OverdueAccumulator accumulator) {
            accumulator.count += 1;
            accumulator.totalOverdueAmount += value.overdueAmount;
            return accumulator;
        }

        @Override
        public OverdueAccumulator getResult(OverdueAccumulator accumulator) {
            return accumulator;
        }

        @Override
        public OverdueAccumulator merge(OverdueAccumulator a, OverdueAccumulator b) {
            OverdueAccumulator merged = new OverdueAccumulator();
            merged.count = a.count + b.count;
            merged.totalOverdueAmount = a.totalOverdueAmount + b.totalOverdueAmount;
            return merged;
        }
    }

    /**
     * 窗口结果函数 — 将聚合结果与窗口信息组合为输出 POJO。
     */
    private static class OverdueCountWindowFunction
            extends ProcessWindowFunction<OverdueAccumulator, OverdueResult, String, TimeWindow> {

        private static final long serialVersionUID = 1L;

        @Override
        public void process(String customerId,
                            ProcessWindowFunction<OverdueAccumulator, OverdueResult, String, TimeWindow>.Context context,
                            Iterable<OverdueAccumulator> elements,
                            Collector<OverdueResult> out) {
            OverdueAccumulator acc = elements.iterator().next();
            TimeWindow window = context.window();
            out.collect(new OverdueResult(
                    customerId,
                    acc.count,
                    acc.totalOverdueAmount,
                    window.getStart(),
                    window.getEnd()
            ));
        }
    }

    // ===================== 内部 POJO =====================

    /**
     * 逾期事件 POJO — 对应 Kafka 消息 JSON 结构。
     */
    public static class OverdueEvent {
        public String customerId;
        public String loanId;
        public int overdueDays;
        public double overdueAmount;
        public LocalDateTime eventTime;

        public OverdueEvent() {
        }

        public OverdueEvent(String customerId, String loanId,
                            int overdueDays, double overdueAmount,
                            LocalDateTime eventTime) {
            this.customerId = customerId;
            this.loanId = loanId;
            this.overdueDays = overdueDays;
            this.overdueAmount = overdueAmount;
            this.eventTime = eventTime;
        }
    }

    /**
     * 输出结果 POJO — 包含客户 ID、逾期次数、逾期总金额和窗口起止时间。
     */
    public static class OverdueResult {
        public String customerId;
        public long overdueCount;
        public double totalOverdueAmount;
        public long windowStart;
        public long windowEnd;

        public OverdueResult() {
        }

        public OverdueResult(String customerId, long overdueCount,
                             double totalOverdueAmount,
                             long windowStart, long windowEnd) {
            this.customerId = customerId;
            this.overdueCount = overdueCount;
            this.totalOverdueAmount = totalOverdueAmount;
            this.windowStart = windowStart;
            this.windowEnd = windowEnd;
        }

        @Override
        public String toString() {
            return String.format(
                    "OverdueResult{customerId='%s', overdueCount=%d, totalOverdueAmount=%.2f, windowStart=%d, windowEnd=%d}",
                    customerId, overdueCount, totalOverdueAmount, windowStart, windowEnd
            );
        }
    }

    // ===================== 工具方法 =====================

    /**
     * 读取环境变量，缺省返回 defaultValue。
     */
    private static String getEnvOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }
}
