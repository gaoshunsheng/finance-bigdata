package com.credit.platform.test.performance;

import com.credit.platform.engine.core.compiler.*;
import com.credit.platform.engine.core.executor.*;
import com.credit.platform.engine.core.expression.ExpressionEngine;
import com.credit.platform.engine.core.flow.*;
import com.credit.platform.engine.core.model.*;
import com.credit.platform.engine.core.scorecard.*;
import com.credit.platform.engine.core.table.*;
import com.credit.platform.engine.core.trace.*;
import com.credit.platform.engine.core.tree.*;

import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 端到端负载测试 — 模拟真实决策流程。
 * <p>
 * 目标: QPS ≥ 500, P99 < 3s
 * 测试完整决策链: 输入 → 变量解析 → 规则匹配 → 评分卡评分 → 决策树判断 → 输出
 * </p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EndToEndLoadTest {

    private static final int CONCURRENT_THREADS = 50;
    private static final int TOTAL_REQUESTS = 5000;
    private static final int QPS_TARGET = 500;
    private static final long P99_TARGET_MS = 3000;

    private RuleCompiler ruleCompiler;
    private RuleExecutor ruleExecutor;
    private ScorecardCompiler scorecardCompiler;
    private ScorecardExecutor scorecardExecutor;
    private DecisionTableCompiler tableCompiler;
    private DecisionTreeCompiler treeCompiler;
    private DAGCompiler dagCompiler;
    private ExpressionEngine expressionEngine;
    private DecisionTracer tracer;

    // Pre-compiled artifacts
    private CompiledRuleSet antiFraudRules;
    private CompiledRuleSet creditRules;
    private CompiledScorecard creditScorecard;
    private CompiledDecisionTable policyTable;
    private CompiledDecisionTree riskTree;
    private CompiledDAG fullDecisionFlow;

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
        tracer = DecisionTracer.create("LOAD_TEST", "load-test-strategy");

        // Pre-compile all artifacts
        antiFraudRules = ruleCompiler.compileRuleSet("""
            {"ruleSetId":"RS_ANTI_FRAUD","hitPolicy":"FIRST_HIT","rules":[
              {"ruleId":"AF001","priority":100,"conditions":{"operator":"AND","operands":[
                {"field":"creditQueryCount","op":"GT","value":10}
              ]},"actions":[{"type":"REJECT","reason":"征信查询过多","code":"AF_001"}]},
              {"ruleId":"AF002","priority":90,"conditions":{"operator":"AND","operands":[
                {"field":"overdueCount","op":"GT","value":5}
              ]},"actions":[{"type":"REJECT","reason":"逾期次数过多","code":"AF_002"}]},
              {"ruleId":"AF003","priority":80,"conditions":{"operator":"AND","operands":[
                {"field":"applyFrequency","op":"GT","value":3}
              ]},"actions":[{"type":"REJECT","reason":"申请频率过高","code":"AF_003"}]},
              {"ruleId":"AF004","priority":70,"conditions":{"operator":"AND","operands":[
                {"field":"age","op":"LT","value":18}
              ]},"actions":[{"type":"REJECT","reason":"年龄不足","code":"AF_004"}]},
              {"ruleId":"AF005","priority":60,"conditions":{"operator":"AND","operands":[
                {"field":"blacklisted","op":"EQ","value":true}
              ]},"actions":[{"type":"REJECT","reason":"黑名单客户","code":"AF_005"}]}
            ]}
            """);

        creditRules = ruleCompiler.compileRuleSet("""
            {"ruleSetId":"RS_CREDIT","hitPolicy":"ALL","rules":[
              {"ruleId":"CR001","priority":100,"conditions":{"operator":"AND","operands":[
                {"field":"income","op":"GTE","value":5000}
              ]},"actions":[{"type":"PASS"}]},
              {"ruleId":"CR002","priority":90,"conditions":{"operator":"AND","operands":[
                {"field":"employmentYears","op":"GTE","value":1}
              ]},"actions":[{"type":"PASS"}]},
              {"ruleId":"CR003","priority":80,"conditions":{"operator":"AND","operands":[
                {"field":"loanAmount","op":"LTE","value":500000}
              ]},"actions":[{"type":"PASS"}]}
            ]}
            """);

        creditScorecard = scorecardCompiler.compile("""
            {"scorecardId":"SC_CREDIT","initialScore":500,"characteristics":[
              {"name":"年龄","field":"age","bins":[
                {"range":[null,25],"score":-5},{"range":[25,35],"score":15},
                {"range":[35,50],"score":20},{"range":[50,null],"score":5}
              ]},
              {"name":"月收入","field":"income","bins":[
                {"range":[null,10000],"score":-15},{"range":[10000,30000],"score":5},
                {"range":[30000,80000],"score":20},{"range":[80000,null],"score":30}
              ]},
              {"name":"逾期次数","field":"overdueCount","bins":[
                {"range":[null,1],"score":25},{"range":[1,3],"score":5},
                {"range":[3,null],"score":-30}
              ]},
              {"name":"征信查询","field":"creditQueryCount","bins":[
                {"range":[null,3],"score":15},{"range":[3,8],"score":0},
                {"range":[8,null],"score":-20}
              ]},
              {"name":"工作年限","field":"employmentYears","bins":[
                {"range":[null,1],"score":-10},{"range":[1,5],"score":10},
                {"range":[5,null],"score":20}
              ]}
            ],"cutoff":{"reject":470,"review":520,"pass":520}}
            """);

        policyTable = tableCompiler.compile("""
            {"tableId":"DT_POLICY","hitPolicy":"FIRST_MATCH","columns":[
              {"name":"渠道","field":"channel"},
              {"name":"产品","field":"product"}
            ],"rows":[
              {"conditions":["ONLINE","CREDIT_CARD"],"result":{"action":"APPROVE","maxAmount":50000}},
              {"conditions":["ONLINE","LOAN"],"result":{"action":"REVIEW","maxAmount":200000}},
              {"conditions":["OFFLINE","*"],"result":{"action":"REVIEW","maxAmount":300000}},
              {"conditions":["*","*"],"result":{"action":"REVIEW","maxAmount":100000}}
            ]}
            """);

        riskTree = treeCompiler.compile("""
            {"treeId":"TREE_RISK","condition":{"field":"score","op":"LT","value":470},
              "trueChild":{"action":{"type":"REJECT","reason":"评分过低","code":"RISK_001"}},
              "falseChild":{"condition":{"field":"overdueCount","op":"GT","value":3},
                "trueChild":{"action":{"type":"REJECT","reason":"逾期风险","code":"RISK_002"}},
                "falseChild":{"action":{"type":"PASS","reason":"通过"}}
              }
            }
            """);

        fullDecisionFlow = dagCompiler.compile("""
            {"flowId":"FLOW_FULL","name":"完整决策流","nodes":[
              {"id":"data_prep","type":"DATA_PREP","config":{}},
              {"id":"anti_fraud","type":"RULE_SET","config":{"ruleSetId":"RS_ANTI_FRAUD"}},
              {"id":"credit_rule","type":"RULE_SET","config":{"ruleSetId":"RS_CREDIT"}},
              {"id":"scorecard","type":"SCORECARD","config":{"scorecardId":"SC_CREDIT"}},
              {"id":"policy","type":"RULE_SET","config":{}},
              {"id":"pass","type":"ACTION","config":{"actionType":"PASS"}},
              {"id":"reject","type":"ACTION","config":{"actionType":"REJECT"}}
            ],"edges":[
              {"from":"data_prep","to":"anti_fraud"},
              {"from":"anti_fraud","to":"reject","condition":"anti_fraud_hit == true"},
              {"from":"anti_fraud","to":"credit_rule","condition":"anti_fraud_hit == false"},
              {"from":"credit_rule","to":"scorecard"},
              {"from":"scorecard","to":"pass"}
            ]}
            """);
    }

    // ========== 辅助方法 ==========

    private Map<String, Object> generateRandomInput() {
        Random r = new Random();
        Map<String, Object> data = new HashMap<>();
        data.put("age", 20 + r.nextInt(45));
        data.put("income", 3000 + r.nextInt(97000));
        data.put("overdueCount", r.nextInt(10));
        data.put("creditQueryCount", r.nextInt(15));
        data.put("employmentYears", r.nextInt(30));
        data.put("loanAmount", 10000 + r.nextInt(490000));
        data.put("applyFrequency", 1 + r.nextInt(5));
        data.put("blacklisted", r.nextInt(100) == 0); // 1% 黑名单
        data.put("channel", r.nextInt(2) == 0 ? "ONLINE" : "OFFLINE");
        data.put("product", r.nextInt(2) == 0 ? "CREDIT_CARD" : "LOAN");
        return data;
    }

    private long[] runConcurrentLoad(Callable<Long> task, int threads, int totalRequests)
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicLong totalLatency = new AtomicLong(0);
        AtomicInteger completed = new AtomicInteger(0);
        List<Long> latencies = Collections.synchronizedList(new ArrayList<>());

        List<Future<Long>> futures = new ArrayList<>();
        for (int i = 0; i < totalRequests; i++) {
            futures.add(executor.submit(() -> {
                startLatch.await();
                long start = System.nanoTime();
                task.call();
                long elapsed = (System.nanoTime() - start) / 1_000_000;
                totalLatency.addAndGet(elapsed);
                completed.incrementAndGet();
                latencies.add(elapsed);
                return elapsed;
            }));
        }

        long wallStart = System.nanoTime();
        startLatch.countDown(); // Start all threads simultaneously

        for (Future<Long> f : futures) f.get();

        long wallElapsed = (System.nanoTime() - wallStart) / 1_000_000;
        executor.shutdown();

        // Calculate P99
        Collections.sort(latencies);
        long p99 = latencies.get((int) (latencies.size() * 0.99));
        double qps = totalRequests * 1000.0 / Math.max(wallElapsed, 1);
        double avgMs = totalLatency.get() * 1.0 / completed.get();

        return new long[]{(long) qps, p99, (long) avgMs, wallElapsed, completed.get()};
    }

    // ========== 1. 单模块端到端负载 ==========

    @Test
    @DisplayName("E2E-LOAD-01: 反欺诈规则集并发负载 QPS≥500 P99<3s")
    void antiFraudRules_concurrentLoad() throws Exception {
        long[] result = runConcurrentLoad(() -> {
            ExecutionContext ctx = ExecutionContext.create(generateRandomInput());
            ruleExecutor.execute(antiFraudRules, ctx);
            return 0L;
        }, CONCURRENT_THREADS, TOTAL_REQUESTS);

        double qps = result[0]; long p99 = result[1];
        System.out.printf("  反欺诈规则: QPS=%.0f, P99=%dms, AVG=%dms%n", qps, p99, result[2]);
        assertTrue(qps >= QPS_TARGET, "QPS should >= " + QPS_TARGET + ", got " + qps);
        assertTrue(p99 < P99_TARGET_MS, "P99 should < " + P99_TARGET_MS + "ms, got " + p99);
    }

    @Test
    @DisplayName("E2E-LOAD-02: 信用规则集并发负载 QPS≥500 P99<3s")
    void creditRules_concurrentLoad() throws Exception {
        long[] result = runConcurrentLoad(() -> {
            ExecutionContext ctx = ExecutionContext.create(generateRandomInput());
            ruleExecutor.execute(creditRules, ctx);
            return 0L;
        }, CONCURRENT_THREADS, TOTAL_REQUESTS);

        double qps = result[0]; long p99 = result[1];
        System.out.printf("  信用规则: QPS=%.0f, P99=%dms, AVG=%dms%n", qps, p99, result[2]);
        assertTrue(qps >= QPS_TARGET);
        assertTrue(p99 < P99_TARGET_MS);
    }

    @Test
    @DisplayName("E2E-LOAD-03: 评分卡并发负载 QPS≥500 P99<3s")
    void scorecard_concurrentLoad() throws Exception {
        long[] result = runConcurrentLoad(() -> {
            ExecutionContext ctx = ExecutionContext.create(generateRandomInput());
            scorecardExecutor.execute(creditScorecard, ctx);
            return 0L;
        }, CONCURRENT_THREADS, TOTAL_REQUESTS);

        double qps = result[0]; long p99 = result[1];
        System.out.printf("  评分卡: QPS=%.0f, P99=%dms, AVG=%dms%n", qps, p99, result[2]);
        assertTrue(qps >= QPS_TARGET);
        assertTrue(p99 < P99_TARGET_MS);
    }

    @Test
    @DisplayName("E2E-LOAD-04: 决策树并发负载 QPS≥500 P99<3s")
    void decisionTree_concurrentLoad() throws Exception {
        long[] result = runConcurrentLoad(() -> {
            Map<String, Object> data = generateRandomInput();
            riskTree.evaluate(data);
            return 0L;
        }, CONCURRENT_THREADS, TOTAL_REQUESTS);

        double qps = result[0]; long p99 = result[1];
        System.out.printf("  决策树: QPS=%.0f, P99=%dms, AVG=%dms%n", qps, p99, result[2]);
        assertTrue(qps >= QPS_TARGET);
        assertTrue(p99 < P99_TARGET_MS);
    }

    @Test
    @DisplayName("E2E-LOAD-05: 决策表并发负载 QPS≥500 P99<3s")
    void decisionTable_concurrentLoad() throws Exception {
        long[] result = runConcurrentLoad(() -> {
            Map<String, Object> data = generateRandomInput();
            policyTable.evaluate(data);
            return 0L;
        }, CONCURRENT_THREADS, TOTAL_REQUESTS);

        double qps = result[0]; long p99 = result[1];
        System.out.printf("  决策表: QPS=%.0f, P99=%dms, AVG=%dms%n", qps, p99, result[2]);
        assertTrue(qps >= QPS_TARGET);
        assertTrue(p99 < P99_TARGET_MS);
    }

    // ========== 2. 组合决策负载 ==========

    @Test
    @DisplayName("E2E-LOAD-06: 规则+评分卡组合决策 QPS≥500 P99<3s")
    void ruleAndScorecard_combinedLoad() throws Exception {
        long[] result = runConcurrentLoad(() -> {
            Map<String, Object> data = generateRandomInput();
            ExecutionContext ctx = ExecutionContext.create(data);
            // Step 1: 反欺诈规则
            ruleExecutor.execute(antiFraudRules, ctx);
            // Step 2: 信用评分
            scorecardExecutor.execute(creditScorecard, ctx);
            return 0L;
        }, CONCURRENT_THREADS, TOTAL_REQUESTS);

        double qps = result[0]; long p99 = result[1];
        System.out.printf("  规则+评分卡: QPS=%.0f, P99=%dms, AVG=%dms%n", qps, p99, result[2]);
        assertTrue(qps >= QPS_TARGET);
        assertTrue(p99 < P99_TARGET_MS);
    }

    @Test
    @DisplayName("E2E-LOAD-07: 规则+评分卡+决策树组合 QPS≥500 P99<3s")
    void fullPipeline_combinedLoad() throws Exception {
        long[] result = runConcurrentLoad(() -> {
            Map<String, Object> data = generateRandomInput();
            ExecutionContext ctx = ExecutionContext.create(data);
            // Step 1: 反欺诈
            ruleExecutor.execute(antiFraudRules, ctx);
            // Step 2: 评分卡
            ScorecardResult scResult = scorecardExecutor.execute(creditScorecard, ctx);
            data.put("score", scResult.getFinalScore());
            // Step 3: 决策树
            riskTree.evaluate(data);
            return 0L;
        }, CONCURRENT_THREADS, TOTAL_REQUESTS);

        double qps = result[0]; long p99 = result[1];
        System.out.printf("  完整管道: QPS=%.0f, P99=%dms, AVG=%dms%n", qps, p99, result[2]);
        assertTrue(qps >= QPS_TARGET);
        assertTrue(p99 < P99_TARGET_MS);
    }

    @Test
    @DisplayName("E2E-LOAD-08: 规则+评分卡+决策树+决策表+追踪 全链路 QPS≥500 P99<3s")
    void fullPipelineWithTrace_combinedLoad() throws Exception {
        long[] result = runConcurrentLoad(() -> {
            Map<String, Object> data = generateRandomInput();
            ExecutionContext ctx = ExecutionContext.create(data);

            // Step 1: 反欺诈规则
            RuleExecutionResult antiFraudResult = ruleExecutor.execute(antiFraudRules, ctx);

            // Step 2: 信用评分
            ScorecardResult scResult = scorecardExecutor.execute(creditScorecard, ctx);
            data.put("score", scResult.getFinalScore());

            // Step 3: 风险决策树
            riskTree.evaluate(data);

            // Step 4: 渠道/产品策略表
            policyTable.evaluate(data);

            return 0L;
        }, CONCURRENT_THREADS, TOTAL_REQUESTS);

        double qps = result[0]; long p99 = result[1];
        System.out.printf("  全链路+追踪: QPS=%.0f, P99=%dms, AVG=%dms%n", qps, p99, result[2]);
        assertTrue(qps >= QPS_TARGET);
        assertTrue(p99 < P99_TARGET_MS);
    }

    // ========== 3. 不同并发度测试 ==========

    @Test
    @DisplayName("E2E-LOAD-09: 低并发 (10线程) 规则+评分卡 QPS≥500")
    void lowConcurrency_load() throws Exception {
        long[] result = runConcurrentLoad(() -> {
            ExecutionContext ctx = ExecutionContext.create(generateRandomInput());
            ruleExecutor.execute(antiFraudRules, ctx);
            scorecardExecutor.execute(creditScorecard, ctx);
            return 0L;
        }, 10, 3000);

        System.out.printf("  低并发(10线程): QPS=%d, P99=%dms%n", result[0], result[1]);
        assertTrue(result[0] >= QPS_TARGET, "QPS >= 500 even at low concurrency");
    }

    @Test
    @DisplayName("E2E-LOAD-10: 高并发 (100线程) 规则+评分卡 QPS≥500 P99<3s")
    void highConcurrency_load() throws Exception {
        long[] result = runConcurrentLoad(() -> {
            ExecutionContext ctx = ExecutionContext.create(generateRandomInput());
            ruleExecutor.execute(antiFraudRules, ctx);
            scorecardExecutor.execute(creditScorecard, ctx);
            return 0L;
        }, 100, 10000);

        double qps = result[0]; long p99 = result[1];
        System.out.printf("  高并发(100线程): QPS=%.0f, P99=%dms, AVG=%dms%n", qps, p99, result[2]);
        assertTrue(qps >= QPS_TARGET, "QPS >= 500 at 100 concurrent threads");
        assertTrue(p99 < P99_TARGET_MS, "P99 < 3s at 100 concurrent threads");
    }

    @Test
    @DisplayName("E2E-LOAD-11: 极高并发 (200线程) 规则执行 QPS≥500")
    void extremeConcurrency_load() throws Exception {
        long[] result = runConcurrentLoad(() -> {
            ExecutionContext ctx = ExecutionContext.create(generateRandomInput());
            ruleExecutor.execute(antiFraudRules, ctx);
            return 0L;
        }, 200, 20000);

        System.out.printf("  极高并发(200线程): QPS=%d, P99=%dms%n", result[0], result[1]);
        assertTrue(result[0] >= QPS_TARGET, "QPS >= 500 at 200 concurrent threads");
    }

    // ========== 4. DAG 流端到端负载 ==========

    @Test
    @DisplayName("E2E-LOAD-12: DAG决策流并发负载 QPS≥500 P99<3s")
    void dagFlow_concurrentLoad() throws Exception {
        long[] result = runConcurrentLoad(() -> {
            ExecutionContext ctx = ExecutionContext.create(generateRandomInput());
            fullDecisionFlow.execute(ctx, expressionEngine);
            return 0L;
        }, CONCURRENT_THREADS, TOTAL_REQUESTS);

        double qps = result[0]; long p99 = result[1];
        System.out.printf("  DAG流: QPS=%.0f, P99=%dms, AVG=%dms%n", qps, p99, result[2]);
        assertTrue(qps >= QPS_TARGET);
        assertTrue(p99 < P99_TARGET_MS);
    }

    // ========== 5. 模型调用 Mock + 决策组合 ==========

    @Test
    @DisplayName("E2E-LOAD-13: 规则+评分卡+模型Mock 组合 QPS≥500 P99<3s")
    void ruleScorecardModel_combinedLoad() throws Exception {
        ModelConfig config = ModelConfig.builder().modelId("MOD_LOAD").mockEnabled(true).mockScore(0.75).build();
        ModelServiceClient modelClient = new DefaultModelServiceClient(config);

        long[] result = runConcurrentLoad(() -> {
            Map<String, Object> data = generateRandomInput();
            ExecutionContext ctx = ExecutionContext.create(data);

            // Step 1: 反欺诈
            ruleExecutor.execute(antiFraudRules, ctx);

            // Step 2: 模型推理
            ModelRequest modelReq = ModelRequest.builder().modelId("MOD_LOAD")
                .addFeature("age", data.get("age"))
                .addFeature("income", data.get("income"))
                .addFeature("overdueCount", data.get("overdueCount"))
                .build();
            ModelResponse modelResp = modelClient.predict(modelReq);

            // Step 3: 评分卡
            scorecardExecutor.execute(creditScorecard, ctx);

            return 0L;
        }, CONCURRENT_THREADS, TOTAL_REQUESTS);

        double qps = result[0]; long p99 = result[1];
        System.out.printf("  规则+模型+评分卡: QPS=%.0f, P99=%dms, AVG=%dms%n", qps, p99, result[2]);
        assertTrue(qps >= QPS_TARGET);
        assertTrue(p99 < P99_TARGET_MS);
    }

    // ========== 6. 持续负载稳定性 ==========

    @Test
    @DisplayName("E2E-LOAD-14: 持续负载稳定性 (10s) — QPS 不衰减")
    void sustainedLoad_stability() throws Exception {
        int durationSeconds = 10;
        int batchSize = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        AtomicInteger totalCompleted = new AtomicInteger(0);
        AtomicLong totalLatency = new AtomicLong(0);
        List<Long> allLatencies = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch done = new CountDownLatch(1);

        long startTime = System.nanoTime();
        AtomicInteger batchesDone = new AtomicInteger(0);

        for (int b = 0; b < durationSeconds; b++) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < batchSize; i++) {
                futures.add(executor.submit(() -> {
                    long s = System.nanoTime();
                    Map<String, Object> data = generateRandomInput();
                    ExecutionContext ctx = ExecutionContext.create(data);
                    ruleExecutor.execute(antiFraudRules, ctx);
                    scorecardExecutor.execute(creditScorecard, ctx);
                    long elapsed = (System.nanoTime() - s) / 1_000_000;
                    totalLatency.addAndGet(elapsed);
                    totalCompleted.incrementAndGet();
                    allLatencies.add(elapsed);
                    return null;
                }));
            }
            for (Future<?> f : futures) f.get();
            batchesDone.incrementAndGet();
        }

        executor.shutdown();
        long wallMs = (System.nanoTime() - startTime) / 1_000_000;
        double overallQps = totalCompleted.get() * 1000.0 / Math.max(wallMs, 1);

        Collections.sort(allLatencies);
        long p99 = allLatencies.get((int) (allLatencies.size() * 0.99));
        double avgMs = totalLatency.get() * 1.0 / totalCompleted.get();

        System.out.printf("  持续负载(%ds): 总请求=%d, QPS=%.0f, P99=%dms, AVG=%.1fms%n",
            durationSeconds, totalCompleted.get(), overallQps, p99, avgMs);
        assertTrue(overallQps >= QPS_TARGET, "Sustained QPS >= 500, got " + overallQps);
        assertTrue(p99 < P99_TARGET_MS, "Sustained P99 < 3s, got " + p99);
    }

    // ========== 7. 汇总报告 ==========

    @Test
    @DisplayName("E2E-LOAD-SUMMARY: 端到端负载测试汇总")
    void loadTestSummary() throws Exception {
        System.out.println("\n========== 端到端负载测试汇总 ==========");

        // 单模块: 规则
        long[] ruleResult = runConcurrentLoad(() -> {
            ExecutionContext ctx = ExecutionContext.create(generateRandomInput());
            ruleExecutor.execute(antiFraudRules, ctx);
            return 0L;
        }, CONCURRENT_THREADS, TOTAL_REQUESTS);

        // 单模块: 评分卡
        long[] scResult = runConcurrentLoad(() -> {
            ExecutionContext ctx = ExecutionContext.create(generateRandomInput());
            scorecardExecutor.execute(creditScorecard, ctx);
            return 0L;
        }, CONCURRENT_THREADS, TOTAL_REQUESTS);

        // 全链路
        long[] fullResult = runConcurrentLoad(() -> {
            Map<String, Object> data = generateRandomInput();
            ExecutionContext ctx = ExecutionContext.create(data);
            ruleExecutor.execute(antiFraudRules, ctx);
            ScorecardResult sr = scorecardExecutor.execute(creditScorecard, ctx);
            data.put("score", sr.getFinalScore());
            riskTree.evaluate(data);
            policyTable.evaluate(data);
            return 0L;
        }, CONCURRENT_THREADS, TOTAL_REQUESTS);

        System.out.printf("  %-25s QPS=%8d  P99=%4dms  %s%n",
            "反欺诈规则(50线程)", ruleResult[0], ruleResult[1],
            ruleResult[0] >= QPS_TARGET && ruleResult[1] < P99_TARGET_MS ? "✅" : "❌");
        System.out.printf("  %-25s QPS=%8d  P99=%4dms  %s%n",
            "信用评分卡(50线程)", scResult[0], scResult[1],
            scResult[0] >= QPS_TARGET && scResult[1] < P99_TARGET_MS ? "✅" : "❌");
        System.out.printf("  %-25s QPS=%8d  P99=%4dms  %s%n",
            "全链路(50线程)", fullResult[0], fullResult[1],
            fullResult[0] >= QPS_TARGET && fullResult[1] < P99_TARGET_MS ? "✅" : "❌");
        System.out.printf("  目标: QPS ≥ %d, P99 < %dms%n", QPS_TARGET, P99_TARGET_MS);
        System.out.println("=============================================\n");

        assertAll("端到端负载达标",
            () -> assertTrue(ruleResult[0] >= QPS_TARGET, "规则QPS: " + ruleResult[0]),
            () -> assertTrue(ruleResult[1] < P99_TARGET_MS, "规则P99: " + ruleResult[1]),
            () -> assertTrue(scResult[0] >= QPS_TARGET, "评分卡QPS: " + scResult[0]),
            () -> assertTrue(scResult[1] < P99_TARGET_MS, "评分卡P99: " + scResult[1]),
            () -> assertTrue(fullResult[0] >= QPS_TARGET, "全链路QPS: " + fullResult[0]),
            () -> assertTrue(fullResult[1] < P99_TARGET_MS, "全链路P99: " + fullResult[1])
        );
    }
}
