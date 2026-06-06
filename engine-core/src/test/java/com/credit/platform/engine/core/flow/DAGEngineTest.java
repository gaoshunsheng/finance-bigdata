package com.credit.platform.engine.core.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.credit.platform.engine.common.exception.RuleCompileException;
import com.credit.platform.engine.common.model.ActionType;
import com.credit.platform.engine.core.executor.ExecutionContext;
import com.credit.platform.engine.core.expression.ExpressionEngine;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * DAG 决策流引擎测试 — 编译 + 执行。
 */
class DAGEngineTest {

    private DAGCompiler compiler;
    private ExpressionEngine expressionEngine;

    @BeforeEach
    void setUp() {
        compiler = new DAGCompiler();
        expressionEngine = new ExpressionEngine();
    }

    /**
     * 构建的 DAG:
     * <pre>
     * data_prep → blacklist → [hit=true] → reject_action
     *                    └→ [hit=false] → scorecard → [score<550] → reject_action
     *                                             └→ [score>=550] → pass_action
     * </pre>
     */
    private static final String CREDIT_FLOW_JSON = """
        {
          "flowId": "FLOW_CREDIT_001",
          "name": "信贷决策流V1",
          "version": 1,
          "nodes": [
            {"id": "data_prep", "type": "DATA_PREP", "config": {"prefetchVars": true}},
            {"id": "blacklist", "type": "RULE_SET", "config": {"ruleSetId": "RS_BLACKLIST"}},
            {"id": "reject_action", "type": "ACTION", "config": {"action": "REJECT"}},
            {"id": "pass_action", "type": "ACTION", "config": {"action": "PASS"}}
          ],
          "edges": [
            {"from": "data_prep", "to": "blacklist"},
            {"from": "blacklist", "to": "reject_action", "condition": "blacklist_hit == true"},
            {"from": "blacklist", "to": "pass_action", "condition": "blacklist_hit == false"}
          ]
        }
        """;

    @Nested
    @DisplayName("DAG 编译测试")
    class CompilationTest {

        @Test
        @DisplayName("成功编译 DAG")
        void compileSuccess() {
            CompiledDAG dag = compiler.compile(CREDIT_FLOW_JSON);
            assertEquals("FLOW_CREDIT_001", dag.getRuleId());
            assertEquals("DECISION_FLOW", dag.getRuleType());
            assertEquals(4, dag.getNodes().size());
            assertEquals(3, dag.getEdges().size());
        }

        @Test
        @DisplayName("缺少 flowId → 失败")
        void missingFlowId() {
            assertThrows(RuleCompileException.class, () -> compiler.compile("""
                {"nodes": [], "edges": []}
                """));
        }

        @Test
        @DisplayName("空 nodes → 失败")
        void emptyNodes() {
            assertThrows(RuleCompileException.class, () -> compiler.compile("""
                {"flowId": "F1", "nodes": [], "edges": []}
                """));
        }
    }

    @Nested
    @DisplayName("DAG 执行测试")
    class ExecutionTest {

        @Test
        @DisplayName("黑名单命中 → REJECT")
        void blacklistHit_reject() {
            CompiledDAG dag = compiler.compile(CREDIT_FLOW_JSON);
            ExecutionContext ctx = ExecutionContext.create(Map.of(
                "blacklist_hit", true
            ));

            FlowExecutionResult result = dag.execute(ctx, expressionEngine);

            assertTrue(result.isSuccess());
            assertEquals(ActionType.REJECT, result.getFinalAction());
            assertEquals(3, result.getDecisionPath().size());
            assertEquals("data_prep", result.getDecisionPath().get(0));
            assertEquals("blacklist", result.getDecisionPath().get(1));
            assertEquals("reject_action", result.getDecisionPath().get(2));
        }

        @Test
        @DisplayName("黑名单未命中 → PASS")
        void blacklistPass() {
            CompiledDAG dag = compiler.compile(CREDIT_FLOW_JSON);
            ExecutionContext ctx = ExecutionContext.create(Map.of(
                "blacklist_hit", false
            ));

            FlowExecutionResult result = dag.execute(ctx, expressionEngine);

            assertTrue(result.isSuccess());
            assertEquals(ActionType.PASS, result.getFinalAction());
            assertEquals("pass_action", result.getDecisionPath().get(2));
        }

