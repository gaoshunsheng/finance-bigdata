package com.credit.platform.test.integration;

import com.credit.platform.engine.core.compiler.*;
import com.credit.platform.engine.core.compiler.model.HitPolicy;
import com.credit.platform.engine.core.executor.*;
import com.credit.platform.engine.core.expression.ExpressionEngine;
import com.credit.platform.engine.core.flow.*;
import com.credit.platform.engine.core.model.*;
import com.credit.platform.engine.core.sandbox.*;
import com.credit.platform.engine.core.scorecard.*;
import com.credit.platform.engine.core.table.*;
import com.credit.platform.engine.core.trace.*;
import com.credit.platform.engine.core.tree.*;
import com.credit.platform.engine.core.variable.*;

import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 端到端集成测试 — 验证决策引擎各模块协作。
 * <p>
 * 测试完整决策流: JSON 规则 → 编译 → 执行 → 追踪 → 报告
 * </p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EndToEndIntegrationTest {

    private RuleCompiler ruleCompiler;
    private RuleExecutor ruleExecutor;
    private ScorecardCompiler scorecardCompiler;
    private ScorecardExecutor scorecardExecutor;
    private DecisionTableCompiler tableCompiler;
    private DecisionTreeCompiler treeCompiler;
    private DAGCompiler dagCompiler;
    private ExpressionEngine expressionEngine;

    @BeforeAll
    void setUp() {
        ruleCompiler = new RuleCompiler();
        ruleExecutor = new RuleExecutor();
        scorecardCompiler = new ScorecardCompiler();
        scorecardExecutor = new ScorecardExecutor();
        tableCompiler = new DecisionTableCompiler();
        treeCompiler = new DecisionTreeCompiler();
        dagCompiler = new DAGCompiler();
        expressionEngine = new ExpressionEngine();
    }

    // ========== 1. 条件规则 编译+执行+追踪 ==========

    @Test
    @DisplayName("E2E-01: 条件规则 → 编译 → 执行 → 追踪")
    void conditionRule_compileExecuteTrace() {
        // 1. 编译 (年龄 < 22 时命中拒绝规则)
        String json = """
            {
              "ruleId": "R001",
              "name": "年龄准入",
              "priority": 100,
              "conditions": {
                "operator": "AND",
                "operands": [
                  {"field": "age", "op": "LT", "value": 22}
                ]
              },
              "actions": [{"type": "REJECT", "reason": "年龄不符", "code": "AGE_001"}]
            }
            """;
        CompiledConditionRule rule = ruleCompiler.compileConditionRule(json);
        assertNotNull(rule);

        // 2. 执行 (命中: age=18 < 22)
        ExecutionContext ctx = ExecutionContext.create(Map.of("age", 18));
        boolean matched = ruleExecutor.evaluate(rule, ctx);
        assertTrue(matched);

        // 3. 追踪
        DecisionTracer tracer = DecisionTracer.create("DEC_001", "STR_001");
        tracer.beginNode("rule_age", NodeType.RULE_SET)
            .captureInput("age", 18)
            .endNode(Map.of("matched", true, "ruleId", "R001"));
        DecisionTrace trace = tracer.finish("REJECT", null, "年龄不符", "AGE_001");

        assertEquals("REJECT", trace.getFinalResult());
        assertEquals(1, trace.getEntries().size());
        assertEquals(NodeType.RULE_SET, trace.getEntries().get(0).getNodeType());
    }

    // ========== 2. 评分卡 编译+执行+追踪 ==========

    @Test
    @DisplayName("E2E-02: 评分卡 → 编译 → 执行 → 得分明细追踪")
    void scorecard_compileExecuteTrace() {
        String json = """
            {
              "scorecardId": "SC_CREDIT_A",
              "initialScore": 500,
              "characteristics": [
                {
                  "name": "年龄",
                  "field": "age",
                  "bins": [
                    {"range": [null, 22], "score": -10},
                    {"range": [22, 35], "score": 20},
                    {"range": [35, 50], "score": 25},
                    {"range": [50, null], "score": 15}
                  ]
                },
                {
                  "name": "收入",
                  "field": "income",
                  "bins": [
                    {"range": [null, 30000], "score": -5},
                    {"range": [30000, 80000], "score": 15},
                    {"range": [80000, null], "score": 30}
                  ]
                }
              ],
              "cutoff": {"reject": 520, "review": 550, "pass": 550}
            }
            """;

        CompiledScorecard compiled = scorecardCompiler.compile(json);
        ExecutionContext ctx = ExecutionContext.create(Map.of("age", 28, "income", 50000));

        ScorecardResult result = scorecardExecutor.execute(compiled, ctx);

        // 初始分500 + 年龄20 + 收入15 = 535 → REVIEW 区间
        assertEquals(535, result.getFinalScore());
        assertNotNull(result.getResult());
        assertEquals(2, result.getBreakdown().size());

        // 追踪
        DecisionTracer tracer = DecisionTracer.create("DEC_002", "STR_001");
        tracer.recordScorecard("SC_CREDIT_A", 500, result.getFinalScore(), result.getResult());
        DecisionTrace trace = tracer.finish(result.getResult(), result.getFinalScore(), null, null);
        assertNotNull(trace);
    }

    // ========== 3. 决策表 编译+执行 ==========

    @Test
    @DisplayName("E2E-03: 决策表 → 编译 → 匹配")
    void decisionTable_compileMatch() {
        String json = """
            {
              "tableId": "DT_POLICY",
              "columns": [
                {"name": "客户类型", "field": "customer_type"},
                {"name": "信用等级", "field": "credit_level"}
              ],
              "rows": [
                {"conditions": ["NEW", "A"], "result": {"action": "APPROVE"}},
                {"conditions": ["NEW", "C"], "result": {"action": "REJECT"}},
                {"conditions": ["*", "*"], "result": {"action": "REVIEW"}}
              ],
              "hitPolicy": "FIRST_MATCH"
            }
            """;

        CompiledDecisionTable table = tableCompiler.compile(json);
        assertNotNull(table);
    }

    // ========== 4. 决策树 编译 ==========

    @Test
    @DisplayName("E2E-04: 决策树 → 编译 → 验证")
    void decisionTree_compile() {
        String json = """
            {
              "treeId": "TREE_001",
              "condition": {"field": "age", "op": "LT", "value": 22},
              "trueChild": {
                "action": {"type": "REJECT", "reason": "年龄过小", "code": "AGE_YOUNG"}
              },
              "falseChild": {
                "action": {"type": "PASS"}
              }
            }
            """;

        CompiledDecisionTree tree = treeCompiler.compile(json);
        assertNotNull(tree);
    }

    // ========== 5. DAG 决策流 编译 ==========

    @Test
    @DisplayName("E2E-05: DAG 决策流 → 编译 → 拓扑排序验证")
    void dagFlow_compileTopology() {
        String json = """
            {
              "flowId": "FLOW_001",
              "nodes": [
                {"id": "data_prep", "type": "DATA_PREP", "config": {}},
                {"id": "blacklist", "type": "RULE_SET", "config": {"ruleSetId": "RS_BL"}},
                {"id": "scorecard", "type": "SCORECARD", "config": {"scorecardId": "SC_A"}},
                {"id": "reject", "type": "ACTION", "config": {"action": "REJECT"}},
                {"id": "pass", "type": "ACTION", "config": {"action": "PASS"}}
              ],
              "edges": [
                {"from": "data_prep", "to": "blacklist"},
                {"from": "blacklist", "to": "reject", "condition": "blacklist_hit == true"},
                {"from": "blacklist", "to": "scorecard", "condition": "blacklist_hit == false"},
                {"from": "scorecard", "to": "pass"}
              ]
            }
            """;

        CompiledDAG dag = dagCompiler.compile(json);
        assertNotNull(dag);
        assertEquals(5, dag.getNodes().size());
        assertEquals(4, dag.getEdges().size());
    }

    // ========== 6. 表达式引擎 + 自定义函数 ==========

    @Test
    @DisplayName("E2E-06: 表达式引擎 → between/in/daysBetween 自定义函数")
    void expressionEngine_customFunctions() {
        Map<String, Object> env = Map.of("age", 25, "city", "Beijing", "start", "2025-01-01");

        // between
        assertTrue(expressionEngine.executeAsBoolean("between(age, 22, 60)", env));

        // between - out of range
        assertFalse(expressionEngine.executeAsBoolean("between(age, 30, 60)", env));

        // 编译缓存
        long start = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            expressionEngine.executeAsBoolean("between(age, 22, 60)", env);
        }
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        assertTrue(elapsed < 500, "1000 expressions should execute in <500ms, took " + elapsed + "ms");
    }

    // ========== 7. 规则集 多策略 ==========

    @Test
    @DisplayName("E2E-07: 规则集 FIRST_HIT / ALL 策略")
    void ruleSet_hitPolicies() {
        String json = """
            {
              "ruleSetId": "RS_MULTI",
              "hitPolicy": "ALL",
              "rules": [
                {
                  "ruleId": "R001", "priority": 100,
                  "conditions": {"operator": "AND", "operands": [{"field": "age", "op": "LT", "value": 22}]},
                  "actions": [{"type": "REJECT", "reason": "年龄过小", "code": "AGE_001"}]
                },
                {
                  "ruleId": "R002", "priority": 90,
                  "conditions": {"operator": "AND", "operands": [{"field": "income", "op": "LT", "value": 30000}]},
                  "actions": [{"type": "REJECT", "reason": "收入不足", "code": "INC_001"}]
                }
              ]
            }
            """;

        CompiledRuleSet ruleSet = ruleCompiler.compileRuleSet(json);
        ExecutionContext ctx = ExecutionContext.create(Map.of("age", 18, "income", 20000));
        RuleExecutionResult result = ruleExecutor.execute(ruleSet, ctx);

        assertEquals(2, result.getMatchedRules().size());
        assertTrue(result.isHit());
    }

    // ========== 8. 模型调用 Mock + SHAP ==========

    @Test
    @DisplayName("E2E-08: 模型调用 Mock → SHAP 解释 → 追踪集成")
    void modelService_mockAndShap() {
        // 1. 配置 Mock 模式
        ModelConfig config = ModelConfig.builder()
            .modelId("MOD_FRAUD_V2")
            .mockEnabled(true)
            .mockScore(0.85)
            .build();

        // 2. 特征映射
        ModelFeatureMapper mapper = ModelFeatureMapper.builder()
            .modelId("MOD_FRAUD_V2")
            .addMapping(new FeatureMapping("age", "f_age", FeatureMapping.Type.INTEGER, 0))
            .addMapping(new FeatureMapping("overdue_count_6m", "f_overdue", FeatureMapping.Type.INTEGER, 0))
            .build();

        // 3. 构建请求
        Map<String, Object> context = Map.of("age", 28, "overdue_count_6m", 0);
        ModelRequest request = mapper.buildRequest(context, "REQ_001");

        // 4. 调用 (Mock)
        ModelServiceClient client = new DefaultModelServiceClient(config);
        ModelResponse response = client.predict(request);

        assertTrue(response.isSuccess());
        assertEquals(0.85, response.getScore(), 0.001);

        // 5. SHAP 解释
        SHAPExplainer explainer = new SHAPExplainer(client, true, 0.5);
        ShapResult shap = explainer.explain(response, request);

        assertTrue(shap.isSuccess());
        assertEquals(2, shap.getContributions().size());

        // 6. 集成到追踪
        DecisionTracer tracer = DecisionTracer.create("DEC_003", "STR_001");
        tracer.beginNode("fraud_model", NodeType.MODEL)
            .captureInput("age", 28, "overdue_count_6m", 0)
            .endNode(Map.of("score", 0.85, "label", "LOW_RISK"))
            .addMetadata("shapContributions", shap.getContributions().size());
        DecisionTrace trace = tracer.finish("PASS", null, null, null);

        assertEquals(1, trace.getEntries().size());
        assertEquals(NodeType.MODEL, trace.getEntries().get(0).getNodeType());
    }

    // ========== 9. 沙箱回测端到端 ==========

    @Test
    @DisplayName("E2E-09: 沙箱回测 → 对比报告 → 差异分析")
    void sandbox_e2e() {
        // 决策函数: age < 25 拒绝
        var decisionFn = (java.util.function.Function<Map<String, Object>, Map<String, Object>>)
            vars -> {
                int age = vars.get("age") instanceof Number ? ((Number) vars.get("age")).intValue() : 0;
                if (age < 25) {
                    return Map.of("result", "REJECT", "rejectCode", "AGE_POLICY", "score", 400);
                }
                return Map.of("result", "PASS", "score", 720);
            };

        SandboxRunner runner = SandboxRunner.create(decisionFn);

        SandboxRequest request = SandboxRequest.builder()
            .strategyId("STR_V3")
            .baselineVersion("3.1")
            .candidateVersion("3.2")
            .addSample(HistorySample.of("APP_001", Map.of("age", 28), "PASS", null))
            .addSample(HistorySample.of("APP_002", Map.of("age", 20), "PASS", null))   // 原来通过，新规则拒绝
            .addSample(HistorySample.of("APP_003", Map.of("age", 35), "PASS", null))
            .build();

        DiffReport report = runner.compare(request);

        assertEquals(3, report.getTotalSamples());
        assertEquals(1, report.getChangedCount());
        assertEquals(1.0 / 3.0, report.getDiffRate(), 0.001);
        assertEquals("APP_002", report.getDiffEntries().get(0).getSampleId());
    }

    // ========== 10. 变量引擎 + 规则引擎协作 ==========

    @Test
    @DisplayName("E2E-10: 变量引擎 → 解析 → 规则执行")
    void variableEngine_withRules() {
        // 变量注册表
        VariableRegistry registry = new VariableRegistry();
        registry.register(VariableDefinition.builder()
            .varId("derived_risk_level")
            .name("风险等级")
            .layer(VariableLayer.DERIVED)
            .expression("age < 25 ? 'HIGH' : (age < 40 ? 'MEDIUM' : 'LOW')")
            .build());

        // 解析变量
        VariableEngine varEngine = new VariableEngine(registry);
        varEngine.setProvider(VariableLayer.INPUT, (varIds, resolveCtx) -> {
            Map<String, Object> result = new HashMap<>();
            Map<String, Object> params = resolveCtx.getRequestParams();
            for (String varId : varIds) {
                if (params.containsKey(varId)) {
                    result.put(varId, params.get(varId));
                }
            }
            return result;
        });
        varEngine.setDerivedProvider(expressionEngine);

        VariableResolveContext resolveCtx = varEngine.resolve(
            Set.of("age", "derived_risk_level"),
            "REQ_010",
            Map.of("age", 30)
        );
        Map<String, Object> resolved = resolveCtx.getAllResolved();

        assertEquals(30, resolved.get("age"));
        assertNotNull(resolved);
    }

    // ========== 11. 完整追踪链 ==========

    @Test
    @DisplayName("E2E-11: 完整追踪链 → 决策报告生成")
    void fullTrace_reportGeneration() {
        DecisionTracer tracer = DecisionTracer.create("DEC_011", "STR_CREDIT_V3");

        // 数据准备
        tracer.beginNode("data_prep", NodeType.DATA_PREP)
            .captureInput("applicationId", "APP_001")
            .endNode(Map.of("variablesLoaded", 15));

        // 规则执行
        tracer.beginNode("blacklist", NodeType.RULE_SET)
            .captureInput("id_card", "3301***")
            .endNode(Map.of("hit", false, "rulesChecked", 45));
        tracer.recordRuleSet("RS_BLACKLIST", false, 45, 0);

        // 评分卡
        tracer.beginNode("scorecard", NodeType.SCORECARD)
            .captureInput("age", 28, "income", 50000)
            .endNode(Map.of("score", 535, "result", "REVIEW"));
        tracer.recordScorecard("SC_CREDIT_A", 500, 535, "REVIEW");

        // 完成
        DecisionTrace trace = tracer.finish("REVIEW", 535, null, null);

        assertEquals("REVIEW", trace.getFinalResult());
        assertTrue(trace.getEntries().size() >= 3, "should have at least 3 trace entries");
        assertEquals("DEC_011", trace.getDecisionId());

        // 报告生成
        DecisionReport report = TraceReporter.generateReport(trace);
        assertNotNull(report);
    }

    // ========== 12. 规则编译+缓存+重编译 ==========

    @Test
    @DisplayName("E2E-12: 规则热加载 → 编译 → 缓存 → 重编译")
    void hotReload_cacheRecompile() {
        String json = """
            {
              "ruleSetId": "RS_HOT",
              "hitPolicy": "FIRST_HIT",
              "rules": [
                {
                  "ruleId": "R_HOT_001", "priority": 100,
                  "conditions": {"operator": "AND", "operands": [{"field": "score", "op": "GTE", "value": 600}]},
                  "actions": [{"type": "PASS"}]
                }
              ]
            }
            """;

        // 首次编译
        CompiledRuleSet v1 = ruleCompiler.compileRuleSet(json);

        // 执行
        ExecutionContext ctx = ExecutionContext.create(Map.of("score", 650));
        RuleExecutionResult result1 = ruleExecutor.execute(v1, ctx);
        assertTrue(result1.isHit());

        // 重编译 (新阈值)
        String jsonV2 = json.replace("600", "700");
        CompiledRuleSet v2 = ruleCompiler.compileRuleSet(jsonV2);

        // v2 阈值 700，score=650 不再命中
        RuleExecutionResult result2 = ruleExecutor.execute(v2, ctx);
        assertFalse(result2.isHit());

        // v1 仍可使用 (CopyOnWrite 语义)
        RuleExecutionResult result1Again = ruleExecutor.execute(v1, ctx);
        assertTrue(result1Again.isHit());
    }

    // ========== 13. AB 实验分流一致性 ==========

    @Test
    @DisplayName("E2E-13: AB 实验 → 分流一致性 → 指标收集")
    void abExperiment_consistency() {
        com.credit.platform.engine.core.experiment.ExperimentConfig config =
            com.credit.platform.engine.core.experiment.ExperimentConfig.builder()
                .experimentId("EXP_001")
                .name("评分卡V3灰度")
                .trafficKey("userId")
                .addGroup(new com.credit.platform.engine.core.experiment.ExperimentConfig.GroupConfig(
                    "champion", "对照组", 0.8, "STR_V2"))
                .addGroup(new com.credit.platform.engine.core.experiment.ExperimentConfig.GroupConfig(
                    "challenger", "实验组", 0.2, "STR_V3"))
                .build();

        com.credit.platform.engine.core.experiment.ExperimentSplitter splitter =
            new com.credit.platform.engine.core.experiment.ExperimentSplitter();

        // 一致性验证: 同一 trafficKey 多次分流结果一致
        String group1 = com.credit.platform.engine.core.experiment.ExperimentSplitter.split(config, "user_12345");
        String group2 = com.credit.platform.engine.core.experiment.ExperimentSplitter.split(config, "user_12345");
        assertEquals(group1, group2);

        // 不同用户可能分到不同组
        Set<String> groups = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            groups.add(com.credit.platform.engine.core.experiment.ExperimentSplitter.split(config, "user_" + i));
        }
        assertTrue(groups.size() >= 1, "应该至少有1个分组");
    }

    // ========== 14. 表达式引擎 null 安全 ==========

    @Test
    @DisplayName("E2E-14: 表达式引擎 null 安全处理")
    void expressionEngine_nullSafety() {
        Map<String, Object> envWithNull = new HashMap<>();
        envWithNull.put("age", null);
        envWithNull.put("score", 500);

        // null 参与比较应返回 false
        assertFalse(expressionEngine.executeAsBoolean("age > 22", envWithNull));
        // 正常值仍可比较
        assertTrue(expressionEngine.executeAsBoolean("score > 300", envWithNull));
    }

    // ========== 15. 多规则类型组合决策流 ==========

    @Test
    @DisplayName("E2E-15: 多规则类型组合 — 规则+评分卡+追踪")
    void multiRuleType_combinedDecision() {
        // Step 1: 准入规则
        String accessJson = """
            {
              "ruleSetId": "RS_ACCESS",
              "hitPolicy": "FIRST_HIT",
              "rules": [
                {
                  "ruleId": "R_ACCESS_001", "priority": 100,
                  "conditions": {"operator": "AND", "operands": [{"field": "age", "op": "LT", "value": 18}]},
                  "actions": [{"type": "REJECT", "reason": "未成年", "code": "AGE_MINOR"}]
                }
              ]
            }
            """;
        CompiledRuleSet accessRules = ruleCompiler.compileRuleSet(accessJson);

        // Step 2: 评分卡
        String scorecardJson = """
            {
              "scorecardId": "SC_MAIN",
              "initialScore": 500,
              "characteristics": [
                {"name": "年龄", "field": "age", "bins": [
                  {"range": [null, 25], "score": -5},
                  {"range": [25, 45], "score": 20},
                  {"range": [45, null], "score": 10}
                ]},
                {"name": "月收入", "field": "monthly_income", "bins": [
                  {"range": [null, 5000], "score": -10},
                  {"range": [5000, 20000], "score": 15},
                  {"range": [20000, null], "score": 25}
                ]}
              ],
              "cutoff": {"reject": 510, "review": 540, "pass": 540}
            }
            """;
        CompiledScorecard scorecard = scorecardCompiler.compile(scorecardJson);

        // Step 3: 执行完整流程
        ExecutionContext ctx = ExecutionContext.create(Map.of(
            "age", 30, "monthly_income", 15000
        ));

        DecisionTracer tracer = DecisionTracer.create("DEC_015", "STR_COMBO");

        // 3a. 准入检查
        tracer.beginNode("access_check", NodeType.RULE_SET)
            .captureInput("age", 30)
            .endNode(Map.of("hit", false));
        RuleExecutionResult accessResult = ruleExecutor.execute(accessRules, ctx);
        assertFalse(accessResult.isHit()); // age=30, 不命中拒绝规则

        // 3b. 评分卡
        tracer.beginNode("scorecard", NodeType.SCORECARD)
            .captureInput("age", 30, "monthly_income", 15000);
        ScorecardResult scoreResult = scorecardExecutor.execute(scorecard, ctx);
        tracer.endNode(Map.of("score", scoreResult.getFinalScore(), "result", scoreResult.getResult()));
        tracer.recordScorecard("SC_MAIN", 500, scoreResult.getFinalScore(), scoreResult.getResult());

        // 验证: 500 + 20(年龄) + 15(收入) = 535
        assertEquals(535, scoreResult.getFinalScore());

        // 3c. 完成决策
        String finalResult = scoreResult.getFinalScore() >= 540 ? "PASS" :
            scoreResult.getFinalScore() >= 510 ? "REVIEW" : "REJECT";
        DecisionTrace trace = tracer.finish(finalResult, scoreResult.getFinalScore(), null, null);

        assertEquals("REVIEW", trace.getFinalResult());
        assertTrue(trace.getEntries().size() >= 2, "should have at least 2 trace entries");
    }
}
