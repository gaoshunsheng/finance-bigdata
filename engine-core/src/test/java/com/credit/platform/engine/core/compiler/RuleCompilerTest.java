package com.credit.platform.engine.core.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.credit.platform.engine.common.exception.RuleCompileException;
import com.credit.platform.engine.common.model.ActionType;
import com.credit.platform.engine.core.compiler.model.ComparisonConditionNode;
import com.credit.platform.engine.core.compiler.model.ComparisonOperator;
import com.credit.platform.engine.core.compiler.model.ConditionNode;
import com.credit.platform.engine.core.compiler.model.HitPolicy;
import com.credit.platform.engine.core.compiler.model.LogicalConditionNode;
import com.credit.platform.engine.core.compiler.model.RuleAction;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link RuleCompiler} 和 AST 模型的综合测试。
 */
class RuleCompilerTest {

    private RuleCompiler compiler;

    @BeforeEach
    void setUp() {
        compiler = new RuleCompiler();
    }

    // ==================== 条件节点 AST 测试 ====================

    @Nested
    @DisplayName("ComparisonConditionNode 测试")
    class ComparisonNodeTest {

        @Test
        @DisplayName("GTE: age >= 22 → age=25 命中")
        void gte_hit() {
            ConditionNode node = new ComparisonConditionNode("age", ComparisonOperator.GTE, 22);
            Map<String, Object> vars = Map.of("age", 25);
            assertTrue(node.evaluate(vars));
        }

        @Test
        @DisplayName("GTE: age >= 22 → age=20 未命中")
        void gte_miss() {
            ConditionNode node = new ComparisonConditionNode("age", ComparisonOperator.GTE, 22);
            Map<String, Object> vars = Map.of("age", 20);
            assertFalse(node.evaluate(vars));
        }

        @Test
        @DisplayName("LT: score < 550 → score=500 命中")
        void lt_hit() {
            ConditionNode node = new ComparisonConditionNode("score", ComparisonOperator.LT, 550);
            assertTrue(node.evaluate(Map.of("score", 500)));
        }

        @Test
        @DisplayName("EQ: status == 'ACTIVE' → 命中")
        void eq_string_hit() {
            ConditionNode node = new ComparisonConditionNode("status", ComparisonOperator.EQ, "ACTIVE");
            assertTrue(node.evaluate(Map.of("status", "ACTIVE")));
        }

        @Test
        @DisplayName("NEQ: level != 'C' → 命中")
        void neq_hit() {
            ConditionNode node = new ComparisonConditionNode("level", ComparisonOperator.NEQ, "C");
            assertTrue(node.evaluate(Map.of("level", "A")));
        }

        @Test
        @DisplayName("BETWEEN: value in [20, 30] → value=25 命中")
        void between_hit() {
            ConditionNode node = new ComparisonConditionNode("value", ComparisonOperator.BETWEEN,
                new Object[]{20, 30});
            assertTrue(node.evaluate(Map.of("value", 25)));
        }

        @Test
        @DisplayName("BETWEEN: value in [20, 30] → value=15 未命中")
        void between_miss() {
            ConditionNode node = new ComparisonConditionNode("value", ComparisonOperator.BETWEEN,
                new Object[]{20, 30});
            assertFalse(node.evaluate(Map.of("value", 15)));
        }

        @Test
        @DisplayName("BETWEEN: null 边界 → null下界表示无下限")
        void between_nullLower() {
            ConditionNode node = new ComparisonConditionNode("value", ComparisonOperator.BETWEEN,
                new Object[]{null, 30});
            assertTrue(node.evaluate(Map.of("value", 10)));
        }

        @Test
        @DisplayName("BETWEEN: null 边界 → null上界表示无上限")
        void between_nullUpper() {
            ConditionNode node = new ComparisonConditionNode("value", ComparisonOperator.BETWEEN,
                new Object[]{20, null});
            assertTrue(node.evaluate(Map.of("value", 100)));
        }

        @Test
        @DisplayName("IN: status in ['A','B','C'] → status='B' 命中")
        void in_hit() {
            ConditionNode node = new ComparisonConditionNode("status", ComparisonOperator.IN,
                List.of("A", "B", "C"));
            assertTrue(node.evaluate(Map.of("status", "B")));
        }

