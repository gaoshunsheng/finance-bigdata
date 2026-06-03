package com.credit.platform.engine.core.trace;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 可解释性追踪引擎测试。
 * <p>
 * 覆盖: TraceEntry 模型、DecisionTrace 容器、DecisionTracer 追踪器、
 * TraceReporter 报告生成、TracePublisher 接口。
 * 共 ≥10 个用例，覆盖完整决策路径追踪、评分卡得分明细、规则命中追踪、
 * DAG 路径追踪、四级报告生成、审计级快照。
 * </p>
 */
@DisplayName("可解释性追踪引擎")
class TraceEngineTest {

    // ========== 1. TraceEntry 数据模型测试 ==========

    @Nested
    @DisplayName("TraceEntry 数据模型")
    class TraceEntryTest {

        @Test
        @DisplayName("1. TraceEntry 构建与不可变性")
        void traceEntry_buildAndImmutable() {
            TraceEntry entry = TraceEntry.builder()
                .traceId("trace-001")
                .nodeId("scorecard_01")
                .nodeType(NodeType.SCORECARD)
                .startTimeMs(1000)
                .endTimeMs(1012)
                .durationMs(12)
                .inputSnapshot(Map.of("age", 25, "income", 50000))
                .outputSnapshot(Map.of("score", 720, "result", "PASS"))
                .success(true)
                .build();

            assertEquals("trace-001", entry.getTraceId());
            assertEquals("scorecard_01", entry.getNodeId());
            assertEquals(NodeType.SCORECARD, entry.getNodeType());
            assertEquals(12, entry.getDurationMs());
            assertTrue(entry.isSuccess());
            assertNull(entry.getErrorMessage());
            assertEquals(Map.of("age", 25, "income", 50000), entry.getInputSnapshot());
            assertEquals(Map.of("score", 720, "result", "PASS"), entry.getOutputSnapshot());

            // 不可变性验证
            assertThrows(UnsupportedOperationException.class,
                () -> entry.getInputSnapshot().put("new", "val"));
        }

        @Test
        @DisplayName("2. TraceEntry fromStart + end 模式")
        void traceEntry_fromStartAndEnd() throws InterruptedException {
            TraceEntry entry = TraceEntry.fromStart("trace-002", "rule_01", NodeType.RULE_SET)
                .inputSnapshot(Map.of("age", 30))
                .end()
                .outputSnapshot(Map.of("hit", true))
                .build();

            assertEquals("trace-002", entry.getTraceId());
            assertEquals("rule_01", entry.getNodeId());
            assertEquals(NodeType.RULE_SET, entry.getNodeType());
            assertTrue(entry.getStartTimeMs() > 0);
            assertTrue(entry.getEndTimeMs() >= entry.getStartTimeMs());
            assertEquals(entry.getEndTimeMs() - entry.getStartTimeMs(), entry.getDurationMs());
        }

        @Test
        @DisplayName("3. TraceEntry 错误记录")
        void traceEntry_errorRecord() {
            TraceEntry entry = TraceEntry.builder()
                .traceId("trace-003")
                .nodeId("model_01")
                .nodeType(NodeType.MODEL)
                .startTimeMs(1000)
                .end()
                .success(false)
                .errorMessage("Model timeout")
                .build();

            assertFalse(entry.isSuccess());
            assertEquals("Model timeout", entry.getErrorMessage());
        }

        @Test
        @DisplayName("4. TraceEntry details 动态添加")
        void traceEntry_addDetail() {
            TraceEntry entry = TraceEntry.builder()
                .traceId("trace-004")
                .nodeId("scorecard_01")
                .nodeType(NodeType.SCORECARD)
                .addDetail("scoreDelta", 45)
                .addDetail("binLabel", "25-35")
                .build();

            assertEquals(45, entry.getDetails().get("scoreDelta"));
            assertEquals("25-35", entry.getDetails().get("binLabel"));
        }
    }

