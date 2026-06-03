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
}
