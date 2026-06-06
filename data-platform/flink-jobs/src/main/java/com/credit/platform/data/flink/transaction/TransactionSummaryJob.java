package com.credit.platform.data.flink.transaction;

import com.credit.platform.data.flink.common.HBaseFeatureSink;
import com.credit.platform.data.flink.common.RedisFeatureSink;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.Collector;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * FLINK_004: 实时交易金额汇总作业
 *
 * <p>功能说明:
 * <ul>
 *   <li>从 Kafka topic "transaction_event" 消费交易事件</li>
 *   <li>解析 JSON 格式的交易数据</li>
 *   <li>按 customerId 分组，使用 1 小时滚动窗口聚合</li>
 *   <li>计算每个客户的交易总额、交易笔数、平均交易金额</li>
 *   <li>输出聚合结果（当前 Sink 为 print，后续可对接 Redis/HBase）</li>
 * </ul>
 *
 * <p>输入 JSON 格式:
 * <pre>
 * {
 *   "customerId": "C001",
 *   "transactionId": "T001",
 *   "amount": 5000.00,
 *   "type": "PAYMENT",
 *   "eventTime": "2026-06-01T10:00:00"
 * }
 * </pre>
 *
 * <p>输出: {@link TransactionSummary} 包含 customerId, totalAmount, transactionCount, avgAmount, windowStart, windowEnd
 */
