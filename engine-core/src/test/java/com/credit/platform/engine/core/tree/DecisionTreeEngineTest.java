package com.credit.platform.engine.core.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.credit.platform.engine.common.exception.RuleCompileException;
import com.credit.platform.engine.common.model.ActionType;
import com.credit.platform.engine.core.compiler.model.RuleAction;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 决策树引擎测试。
 */
class DecisionTreeEngineTest {

    private DecisionTreeCompiler compiler;

    @BeforeEach
    void setUp() {
        compiler = new DecisionTreeCompiler();
    }

    /**
     * 构建的决策树:
     * <pre>
     *           age >= 22?
     *          /          \
     *    score >= 550?    REJECT(年龄不足)
     *    /        \
     *  PASS     score >= 500?
     *           /        \
     *        REVIEW     REJECT(评分过低)
     * </pre>
     */
    private static final String CREDIT_TREE_JSON = """
        {
          "treeId": "TREE_CREDIT_001",
          "name": "信贷准入决策树",
          "condition": {"field": "age", "op": "GTE", "value": 22},
          "trueChild": {
            "condition": {"field": "score", "op": "GTE", "value": 550},
            "trueChild": {
              "action": {"type": "PASS", "reason": "准入通过"}
            },
            "falseChild": {
              "condition": {"field": "score", "op": "GTE", "value": 500},
              "trueChild": {
                "action": {"type": "REVIEW", "reason": "评分偏低，人工审核"}
              },
              "falseChild": {
                "action": {"type": "REJECT", "reason": "评分过低", "code": "SCORE_LOW"}
              }
            }
          },
          "falseChild": {
            "action": {"type": "REJECT", "reason": "年龄不足22岁", "code": "AGE_LOW"}
          }
        }
        """;

    @Nested
    @DisplayName("决策树编译测试")
    class CompilationTest {

        @Test
        @DisplayName("成功编译决策树")
        void compileSuccess() {
            CompiledDecisionTree tree = compiler.compile(CREDIT_TREE_JSON);
            assertEquals("TREE_CREDIT_001", tree.getRuleId());
            assertEquals("DECISION_TREE", tree.getRuleType());
            assertTrue(tree.getRoot() instanceof BranchNode);
        }

        @Test
        @DisplayName("缺少 treeId → 失败")
        void missingId() {
            assertThrows(RuleCompileException.class, () -> compiler.compile("""
                {"condition": {"field": "x", "op": "GT", "value": 0},
                 "trueChild": {"action": {"type": "PASS"}},
                 "falseChild": {"action": {"type": "REJECT"}}}
                """));
        }
    }

    @Nested
    @DisplayName("决策树执行测试")
    class ExecutionTest {

        private CompiledDecisionTree tree;

        @BeforeEach
        void compile() {
            tree = compiler.compile(CREDIT_TREE_JSON);
        }

        @Test
        @DisplayName("年龄 < 22 → REJECT 年龄不足")
        void ageTooYoung() {
            RuleAction action = tree.evaluate(Map.of("age", 18, "score", 600));
            assertEquals(ActionType.REJECT, action.getType());
            assertEquals("年龄不足22岁", action.getReason());
        }

        @Test
        @DisplayName("年龄 >= 22 且 score >= 550 → PASS")
        void pass() {
            RuleAction action = tree.evaluate(Map.of("age", 30, "score", 580));
            assertEquals(ActionType.PASS, action.getType());
        }

        @Test
        @DisplayName("年龄 >= 22 且 500 <= score < 550 → REVIEW")
        void review() {
            RuleAction action = tree.evaluate(Map.of("age", 25, "score", 520));
            assertEquals(ActionType.REVIEW, action.getType());
            assertEquals("评分偏低，人工审核", action.getReason());
        }

        @Test
        @DisplayName("年龄 >= 22 且 score < 500 → REJECT 评分过低")
        void scoreTooLow() {
            RuleAction action = tree.evaluate(Map.of("age", 30, "score", 450));
            assertEquals(ActionType.REJECT, action.getType());
            assertEquals("评分过低", action.getReason());
        }

        @Test
        @DisplayName("变量缺失 → 走 false 分支（null 安全）")
        void nullVariables() {
            // age = null → age >= 22 → false → REJECT 年龄不足
            RuleAction action = tree.evaluate(Map.of());
            assertEquals(ActionType.REJECT, action.getType());
        }
    }
}