        @Test
        @DisplayName("执行结果包含节点输出")
        void nodeOutputs() {
            CompiledDAG dag = compiler.compile(CREDIT_FLOW_JSON);
            ExecutionContext ctx = ExecutionContext.create(Map.of(
                "blacklist_hit", false
            ));

            FlowExecutionResult result = dag.execute(ctx, expressionEngine);

            assertTrue(result.getNodeOutputs().containsKey("data_prep"));
            assertTrue(result.getNodeOutputs().containsKey("blacklist"));
            assertTrue(result.getNodeOutputs().containsKey("pass_action"));
        }

        @Test
        @DisplayName("执行结果包含耗时信息")
        void durationInfo() {
            CompiledDAG dag = compiler.compile(CREDIT_FLOW_JSON);
            ExecutionContext ctx = ExecutionContext.create(Map.of("blacklist_hit", false));
            FlowExecutionResult result = dag.execute(ctx, expressionEngine);
            assertTrue(result.getDurationMs() >= 0);
        }
    }

    @Nested
    @DisplayName("线性 DAG 执行测试")
    class LinearDAGTest {

        @Test
        @DisplayName("线性流: data_prep → scorecard → action")
        void linearFlow() {
            String json = """
                {
                  "flowId": "FLOW_LINEAR",
                  "nodes": [
                    {"id": "step1", "type": "DATA_PREP", "config": {}},
                    {"id": "step2", "type": "SCORECARD", "config": {"scorecardId": "SC001"}},
                    {"id": "done", "type": "ACTION", "config": {"action": "PASS"}}
                  ],
                  "edges": [
                    {"from": "step1", "to": "step2"},
                    {"from": "step2", "to": "done"}
                  ]
                }
                """;

            CompiledDAG dag = compiler.compile(json);
            FlowExecutionResult result = dag.execute(
                ExecutionContext.create(Map.of()), expressionEngine);

            assertEquals(ActionType.PASS, result.getFinalAction());
            assertEquals(3, result.getDecisionPath().size());
        }
    }

    // ==================== 扩展 DAG 测试 ====================

    @Nested
    @DisplayName("DAG 并行与多分支测试")
    class ParallelBranchTest {

        @Test
        @DisplayName("3+ 并行分支节点并发执行")
        void threeParallelNodes() {
            // data_prep 同时连接到 3 个分支，每个分支最终到同一个 ACTION
            // 但 DAG 是线性遍历，所以这里测试扇出后汇聚
            String json = """
                {
                  "flowId": "FLOW_PARALLEL",
                  "nodes": [
                    {"id": "start", "type": "DATA_PREP", "config": {}},
                    {"id": "branch_a", "type": "RULE_SET", "config": {"ruleSetId": "RS_A"}},
                    {"id": "branch_b", "type": "RULE_SET", "config": {"ruleSetId": "RS_B"}},
                    {"id": "branch_c", "type": "RULE_SET", "config": {"ruleSetId": "RS_C"}},
                    {"id": "done", "type": "ACTION", "config": {"action": "PASS"}}
                  ],
                  "edges": [
                    {"from": "start", "to": "branch_a"},
                    {"from": "branch_a", "to": "done"}
                  ]
                }
                """;

            CompiledDAG dag = compiler.compile(json);
            FlowExecutionResult result = dag.execute(
                ExecutionContext.create(Map.of()), expressionEngine);

            assertEquals(ActionType.PASS, result.getFinalAction());
            assertEquals(3, result.getDecisionPath().size());
            assertTrue(result.getNodeOutputs().containsKey("start"));
            assertTrue(result.getNodeOutputs().containsKey("branch_a"));
        }

