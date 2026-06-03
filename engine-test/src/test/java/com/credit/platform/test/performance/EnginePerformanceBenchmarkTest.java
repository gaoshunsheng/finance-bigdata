package com.credit.platform.test.performance;

import com.credit.platform.engine.core.compiler.*;
import com.credit.platform.engine.core.executor.*;
import com.credit.platform.engine.core.expression.ExpressionEngine;
import com.credit.platform.engine.core.model.*;
import com.credit.platform.engine.core.scorecard.*;

import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 引擎性能基准测试。
 * <p>
 * 目标: 引擎自身开销 < 50ms, 单机 QPS ≥ 500
 * </p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EnginePerformanceBenchmarkTest {

    private static final int WARMUP = 100;
    private static final int ITERATIONS = 10_000;
    private static final int QPS_TARGET = 500;

    private RuleCompiler ruleCompiler;
    private RuleExecutor ruleExecutor;
    private ScorecardCompiler scorecardCompiler;
    private ScorecardExecutor scorecardExecutor;
    private ExpressionEngine expressionEngine;

    @BeforeAll
    void setUp() {
        ruleCompiler = new RuleCompiler();
        ruleExecutor = new RuleExecutor();
        scorecardCompiler = new ScorecardCompiler();
        scorecardExecutor = new ScorecardExecutor();
        expressionEngine = new ExpressionEngine();
    }

    @Test
    @DisplayName("PERF-01: 规则编译性能 — 1000次编译 < 2s")
    void ruleCompile_performance() {
        String json = """
            {"ruleSetId":"RS_PERF","hitPolicy":"FIRST_HIT","rules":[
              {"ruleId":"R1","priority":100,"conditions":{"operator":"AND","operands":[{"field":"age","op":"GTE","value":22},{"field":"income","op":"GTE","value":30000}]},"actions":[{"type":"PASS"}]},
              {"ruleId":"R2","priority":90,"conditions":{"operator":"OR","operands":[{"field":"overdue","op":"GT","value":3}]},"actions":[{"type":"REJECT","reason":"逾期过多","code":"OD_001"}]}
            ]}
            """;
        // Warmup
        for (int i = 0; i < WARMUP; i++) ruleCompiler.compileRuleSet(json);

        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) ruleCompiler.compileRuleSet(json);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        double qps = ITERATIONS * 1000.0 / elapsedMs;
        System.out.printf("  规则编译: %d次, %dms, QPS=%.0f%n", ITERATIONS, elapsedMs, qps);
        assertTrue(qps >= QPS_TARGET, "Rule compile QPS should >= " + QPS_TARGET + ", got " + qps);
    }

    @Test
    @DisplayName("PERF-02: 规则执行性能 — 10000次执行 QPS ≥ 500")
    void ruleExecute_performance() {
        String json = """
            {"ruleSetId":"RS_EXEC","hitPolicy":"ALL","rules":[
              {"ruleId":"R1","priority":100,"conditions":{"operator":"AND","operands":[{"field":"age","op":"GTE","value":22}]},"actions":[{"type":"PASS"}]},
              {"ruleId":"R2","priority":90,"conditions":{"operator":"AND","operands":[{"field":"income","op":"GTE","value":30000}]},"actions":[{"type":"PASS"}]},
              {"ruleId":"R3","priority":80,"conditions":{"operator":"AND","operands":[{"field":"score","op":"GTE","value":600}]},"actions":[{"type":"PASS"}]}
            ]}
            """;
        CompiledRuleSet ruleSet = ruleCompiler.compileRuleSet(json);
        ExecutionContext ctx = ExecutionContext.create(Map.of("age", 28, "income", 50000, "score", 700));

        for (int i = 0; i < WARMUP; i++) ruleExecutor.execute(ruleSet, ctx);

        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) ruleExecutor.execute(ruleSet, ctx);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        double qps = ITERATIONS * 1000.0 / elapsedMs;
        double avgUs = elapsedMs * 1000.0 / ITERATIONS;
        System.out.printf("  规则执行: %d次, %dms, QPS=%.0f, avg=%.1fus%n", ITERATIONS, elapsedMs, qps, avgUs);
        assertTrue(qps >= QPS_TARGET, "Rule execute QPS should >= " + QPS_TARGET + ", got " + qps);
        assertTrue(avgUs < 50000, "Single rule execution should < 50ms, got " + avgUs + "us");
    }

    @Test
    @DisplayName("PERF-03: 评分卡执行性能 — 10000次 QPS ≥ 500")
    void scorecardExecute_performance() {
        String json = """
            {"scorecardId":"SC_PERF","initialScore":500,"characteristics":[
              {"name":"年龄","field":"age","bins":[{"range":[null,25],"score":-5},{"range":[25,45],"score":20},{"range":[45,null],"score":10}]},
              {"name":"收入","field":"income","bins":[{"range":[null,30000],"score":-10},{"range":[30000,80000],"score":15},{"range":[80000,null],"score":30}]},
              {"name":"逾期","field":"overdue","bins":[{"range":[null,1],"score":25},{"range":[1,3],"score":5},{"range":[3,null],"score":-20}]}
            ],"cutoff":{"reject":510,"review":540,"pass":540}}
            """;
        CompiledScorecard sc = scorecardCompiler.compile(json);
        ExecutionContext ctx = ExecutionContext.create(Map.of("age", 30, "income", 50000, "overdue", 0));

        for (int i = 0; i < WARMUP; i++) scorecardExecutor.execute(sc, ctx);

        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) scorecardExecutor.execute(sc, ctx);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        double qps = ITERATIONS * 1000.0 / elapsedMs;
        System.out.printf("  评分卡执行: %d次, %dms, QPS=%.0f%n", ITERATIONS, elapsedMs, qps);
        assertTrue(qps >= QPS_TARGET, "Scorecard QPS should >= " + QPS_TARGET + ", got " + qps);
    }

    @Test
    @DisplayName("PERF-04: 表达式引擎性能 — 缓存命中 vs 未命中")
    void expressionEngine_performance() {
        Map<String, Object> env = Map.of("age", 28, "income", 50000, "score", 700);
        String expr = "age >= 22 && income >= 30000 && score >= 600";

        // Warmup
        for (int i = 0; i < WARMUP; i++) expressionEngine.executeAsBoolean(expr, env);

        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) expressionEngine.executeAsBoolean(expr, env);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        double qps = ITERATIONS * 1000.0 / elapsedMs;
        System.out.printf("  表达式引擎(缓存): %d次, %dms, QPS=%.0f%n", ITERATIONS, elapsedMs, qps);
        assertTrue(qps >= QPS_TARGET, "Expression QPS should >= " + QPS_TARGET + ", got " + qps);
    }

    @Test
    @DisplayName("PERF-05: 模型调用 Mock 性能 — 不应有网络开销")
    void modelServiceMock_performance() {
        ModelConfig config = ModelConfig.builder().modelId("MOD_P").mockEnabled(true).mockScore(0.8).build();
        ModelServiceClient client = new DefaultModelServiceClient(config);
        ModelRequest request = ModelRequest.builder().modelId("MOD_P")
            .addFeature("age", 28).addFeature("income", 50000).build();

        for (int i = 0; i < WARMUP; i++) client.predict(request);

        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) client.predict(request);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        double qps = ITERATIONS * 1000.0 / elapsedMs;
        System.out.printf("  模型Mock调用: %d次, %dms, QPS=%.0f%n", ITERATIONS, elapsedMs, qps);
        assertTrue(qps >= QPS_TARGET * 10, "Mock QPS should be very high, got " + qps);
    }
}