public class TransactionSummaryJob {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        OBJECT_MAPPER.registerModule(new JavaTimeModule());
    }

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"));

    public static void main(String[] args) throws Exception {
        // 1. 创建流执行环境
        long windowSizeMs = Long.parseLong(getEnv("FLINK_WINDOW_SIZE_MS", String.valueOf(Time.hours(1).toMilliseconds())));

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // 2. 构建 Kafka Source
        KafkaSource<String> source = KafkaSource.<String>builder()
                .setBootstrapServers(getEnv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"))
                .setGroupId("flink-transaction-summary")
                .setTopics(getEnv("KAFKA_TOPIC", "transaction_event"))
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        // 3. 从 Source 创建 DataStream，提取 eventTime 作为 Watermark
        DataStream<String> rawStream = env.fromSource(
                source,
                WatermarkStrategy.<String>forBoundedOutOfOrderness(Duration.ofSeconds(5))
                        .withTimestampAssigner((event, timestamp) -> {
                            try {
                                com.fasterxml.jackson.databind.JsonNode node = OBJECT_MAPPER.readTree(event);
                                String eventTimeStr = node.get("eventTime").asText();
                                return Instant.parse(eventTimeStr).toEpochMilli();
                            } catch (Exception e) {
                                return System.currentTimeMillis();
                            }
                        }),
                "transaction-event-source"
        );

        // 4. 解析 JSON → TransactionEvent，过滤解析失败的记录
        DataStream<TransactionEvent> eventStream = rawStream
                .flatMap(new TransactionEventParser())
                .name("parse-transaction-event");

        // 5. 按 customerId 分组 → 1 小时滚动窗口 → 聚合计算
        DataStream<TransactionSummary> summaryStream = eventStream
                .keyBy(event -> event.customerId)
                .window(TumblingEventTimeWindows.of(Time.milliseconds(windowSizeMs)))
                .aggregate(new TransactionAggregator())
                .name("aggregate-transaction-summary");

        // 6. 转换为 Map 并写入 Redis + HBase + print 调试
        DataStream<Map<String, Object>> featureStream = summaryStream.map(summary -> {
            Map<String, Object> map = new HashMap<>();
            map.put("customerId", summary.customerId);
            map.put("transaction_summary_total", summary.totalAmount);
            map.put("transaction_summary_count", summary.transactionCount);
            map.put("transaction_summary_avg", summary.avgAmount);
            map.put("windowStart", summary.windowStart);
            map.put("windowEnd", summary.windowEnd);
            return map;
        });

        String redisUri = getEnv("REDIS_URI", "redis://localhost:6379");
        String zkQuorum = getEnv("HBASE_ZK_QUORUM", "localhost");
        String zkPort = getEnv("HBASE_ZK_PORT", "2181");
        featureStream.addSink(new RedisFeatureSink(redisUri, "transaction_summary_1h"));
        featureStream.addSink(new HBaseFeatureSink(zkQuorum, zkPort, "transaction_summary_1h"));
        featureStream.print("TransactionSummary");

        // 7. 执行作业
        env.execute("FLINK_004 - Transaction Summary Job");
    }

    // ======================== 解析函数 ========================

    /**
     * 交易事件 JSON 解析器 — 将 JSON 字符串解析为 TransactionEvent 对象。
     *
     * <p>过滤规则: customerId 不为 null 且 amount >= 0 的记录才会输出。
     * 解析失败的记录会被静默跳过。
     */
    public static class TransactionEventParser implements FlatMapFunction<String, TransactionEvent> {

        private static final long serialVersionUID = 1L;

        @Override
        public void flatMap(String value, Collector<TransactionEvent> out) throws Exception {
            try {
                TransactionEvent event = OBJECT_MAPPER.readValue(value, TransactionEvent.class);
                if (event.customerId != null && event.amount >= 0) {
                    out.collect(event);
                }
            } catch (Exception e) {
                // 跳过无法解析的记录
            }
        }
    }

    // ======================== 聚合函数 ========================

    /**
     * 交易聚合函数 — 在窗口内累加交易总额和交易笔数，窗口触发时计算平均值。
     *
     * <p>累加器结构: (double totalAmount, long count)
     */
    public static class TransactionAggregator implements AggregateFunction<
            TransactionEvent, TransactionAccumulator, TransactionSummary> {

        private static final long serialVersionUID = 1L;

        @Override
        public TransactionAccumulator createAccumulator() {
            return new TransactionAccumulator(null, 0.0, 0L);
        }

        @Override
        public TransactionAccumulator add(TransactionEvent event, TransactionAccumulator acc) {
            String customerId = acc.customerId != null ? acc.customerId : event.customerId;
            return new TransactionAccumulator(customerId, acc.totalAmount + event.amount, acc.count + 1);
        }

        @Override
        public TransactionSummary getResult(TransactionAccumulator acc) {
            double avgAmount = acc.count > 0 ? acc.totalAmount / acc.count : 0.0;
            return new TransactionSummary(
                    acc.customerId,
                    acc.totalAmount,
                    acc.count,
                    avgAmount,
                    null,
                    null
            );
        }

        @Override
        public TransactionAccumulator merge(TransactionAccumulator a, TransactionAccumulator b) {
            String customerId = a.customerId != null ? a.customerId : b.customerId;
            return new TransactionAccumulator(customerId, a.totalAmount + b.totalAmount, a.count + b.count);
        }
    }

    // ======================== 内部 POJO ========================

    /**
     * 交易事件 POJO — 对应 Kafka 中的 JSON 消息体。
     */
    public static class TransactionEvent {
        public String customerId;
        public String transactionId;
        public double amount;
        public String type;
        public String eventTime;

        public TransactionEvent() {
        }

        public TransactionEvent(String customerId, String transactionId, double amount,
                                String type, String eventTime) {
            this.customerId = customerId;
            this.transactionId = transactionId;
            this.amount = amount;
            this.type = type;
            this.eventTime = eventTime;
        }

        @Override
        public String toString() {
            return "TransactionEvent{customerId='" + customerId
                    + "', transactionId='" + transactionId
                    + "', amount=" + amount
                    + ", type='" + type
                    + "', eventTime='" + eventTime + "'}";
        }
    }

    /**
     * 聚合累加器 — 保存窗口内的中间聚合结果。
     */
    public static class TransactionAccumulator {
        public String customerId;
        public double totalAmount;
        public long count;

        public TransactionAccumulator() {
        }

        public TransactionAccumulator(String customerId, double totalAmount, long count) {
            this.customerId = customerId;
            this.totalAmount = totalAmount;
            this.count = count;
        }
    }

    /**
     * 交易汇总结果 POJO — 窗口聚合输出。
     */
    public static class TransactionSummary {
        public String customerId;
        public double totalAmount;
        public long transactionCount;
        public double avgAmount;
        public String windowStart;
        public String windowEnd;

        public TransactionSummary() {
        }

        public TransactionSummary(String customerId, double totalAmount, long transactionCount,
                                  double avgAmount, String windowStart, String windowEnd) {
            this.customerId = customerId;
            this.totalAmount = totalAmount;
            this.transactionCount = transactionCount;
            this.avgAmount = avgAmount;
            this.windowStart = windowStart;
            this.windowEnd = windowEnd;
        }

        @Override
        public String toString() {
            return "TransactionSummary{customerId='" + customerId
                    + "', totalAmount=" + totalAmount
                    + ", transactionCount=" + transactionCount
                    + ", avgAmount=" + String.format("%.2f", avgAmount)
                    + ", windowStart=" + windowStart
                    + ", windowEnd=" + windowEnd + "}";
        }
    }

    // ======================== 工具方法 ========================

    /**
     * 读取环境变量，若不存在则返回默认值。
     *
     * @param key          环境变量名
     * @param defaultValue 默认值
     * @return 环境变量值或默认值
     */
    private static String getEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        return value != null ? value : defaultValue;
    }
}