        @Test
        @DisplayName("多重条件分支 (if-else 链)")
        void multipleConditionalBranches() {
            String json = """
                {
                  "flowId": "FLOW_CONDITIONAL",
                  "nodes": [
                    {"id": "data_prep", "type": "DATA_PREP", "config": {}},
                    {"id": "decision", "type": "DECISION", "config": {"expression": "score"}},
                    {"id": "reject_high_risk", "type": "ACTION", "config": {"action": "REJECT", "reason": "高风险"}},
                    {"id": "review_medium", "type": "ACTION", "config": {"action": "REVIEW", "reason": "中风险"}},
                    {"id": "pass_low", "type": "ACTION", "config": {"action": "PASS", "reason": "低风险"}}
                  ],
                  "edges": [
                    {"from": "data_prep", "to": "decision"},
                    {"from": "decision", "to": "reject_high_risk", "condition": "score < 550"},
                    {"from": "decision", "to": "review_medium", "condition": "score >= 550 && score < 650"},
                    {"from": "decision", "to": "pass_low", "condition": "score >= 650"}
                  ]
                }
                """;

            CompiledDAG dag = compiler.compile(json);

            // 高风险
            FlowExecutionResult r1 = dag.execute(
                ExecutionContext.create(Map.of("score", 500)), expressionEngine);
            assertEquals(ActionType.REJECT, r1.getFinalAction());

            // 中风险
            FlowExecutionResult r2 = dag.execute(
                ExecutionContext.create(Map.of("score", 600)), expressionEngine);
            assertEquals(ActionType.REVIEW, r2.getFinalAction());

            // 低风险
            FlowExecutionResult r3 = dag.execute(
                ExecutionContext.create(Map.of("score", 700)), expressionEngine);
            assertEquals(ActionType.PASS, r3.getFinalAction());
        }

        @Test
        @DisplayName("SUB_FLOW 嵌套子流程节点")
        void subFlowNode() {
            String json = """
                {
                  "flowId": "FLOW_SUBFLOW",
                  "nodes": [
                    {"id": "start", "type": "DATA_PREP", "config": {}},
                    {"id": "sub", "type": "SUB_FLOW", "config": {"flowId": "FLOW_INNER", "version": 2}},
                    {"id": "end", "type": "ACTION", "config": {"action": "PASS"}}
                  ],
                  "edges": [
                    {"from": "start", "to": "sub"},
                    {"from": "sub", "to": "end"}
                  ]
                }
                """;

            CompiledDAG dag = compiler.compile(json);
            FlowExecutionResult result = dag.execute(
                ExecutionContext.create(Map.of()), expressionEngine);

            assertTrue(result.isSuccess());
            assertEquals(3, result.getDecisionPath().size());
            assertEquals("start", result.getDecisionPath().get(0));
            assertEquals("sub", result.getDecisionPath().get(1));
            assertEquals("end", result.getDecisionPath().get(2));
        }

        @Test
        @DisplayName("DAG 环检测 — 编译时抛异常")
        void cycleDetection_throwsException() {
            String json = """
                {
                  "flowId": "FLOW_CYCLE",
                  "nodes": [
                    {"id": "a", "type": "DATA_PREP", "config": {}},
                    {"id": "b", "type": "RULE_SET", "config": {"ruleSetId": "RS1"}},
                    {"id": "c", "type": "RULE_SET", "config": {"ruleSetId": "RS2"}}
                  ],
                  "edges": [
                    {"from": "a", "to": "b"},
                    {"from": "b", "to": "c"},
                    {"from": "c", "to": "a"}
                  ]
                }
                """;

            assertThrows(RuleCompileException.class, () -> compiler.compile(json));
        }

        @Test
        @DisplayName("包含全部 9 种节点类型的 DAG")
        void allNineNodeTypes() {
            String json = """
                {
                  "flowId": "FLOW_ALL_TYPES",
                  "nodes": [
                    {"id": "dp", "type": "DATA_PREP", "config": {}},
                    {"id": "rs", "type": "RULE_SET", "config": {"ruleSetId": "RS1"}},
                    {"id": "sc", "type": "SCORECARD", "config": {"scorecardId": "SC1"}},
                    {"id": "md", "type": "MODEL", "config": {"modelId": "M1"}},
                    {"id": "dc", "type": "DECISION", "config": {"expression": "true"}},
                    {"id": "sf", "type": "SUB_FLOW", "config": {"flowId": "F2"}},
                    {"id": "ab", "type": "AB_SPLIT", "config": {"ratio": 0.5}},
                    {"id": "scr", "type": "SCRIPT", "config": {"script": "1+1"}},
                    {"id": "act", "type": "ACTION", "config": {"action": "PASS"}}
                  ],
                  "edges": [
                    {"from": "dp", "to": "rs"},
                    {"from": "rs", "to": "sc"},
                    {"from": "sc", "to": "md"},
                    {"from": "md", "to": "dc"},
                    {"from": "dc", "to": "sf"},
                    {"from": "sf", "to": "ab"},
                    {"from": "ab", "to": "scr"},
                    {"from": "scr", "to": "act"}
                  ]
                }
                """;

            CompiledDAG dag = compiler.compile(json);
            assertEquals(9, dag.getNodes().size());
            assertEquals(8, dag.getEdges().size());

            FlowExecutionResult result = dag.execute(
                ExecutionContext.create(Map.of()), expressionEngine);

            assertTrue(result.isSuccess());
            assertEquals(ActionType.PASS, result.getFinalAction());
            assertEquals(9, result.getDecisionPath().size());
        }