    // ========== 5. DecisionTracer 追踪器测试 ==========

    @Nested
    @DisplayName("DecisionTracer 追踪器")
    class DecisionTracerTest {

        @Test
        @DisplayName("5. 完整决策路径追踪 — begin/end 模式")
        void tracer_beginEndPattern() {
            DecisionTracer tracer = DecisionTracer.create("decision-001", "strategy-v1");

            // 模拟数据准备节点
            tracer.beginNode("data_prep_01", NodeType.DATA_PREP);
            tracer.captureInput("age", 25, "income", 50000);
            tracer.endNode(Map.of("variablesLoaded", 5));

            // 模拟规则集节点
            tracer.beginNode("rule_blacklist", NodeType.RULE_SET);
            tracer.endNode(Map.of("hit", true, "matchedCount", 2, "totalEvaluated", 5));

            // 模拟评分卡节点
            tracer.beginNode("scorecard_01", NodeType.SCORECARD);
            tracer.endNode(Map.of("initialScore", 650, "finalScore", 720, "result", "PASS"));

            DecisionTrace trace = tracer.finish("PASS", 720, null, null);

            assertEquals(3, trace.getEntryCount());
            assertEquals(List.of("data_prep_01", "rule_blacklist", "scorecard_01"),
                trace.getDecisionPath());
            assertEquals("PASS", trace.getFinalResult());
            assertEquals(720, trace.getFinalScore());
            assertTrue(trace.getTotalDurationMs() >= 0);
        }

        @Test
        @DisplayName("6. 快捷方法 — 规则集 + 评分卡 + 变量解析")
        void tracer_shortcutMethods() {
            DecisionTracer tracer = DecisionTracer.create("decision-002", "strategy-v1");

            tracer.recordRuleSet("rule_blacklist", true, 10, 3);
            tracer.recordScorecard("scorecard_01", 650, 720, "PASS");
            tracer.recordVariableResolve("derived_ratio", "DERIVED", 0.85, 2);

            DecisionTrace trace = tracer.finish("PASS", 720, null, null);

            assertEquals(3, trace.getEntryCount());
            // recordRuleSet 和 recordScorecard 添加到决策路径，recordVariableResolve 不添加
            assertEquals(2, trace.getDecisionPath().size());

            // 验证规则集追踪
            TraceEntry ruleEntry = trace.getEntryByNodeId("rule_blacklist");
            assertNotNull(ruleEntry);
            assertEquals(NodeType.RULE_SET, ruleEntry.getNodeType());
            assertTrue((Boolean) ruleEntry.getOutputSnapshot().get("hit"));

            // 验证评分卡追踪
            TraceEntry scorecardEntry = trace.getEntryByNodeId("scorecard_01");
            assertNotNull(scorecardEntry);
            assertEquals(NodeType.SCORECARD, scorecardEntry.getNodeType());
            assertEquals(720, scorecardEntry.getOutputSnapshot().get("finalScore"));
        }

        @Test
        @DisplayName("7. 错误追踪 — 节点执行失败")
        void tracer_errorTracking() {
            DecisionTracer tracer = DecisionTracer.create("decision-003", "strategy-v1");

            tracer.beginNode("model_01", NodeType.MODEL);
            tracer.captureInput(Map.of("features", 10));
            tracer.endNodeWithError("Model service timeout after 3000ms");

            DecisionTrace trace = tracer.finish("REVIEW", null, "模型服务超时", "MODEL_TIMEOUT");

            assertEquals(1, trace.getEntryCount());
            TraceEntry errorEntry = trace.getEntries().get(0);
            assertFalse(errorEntry.isSuccess());
            assertEquals("Model service timeout after 3000ms", errorEntry.getErrorMessage());
            assertEquals("REVIEW", trace.getFinalResult());
            assertEquals("模型服务超时", trace.getRejectReason());
            assertEquals("MODEL_TIMEOUT", trace.getRejectCode());
        }

