package com.credit.platform.test.performance;

import com.credit.platform.engine.core.cache.*;
import com.credit.platform.engine.core.compiler.*;
import com.credit.platform.engine.core.executor.*;
import com.credit.platform.engine.core.expression.ExpressionEngine;
import com.credit.platform.engine.core.flow.*;
import com.credit.platform.engine.core.model.*;
import com.credit.platform.engine.core.scorecard.*;
import com.credit.platform.engine.core.table.*;
import com.credit.platform.engine.core.tree.*;
import com.credit.platform.engine.core.variable.VariableEngine;

import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 引擎性能基准测试 — 覆盖规则编译/执行、评分卡、DAG流、决策表、决策树、缓存等各模块。
 * <p>
 * 目标: 单机 QPS ≥ 500, P99 &lt; 3s
 * </p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EnginePerformanceBenchmarkTest {

    private static final int WARMUP = 200;
    private static final int ITERATIONS = 10_000;
    private static final int QPS_TARGET = 500;

    private RuleCompiler ruleCompiler;
    private RuleExecutor ruleExecutor;
    private ScorecardCompiler scorecardCompiler;
    private ScorecardExecutor scorecardExecutor;
    private DecisionTableCompiler tableCompiler;
    private DecisionTreeCompiler treeCompiler;
    private DAGCompiler dagCompiler;
    private ExpressionEngine expressionEngine;
    private RuleCacheManager cacheManager;

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
        cacheManager = new RuleCacheManager();
    }

    // ========== 辅助方法 ==========

    private double measureQps(Runnable action) {
        for (int i = 0; i < WARMUP; i++) action.run();
        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) action.run();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        return ITERATIONS * 1000.0 / Math.max(elapsedMs, 1);
    }

    private void assertQps(double qps, String label) {
        System.out.printf("  %-45s QPS=%,.0f%n", label, qps);
        assertTrue(qps >= QPS_TARGET, label + " QPS should >= " + QPS_TARGET + ", got " + qps);
    }

    private String buildSimpleRuleJson() {
        return """
            {"ruleSetId":"RS_SIMPLE","hitPolicy":"FIRST_HIT","rules":[
              {"ruleId":"R1","priority":100,"conditions":{"operator":"AND","operands":[
                {"field":"age","op":"GTE","value":22},{"field":"income","op":"GTE","value":30000}
              ]},"actions":[{"type":"PASS"}]},
              {"ruleId":"R2","priority":90,"conditions":{"operator":"OR","operands":[
                {"field":"overdue","op":"GT","value":3}
              ]},"actions":[{"type":"REJECT","reason":"逾期过多","code":"OD_001"}]}
            ]}
            """;
    }

    private String buildComplexRuleJson(int ruleCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"ruleSetId\":\"RS_COMPLEX\",\"hitPolicy\":\"ALL\",\"rules\":[");
        for (int i = 0; i < ruleCount; i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"ruleId\":\"R").append(i).append("\",\"priority\":")
              .append(100 - i).append(",\"conditions\":{\"operator\":\"AND\",\"operands\":[");
            sb.append("{\"field\":\"age\",\"op\":\"GTE\",\"value\":22},");
            sb.append("{\"field\":\"income\",\"op\":\"GTE\",\"value\":30000},");
            sb.append("{\"field\":\"score\",\"op\":\"GTE\",\"value\":600},");
            sb.append("{\"field\":\"overdue\",\"op\":\"LTE\",\"value\":").append(i % 5).append("}");
            sb.append("]},\"actions\":[{\"type\":\"PASS\"}]}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private String buildDeepNestedRuleJson() {
        return """
            {"ruleSetId":"RS_NESTED","hitPolicy":"FIRST_HIT","rules":[
              {"ruleId":"R1","priority":100,"conditions":{"operator":"AND","operands":[
                {"operator":"OR","operands":[
                  {"operator":"AND","operands":[
                    {"field":"age","op":"GTE","value":25},
                    {"field":"income","op":"GTE","value":50000}
                  ]},
                  {"operator":"AND","operands":[
                    {"field":"creditScore","op":"GTE","value":700},
                    {"field":"employmentYears","op":"GTE","value":5}
                  ]}
                ]},
                {"operator":"NOT","operands":[
                  {"field":"blacklisted","op":"EQ","value":true}
                ]},
                {"field":"loanAmount","op":"LTE","value":500000}
              ]},"actions":[{"type":"PASS"}]}
            ]}
            """;
    }

    private String buildSimpleScorecardJson() {
        return """
            {"scorecardId":"SC_PERF","initialScore":500,"characteristics":[
              {"name":"年龄","field":"age","bins":[{"range":[null,25],"score":-5},{"range":[25,45],"score":20},{"range":[45,null],"score":10}]},
              {"name":"收入","field":"income","bins":[{"range":[null,30000],"score":-10},{"range":[30000,80000],"score":15},{"range":[80000,null],"score":30}]},
              {"name":"逾期","field":"overdue","bins":[{"range":[null,1],"score":25},{"range":[1,3],"score":5},{"range":[3,null],"score":-20}]}
            ],"cutoff":{"reject":510,"review":540,"pass":540}}
            """;
    }

    private String buildLargeScorecardJson(int characteristics) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"scorecardId\":\"SC_LARGE\",\"initialScore\":500,\"characteristics\":[");
        for (int i = 0; i < characteristics; i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"name\":\"特征").append(i).append("\",\"field\":\"f").append(i)
              .append("\",\"bins\":[")
              .append("{\"range\":[null,10],\"score\":-5},")
              .append("{\"range\":[10,50],\"score\":10},")
              .append("{\"range\":[50,null],\"score\":20}")
              .append("]}");
        }
        sb.append("],\"cutoff\":{\"reject\":480,\"review\":520,\"pass\":520}}");
        return sb.toString();
    }

    private String buildSimpleDagJson() {
        return """
            {"flowId":"DAG_SIMPLE","name":"简单DAG","nodes":[
              {"id":"N1","type":"DATA_PREP","name":"数据准备","config":{"outputVar":"prepared"}},
              {"id":"N2","type":"RULE_SET","name":"规则检查","config":{"ruleSetId":"RS1"}},
              {"id":"N3","type":"ACTION","name":"通过","config":{"actionType":"PASS"}}
            ],"edges":[
              {"from":"N1","to":"N2"},
              {"from":"N2","to":"N3","condition":"result != 'REJECT'"}
            ]}
            """;
    }

    private String buildComplexDagJson(int nodeCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"flowId\":\"DAG_COMPLEX\",\"name\":\"复杂DAG\",\"nodes\":[");
        for (int i = 0; i < nodeCount; i++) {
            if (i > 0) sb.append(",");
            String type = switch (i % 5) {
                case 0 -> "DATA_PREP";
                case 1 -> "RULE_SET";
                case 2 -> "SCORECARD";
                case 3 -> "DECISION";
                default -> "ACTION";
            };
            sb.append("{\"id\":\"N").append(i).append("\",\"type\":\"")
              .append(type).append("\",\"name\":\"节点").append(i).append("\"}");
        }
        sb.append("],\"edges\":[");
        for (int i = 0; i < nodeCount - 1; i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"from\":\"N").append(i).append("\",\"to\":\"N").append(i + 1).append("\"}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private String buildDecisionTableJson(int rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"tableId\":\"DT_PERF\",\"hitPolicy\":\"FIRST_MATCH\",\"columns\":[")
          .append("{\"name\":\"年龄\",\"field\":\"age\"},")
          .append("{\"name\":\"收入\",\"field\":\"income\"}")
          .append("],\"rows\":[");
        for (int i = 0; i < rows; i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"conditions\":[").append(20 + i).append(",").append(20000 + i * 1000)
              .append("],\"result\":{\"action\":\"outcome_").append(i).append("\"}}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private String buildDecisionTreeJson(int depth) {
        return "{\"treeId\":\"TREE_PERF\"," + buildTreeNode(0, depth) + "}";
    }

    private String buildTreeNode(int current, int maxDepth) {
        if (current >= maxDepth) {
            return "\"action\":{\"type\":\"PASS\",\"reason\":\"depth_" + current + "\"}";
        }
        return "\"condition\":{\"field\":\"f" + current + "\",\"op\":\"GTE\",\"value\":50},"
             + "\"trueChild\":{" + buildTreeNode(current + 1, maxDepth) + "},"
             + "\"falseChild\":{" + buildTreeNode(current + 1, maxDepth) + "}";
    }

    // ========== 1. 规则编译性能 (6 tests) ==========

    @Test
    @DisplayName("PERF-R01: 简单规则编译 (2条规则) QPS≥500")
    void ruleCompile_simple() {
        String json = buildSimpleRuleJson();
        double qps = measureQps(() -> ruleCompiler.compileRuleSet(json));
        assertQps(qps, "简单规则编译 (2条规则)");
    }

    @Test
    @DisplayName("PERF-R02: 中等规则集编译 (10条规则) QPS≥500")
    void ruleCompile_medium() {
        String json = buildComplexRuleJson(10);
        double qps = measureQps(() -> ruleCompiler.compileRuleSet(json));
        assertQps(qps, "中等规则集编译 (10条规则)");
    }

    @Test
    @DisplayName("PERF-R03: 大规则集编译 (50条规则) QPS≥500")
    void ruleCompile_large() {
        String json = buildComplexRuleJson(50);
        double qps = measureQps(() -> ruleCompiler.compileRuleSet(json));
        assertQps(qps, "大规则集编译 (50条规则)");
    }

    @Test
    @DisplayName("PERF-R04: 深层嵌套规则编译 (AND/OR/NOT) QPS≥500")
    void ruleCompile_deepNested() {
        String json = buildDeepNestedRuleJson();
        double qps = measureQps(() -> ruleCompiler.compileRuleSet(json));
        assertQps(qps, "深层嵌套规则编译");
    }

    @Test
    @DisplayName("PERF-R05: 重复编译缓存效果 — 同一JSON编译10000次")
    void ruleCompile_repeatedSame() {
        String json = buildSimpleRuleJson();
        double qps = measureQps(() -> ruleCompiler.compileRuleSet(json));
        assertQps(qps, "重复编译同一规则 (缓存)");
        assertTrue(qps > 1000, "重复编译应利用缓存, QPS 应远高于 1000, got " + qps);
    }

    @Test
    @DisplayName("PERF-R06: 多种规则交替编译 QPS≥500")
    void ruleCompile_alternating() {
        String[] jsons = {buildSimpleRuleJson(), buildDeepNestedRuleJson(), buildComplexRuleJson(5)};
        double qps = measureQps(() -> {
            for (String json : jsons) ruleCompiler.compileRuleSet(json);
        });
        qps *= jsons.length; // 调整为单次编译的QPS
        assertQps(qps, "多种规则交替编译");
    }

    // ========== 2. 规则执行性能 (7 tests) ==========

    @Test
    @DisplayName("PERF-R07: 简单规则执行 (FIRST_HIT, 2条) QPS≥500")
    void ruleExecute_simpleFirstHit() {
        CompiledRuleSet rs = ruleCompiler.compileRuleSet(buildSimpleRuleJson());
        ExecutionContext ctx = ExecutionContext.create(Map.of("age", 28, "income", 50000, "overdue", 0));
        double qps = measureQps(() -> ruleExecutor.execute(rs, ctx));
        assertQps(qps, "简单规则执行 (FIRST_HIT, 2条)");
    }

    @Test
    @DisplayName("PERF-R08: ALL策略规则执行 (3条全部匹配) QPS≥500")
    void ruleExecute_allPolicy() {
        String json = """
            {"ruleSetId":"RS_ALL","hitPolicy":"ALL","rules":[
              {"ruleId":"R1","priority":100,"conditions":{"operator":"AND","operands":[{"field":"age","op":"GTE","value":22}]},"actions":[{"type":"PASS"}]},
              {"ruleId":"R2","priority":90,"conditions":{"operator":"AND","operands":[{"field":"income","op":"GTE","value":30000}]},"actions":[{"type":"PASS"}]},
              {"ruleId":"R3","priority":80,"conditions":{"operator":"AND","operands":[{"field":"score","op":"GTE","value":600}]},"actions":[{"type":"PASS"}]}
            ]}
            """;
        CompiledRuleSet rs = ruleCompiler.compileRuleSet(json);
        ExecutionContext ctx = ExecutionContext.create(Map.of("age", 28, "income", 50000, "score", 700));
        double qps = measureQps(() -> ruleExecutor.execute(rs, ctx));
        assertQps(qps, "ALL策略规则执行 (3条)");
    }

    @Test
    @DisplayName("PERF-R09: 大规则集执行 (50条规则, ALL) QPS≥500")
    void ruleExecute_largeRuleSet() {
        CompiledRuleSet rs = ruleCompiler.compileRuleSet(buildComplexRuleJson(50));
        Map<String, Object> data = new HashMap<>();
        data.put("age", 28);
        data.put("income", 50000);
        data.put("score", 700);
        data.put("overdue", 2);
        ExecutionContext ctx = ExecutionContext.create(data);
        double qps = measureQps(() -> ruleExecutor.execute(rs, ctx));
        assertQps(qps, "大规则集执行 (50条, ALL)");
    }

    @Test
    @DisplayName("PERF-R10: 深层嵌套条件执行 (AND/OR/NOT) QPS≥500")
    void ruleExecute_deepNested() {
        CompiledRuleSet rs = ruleCompiler.compileRuleSet(buildDeepNestedRuleJson());
        Map<String, Object> data = new HashMap<>();
        data.put("age", 28); data.put("income", 60000);
        data.put("creditScore", 720); data.put("employmentYears", 8);
        data.put("blacklisted", false); data.put("loanAmount", 300000);
        ExecutionContext ctx = ExecutionContext.create(data);
        double qps = measureQps(() -> ruleExecutor.execute(rs, ctx));
        assertQps(qps, "深层嵌套条件执行");
    }

    @Test
    @DisplayName("PERF-R11: 规则执行 — 边界值混合 QPS≥500")
    void ruleExecute_boundaryValues() {
        CompiledRuleSet rs = ruleCompiler.compileRuleSet(buildSimpleRuleJson());
        ExecutionContext ctx = ExecutionContext.create(Map.of("age", 0, "income", 0, "overdue", 0));
        double qps = measureQps(() -> ruleExecutor.execute(rs, ctx));
        assertQps(qps, "规则执行 — 边界值混合");
    }

    @Test
    @DisplayName("PERF-R12: 规则执行 — 短路求值 (FIRST_HIT首条命中) QPS≥500")
    void ruleExecute_shortCircuit() {
        String json = """
            {"ruleSetId":"RS_SC","hitPolicy":"FIRST_HIT","rules":[
              {"ruleId":"R1","priority":100,"conditions":{"operator":"AND","operands":[{"field":"age","op":"GTE","value":18}]},"actions":[{"type":"PASS"}]},
              {"ruleId":"R2","priority":90,"conditions":{"operator":"AND","operands":[{"field":"income","op":"GTE","value":100000}]},"actions":[{"type":"PASS"}]},
              {"ruleId":"R3","priority":80,"conditions":{"operator":"AND","operands":[{"field":"score","op":"GTE","value":800}]},"actions":[{"type":"PASS"}]},
              {"ruleId":"R4","priority":70,"conditions":{"operator":"AND","operands":[{"field":"level","op":"GTE","value":5}]},"actions":[{"type":"PASS"}]},
              {"ruleId":"R5","priority":60,"conditions":{"operator":"AND","operands":[{"field":"years","op":"GTE","value":10}]},"actions":[{"type":"PASS"}]}
            ]}
            """;
        CompiledRuleSet rs = ruleCompiler.compileRuleSet(json);
        ExecutionContext ctx = ExecutionContext.create(Map.of("age", 25, "income", 50000, "score", 700, "level", 3, "years", 5));
        double qps = measureQps(() -> ruleExecutor.execute(rs, ctx));
        assertQps(qps, "短路求值 (FIRST_HIT首条命中)");
    }

    @Test
    @DisplayName("PERF-R13: 规则编译+执行联合性能 QPS≥500")
    void ruleCompileAndExecute_combined() {
        String json = buildSimpleRuleJson();
        ExecutionContext ctx = ExecutionContext.create(Map.of("age", 28, "income", 50000, "overdue", 0));
        double qps = measureQps(() -> {
            CompiledRuleSet rs = ruleCompiler.compileRuleSet(json);
            ruleExecutor.execute(rs, ctx);
        });
        assertQps(qps, "规则编译+执行联合");
    }

    // ========== 3. 评分卡性能 (7 tests) ==========

    @Test
    @DisplayName("PERF-S01: 简单评分卡编译 (3特征) QPS≥500")
    void scorecardCompile_simple() {
        String json = buildSimpleScorecardJson();
        double qps = measureQps(() -> scorecardCompiler.compile(json));
        assertQps(qps, "评分卡编译 (3特征)");
    }

    @Test
    @DisplayName("PERF-S02: 大评分卡编译 (20特征) QPS≥500")
    void scorecardCompile_large() {
        String json = buildLargeScorecardJson(20);
        double qps = measureQps(() -> scorecardCompiler.compile(json));
        assertQps(qps, "大评分卡编译 (20特征)");
    }

    @Test
    @DisplayName("PERF-S03: 简单评分卡执行 (3特征) QPS≥500")
    void scorecardExecute_simple() {
        CompiledScorecard sc = scorecardCompiler.compile(buildSimpleScorecardJson());
        ExecutionContext ctx = ExecutionContext.create(Map.of("age", 30, "income", 50000, "overdue", 0));
        double qps = measureQps(() -> scorecardExecutor.execute(sc, ctx));
        assertQps(qps, "评分卡执行 (3特征)");
    }

    @Test
    @DisplayName("PERF-S04: 大评分卡执行 (20特征) QPS≥500")
    void scorecardExecute_large() {
        CompiledScorecard sc = scorecardCompiler.compile(buildLargeScorecardJson(20));
        Map<String, Object> data = new HashMap<>();
        for (int i = 0; i < 20; i++) data.put("f" + i, 30);
        ExecutionContext ctx = ExecutionContext.create(data);
        double qps = measureQps(() -> scorecardExecutor.execute(sc, ctx));
        assertQps(qps, "大评分卡执行 (20特征)");
    }

    @Test
    @DisplayName("PERF-S05: 评分卡执行 — 边界值 (得分=cutoff) QPS≥500")
    void scorecardExecute_boundaryCutoff() {
        CompiledScorecard sc = scorecardCompiler.compile(buildSimpleScorecardJson());
        // 初始500 + 年龄25→20 + 收入30000→-10 + 逾期0→25 = 535, 接近review线540
        ExecutionContext ctx = ExecutionContext.create(Map.of("age", 25, "income", 30000, "overdue", 0));
        double qps = measureQps(() -> scorecardExecutor.execute(sc, ctx));
        assertQps(qps, "评分卡执行 — 边界值");
    }

    @Test
    @DisplayName("PERF-S06: 评分卡执行 — 高分特征组合 QPS≥500")
    void scorecardExecute_highScore() {
        CompiledScorecard sc = scorecardCompiler.compile(buildSimpleScorecardJson());
        ExecutionContext ctx = ExecutionContext.create(Map.of("age", 35, "income", 90000, "overdue", 0));
        double qps = measureQps(() -> scorecardExecutor.execute(sc, ctx));
        assertQps(qps, "评分卡执行 — 高分特征组合");
    }

    @Test
    @DisplayName("PERF-S07: 评分卡编译+执行联合 QPS≥500")
    void scorecardCompileAndExecute() {
        String json = buildSimpleScorecardJson();
        ExecutionContext ctx = ExecutionContext.create(Map.of("age", 30, "income", 50000, "overdue", 0));
        double qps = measureQps(() -> {
            CompiledScorecard sc = scorecardCompiler.compile(json);
            scorecardExecutor.execute(sc, ctx);
        });
        assertQps(qps, "评分卡编译+执行联合");
    }

    // ========== 4. DAG 流性能 (6 tests) ==========

    @Test
    @DisplayName("PERF-D01: 简单DAG编译 (3节点) QPS≥500")
    void dagCompile_simple() {
        String json = buildSimpleDagJson();
        double qps = measureQps(() -> dagCompiler.compile(json));
        assertQps(qps, "简单DAG编译 (3节点)");
    }

    @Test
    @DisplayName("PERF-D02: 复杂DAG编译 (10节点) QPS≥500")
    void dagCompile_complex() {
        String json = buildComplexDagJson(10);
        double qps = measureQps(() -> dagCompiler.compile(json));
        assertQps(qps, "复杂DAG编译 (10节点)");
    }

    @Test
    @DisplayName("PERF-D03: 简单DAG执行 (3节点串行) QPS≥500")
    void dagExecute_simple() {
        CompiledDAG dag = dagCompiler.compile(buildSimpleDagJson());
        ExecutionContext ctx = ExecutionContext.create(Map.of("age", 28, "income", 50000));
        double qps = measureQps(() -> dag.execute(ctx, expressionEngine));
        assertQps(qps, "简单DAG执行 (3节点串行)");
    }

    @Test
    @DisplayName("PERF-D04: 多次DAG编译吞吐 QPS≥500")
    void dagCompile_throughput() {
        String json = buildSimpleDagJson();
        // 测试不同DAG JSON的编译吞吐量
        String[] dagJsons = new String[5];
        for (int i = 0; i < 5; i++) {
            dagJsons[i] = json.replace("DAG_SIMPLE", "DAG_" + i);
        }
        double qps = measureQps(() -> {
            for (String j : dagJsons) dagCompiler.compile(j);
        });
        qps *= dagJsons.length;
        assertQps(qps, "DAG批量编译吞吐");
    }

    @Test
    @DisplayName("PERF-D05: DAG编译+执行联合 QPS≥500")
    void dagCompileAndExecute() {
        String json = buildSimpleDagJson();
        ExecutionContext ctx = ExecutionContext.create(Map.of("age", 28, "income", 50000));
        double qps = measureQps(() -> {
            CompiledDAG dag = dagCompiler.compile(json);
            dag.execute(ctx, expressionEngine);
        });
        assertQps(qps, "DAG编译+执行联合");
    }

    @Test
    @DisplayName("PERF-D06: DAG执行 — 条件分支路径 QPS≥500")
    void dagExecute_conditionalBranch() {
        String json = """
            {"flowId":"DAG_COND","name":"条件分支","nodes":[
              {"id":"N1","type":"DATA_PREP","name":"数据准备","config":{}},
              {"id":"N2","type":"DECISION","name":"高收入判断","config":{"expression":"income >= 50000"}},
              {"id":"N3","type":"ACTION","name":"优质客户","config":{"actionType":"PASS"}},
              {"id":"N4","type":"ACTION","name":"普通客户","config":{"actionType":"REVIEW"}}
            ],"edges":[
              {"from":"N1","to":"N2"},
              {"from":"N2","to":"N3","condition":"income >= 50000"},
              {"from":"N2","to":"N4","condition":"income < 50000"}
            ]}
            """;
        CompiledDAG dag = dagCompiler.compile(json);
        ExecutionContext ctx = ExecutionContext.create(Map.of("income", 60000));
        double qps = measureQps(() -> dag.execute(ctx, expressionEngine));
        assertQps(qps, "DAG条件分支执行");
    }

    // ========== 5. 决策表性能 (4 tests) ==========

    @Test
    @DisplayName("PERF-T01: 决策表编译 (10行) QPS≥500")
    void tableCompile_small() {
        String json = buildDecisionTableJson(10);
        double qps = measureQps(() -> tableCompiler.compile(json));
        assertQps(qps, "决策表编译 (10行)");
    }

    @Test
    @DisplayName("PERF-T02: 决策表编译 (100行) QPS≥500")
    void tableCompile_large() {
        String json = buildDecisionTableJson(100);
        double qps = measureQps(() -> tableCompiler.compile(json));
        assertQps(qps, "决策表编译 (100行)");
    }

    @Test
    @DisplayName("PERF-T03: 决策表执行 (10行匹配) QPS≥500")
    void tableExecute_small() {
        String json = buildDecisionTableJson(10);
        CompiledDecisionTable table = tableCompiler.compile(json);
        Map<String, Object> data = new HashMap<>();
        data.put("age", 25); data.put("income", 25000);
        double qps = measureQps(() -> table.evaluate(data));
        assertQps(qps, "决策表执行 (10行)");
    }

    @Test
    @DisplayName("PERF-T04: 决策表执行 (100行匹配) QPS≥500")
    void tableExecute_large() {
        String json = buildDecisionTableJson(100);
        CompiledDecisionTable table = tableCompiler.compile(json);
        Map<String, Object> data = new HashMap<>();
        data.put("age", 50); data.put("income", 70000);
        double qps = measureQps(() -> table.evaluate(data));
        assertQps(qps, "决策表执行 (100行)");
    }

    // ========== 6. 决策树性能 (3 tests) ==========

    @Test
    @DisplayName("PERF-TR01: 决策树编译 (深度4) QPS≥500")
    void treeCompile_depth4() {
        String json = buildDecisionTreeJson(4);
        double qps = measureQps(() -> treeCompiler.compile(json));
        assertQps(qps, "决策树编译 (深度4)");
    }

    @Test
    @DisplayName("PERF-TR02: 决策树执行 (深度4) QPS≥500")
    void treeExecute_depth4() {
        CompiledDecisionTree tree = treeCompiler.compile(buildDecisionTreeJson(4));
        Map<String, Object> data = new HashMap<>();
        for (int i = 0; i < 4; i++) data.put("f" + i, 60);
        double qps = measureQps(() -> tree.evaluate(data));
        assertQps(qps, "决策树执行 (深度4)");
    }

    @Test
    @DisplayName("PERF-TR03: 决策树执行 (深度8) QPS≥500")
    void treeExecute_depth8() {
        CompiledDecisionTree tree = treeCompiler.compile(buildDecisionTreeJson(8));
        Map<String, Object> data = new HashMap<>();
        for (int i = 0; i < 8; i++) data.put("f" + i, 40);
        double qps = measureQps(() -> tree.evaluate(data));
        assertQps(qps, "决策树执行 (深度8)");
    }

    // ========== 7. 表达式引擎性能 (4 tests) ==========

    @Test
    @DisplayName("PERF-E01: 简单表达式 (age >= 22) QPS≥500")
    void expression_simple() {
        Map<String, Object> env = Map.of("age", 28);
        String expr = "age >= 22";
        double qps = measureQps(() -> expressionEngine.executeAsBoolean(expr, env));
        assertQps(qps, "简单表达式执行");
    }

    @Test
    @DisplayName("PERF-E02: 复合表达式 (AND/OR) QPS≥500")
    void expression_compound() {
        Map<String, Object> env = Map.of("age", 28, "income", 50000, "score", 700);
        String expr = "age >= 22 && income >= 30000 && score >= 600";
        double qps = measureQps(() -> expressionEngine.executeAsBoolean(expr, env));
        assertQps(qps, "复合表达式 (AND)");
    }

    @Test
    @DisplayName("PERF-E03: 含自定义函数 (between/in) QPS≥500")
    void expression_customFunctions() {
        Map<String, Object> env = Map.of("age", 30, "level", 3);
        double qps = measureQps(() -> {
            expressionEngine.executeAsBoolean("between(age, 25, 45)", env);
            expressionEngine.executeAsBoolean("in(level, seq(1,2,3,4,5))", env);
        });
        qps *= 2; // 两次调用
        assertQps(qps, "自定义函数表达式 (between+in)");
    }

    @Test
    @DisplayName("PERF-E04: 表达式首次编译 vs 缓存命中")
    void expression_cacheVsFresh() {
        Map<String, Object> env = Map.of("x", 100);
        String expr = "x > 50";

        // 首次 (冷启动)
        long startCold = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            expressionEngine.executeAsBoolean(expr + " && x < " + (200 + i), env);
        }
        long coldMs = (System.nanoTime() - startCold) / 1_000_000;

        // 缓存命中
        long startHot = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            expressionEngine.executeAsBoolean(expr, env);
        }
        long hotMs = (System.nanoTime() - startHot) / 1_000_000;

        double coldQps = 1000 * 1000.0 / Math.max(coldMs, 1);
        double hotQps = ITERATIONS * 1000.0 / Math.max(hotMs, 1);
        System.out.printf("  表达式冷启动: QPS=%,.0f, 缓存命中: QPS=%,.0f, 提升=%.1fx%n",
            coldQps, hotQps, hotQps / Math.max(coldQps, 1));
        assertTrue(hotQps >= QPS_TARGET, "缓存命中 QPS should >= " + QPS_TARGET);
    }

    // ========== 8. 缓存性能 (3 tests) ==========

    @Test
    @DisplayName("PERF-C01: 缓存写入 (put) QPS≥50000")
    void cacheWrite_performance() {
        CompiledRuleSet rs = ruleCompiler.compileRuleSet(buildSimpleRuleJson());
        double qps = measureQps(() -> cacheManager.putRule("key_" + System.nanoTime(), rs));
        assertQps(qps, "缓存写入 (put)");
        assertTrue(qps >= 50_000, "Cache put QPS should >= 50000, got " + qps);
    }

    @Test
    @DisplayName("PERF-C02: 缓存读取 (get) QPS≥100000")
    void cacheRead_performance() {
        cacheManager.putRule("bench_key", ruleCompiler.compileRuleSet(buildSimpleRuleJson()));
        double qps = measureQps(() -> cacheManager.getRule("bench_key"));
        assertQps(qps, "缓存读取 (get)");
        assertTrue(qps >= 100_000, "Cache get QPS should >= 100000, got " + qps);
    }

    @Test
    @DisplayName("PERF-C03: 缓存失效+重加载 QPS≥500")
    void cacheInvalidationAndReload() {
        String key = "reload_key";
        String json = buildSimpleRuleJson();
        double qps = measureQps(() -> {
            cacheManager.invalidate("rule", key);
            cacheManager.putRule(key, ruleCompiler.compileRuleSet(json));
            cacheManager.getRule(key);
        });
        assertQps(qps, "缓存失效+重加载");
    }

    // ========== 9. 模型调用Mock性能 (2 tests) ==========

    @Test
    @DisplayName("PERF-M01: 模型调用Mock (无网络) QPS≥50000")
    void modelMock_predict() {
        ModelConfig config = ModelConfig.builder().modelId("MOD_P").mockEnabled(true).mockScore(0.8).build();
        ModelServiceClient client = new DefaultModelServiceClient(config);
        ModelRequest request = ModelRequest.builder().modelId("MOD_P")
            .addFeature("age", 28).addFeature("income", 50000).build();
        double qps = measureQps(() -> client.predict(request));
        assertQps(qps, "模型Mock调用 (无网络)");
        assertTrue(qps >= QPS_TARGET * 10, "Mock QPS should be very high, got " + qps);
    }

    @Test
    @DisplayName("PERF-M02: 模型调用 + 超时降级 Mock QPS≥10000")
    void modelMock_predictWithFallback() {
        ModelConfig config = ModelConfig.builder().modelId("MOD_FB").mockEnabled(true).mockScore(0.75).build();
        DefaultModelServiceClient client = new DefaultModelServiceClient(config);
        ModelRequest request = ModelRequest.builder().modelId("MOD_FB")
            .addFeature("age", 28).addFeature("income", 50000).addFeature("score", 700).build();
        double qps = measureQps(() -> client.predict(request, 3000));
        assertQps(qps, "模型Mock调用(含超时配置)");
        assertTrue(qps >= 10_000, "Mock with timeout QPS should >= 10000, got " + qps);
    }

    // ========== 10. 综合性能汇总 ==========

    @Test
    @DisplayName("PERF-SUMMARY: 全模块综合性能摘要")
    void performanceSummary() {
        System.out.println("\n========== 性能基准测试汇总 ==========");

        // 规则
        CompiledRuleSet rs = ruleCompiler.compileRuleSet(buildSimpleRuleJson());
        ExecutionContext ruleCtx = ExecutionContext.create(Map.of("age", 28, "income", 50000, "overdue", 0));
        double ruleQps = measureQps(() -> ruleExecutor.execute(rs, ruleCtx));

        // 评分卡
        CompiledScorecard sc = scorecardCompiler.compile(buildSimpleScorecardJson());
        ExecutionContext scCtx = ExecutionContext.create(Map.of("age", 30, "income", 50000, "overdue", 0));
        double scQps = measureQps(() -> scorecardExecutor.execute(sc, scCtx));

        // DAG
        CompiledDAG dag = dagCompiler.compile(buildSimpleDagJson());
        ExecutionContext dagCtx = ExecutionContext.create(Map.of("age", 28, "income", 50000));
        double dagQps = measureQps(() -> dag.execute(dagCtx, expressionEngine));

        // 决策表 (使用简单表格)
        String tableJson = """
            {"tableId":"DT_SUM","hitPolicy":"FIRST_MATCH","columns":[
              {"name":"年龄","field":"age"},{"name":"收入","field":"income"}
            ],"rows":[
              {"conditions":[25,30000],"result":{"action":"APPROVE"}},
              {"conditions":["*","*"],"result":{"action":"REVIEW"}}
            ]}
            """;
        CompiledDecisionTable table = tableCompiler.compile(tableJson);
        Map<String, Object> tableData = Map.of("age", 25, "income", 30000);
        double tableQps = measureQps(() -> table.evaluate(tableData));

        // 决策树
        CompiledDecisionTree tree = treeCompiler.compile(buildDecisionTreeJson(6));
        Map<String, Object> treeData = new HashMap<>();
        for (int i = 0; i < 6; i++) treeData.put("f" + i, 60);
        double treeQps = measureQps(() -> tree.evaluate(treeData));

        // 表达式
        Map<String, Object> exprEnv = Map.of("age", 28, "income", 50000);
        double exprQps = measureQps(() -> expressionEngine.executeAsBoolean("age >= 22 && income >= 30000", exprEnv));

        System.out.printf("  %-20s %10.0f QPS  %s%n", "规则执行", ruleQps, ruleQps >= QPS_TARGET ? "✅" : "❌");
        System.out.printf("  %-20s %10.0f QPS  %s%n", "评分卡执行", scQps, scQps >= QPS_TARGET ? "✅" : "❌");
        System.out.printf("  %-20s %10.0f QPS  %s%n", "DAG流执行", dagQps, dagQps >= QPS_TARGET ? "✅" : "❌");
        System.out.printf("  %-20s %10.0f QPS  %s%n", "决策表执行", tableQps, tableQps >= QPS_TARGET ? "✅" : "❌");
        System.out.printf("  %-20s %10.0f QPS  %s%n", "决策树执行", treeQps, treeQps >= QPS_TARGET ? "✅" : "❌");
        System.out.printf("  %-20s %10.0f QPS  %s%n", "表达式引擎", exprQps, exprQps >= QPS_TARGET ? "✅" : "❌");
        System.out.printf("  目标: QPS ≥ %d%n", QPS_TARGET);
        System.out.println("=========================================\n");

        assertAll("全模块QPS达标",
            () -> assertTrue(ruleQps >= QPS_TARGET, "规则QPS: " + ruleQps),
            () -> assertTrue(scQps >= QPS_TARGET, "评分卡QPS: " + scQps),
            () -> assertTrue(dagQps >= QPS_TARGET, "DAG QPS: " + dagQps),
            () -> assertTrue(tableQps >= QPS_TARGET, "决策表QPS: " + tableQps),
            () -> assertTrue(treeQps >= QPS_TARGET, "决策树QPS: " + treeQps),
            () -> assertTrue(exprQps >= QPS_TARGET, "表达式QPS: " + exprQps)
        );
    }
}
