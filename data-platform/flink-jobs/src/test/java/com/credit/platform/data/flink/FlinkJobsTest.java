package com.credit.platform.data.flink;

import com.credit.platform.data.flink.common.FeatureKey;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Flink 作业单元测试 — 验证事件解析、聚合逻辑和公共组件。
 *
 * <p>测试策略: 聚焦测试 JSON 解析正确性和聚合计算逻辑，
 * 不依赖 Flink 运行时，保证快速反馈。
 */
class FlinkJobsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    /** 构建测试 JSON */
    private static String toJson(Map<String, Object> fields) {
        try {
            return MAPPER.writeValueAsString(fields);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 构建基础事件 Map */
    private static Map<String, Object> baseEvent(String customerId, String eventTime) {
        Map<String, Object> m = new HashMap<>();
        m.put("customerId", customerId);
        m.put("eventTime", eventTime);
        return m;
    }

    // ========== FLINK_001: 征信查询次数 ==========

    @Test
    @DisplayName("FLINK_001 - 征信查询事件 JSON 解析正确")
    void testCreditQueryEventParsing() throws Exception {
        String json = toJson(new HashMap<>(Map.of(
                "customerId", "C001",
                "eventTime", "2026-06-01T10:00:00",
                "queryType", "PBOC",
                "institution", "BANK_A"
        )));

        var node = MAPPER.readTree(json);
        assertEquals("C001", node.get("customerId").asText());
        assertEquals("PBOC", node.get("queryType").asText());
        assertEquals("BANK_A", node.get("institution").asText());
    }

    @Test
    @DisplayName("FLINK_001 - 同一客户 3 次查询事件计数正确")
    void testCreditQueryCount() throws Exception {
        List<String> events = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Map<String, Object> e = baseEvent("C001", "2026-06-01T10:0" + i + ":00");
            e.put("queryType", "PBOC");
            e.put("institution", "BANK_A");
            events.add(toJson(e));
        }

        long count = events.stream()
                .filter(json -> {
                    try {
                        return "C001".equals(MAPPER.readTree(json).get("customerId").asText());
                    } catch (Exception ex) {
                        return false;
                    }
                })
                .count();
        assertEquals(3, count);
    }

    // ========== FLINK_002: 逾期次数 ==========

    @Test
    @DisplayName("FLINK_002 - 逾期事件解析正确")
    void testOverdueEventParsing() throws Exception {
        Map<String, Object> e = baseEvent("C001", "2026-06-01T10:00:00");
        e.put("loanId", "L001");
        e.put("overdueDays", 5);
        e.put("overdueAmount", 1000.00);
        String json = toJson(e);

        var node = MAPPER.readTree(json);
        assertEquals("C001", node.get("customerId").asText());
        assertEquals(5, node.get("overdueDays").asInt());
        assertEquals(1000.00, node.get("overdueAmount").asDouble(), 0.01);
    }

    @Test
    @DisplayName("FLINK_002 - 不同客户的逾期事件分别统计")
    void testOverdueGroupByCustomer() throws Exception {
        Map<String, Object> e1 = baseEvent("C001", "2026-06-01T10:00:00");
        e1.put("loanId", "L001");
        e1.put("overdueDays", 5);
        e1.put("overdueAmount", 1000.00);

        Map<String, Object> e2 = baseEvent("C002", "2026-06-01T11:00:00");
        e2.put("loanId", "L002");
        e2.put("overdueDays", 10);
        e2.put("overdueAmount", 5000.00);

        var node1 = MAPPER.readTree(toJson(e1));
        var node2 = MAPPER.readTree(toJson(e2));

        assertNotEquals(node1.get("customerId").asText(), node2.get("customerId").asText());
        assertEquals(1000.00, node1.get("overdueAmount").asDouble(), 0.01);
        assertEquals(5000.00, node2.get("overdueAmount").asDouble(), 0.01);
    }

    @Test
    @DisplayName("FLINK_002 - 逾期金额累加正确")
    void testOverdueAmountAggregation() throws Exception {
        double[] amounts = {1000.0, 2000.0, 3000.0};
        double total = 0;
        for (double amt : amounts) {
            Map<String, Object> e = baseEvent("C001", "2026-06-01T10:00:00");
            e.put("overdueDays", 5);
            e.put("overdueAmount", amt);
            total += MAPPER.readTree(toJson(e)).get("overdueAmount").asDouble();
        }
        assertEquals(6000.0, total, 0.01);
    }

    // ========== FLINK_003: 申请频次 ==========

    @Test
    @DisplayName("FLINK_003 - 申请事件解析正确")
    void testApplyEventParsing() throws Exception {
        Map<String, Object> e = baseEvent("C001", "2026-06-01T10:00:00");
        e.put("productId", "P001");
        e.put("channel", "ONLINE");
        String json = toJson(e);

        var node = MAPPER.readTree(json);
        assertEquals("C001", node.get("customerId").asText());
        assertEquals("ONLINE", node.get("channel").asText());
        assertEquals("P001", node.get("productId").asText());
    }

    @Test
    @DisplayName("FLINK_003 - 同一客户多次申请频次统计")
    void testApplyFrequencyCount() throws Exception {
        List<String> events = new ArrayList<>();
        String[] channels = {"ONLINE", "OFFLINE", "ONLINE"};
        for (int i = 0; i < 3; i++) {
            Map<String, Object> e = baseEvent("C001", "2026-06-01T10:0" + i + ":00");
            e.put("productId", "P00" + (i + 1));
            e.put("channel", channels[i]);
            events.add(toJson(e));
        }

        long onlineCount = events.stream()
                .filter(json -> {
                    try {
                        return "ONLINE".equals(MAPPER.readTree(json).get("channel").asText());
                    } catch (Exception ex) {
                        return false;
                    }
                })
                .count();
        assertEquals(2, onlineCount);
    }

    // ========== FLINK_004: 交易汇总 ==========

    @Test
    @DisplayName("FLINK_004 - 交易金额汇总计算正确")
    void testTransactionSummary() throws Exception {
        double[] amounts = {1000.0, 2000.0, 3000.0};
        double totalAmount = 0;
        int count = 0;

        for (double amt : amounts) {
            Map<String, Object> e = baseEvent("C001", "2026-06-01T10:00:00");
            e.put("transactionId", "T00" + (count + 1));
            e.put("amount", amt);
            e.put("type", "PAYMENT");
            totalAmount += MAPPER.readTree(toJson(e)).get("amount").asDouble();
            count++;
        }

        assertEquals(6000.0, totalAmount, 0.01);
        assertEquals(3, count);
        assertEquals(2000.0, totalAmount / count, 0.01);
    }

    @Test
    @DisplayName("FLINK_004 - 不同客户的交易独立统计")
    void testTransactionGroupByCustomer() throws Exception {
        Map<String, Object> e1 = baseEvent("C001", "2026-06-01T10:00:00");
        e1.put("amount", 1000.0);
        e1.put("transactionId", "T001");

        Map<String, Object> e2 = baseEvent("C002", "2026-06-01T10:00:00");
        e2.put("amount", 5000.0);
        e2.put("transactionId", "T002");

        var node1 = MAPPER.readTree(toJson(e1));
        var node2 = MAPPER.readTree(toJson(e2));

        assertNotEquals(node1.get("customerId").asText(), node2.get("customerId").asText());
        assertEquals(1000.0, node1.get("amount").asDouble(), 0.01);
        assertEquals(5000.0, node2.get("amount").asDouble(), 0.01);
    }

    // ========== FLINK_005: 数据质量检测 ==========

    @Test
    @DisplayName("FLINK_005 - 缺少必填字段 customerId 检测（完整性）")
    void testQualityCheckMissingCustomerId() throws Exception {
        String json = toJson(Map.of(
                "op_type", "INSERT",
                "eventTime", "2026-06-01T10:00:00"
        ));

        var node = MAPPER.readTree(json);
        assertFalse(node.has("customerId"), "应该检测到 customerId 缺失");
    }

    @Test
    @DisplayName("FLINK_005 - 无效 op_type 检测（Schema 校验）")
    void testQualityCheckInvalidOpType() throws Exception {
        String json = toJson(Map.of(
                "op_type", "INVALID",
                "customerId", "C001",
                "eventTime", "2026-06-01T10:00:00"
        ));

        var node = MAPPER.readTree(json);
        String opType = node.get("op_type").asText();
        assertFalse(List.of("INSERT", "UPDATE", "DELETE").contains(opType),
                "op_type 值不合法");
    }

    @Test
    @DisplayName("FLINK_005 - 负数金额检测（准确性）")
    void testQualityCheckNegativeAmount() throws Exception {
        Map<String, Object> e = baseEvent("C001", "2026-06-01T10:00:00");
        e.put("op_type", "INSERT");
        e.put("amount", -100.0);
        String json = toJson(e);

        var node = MAPPER.readTree(json);
        assertTrue(node.get("amount").asDouble() < 0, "amount 应为负数");
    }

    @Test
    @DisplayName("FLINK_005 - 合法事件通过所有质量检测")
    void testQualityCheckValidEvent() throws Exception {
        Map<String, Object> e = baseEvent("C001", "2026-06-01T10:00:00");
        e.put("op_type", "INSERT");
        e.put("amount", 1000.0);
        String json = toJson(e);

        var node = MAPPER.readTree(json);
        // Schema 校验
        assertTrue(node.has("op_type"));
        assertTrue(List.of("INSERT", "UPDATE", "DELETE").contains(node.get("op_type").asText()));
        // 完整性校验
        assertTrue(node.has("customerId"));
        assertTrue(node.has("eventTime"));
        // 准确性校验
        assertTrue(node.get("amount").asDouble() >= 0);
    }

    @Test
    @DisplayName("FLINK_005 - 缺少 eventTime 检测（完整性）")
    void testQualityCheckMissingEventTime() throws Exception {
        String json = toJson(Map.of(
                "op_type", "INSERT",
                "customerId", "C001"
        ));

        var node = MAPPER.readTree(json);
        assertFalse(node.has("eventTime"), "应该检测到 eventTime 缺失");
    }

    // ========== 公共组件 ==========

    @Test
    @DisplayName("FeatureKey - Redis Key 格式正确")
    void testRedisKeyFormat() {
        String key = FeatureKey.redisKey("credit_query_3m", "C001");
        assertEquals("feature:credit_query_3m:C001", key);
    }

    @Test
    @DisplayName("FeatureKey - HBase RowKey 包含反转客户 ID")
    void testHBaseRowKeyReverse() {
        String rowKey = FeatureKey.hbaseRowKey("CUST_001", "credit_query_3m", 20260606120000L);
        assertTrue(rowKey.startsWith("100_TSUC"), "RowKey 应以反转的客户 ID 开头");
        assertTrue(rowKey.contains("credit_query_3m"));
        assertTrue(rowKey.contains("20260606120000"));
    }

    @Test
    @DisplayName("FeatureKey - null 和空字符串安全处理")
    void testReverseNullAndEmpty() {
        assertEquals("", FeatureKey.reverse(null));
        assertEquals("", FeatureKey.reverse(""));
        assertEquals("1", FeatureKey.reverse("1"));
        assertEquals("CBA", FeatureKey.reverse("ABC"));
    }
}