        @Test
        @DisplayName("IN: status in ['A','B','C'] → status='D' 未命中")
        void in_miss() {
            ConditionNode node = new ComparisonConditionNode("status", ComparisonOperator.IN,
                List.of("A", "B", "C"));
            assertFalse(node.evaluate(Map.of("status", "D")));
        }

        @Test
        @DisplayName("null 字段值 → 任何比较都返回 false")
        void nullFieldValue_returnsFalse() {
            ConditionNode node = new ComparisonConditionNode("age", ComparisonOperator.GTE, 22);
            Map<String, Object> vars = new HashMap<>();
            vars.put("age", null);
            assertFalse(node.evaluate(vars));
        }

        @Test
        @DisplayName("字段不存在 → 返回 false")
        void missingField_returnsFalse() {
            ConditionNode node = new ComparisonConditionNode("age", ComparisonOperator.GTE, 22);
            assertFalse(node.evaluate(new HashMap<>()));
        }

        @Test
        @DisplayName("Number 跨类型比较: Integer vs Long → age=25(Integer) >= 22(Long)")
        void numberCrossType() {
            ConditionNode node = new ComparisonConditionNode("age", ComparisonOperator.GTE, 22L);
            Map<String, Object> vars = new HashMap<>();
            vars.put("age", 25); // Integer
            assertTrue(node.evaluate(vars));
        }

        @Test
        @DisplayName("getReferencedFields 返回正确的字段名")
        void referencedFields() {
            ComparisonConditionNode node = new ComparisonConditionNode("age", ComparisonOperator.GTE, 22);
            Set<String> fields = node.getReferencedFields();
            assertEquals(1, fields.size());
            assertTrue(fields.contains("age"));
        }
    }

    // ==================== 逻辑组合节点测试 ====================

    @Nested
    @DisplayName("LogicalConditionNode 测试")
    class LogicalNodeTest {

        @Test
        @DisplayName("AND: 两个条件都为 true → true")
        void and_bothTrue() {
            ConditionNode node = LogicalConditionNode.and(
                new ComparisonConditionNode("age", ComparisonOperator.GTE, 22),
                new ComparisonConditionNode("age", ComparisonOperator.LTE, 60)
            );
            assertTrue(node.evaluate(Map.of("age", 25)));
        }

        @Test
        @DisplayName("AND: 一个条件为 false → false (短路)")
        void and_oneFalse() {
            ConditionNode node = LogicalConditionNode.and(
                new ComparisonConditionNode("age", ComparisonOperator.GTE, 22),
                new ComparisonConditionNode("age", ComparisonOperator.LTE, 60)
            );
            assertFalse(node.evaluate(Map.of("age", 15)));
        }

        @Test
        @DisplayName("OR: 一个条件为 true → true (短路)")
        void or_oneTrue() {
            ConditionNode node = LogicalConditionNode.or(
                new ComparisonConditionNode("level", ComparisonOperator.EQ, "A"),
                new ComparisonConditionNode("level", ComparisonOperator.EQ, "B")
            );
            assertTrue(node.evaluate(Map.of("level", "B")));
        }

        @Test
        @DisplayName("OR: 两个都为 false → false")
        void or_bothFalse() {
            ConditionNode node = LogicalConditionNode.or(
                new ComparisonConditionNode("level", ComparisonOperator.EQ, "A"),
                new ComparisonConditionNode("level", ComparisonOperator.EQ, "B")
            );
            assertFalse(node.evaluate(Map.of("level", "C")));
        }

        @Test
        @DisplayName("NOT: 取反")
        void not_negation() {
            ConditionNode node = LogicalConditionNode.not(
                new ComparisonConditionNode("blacklist", ComparisonOperator.EQ, true)
            );
            assertTrue(node.evaluate(Map.of("blacklist", false)));
        }

        @Test
        @DisplayName("嵌套: (age >= 22 AND age <= 60) OR level == 'VIP'")
        void nestedTree() {
            ConditionNode tree = LogicalConditionNode.or(
                LogicalConditionNode.and(
                    new ComparisonConditionNode("age", ComparisonOperator.GTE, 22),
                    new ComparisonConditionNode("age", ComparisonOperator.LTE, 60)
                ),
                new ComparisonConditionNode("level", ComparisonOperator.EQ, "VIP")
            );
            // 不满足年龄但 VIP → true
            assertTrue(tree.evaluate(Map.of("age", 15, "level", "VIP")));
            // 满足年龄但非 VIP → true
            assertTrue(tree.evaluate(Map.of("age", 25, "level", "NORMAL")));
            // 都不满足 → false
            assertFalse(tree.evaluate(Map.of("age", 15, "level", "NORMAL")));
        }