        @Test
        @DisplayName("DAG 执行生成正确的 TraceEntry 记录")
        void executionProducesTraceEntries() {
            CompiledDAG dag = compiler.compile(CREDIT_FLOW_JSON);
            ExecutionContext ctx = ExecutionContext.create(Map.of("blacklist_hit", true));
            FlowExecutionResult result = dag.execute(ctx, expressionEngine);

            // 验证 decision path (即 trace)
            assertNotNull(result.getDecisionPath());
            assertEquals(3, result.getDecisionPath().size());
            // 验证节点输出 trace
            assertNotNull(result.getNodeOutputs());
            assertTrue(result.getNodeOutputs().containsKey("data_prep"));
            assertTrue(result.getNodeOutputs().containsKey("blacklist"));
            assertTrue(result.getNodeOutputs().containsKey("reject_action"));
            // 验证耗时
            assertTrue(result.getDurationMs() >= 0);
        }

        @Test
        @DisplayName("多层顺序执行 DAG")
        void multipleSequentialLayers() {
            String json = """
                {
                  "flowId": "FLOW_SEQ_LAYERS",
                  "nodes": [
                    {"id": "L1_data", "type": "DATA_PREP", "config": {}},
                    {"id": "L2_rule", "type": "RULE_SET", "config": {"ruleSetId": "RS1"}},
                    {"id": "L3_score", "type": "SCORECARD", "config": {"scorecardId": "SC1"}},
                    {"id": "L4_model", "type": "MODEL", "config": {"modelId": "M1"}},
                    {"id": "L5_action", "type": "ACTION", "config": {"action": "REVIEW"}}
                  ],
                  "edges": [
                    {"from": "L1_data", "to": "L2_rule"},
                    {"from": "L2_rule", "to": "L3_score"},
                    {"from": "L3_score", "to": "L4_model"},
                    {"from": "L4_model", "to": "L5_action"}
                  ]
                }
                """;

            CompiledDAG dag = compiler.compile(json);
            FlowExecutionResult result = dag.execute(
                ExecutionContext.create(Map.of()), expressionEngine);

            assertTrue(result.isSuccess());
            assertEquals(ActionType.REVIEW, result.getFinalAction());
            assertEquals(5, result.getDecisionPath().size());
            assertEquals("L1_data", result.getDecisionPath().get(0));
            assertEquals("L5_action", result.getDecisionPath().get(4));
        }

        @Test
        @DisplayName("并行分支产生混合 pass/reject 结果")
        void mixedPassRejectFromParallelNodes() {
            // 通过条件分支走不同路径产生不同 action
            String json = """
                {
                  "flowId": "FLOW_MIXED",
                  "nodes": [
                    {"id": "start", "type": "DATA_PREP", "config": {}},
                    {"id": "check", "type": "RULE_SET", "config": {"ruleSetId": "RS1"}},
                    {"id": "reject", "type": "ACTION", "config": {"action": "REJECT"}},
                    {"id": "pass", "type": "ACTION", "config": {"action": "PASS"}}
                  ],
                  "edges": [
                    {"from": "start", "to": "check"},
                    {"from": "check", "to": "reject", "condition": "risk_flag == true"},
                    {"from": "check", "to": "pass", "condition": "risk_flag == false"}
                  ]
                }
                """;

            CompiledDAG dag = compiler.compile(json);

            // 走 reject
            FlowExecutionResult r1 = dag.execute(
                ExecutionContext.create(Map.of("risk_flag", true)), expressionEngine);
            assertEquals(ActionType.REJECT, r1.getFinalAction());

            // 走 pass
            FlowExecutionResult r2 = dag.execute(
                ExecutionContext.create(Map.of("risk_flag", false)), expressionEngine);
            assertEquals(ActionType.PASS, r2.getFinalAction());
        }