        @Test
        @DisplayName("8. 评分卡得分明细追踪")
        void tracer_scorecardBreakdown() {
            DecisionTracer tracer = DecisionTracer.create("decision-004", "strategy-v1");

            Map<String, Object> breakdown = new LinkedHashMap<>();
            breakdown.put("age_score", 15);
            breakdown.put("income_score", 20);
            breakdown.put("credit_history_score", 10);
            breakdown.put("scoreDelta", 45);

            tracer.recordScorecard("scorecard_credit", 650, 695, "PASS", breakdown);

            DecisionTrace trace = tracer.finish("PASS", 695, null, null);

            TraceEntry entry = trace.getEntryByNodeId("scorecard_credit");
            assertNotNull(entry);
            assertEquals(45, entry.getDetails().get("scoreDelta"));
            assertEquals(15, entry.getDetails().get("age_score"));
            assertEquals(20, entry.getDetails().get("income_score"));
        }
    }

    // ========== 9-10. TraceReporter 四级报告测试 ==========

    @Nested
    @DisplayName("TraceReporter 四级报告")
    class TraceReporterTest {

        private DecisionTrace createSampleTrace() {
            DecisionTracer tracer = DecisionTracer.create("decision-005", "strategy-v1");

            tracer.beginNode("data_prep", NodeType.DATA_PREP);
            tracer.captureInput("age", 25, "income", 30000, "credit_history", 6);
            tracer.endNode(Map.of("loaded", true));

            tracer.recordRuleSet("rule_blacklist", true, 10, 2);
            tracer.recordScorecard("scorecard_risk", 500, 420, "REJECT");

            return tracer.finish("REJECT", 420, "评分过低", "SCORE_TOO_LOW");
        }

        @Test
        @DisplayName("9. L1 规则级报告 — 业务用户")
        void reporter_L1_ruleLevel() {
            DecisionReport report = TraceReporter.generateReport(
                createSampleTrace(), TraceLevel.RULE);

            assertEquals("REJECT", report.getFinalResult());
            assertEquals(420, report.getFinalScore());
            assertEquals("评分过低", report.getRejectReason());
            assertEquals("SCORE_TOO_LOW", report.getRejectCode());
            assertFalse(report.getNodeReports().isEmpty());

            // L1 不包含决策路径
            assertTrue(report.getDecisionPath().isEmpty());

            // L1 不包含 inputSnapshot
            for (DecisionReport.NodeReport node : report.getNodeReports()) {
                assertTrue(node.getInputSnapshot().isEmpty());
            }
        }

        @Test
        @DisplayName("10. L2 流程级报告 — 策略分析师")
        void reporter_L2_flowLevel() {
            DecisionReport report = TraceReporter.generateReport(
                createSampleTrace(), TraceLevel.FLOW);

            // L2 包含决策路径
            assertFalse(report.getDecisionPath().isEmpty());
            assertTrue(report.getDecisionPath().contains("data_prep"));
            assertTrue(report.getDecisionPath().contains("rule_blacklist"));
            assertTrue(report.getDecisionPath().contains("scorecard_risk"));

            // L2 包含耗时
            for (DecisionReport.NodeReport node : report.getNodeReports()) {
                assertTrue(node.getDurationMs() >= 0);
            }
        }

        @Test
        @DisplayName("11. L3 模型级报告 — 模型工程师")
        void reporter_L3_modelLevel() {
            DecisionReport report = TraceReporter.generateReport(
                createSampleTrace(), TraceLevel.MODEL);

            // L3 包含 details
            boolean hasDetails = report.getNodeReports().stream()
                .anyMatch(n -> !n.getDetails().isEmpty());
            assertTrue(hasDetails, "L3 should include node details");
        }

