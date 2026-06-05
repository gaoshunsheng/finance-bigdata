package com.credit.platform.data.flink.apply_freq;

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

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * FLINK_003: 近1月申请频次统计作业。
 *
 * <p>处理流程:
 * <ol>
 *   <li>Kafka Source 消费 {@code application_event} Topic</li>
 *   <li>JSON 解析为 ApplyEvent POJO</li>
 *   <li>按 customerId 分组</li>
 *   <li>滑动窗口: 30 天窗口，1 小时滑动</li>
 *   <li>聚合统计每个客户的申请次数</li>
 *   <li>输出结果（当前为 print，后续接入 Redis/HBase Sink）</li>
 * </ol>
 *
 * <p>输入 JSON 格式:
 * <pre>{@code
 * {
 *   "customerId": "C001",
 *   "productId": "P001",
 *   "channel": "ONLINE",
 *   "eventTime": "2026-06-01T10:00:00"
 * }
 * }</pre>
 */
public class ApplyFreq1mJob {

    private static final String JOB_NAME = "ApplyFreq1mJob";
    private static final String DEFAULT_BOOTSTRAP_SERVERS = "localhost:9092";
    private static final String DEFAULT_GROUP_ID = "flink-apply-freq-1m";
    private static final String TOPIC = "application_event";

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

        DataStream<ApplyFreqResult> resultStream = rawStream
                // 1. JSON 解析
                .process(new JsonParseFunction())
                // 2. 分配时间戳和水位线
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<ApplyEvent>forMonotonousTimestamps()
                                .withTimestampAssigner((event, ts) -> event.eventTime.toEpochSecond(ZoneOffset.ofHours(8)) * 1000)
                )
                // 3. 按客户 ID 分组
                .keyBy(event -> event.customerId)
                // 4. 滑动窗口: 30 天，每 1 小时滑动
                .window(SlidingEventTimeWindows.of(Time.days(30), Time.hours(1)))
                // 5. 聚合: 统计申请次数
                .aggregate(new ApplyCountAggregator(), new ApplyCountWindowFunction());

        // 6. 输出（占位 Sink，后续接入 Redis/HBase）
        resultStream.print();

        env.execute(JOB_NAME);
    }

    // ===================== JSON 解析 =====================

    /**
     * JSON 解析 ProcessFunction — 将原始 JSON 字符串解析为 ApplyEvent。
     *
     * <p>解析失败时跳过该条记录并打印警告日志，保证作业容错运行。
     */
    private static class JsonParseFunction extends ProcessFunction<String, ApplyEvent> {

        private static final long serialVersionUID = 1L;
        private transient ObjectMapper mapper;

        @Override
        public void open(org.apache.flink.configuration.Configuration parameters) {
            mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
        }

        @Override
        public void processElement(String value, ProcessFunction<String, ApplyEvent>.Context ctx,
                                   Collector<ApplyEvent> out) throws Exception {
            try {
                ApplyEvent event = mapper.readValue(value, ApplyEvent.class);
                out.collect(event);
            } catch (Exception e) {
                // 解析失败，跳过脏数据
                System.err.println("[" + JOB_NAME + "] JSON 解析失败: " + value + ", 原因: " + e.getMessage());
            }
        }
    }

    // ===================== 聚合函数 =====================

    /**
     * 窗口内聚合函数 — 累加统计申请次数。
     */
    private static class ApplyCountAggregator implements AggregateFunction<ApplyEvent, Long, Long> {

        private static final long serialVersionUID = 1L;

        @Override
        public Long createAccumulator() {
            return 0L;
        }

        @Override
        public Long add(ApplyEvent value, Long accumulator) {
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
    private static class ApplyCountWindowFunction
            extends ProcessWindowFunction<Long, ApplyFreqResult, String, TimeWindow> {

        private static final long serialVersionUID = 1L;

        @Override
        public void process(String customerId,
                            ProcessWindowFunction<Long, ApplyFreqResult, String, TimeWindow>.Context context,
                            Iterable<Long> elements,
                            Collector<ApplyFreqResult> out) {
            Long count = elements.iterator().next();
            TimeWindow window = context.window();
            out.collect(new ApplyFreqResult(
                    customerId,
                    count,
                    window.getStart(),
                    window.getEnd()
            ));
        }
    }

    // ===================== 内部 POJO =====================

    /**
     * 申请事件 POJO — 对应 Kafka 消息 JSON 结构。
     */
    public static class ApplyEvent {
        public String customerId;
        public String productId;
        public String channel;
        public LocalDateTime eventTime;

        public ApplyEvent() {
        }

        public ApplyEvent(String customerId, String productId,
                          String channel, LocalDateTime eventTime) {
            this.customerId = customerId;
            this.productId = productId;
            this.channel = channel;
            this.eventTime = eventTime;
        }
    }

    /**
     * 输出结果 POJO — 包含客户 ID、申请次数和窗口起止时间。
     */
    public static class ApplyFreqResult {
        public String customerId;
        public long applyCount;
        public long windowStart;
        public long windowEnd;

        public ApplyFreqResult() {
        }

        public ApplyFreqResult(String customerId, long applyCount,
                               long windowStart, long windowEnd) {
            this.customerId = customerId;
            this.applyCount = applyCount;
            this.windowStart = windowStart;
            this.windowEnd = windowEnd;
        }

        @Override
        public String toString() {
            return String.format(
                    "ApplyFreqResult{customerId='%s', applyCount=%d, windowStart=%d, windowEnd=%d}",
                    customerId, applyCount, windowStart, windowEnd
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
