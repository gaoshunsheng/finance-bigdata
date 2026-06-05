package com.credit.platform.data.flink.quality;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Collector;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * FLINK_005: 数据质量实时检测作业
 *
 * <p>功能说明:
 * <ul>
 *   <li>从 Kafka topic "cdc_all_events" 消费 CDC 变更事件（订阅所有 CDC Topic）</li>
 *   <li>逐条对每条事件执行质量规则检测（无窗口，纯事件驱动）</li>
 *   <li>检测规则包括:
 *     <ol>
 *       <li>完整性: 必填字段不为空（customerId, eventTime）</li>
 *       <li>准确性: amount >= 0, overdueDays >= 0</li>
 *       <li>Schema 校验: 必须包含 "op_type" 字段且值为 INSERT/UPDATE/DELETE</li>
 *     </ol>
 *   </li>
 *   <li>违反规则时输出 {@link QualityViolation} 对象</li>
 *   <li>当前 Sink 为 print（后续可对接 Elasticsearch 进行质量告警）</li>
 * </ul>
 *
 * <p>本作业不使用窗口操作，每条事件独立检测，适合实时数据质量监控场景。
 */
public class DataQualityCheckJob {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        OBJECT_MAPPER.registerModule(new JavaTimeModule());
    }

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"));

    /** 合法的 op_type 取值集合 */
    private static final Set<String> VALID_OP_TYPES = new HashSet<>(
            Arrays.asList("INSERT", "UPDATE", "DELETE")
    );

    /** 必填字段列表 — 完整性检查 */
    private static final List<String> REQUIRED_FIELDS = Arrays.asList("customerId", "eventTime");

    /** 数值型非负字段列表 — 准确性检查 */
    private static final List<String> NON_NEGATIVE_FIELDS = Arrays.asList("amount", "overdueDays");

    public static void main(String[] args) throws Exception {
        // 1. 创建流执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // 2. 构建 Kafka Source — 订阅所有 CDC Topic
        KafkaSource<String> source = KafkaSource.<String>builder()
                .setBootstrapServers(getEnv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"))
                .setGroupId("flink-data-quality-check")
                .setTopics(getEnv("KAFKA_TOPIC", "cdc_all_events"))
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        // 3. 从 Source 创建 DataStream（无需 Watermark，无窗口操作）
        DataStream<String> rawStream = env.fromSource(
                source,
                WatermarkStrategy.noWatermarks(),
                "cdc-quality-source"
        );

        // 4. 逐条解析 JSON 并执行质量检测
        SingleOutputStreamOperator<QualityViolation> violationStream = rawStream
                .flatMap(DataQualityCheckJob::checkQuality)
                .name("quality-check-rules");

        // 5. 输出违规记录（print 为占位，后续对接 Elasticsearch）
        violationStream.print("QualityViolation");

        // 6. 执行作业
        env.execute("FLINK_005 - Data Quality Check Job");
    }

    // ======================== 质量检测逻辑 ========================

    /**
     * 对单条 JSON 事件执行全部质量规则检测，输出所有违规记录。
     *
     * <p>规则检测顺序:
     * <ol>
     *   <li>Schema 校验: op_type 字段是否存在且取值合法</li>
     *   <li>完整性: 必填字段（customerId, eventTime）不为空</li>
     *   <li>准确性: 数值型字段（amount, overdueDays）必须 >= 0</li>
     * </ol>
     *
     * @param jsonStr 原始 JSON 字符串
     * @param out     违规记录收集器
     */
    private static void checkQuality(String jsonStr, Collector<QualityViolation> out) {
        String timestamp = FORMATTER.format(Instant.now());

        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(jsonStr);
        } catch (Exception e) {
            // JSON 解析失败 — 记录 Schema 违规
            out.collect(new QualityViolation(
                    "Schema校验",
                    "payload",
                    "JSON解析失败: " + e.getMessage(),
                    jsonStr,
                    timestamp
            ));
            return;
        }

        // ---- 规则1: Schema 校验 — op_type 必须存在且值合法 ----
        JsonNode opTypeNode = root.get("op_type");
        if (opTypeNode == null || opTypeNode.isNull()) {
            out.collect(new QualityViolation(
                    "Schema校验",
                    "op_type",
                    "缺少必填字段 op_type，合法值为: INSERT/UPDATE/DELETE",
                    jsonStr,
                    timestamp
            ));
        } else {
            String opType = opTypeNode.asText();
            if (!VALID_OP_TYPES.contains(opType)) {
                out.collect(new QualityViolation(
                        "Schema校验",
                        "op_type",
                        "op_type 值非法: '" + opType + "'，合法值为: INSERT/UPDATE/DELETE",
                        jsonStr,
                        timestamp
                ));
            }
        }

        // ---- 规则2: 完整性检查 — 必填字段不为空 ----
        for (String field : REQUIRED_FIELDS) {
            JsonNode fieldNode = root.get(field);
            if (fieldNode == null || fieldNode.isNull() || fieldNode.asText().isEmpty()) {
                out.collect(new QualityViolation(
                        "完整性检查",
                        field,
                        "必填字段 '" + field + "' 为空或缺失",
                        jsonStr,
                        timestamp
                ));
            }
        }

        // ---- 规则3: 准确性检查 — 数值型字段必须 >= 0 ----
        for (String field : NON_NEGATIVE_FIELDS) {
            JsonNode fieldNode = root.get(field);
            if (fieldNode != null && !fieldNode.isNull() && fieldNode.isNumber()) {
                double value = fieldNode.asDouble();
                if (value < 0) {
                    out.collect(new QualityViolation(
                            "准确性检查",
                            field,
                            "字段 '" + field + "' 值为 " + value + "，不满足 >= 0 的约束",
                            jsonStr,
                            timestamp
                    ));
                }
            }
        }
    }

    // ======================== 内部 POJO ========================

    /**
     * 数据质量违规记录 POJO — 描述一条质量规则违反的详细信息。
     *
     * <p>字段说明:
     * <ul>
     *   <li>ruleName — 违反的规则名称（如 完整性检查、准确性检查、Schema校验）</li>
     *   <li>fieldName — 违反规则的字段名</li>
     *   <li>violationDetail — 违规详情描述</li>
     *   <li>originalPayload — 原始 JSON 消息体（用于追溯）</li>
     *   <li>timestamp — 检测时间（北京时间）</li>
     * </ul>
     */
    public static class QualityViolation {
        public String ruleName;
        public String fieldName;
        public String violationDetail;
        public String originalPayload;
        public String timestamp;

        public QualityViolation() {
        }

        public QualityViolation(String ruleName, String fieldName, String violationDetail,
                                String originalPayload, String timestamp) {
            this.ruleName = ruleName;
            this.fieldName = fieldName;
            this.violationDetail = violationDetail;
            this.originalPayload = originalPayload;
            this.timestamp = timestamp;
        }

        @Override
        public String toString() {
            // 截断原始 payload 以避免日志过长
            String truncatedPayload = originalPayload != null && originalPayload.length() > 200
                    ? originalPayload.substring(0, 200) + "..."
                    : originalPayload;
            return "QualityViolation{rule='" + ruleName
                    + "', field='" + fieldName
                    + "', detail='" + violationDetail
                    + "', timestamp=" + timestamp
                    + ", payload=" + truncatedPayload + "}";
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
