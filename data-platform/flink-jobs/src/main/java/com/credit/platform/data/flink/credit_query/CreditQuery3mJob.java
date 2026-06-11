package com.credit.platform.data.flink.credit_query;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.typeinfo.TypeHint;
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

import com.credit.platform.data.flink.common.RedisFeatureSink;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

/**
 * FLINK_001: 近3月征信查询次数统计作业。
 *
 * <p>处理流程:
 * <ol>
 *   <li>Kafka Source 消费 {@code cdc_credit_query} Topic</li>
 *   <li>JSON 解析为 CreditQueryEvent POJO</li>
 *   <li>按 customerId 分组</li>
 *   <li>滑动窗口: 90 天窗口，1 小时滑动</li>
 *   <li>聚合统计每个客户的查询次数</li>
 *   <li>输出结果（当前为 print，后续接入 Redis/HBase Sink）</li>
 * </ol>
 *
 * <p>输入 JSON 格式:
 * <pre>{@code
 * {
 *   "customerId": "C001",
 *   "eventTime": "2026-06-01T10:00:00",
 *   "queryType": "PBOC",
 *   "institution": "BANK_A"
 * }
 * }</pre>
 */
public class CreditQuery3mJob {

    private static final String JOB_NAME = "CreditQuery3mJob";
    private static final String DEFAULT_BOOTSTRAP_SERVERS = "localhost:9092";
    private static final String DEFAULT_GROUP_ID = "flink-credit-query-3m";
    private static final String TOPIC = "cdc_credit_query";