        @Test
        @DisplayName("12. L4 审计级报告 — 合规人员 (完整快照)")
        void reporter_L4_auditLevel() {
            DecisionReport report = TraceReporter.generateReport(
                createSampleTrace(), TraceLevel.AUDIT);

            // L4 包含完整 summary
            assertNotNull(report.getSummary());
            assertTrue(report.getSummary().containsKey("totalNodes"));

            // L4 包含输入快照
            boolean hasInputSnapshot = report.getNodeReports().stream()
                .anyMatch(n -> !n.getInputSnapshot().isEmpty());
            assertTrue(hasInputSnapshot, "L4 should include input snapshots");

            // L4 包含时间信息
            assertTrue(report.getSummary().containsKey("startTimeMs"));
            assertTrue(report.getSummary().containsKey("totalDurationMs"));
        }

        @Test
        @DisplayName("13. JSON Map 输出 — L4 审计级")
        void reporter_toJsonMap_audit() {
            DecisionReport report = TraceReporter.generateReport(
                createSampleTrace(), TraceLevel.AUDIT);
            Map<String, Object> json = TraceReporter.toJsonMap(report, TraceLevel.AUDIT);

            assertEquals("REJECT", json.get("finalResult"));
            assertEquals(420, json.get("finalScore"));
            assertEquals("评分过低", json.get("rejectReason"));
            assertNotNull(json.get("decisionPath"));
            assertNotNull(json.get("nodes"));
            assertNotNull(json.get("topFactors"));
            assertNotNull(json.get("summary"));

            // 验证节点列表包含审计级字段
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> nodes = (List<Map<String, Object>>) json.get("nodes");
            boolean hasInputSnapshot = nodes.stream()
                .anyMatch(n -> n.containsKey("inputSnapshot"));
            assertTrue(hasInputSnapshot, "Audit level JSON should include inputSnapshot");
        }

        @Test
        @DisplayName("14. 贡献因子排序 — 评分卡 + 规则集")
        void reporter_contributionFactors() {
            DecisionReport report = TraceReporter.generateReport(
                createSampleTrace(), TraceLevel.FLOW);

            List<DecisionReport.ContributionFactor> factors = report.getTopFactors();
            assertFalse(factors.isEmpty());

            // 验证按影响度降序
            for (int i = 1; i < factors.size(); i++) {
                assertTrue(
                    factors.get(i - 1).getInfluence() >= factors.get(i).getInfluence(),
                    "Factors should be sorted by influence descending"
                );
            }
        }
    }

    // ========== 15. TracePublisher 接口测试 ==========

    @Nested
    @DisplayName("TracePublisher 接口")
    class TracePublisherTest {

        @Test
        @DisplayName("15. NoopTracePublisher 不抛异常")
        void noopPublisher_noException() {
            TracePublisher publisher = NoopTracePublisher.getInstance();
            DecisionTrace trace = DecisionTrace.create("trace-noop", "dec-001", "strat-v1");
            assertDoesNotThrow(() -> publisher.publish(trace));
        }

        @Test
        @DisplayName("16. LoggingTracePublisher 不阻塞不抛异常")
        void loggingPublisher_noBlockNoException() {
            TracePublisher publisher = LoggingTracePublisher.getInstance();
            DecisionTracer tracer = DecisionTracer.create("decision-006", "strategy-v1");
            tracer.recordRuleSet("rule_test", false, 5, 0);
            DecisionTrace trace = tracer.finish("PASS", 700, null, null);
            assertDoesNotThrow(() -> publisher.publish(trace));
        }

        @Test
        @DisplayName("17. 自定义 TracePublisher — lambda 函数式接口")
        void customPublisher_functionalInterface() {
            final boolean[] published = {false};
            TracePublisher customPublisher = t -> published[0] = true;

            DecisionTrace trace = DecisionTrace.create("trace-custom", "dec-001", "strat-v1");
            customPublisher.publish(trace);

            assertTrue(published[0], "Custom publisher should have been called");
        }
    }