        @Test
        @DisplayName("getReferencedFields 收集所有子节点字段")
        void referencedFields_nested() {
            ConditionNode tree = LogicalConditionNode.and(
                new ComparisonConditionNode("age", ComparisonOperator.GTE, 22),
                new ComparisonConditionNode("level", ComparisonOperator.EQ, "A")
            );
            Set<String> fields = tree.getReferencedFields();
            assertEquals(2, fields.size());
            assertTrue(fields.contains("age"));
            assertTrue(fields.contains("level"));
        }
    }

    // ==================== JSON 编译测试 ====================

    @Nested
    @DisplayName("RuleCompiler JSON 编译测试")
    class JsonCompilationTest {

        @Test
        @DisplayName("编译简单的条件规则")
        void compileSimpleConditionRule() {
            String json = """
                {
                  "ruleId": "R001",
                  "name": "年龄准入检查",
                  "priority": 100,
                  "conditions": {
                    "operator": "AND",
                    "operands": [
                      {"field": "age", "op": "GTE", "value": 22},
                      {"field": "age", "op": "LTE", "value": 60}
                    ]
                  },
                  "actions": [
                    {"type": "REJECT", "reason": "年龄不符", "code": "AGE_001"}
                  ]
                }
                """;

            CompiledConditionRule rule = compiler.compileConditionRule(json);

            assertEquals("R001", rule.getRuleId());
            assertEquals("年龄准入检查", rule.getName());
            assertEquals("CONDITION", rule.getRuleType());
            assertEquals(100, rule.getPriority());
            assertEquals(1, rule.getVersion());
            assertEquals(1, rule.getActions().size());
            assertEquals(ActionType.REJECT, rule.getActions().get(0).getType());
            assertEquals("年龄不符", rule.getActions().get(0).getReason());
            assertEquals("AGE_001", rule.getActions().get(0).getCode());
            assertTrue(rule.getReferencedFields().contains("age"));
        }

        @Test
        @DisplayName("编译带 BETWEEN 操作符的规则")
        void compileBetweenRule() {
            String json = """
                {
                  "ruleId": "R002",
                  "name": "信用分范围",
                  "priority": 50,
                  "conditions": {
                    "field": "credit_score",
                    "op": "BETWEEN",
                    "value": [300, 850]
                  },
                  "actions": [
                    {"type": "PASS"}
                  ]
                }
                """;

            CompiledConditionRule rule = compiler.compileConditionRule(json);
            assertEquals("R002", rule.getRuleId());
            assertTrue(rule.getReferencedFields().contains("credit_score"));
        }

        @Test
        @DisplayName("编译带 IN 操作符的规则")
        void compileInRule() {
            String json = """
                {
                  "ruleId": "R003",
                  "name": "客户类型检查",
                  "priority": 80,
                  "conditions": {
                    "field": "customer_type",
                    "op": "IN",
                    "value": ["VIP", "GOLD", "SILVER"]
                  },
                  "actions": [
                    {"type": "PASS"}
                  ]
                }
                """;

            CompiledConditionRule rule = compiler.compileConditionRule(json);
            assertEquals("R003", rule.getRuleId());
        }

        @Test
        @DisplayName("编译嵌套逻辑条件 (NOT + AND)")
        void compileNestedLogic() {
            String json = """
                {
                  "ruleId": "R004",
                  "name": "非黑名单且高风险地区",
                  "priority": 90,
                  "conditions": {
                    "operator": "AND",
                    "operands": [
                      {
                        "operator": "NOT",
                        "operands": [
                          {"field": "blacklist", "op": "EQ", "value": true}
                        ]
                      },
                      {"field": "risk_level", "op": "GTE", "value": 3}
                    ]
                  },
                  "actions": [
                    {"type": "MANUAL", "reason": "高风险人工审核", "code": "RISK_001"}
                  ]
                }
                """;

            CompiledConditionRule rule = compiler.compileConditionRule(json);
            assertEquals("R004", rule.getRuleId());
            assertEquals(ActionType.MANUAL, rule.getActions().get(0).getType());
        }