    public static void main(String[] args) throws Exception {
        // ---- 配置参数：优先环境变量，缺省使用默认值 ----
        String bootstrapServers = getEnvOrDefault("KAFKA_BOOTSTRAP_SERVERS", DEFAULT_BOOTSTRAP_SERVERS);
        String groupId = getEnvOrDefault("KAFKA_GROUP_ID", DEFAULT_GROUP_ID);
        int parallelism = Integer.parseInt(getEnvOrDefault("FLINK_PARALLELISM", "1"));
        long windowSizeMs = Long.parseLong(getEnvOrDefault("FLINK_WINDOW_SIZE_MS", String.valueOf(Time.days(90).toMilliseconds())));
        long slideMs = Long.parseLong(getEnvOrDefault("FLINK_WINDOW_SLIDE_MS", String.valueOf(Time.hours(1).toMilliseconds())));

        // ---- 执行环境 ----
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(parallelism);

        // ---- Kafka Source ----
        KafkaSource<String> kafkaSource = KafkaSource.<String>builder()
                .setBootstrapServers(bootstrapServers)
                .setGroupId(groupId)
                .setTopics(TOPIC)
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        // ---- 数据流处理 ----
        DataStream<String> rawStream = env
                .fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), "kafka-" + TOPIC);

        DataStream<CreditQueryResult> resultStream = rawStream
                // 1. JSON 解析
                .process(new JsonParseFunction())
                // 2. 分配时间戳和水位线
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<CreditQueryEvent>forMonotonousTimestamps()
                                .withTimestampAssigner((event, ts) -> event.eventTime.toEpochSecond(ZoneOffset.ofHours(8)) * 1000)
                )
                // 3. 按客户 ID 分组
                .keyBy(event -> event.customerId)
                // 4. 滑动窗口: 默认 90 天/1 小时滑动，可通过环境变量调整
                .window(SlidingEventTimeWindows.of(Time.milliseconds(windowSizeMs), Time.milliseconds(slideMs)))
                // 5. 聚合: 统计查询次数
                .aggregate(new QueryCountAggregator(), new QueryCountWindowFunction());

        // 6. 转换为 Map 并写入 Redis + print 调试
        DataStream<Map<String, Object>> featureStream = resultStream.map(result -> {
            Map<String, Object> map = new HashMap<>();
            map.put("customerId", result.customerId);
            map.put("credit_query_count_3m", result.count);
            map.put("windowStart", result.windowStart);
            map.put("windowEnd", result.windowEnd);
            return map;
        }).returns(new TypeHint<Map<String, Object>>() {});

        String redisUri = getEnvOrDefault("REDIS_URI", "redis://localhost:6379");
        featureStream.addSink(new RedisFeatureSink(redisUri, "credit_query_3m"));
        featureStream.print();

        env.execute(JOB_NAME);
    }

    // ===================== JSON 解析 =====================

    /**
     * JSON 解析 ProcessFunction — 将原始 JSON 字符串解析为 CreditQueryEvent。
     *
     * <p>解析失败时跳过该条记录并打印警告日志，保证作业容错运行。
     */
    private static class JsonParseFunction extends ProcessFunction<String, CreditQueryEvent> {

        private static final long serialVersionUID = 1L;
        private transient ObjectMapper mapper;

        @Override
        public void open(org.apache.flink.configuration.Configuration parameters) {
            mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
        }

        @Override
        public void processElement(String value, ProcessFunction<String, CreditQueryEvent>.Context ctx,
                                   Collector<CreditQueryEvent> out) throws Exception {
            try {
                CreditQueryEvent event = mapper.readValue(value, CreditQueryEvent.class);
                out.collect(event);
            } catch (Exception e) {
                // 解析失败，跳过脏数据
                System.err.println("[" + JOB_NAME + "] JSON 解析失败: " + value + ", 原因: " + e.getMessage());
            }
        }
    }

    // ===================== 聚合函数 =====================

    /**
     * 窗口内聚合函数 — 累加统计查询次数。
     */
    private static class QueryCountAggregator implements AggregateFunction<CreditQueryEvent, Long, Long> {

        private static final long serialVersionUID = 1L;

        @Override
        public Long createAccumulator() {
            return 0L;
        }

        @Override
        public Long add(CreditQueryEvent value, Long accumulator) {
            return accumulator + 1;
        }

        @Override
        public Long getResult(Long accumulator) {
            return accumulator;
        }

        @Override
        public Long merge(Long a, Long b) {
            return a + b;
        }
    }

    /**
     * 窗口结果函数 — 将聚合结果与窗口信息组合为输出 POJO。
     */
    private static class QueryCountWindowFunction
            extends ProcessWindowFunction<Long, CreditQueryResult, String, TimeWindow> {

        private static final long serialVersionUID = 1L;

        @Override
        public void process(String customerId,
                            ProcessWindowFunction<Long, CreditQueryResult, String, TimeWindow>.Context context,
                            Iterable<Long> elements,
                            Collector<CreditQueryResult> out) {
            Long count = elements.iterator().next();
            TimeWindow window = context.window();
            out.collect(new CreditQueryResult(
                    customerId,
                    count,
                    window.getStart(),
                    window.getEnd()
            ));
        }
    }

    // ===================== 内部 POJO =====================

    /**
     * 征信查询事件 POJO — 对应 Kafka 消息 JSON 结构。
     */
    public static class CreditQueryEvent {
        public String customerId;
        public LocalDateTime eventTime;
        public String queryType;
        public String institution;

        public CreditQueryEvent() {
        }

        public CreditQueryEvent(String customerId, LocalDateTime eventTime,
                                String queryType, String institution) {
            this.customerId = customerId;
            this.eventTime = eventTime;
            this.queryType = queryType;
            this.institution = institution;
        }
    }

    /**
     * 输出结果 POJO — 包含客户 ID、查询次数和窗口起止时间。
     */
    public static class CreditQueryResult {
        public String customerId;
        public long count;
        public long windowStart;
        public long windowEnd;

        public CreditQueryResult() {
        }

        public CreditQueryResult(String customerId, long count,
                                 long windowStart, long windowEnd) {
            this.customerId = customerId;
            this.count = count;
            this.windowStart = windowStart;
            this.windowEnd = windowEnd;
        }

        @Override
        public String toString() {
            return String.format(
                    "CreditQueryResult{customerId='%s', count=%d, windowStart=%d, windowEnd=%d}",
                    customerId, count, windowStart, windowEnd
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