    // ========== 18. DecisionTrace 容器查询测试 ==========

    @Nested
    @DisplayName("DecisionTrace 查询")
    class DecisionTraceQueryTest {

        @Test
        @DisplayName("18. 按类型和节点ID查询")
        void trace_queryByTypeAndNodeId() {
            DecisionTracer tracer = DecisionTracer.create("decision-007", "strategy-v1");
            tracer.recordRuleSet("rule_01", true, 10, 3);
            tracer.recordRuleSet("rule_02", false, 8, 0);
            tracer.recordScorecard("scorecard_01", 500, 650, "PASS");
            tracer.recordVariableResolve("age", "INPUT", 25, 0);

            DecisionTrace trace = tracer.finish("PASS", 650, null, null);

            // 按类型查询
            List<TraceEntry> ruleEntries = trace.getEntriesByType(NodeType.RULE_SET);
            assertEquals(2, ruleEntries.size());

            List<TraceEntry> scorecardEntries = trace.getEntriesByType(NodeType.SCORECARD);
            assertEquals(1, scorecardEntries.size());

            List<TraceEntry> varEntries = trace.getEntriesByType(NodeType.VARIABLE_RESOLVE);
            assertEquals(1, varEntries.size());

            // 按节点 ID 查询
            TraceEntry entry = trace.getEntryByNodeId("rule_01");
            assertNotNull(entry);
            assertTrue((Boolean) entry.getOutputSnapshot().get("hit"));

            assertNull(trace.getEntryByNodeId("non_existent"));
        }

        @Test
        @DisplayName("19. DAG 决策流完整路径追踪")
        void trace_dagFullDecisionPath() {
            DecisionTracer tracer = DecisionTracer.create("decision-008", "strategy-v1");

            // 模拟 DAG 决策流: data_prep → rule_blacklist → scorecard → action
            tracer.recordDecisionPath("data_prep");
            tracer.recordRuleSet("rule_blacklist", false, 10, 0);
            tracer.recordScorecard("scorecard_credit", 500, 720, "PASS");
            tracer.recordDecisionPath("action_approve");

            DecisionTrace trace = tracer.finish("PASS", 720, null, null);

            // 决策路径包含 DAG 路径节点和引擎追踪节点
            List<String> path = trace.getDecisionPath();
            assertTrue(path.contains("data_prep"));
            assertTrue(path.contains("rule_blacklist"));
            assertTrue(path.contains("scorecard_credit"));
            assertTrue(path.contains("action_approve"));
        }

        @Test
        @DisplayName("20. 元数据追踪")
        void trace_metadata() {
            DecisionTracer tracer = DecisionTracer.create("decision-009", "strategy-v1");
            tracer.addMetadata("requestIp", "192.168.1.1");
            tracer.addMetadata("userId", "user-123");
            tracer.addMetadata("channel", "APP");

            DecisionTrace trace = tracer.getTrace();
            assertEquals("192.168.1.1", trace.getMetadata().get("requestIp"));
            assertEquals("user-123", trace.getMetadata().get("userId"));
            assertEquals("APP", trace.getMetadata().get("channel"));
        }
    }

    // ========== TraceLevel 级别测试 ==========

    @Test
    @DisplayName("21. TraceLevel 包含关系验证")
    void traceLevel_includes() {
        // AUDIT 包含所有
        assertTrue(TraceLevel.AUDIT.includes(TraceLevel.RULE));
        assertTrue(TraceLevel.AUDIT.includes(TraceLevel.FLOW));
        assertTrue(TraceLevel.AUDIT.includes(TraceLevel.MODEL));
        assertTrue(TraceLevel.AUDIT.includes(TraceLevel.AUDIT));

        // FLOW 包含 RULE 和 FLOW，不包含 MODEL 和 AUDIT
        assertTrue(TraceLevel.FLOW.includes(TraceLevel.RULE));
        assertTrue(TraceLevel.FLOW.includes(TraceLevel.FLOW));
        assertFalse(TraceLevel.FLOW.includes(TraceLevel.MODEL));
        assertFalse(TraceLevel.FLOW.includes(TraceLevel.AUDIT));

        // RULE 只包含自己
        assertTrue(TraceLevel.RULE.includes(TraceLevel.RULE));
        assertFalse(TraceLevel.RULE.includes(TraceLevel.FLOW));
    }