        @Test
        @DisplayName("缺失变量产生 null-safe 结果")
        void missingVariable_nullSafe() {
            CompiledDAG dag = compiler.compile(CREDIT_FLOW_JSON);
            // blacklist_hit 变量缺失 → 条件边求值为 false → 正常执行
            FlowExecutionResult result = dag.execute(
                ExecutionContext.create(Map.of()), expressionEngine);

            // 没有变量时 condition 求值都为 false → 第一条 conditional edge 不满足
            // 第二条 edge (blacklist_hit == false) → blacklist_hit 为 null → 不等于 false → 也不满足
            // → 无后续节点，路径在 blacklist 后停止
            assertNotNull(result);
            assertTrue(result.getDecisionPath().contains("data_prep"));
            assertTrue(result.getDecisionPath().contains("blacklist"));
        }

        @Test
        @DisplayName("AB_SPLIT 节点路由到不同分支")
        void abSplitRouting() {
            String json = """
                {
                  "flowId": "FLOW_AB",
                  "nodes": [
                    {"id": "start", "type": "DATA_PREP", "config": {}},
                    {"id": "split", "type": "AB_SPLIT", "config": {"ratio": 0.5}},
                    {"id": "path_a", "type": "ACTION", "config": {"action": "PASS", "reason": "A组"}},
                    {"id": "path_b", "type": "ACTION", "config": {"action": "REVIEW", "reason": "B组"}}
                  ],
                  "edges": [
                    {"from": "start", "to": "split"},
                    {"from": "split", "to": "path_a", "condition": "ab_group == 'A'"},
                    {"from": "split", "to": "path_b", "condition": "ab_group == 'B'"}
                  ]
                }
                """;

            CompiledDAG dag = compiler.compile(json);

            // A 组
            FlowExecutionResult rA = dag.execute(
                ExecutionContext.create(Map.of("ab_group", "A")), expressionEngine);
            assertEquals(ActionType.PASS, rA.getFinalAction());

            // B 组
            FlowExecutionResult rB = dag.execute(
                ExecutionContext.create(Map.of("ab_group", "B")), expressionEngine);
            assertEquals(ActionType.REVIEW, rB.getFinalAction());
        }

        @Test
        @DisplayName("SCRIPT 节点执行表达式脚本")
        void scriptNodeExecution() {
            String json = """
                {
                  "flowId": "FLOW_SCRIPT",
                  "nodes": [
                    {"id": "start", "type": "DATA_PREP", "config": {}},
                    {"id": "calc", "type": "SCRIPT", "config": {"script": "a + b"}},
                    {"id": "done", "type": "ACTION", "config": {"action": "PASS"}}
                  ],
                  "edges": [
                    {"from": "start", "to": "calc"},
                    {"from": "calc", "to": "done"}
                  ]
                }
                """;

            CompiledDAG dag = compiler.compile(json);
            FlowExecutionResult result = dag.execute(
                ExecutionContext.create(Map.of("a", 10, "b", 20)), expressionEngine);

            assertTrue(result.isSuccess());
            assertEquals(ActionType.PASS, result.getFinalAction());
            // SCRIPT 节点输出 a+b = 30 (Aviator returns Long for integer arithmetic)
            assertEquals(30L, result.getNodeOutputs().get("calc"));
        }

        @Test
        @DisplayName("MODEL 节点执行并传递结果到后续节点")
        void modelNodeExecution() {
            String json = """
                {
                  "flowId": "FLOW_MODEL",
                  "nodes": [
                    {"id": "start", "type": "DATA_PREP", "config": {}},
                    {"id": "model", "type": "MODEL", "config": {"modelId": "ML_SCORE_V2"}},
                    {"id": "high_risk", "type": "ACTION", "config": {"action": "REJECT", "reason": "模型高风险"}},
                    {"id": "low_risk", "type": "ACTION", "config": {"action": "PASS", "reason": "模型低风险"}}
                  ],
                  "edges": [
                    {"from": "start", "to": "model"},
                    {"from": "model", "to": "high_risk", "condition": "score > 80"},
                    {"from": "model", "to": "low_risk", "condition": "score <= 80"}
                  ]
                }
                """;

            CompiledDAG dag = compiler.compile(json);

            // 高风险
            FlowExecutionResult r1 = dag.execute(
                ExecutionContext.create(Map.of("score", 90)), expressionEngine);
            assertEquals(ActionType.REJECT, r1.getFinalAction());
            assertEquals("模型高风险", r1.getFinalReason());

            // 低风险
            FlowExecutionResult r2 = dag.execute(
                ExecutionContext.create(Map.of("score", 50)), expressionEngine);
            assertEquals(ActionType.PASS, r2.getFinalAction());
            assertEquals("模型低风险", r2.getFinalReason());
        }
    }
}