        @Test
        @DisplayName("编译规则集 — FIRST_HIT 策略")
        void compileRuleSet_firstHit() {
            String json = """
                {
                  "ruleSetId": "RS_BLACKLIST",
                  "name": "黑名单规则集",
                  "hitPolicy": "FIRST_HIT",
                  "rules": [
                    {
                      "ruleId": "R001",
                      "name": "身份证黑名单",
                      "priority": 100,
                      "conditions": {"field": "id_blacklist", "op": "EQ", "value": true},
                      "actions": [{"type": "REJECT", "reason": "身份证命中黑名单", "code": "BL_001"}]
                    },
                    {
                      "ruleId": "R002",
                      "name": "手机号黑名单",
                      "priority": 90,
                      "conditions": {"field": "phone_blacklist", "op": "EQ", "value": true},
                      "actions": [{"type": "REJECT", "reason": "手机号命中黑名单", "code": "BL_002"}]
                    }
                  ]
                }
                """;

            CompiledRuleSet ruleSet = compiler.compileRuleSet(json);

            assertEquals("RS_BLACKLIST", ruleSet.getRuleId());
            assertEquals(HitPolicy.FIRST_HIT, ruleSet.getHitPolicy());
            assertEquals(2, ruleSet.getRules().size());
            assertEquals(2, ruleSet.getReferencedFields().size());
        }

        @Test
        @DisplayName("编译规则集 — PRIORITY 策略自动按优先级排序")
        void compileRuleSet_prioritySorts() {
            String json = """
                {
                  "ruleSetId": "RS_PRIORITY",
                  "name": "优先级测试",
                  "hitPolicy": "PRIORITY",
                  "rules": [
                    {
                      "ruleId": "R_LOW",
                      "name": "低优先级",
                      "priority": 10,
                      "conditions": {"field": "x", "op": "GT", "value": 0},
                      "actions": [{"type": "REVIEW"}]
                    },
                    {
                      "ruleId": "R_HIGH",
                      "name": "高优先级",
                      "priority": 100,
                      "conditions": {"field": "x", "op": "GT", "value": 0},
                      "actions": [{"type": "REJECT"}]
                    }
                  ]
                }
                """;

            CompiledRuleSet ruleSet = compiler.compileRuleSet(json);

            // PRIORITY 模式下，高优先级排前面
            assertEquals("R_HIGH", ruleSet.getRules().get(0).getRuleId());
            assertEquals("R_LOW", ruleSet.getRules().get(1).getRuleId());
        }

        // ==================== 编译错误测试 ====================

        @Test
        @DisplayName("缺少 ruleId → 编译失败")
        void compile_missingRuleId_throws() {
            String json = """
                {
                  "conditions": {"field": "age", "op": "GTE", "value": 22},
                  "actions": [{"type": "REJECT"}]
                }
                """;
            assertThrows(RuleCompileException.class, () -> compiler.compileConditionRule(json));
        }

        @Test
        @DisplayName("缺少 conditions → 编译失败")
        void compile_missingConditions_throws() {
            String json = """
                {
                  "ruleId": "R001",
                  "actions": [{"type": "REJECT"}]
                }
                """;
            assertThrows(RuleCompileException.class, () -> compiler.compileConditionRule(json));
        }

        @Test
        @DisplayName("空 actions → 编译失败")
        void compile_emptyActions_throws() {
            String json = """
                {
                  "ruleId": "R001",
                  "conditions": {"field": "age", "op": "GTE", "value": 22},
                  "actions": []
                }
                """;
            assertThrows(RuleCompileException.class, () -> compiler.compileConditionRule(json));
        }

        @Test
        @DisplayName("无效 JSON → 编译失败")
        void compile_invalidJson_throws() {
            assertThrows(RuleCompileException.class, () -> compiler.compileConditionRule("not json"));
        }

        @Test
        @DisplayName("规则集缺少 rules → 编译失败")
        void compileRuleSet_missingRules_throws() {
            String json = """
                {
                  "ruleSetId": "RS001",
                  "hitPolicy": "FIRST_HIT"
                }
                """;
            assertThrows(RuleCompileException.class, () -> compiler.compileRuleSet(json));
        }
    }
}