    @Test
    @DisplayName("22. NodeType 枚举完整性")
    void nodeType_allTypes() {
        NodeType[] types = NodeType.values();
        assertTrue(types.length >= 10, "Should have at least 10 node types");
        assertEquals("规则集", NodeType.RULE_SET.getDescription());
        assertEquals("评分卡", NodeType.SCORECARD.getDescription());
        assertEquals("决策表", NodeType.DECISION_TABLE.getDescription());
        assertEquals("决策树", NodeType.DECISION_TREE.getDescription());
        assertEquals("决策流", NodeType.DECISION_FLOW.getDescription());
        assertEquals("变量解析", NodeType.VARIABLE_RESOLVE.getDescription());
    }

    // ========== 端到端集成测试 ==========

    @Test
    @DisplayName("23. 端到端 — 拒绝决策完整追踪 + 报告")
    void e2e_rejectDecisionWithFullTraceAndReport() {
        // 1. 创建追踪器
        DecisionTracer tracer = DecisionTracer.create("decision-e2e", "anti-fraud-v2");
        tracer.addMetadata("channel", "MOBILE_APP");

        // 2. 数据准备
        tracer.beginNode("data_prep", NodeType.DATA_PREP);
        tracer.captureInput(Map.of("age", 20, "income", 15000, "loanAmount", 500000));
        tracer.endNode(Map.of("variablesLoaded", 3));

        // 3. 规则集: 黑名单命中
        tracer.beginNode("rule_blacklist", NodeType.RULE_SET);
        tracer.captureInput("userId", "user-999");
        tracer.endNode(Map.of("hit", true, "matchedCount", 2, "totalEvaluated", 10));

        // 4. 评分卡: 低分
        Map<String, Object> breakdown = new LinkedHashMap<>();
        breakdown.put("age_bin", "18-25 (低龄)");
        breakdown.put("age_score", -20);
        breakdown.put("income_bin", "<20k (低收入)");
        breakdown.put("income_score", -30);
        breakdown.put("scoreDelta", -50);
        tracer.recordScorecard("scorecard_risk", 500, 450, "REJECT", breakdown);

        // 5. 完成决策
        DecisionTrace trace = tracer.finish("REJECT", 450, "黑名单命中+评分过低", "BLACKLIST_SCORE");

        // 6. 生成审计级报告
        DecisionReport report = TraceReporter.generateReport(trace, TraceLevel.AUDIT);
        Map<String, Object> json = TraceReporter.toJsonMap(report, TraceLevel.AUDIT);

        // 7. 验证
        assertEquals("REJECT", report.getFinalResult());
        assertEquals(450, report.getFinalScore());
        assertEquals("黑名单命中+评分过低", report.getRejectReason());
        assertEquals("BLACKLIST_SCORE", report.getRejectCode());
        assertEquals(3, report.getNodeReports().size());
        assertFalse(report.getDecisionPath().isEmpty());
        assertFalse(report.getTopFactors().isEmpty());

        // 验证 JSON
        assertEquals("REJECT", json.get("finalResult"));
        assertEquals(450, json.get("finalScore"));
        assertEquals("黑名单命中+评分过低", json.get("rejectReason"));

        // 8. 异步发布
        final boolean[] published = {false};
        TracePublisher publisher = t -> published[0] = true;
        publisher.publish(trace);
        assertTrue(published[0]);
    }
}
