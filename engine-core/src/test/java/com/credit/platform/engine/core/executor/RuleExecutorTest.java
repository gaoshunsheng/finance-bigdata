package com.credit.platform.engine.core.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.credit.platform.engine.common.model.ActionType;
import com.credit.platform.engine.core.compiler.CompiledConditionRule;
import com.credit.platform.engine.core.compiler.CompiledRuleSet;
import com.credit.platform.engine.core.compiler.RuleCompiler;
import com.credit.platform.engine.core.compiler.model.RuleAction;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link RuleExecutor} 和 {@link ExecutionContext} 综合测试。
 */
class RuleExecutorTest {

    private RuleCompiler compiler;
    private RuleExecutor executor;

    @BeforeEach
    void setUp() {
        compiler = new RuleCompiler();
        executor = new RuleExecutor();
    }

    // ==================== ExecutionContext 测试 ====================

    @Nested
    @DisplayName("ExecutionContext 测试")
    class ContextTest {

        @Test
        @DisplayName("创建上下文并读写变量")
        void createAndAccess() {
            Map<String, Object> init = Map.of("name", "张三", "age", 28);
            ExecutionContext ctx = ExecutionContext.create(init);

            assertEquals("张三", ctx.getVariable("name"));
            assertEquals(28, ctx.getVariable("age", 0));
        }

        @Test
        @DisplayName("setVariable 添加新变量")
        void setVariable() {
            ExecutionContext ctx = ExecutionContext.create(new HashMap<>());
            ctx.setVariable("score", 750);
            assertEquals(750, ctx.getVariable("score", 0));
        }

        @Test
        @DisplayName("getVariable 带默认值 — 不存在时返回默认值")
        void getVariable_defaultValue() {
            ExecutionContext ctx = ExecutionContext.create(new HashMap<>());
            assertEquals(0, ctx.getVariable("missing", 0));
        }

        @Test
        @DisplayName("hasVariable 检查变量存在性")
        void hasVariable() {
            ExecutionContext ctx = ExecutionContext.create(Map.of("x", 1));
            assertTrue(ctx.hasVariable("x"));
            assertFalse(ctx.hasVariable("y"));
        }

        @Test
        @DisplayName("setVariables 批量设置")
        void setVariables_batch() {
            ExecutionContext ctx = ExecutionContext.create(new HashMap<>());
            ctx.setVariables(Map.of("a", 1, "b", 2));
            assertEquals(1, ctx.getVariable("a", 0));
            assertEquals(2, ctx.getVariable("b", 0));
        }
    }

    // ==================== 单条规则执行测试 ====================

    @Nested
    @DisplayName("单条条件规则执行")
    class SingleRuleExecutionTest {

        @Test
        @DisplayName("年龄准入检查 — 命中")
        void ageCheck_hit() {
            CompiledConditionRule rule = compiler.compileConditionRule("""
                {
                  "ruleId": "R001",
                  "name": "年龄准入",
                  "priority": 100,
                  "conditions": {
                    "operator": "OR",
                    "operands": [
                      {"field": "age", "op": "LT", "value": 22},
                      {"field": "age", "op": "GT", "value": 60}
                    ]
                  },
                  "actions": [{"type": "REJECT", "reason": "年龄不符", "code": "AGE_001"}]
                }
                """);

            ExecutionContext ctx = ExecutionContext.create(Map.of("age", 15));
            assertTrue(executor.evaluate(rule, ctx));

            ctx = ExecutionContext.create(Map.of("age", 65));
            assertTrue(executor.evaluate(rule, ctx));

            ctx = ExecutionContext.create(Map.of("age", 30));
            assertFalse(executor.evaluate(rule, ctx));
        }

        @Test
        @DisplayName("黑名单检查 — ID 命中")
        void blacklist_hit() {
            CompiledConditionRule rule = compiler.compileConditionRule("""
                {
                  "ruleId": "R_BL_001",
                  "name": "身份证黑名单",
                  "priority": 100,
                  "conditions": {"field": "id_blacklist", "op": "EQ", "value": true},
                  "actions": [{"type": "REJECT", "reason": "命中身份证黑名单", "code": "BL_ID"}]
                }
                """);

            ExecutionContext ctx = ExecutionContext.create(Map.of("id_blacklist", true));
            assertTrue(executor.evaluate(rule, ctx));

            ctx = ExecutionContext.create(Map.of("id_blacklist", false));
            assertFalse(executor.evaluate(rule, ctx));
        }

        @Test
        @DisplayName("BETWEEN 评分范围检查")
        void scorecardRange() {
            CompiledConditionRule rule = compiler.compileConditionRule("""
                {
                  "ruleId": "R_SCORE",
                  "name": "评分范围",
                  "priority": 50,
                  "conditions": {"field": "score", "op": "BETWEEN", "value": [550, 620]},
                  "actions": [{"type": "REVIEW", "reason": "评分在审核区间", "code": "SCORE_REVIEW"}]
                }
                """);

            // 在范围内
            assertTrue(executor.evaluate(rule, ExecutionContext.create(Map.of("score", 580))));
            // 边界
            assertTrue(executor.evaluate(rule, ExecutionContext.create(Map.of("score", 550))));
            assertTrue(executor.evaluate(rule, ExecutionContext.create(Map.of("score", 620))));
            // 范围外
            assertFalse(executor.evaluate(rule, ExecutionContext.create(Map.of("score", 540))));
            assertFalse(executor.evaluate(rule, ExecutionContext.create(Map.of("score", 650))));
        }
    }

    // ==================== 规则集执行测试 ====================

    @Nested
    @DisplayName("规则集执行")
    class RuleSetExecutionTest {

        private CompiledRuleSet blacklistRuleSet;

        @BeforeEach
        void compileBlacklistRules() {
            blacklistRuleSet = compiler.compileRuleSet("""
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
                      "actions": [{"type": "REJECT", "reason": "命中身份证黑名单", "code": "BL_001"}]
                    },
                    {
                      "ruleId": "R002",
                      "name": "手机号黑名单",
                      "priority": 90,
                      "conditions": {"field": "phone_blacklist", "op": "EQ", "value": true},
                      "actions": [{"type": "REJECT", "reason": "命中手机号黑名单", "code": "BL_002"}]
                    },
                    {
                      "ruleId": "R003",
                      "name": "设备指纹黑名单",
                      "priority": 80,
                      "conditions": {"field": "device_blacklist", "op": "EQ", "value": true},
                      "actions": [{"type": "REJECT", "reason": "命中设备黑名单", "code": "BL_003"}]
                    }
                  ]
                }
                """);
        }

        @Test
        @DisplayName("FIRST_HIT — 首条命中即停")
        void firstHit_stopsAtFirst() {
            ExecutionContext ctx = ExecutionContext.create(Map.of(
                "id_blacklist", true,
                "phone_blacklist", true,
                "device_blacklist", true
            ));

            RuleExecutionResult result = executor.execute(blacklistRuleSet, ctx);

            assertTrue(result.isHit());
            assertEquals(1, result.getMatchedRules().size());
            assertEquals("R001", result.getMatchedRules().get(0).getRuleId());
            assertEquals(1, result.getTriggeredActions().size());
            assertEquals("命中身份证黑名单", result.getTriggeredActions().get(0).getReason());
        }

        @Test
        @DisplayName("FIRST_HIT — 全部未命中")
        void firstHit_noMatch() {
            ExecutionContext ctx = ExecutionContext.create(Map.of(
                "id_blacklist", false,
                "phone_blacklist", false,
                "device_blacklist", false
            ));

            RuleExecutionResult result = executor.execute(blacklistRuleSet, ctx);

            assertFalse(result.isHit());
            assertTrue(result.getMatchedRules().isEmpty());
            assertTrue(result.getTriggeredActions().isEmpty());
            assertEquals(3, result.getTotalRulesEvaluated());
        }

        @Test
        @DisplayName("ALL — 收集所有命中结果")
        void allPolicy_collectsAll() {
            CompiledRuleSet allRuleSet = compiler.compileRuleSet("""
                {
                  "ruleSetId": "RS_ALL",
                  "name": "全量检查",
                  "hitPolicy": "ALL",
                  "rules": [
                    {
                      "ruleId": "R001",
                      "name": "规则1",
                      "priority": 100,
                      "conditions": {"field": "flag1", "op": "EQ", "value": true},
                      "actions": [{"type": "REJECT", "reason": "flag1命中", "code": "F1"}]
                    },
                    {
                      "ruleId": "R002",
                      "name": "规则2",
                      "priority": 90,
                      "conditions": {"field": "flag2", "op": "EQ", "value": true},
                      "actions": [{"type": "REJECT", "reason": "flag2命中", "code": "F2"}]
                    },
                    {
                      "ruleId": "R003",
                      "name": "规则3",
                      "priority": 80,
                      "conditions": {"field": "flag3", "op": "EQ", "value": true},
                      "actions": [{"type": "REVIEW", "reason": "flag3命中", "code": "F3"}]
                    }
                  ]
                }
                """);

            // flag1 和 flag3 命中
            ExecutionContext ctx = ExecutionContext.create(Map.of(
                "flag1", true, "flag2", false, "flag3", true
            ));

            RuleExecutionResult result = executor.execute(allRuleSet, ctx);

            assertTrue(result.isHit());
            assertEquals(2, result.getMatchedRules().size());
            assertEquals(2, result.getTriggeredActions().size());
        }

        @Test
        @DisplayName("PRIORITY — 按优先级排序后首条命中即停")
        void priorityPolicy_sortedExecution() {
            CompiledRuleSet prioritySet = compiler.compileRuleSet("""
                {
                  "ruleSetId": "RS_PRIORITY",
                  "name": "优先级测试",
                  "hitPolicy": "PRIORITY",
                  "rules": [
                    {
                      "ruleId": "R_LOW",
                      "name": "低优先级规则",
                      "priority": 10,
                      "conditions": {"field": "x", "op": "GT", "value": 0},
                      "actions": [{"type": "REVIEW"}]
                    },
                    {
                      "ruleId": "R_HIGH",
                      "name": "高优先级规则",
                      "priority": 100,
                      "conditions": {"field": "x", "op": "GT", "value": 0},
                      "actions": [{"type": "REJECT"}]
                    }
                  ]
                }
                """);

            ExecutionContext ctx = ExecutionContext.create(Map.of("x", 5));
            RuleExecutionResult result = executor.execute(prioritySet, ctx);

            assertTrue(result.isHit());
            // 应该命中高优先级规则
            assertEquals("R_HIGH", result.getMatchedRules().get(0).getRuleId());
            assertEquals(ActionType.REJECT, result.getTriggeredActions().get(0).getType());
        }

        @Test
        @DisplayName("执行结果包含耗时信息")
        void resultContainsTiming() {
            ExecutionContext ctx = ExecutionContext.create(Map.of("id_blacklist", false));
            RuleExecutionResult result = executor.execute(blacklistRuleSet, ctx);

            assertTrue(result.getDurationMs() >= 0);
            assertEquals(3, result.getTotalRulesEvaluated());
        }
    }

    // ==================== 端到端集成测试 ====================

    @Nested
    @DisplayName("端到端: 编译 → 执行")
    class EndToEndTest {

        @Test
        @DisplayName("完整信贷准入检查流程")
        void creditAccessCheck_fullFlow() {
            // 编译规则集
            CompiledRuleSet ruleSet = compiler.compileRuleSet("""
                {
                  "ruleSetId": "RS_ACCESS",
                  "name": "信贷准入检查",
                  "hitPolicy": "FIRST_HIT",
                  "rules": [
                    {
                      "ruleId": "R_AGE",
                      "name": "年龄准入",
                      "priority": 100,
                      "conditions": {
                        "operator": "OR",
                        "operands": [
                          {"field": "age", "op": "LT", "value": 22},
                          {"field": "age", "op": "GT", "value": 60}
                        ]
                      },
                      "actions": [{"type": "REJECT", "reason": "年龄不符准入要求", "code": "AGE_001"}]
                    },
                    {
                      "ruleId": "R_BLACKLIST",
                      "name": "黑名单检查",
                      "priority": 90,
                      "conditions": {"field": "blacklist_hit", "op": "EQ", "value": true},
                      "actions": [{"type": "REJECT", "reason": "命中黑名单", "code": "BL_001"}]
                    },
                    {
                      "ruleId": "R_OVERDUE",
                      "name": "逾期次数检查",
                      "priority": 80,
                      "conditions": {"field": "overdue_count_6m", "op": "GT", "value": 3},
                      "actions": [{"type": "REJECT", "reason": "近6月逾期次数过多", "code": "OD_001"}]
                    }
                  ]
                }
                """);

            // 场景1: 正常客户 — 不命中任何规则
            ExecutionContext normalCtx = ExecutionContext.create(Map.of(
                "age", 30, "blacklist_hit", false, "overdue_count_6m", 0
            ));
            RuleExecutionResult normalResult = executor.execute(ruleSet, normalCtx);
            assertFalse(normalResult.isHit());

            // 场景2: 年龄不符合 — 命中第一条
            ExecutionContext youngCtx = ExecutionContext.create(Map.of(
                "age", 18, "blacklist_hit", false, "overdue_count_6m", 0
            ));
            RuleExecutionResult youngResult = executor.execute(ruleSet, youngCtx);
            assertTrue(youngResult.isHit());
            assertEquals("R_AGE", youngResult.getMatchedRules().get(0).getRuleId());
            assertEquals("年龄不符准入要求", youngResult.getTriggeredActions().get(0).getReason());

            // 场景3: 命中黑名单
            ExecutionContext blackCtx = ExecutionContext.create(Map.of(
                "age", 30, "blacklist_hit", true, "overdue_count_6m", 0
            ));
            RuleExecutionResult blackResult = executor.execute(ruleSet, blackCtx);
            assertTrue(blackResult.isHit());
            assertEquals("R_BLACKLIST", blackResult.getMatchedRules().get(0).getRuleId());

            // 场景4: 逾期次数过多
            ExecutionContext overdueCtx = ExecutionContext.create(Map.of(
                "age", 30, "blacklist_hit", false, "overdue_count_6m", 5
            ));
            RuleExecutionResult overdueResult = executor.execute(ruleSet, overdueCtx);
            assertTrue(overdueResult.isHit());
            assertEquals("R_OVERDUE", overdueResult.getMatchedRules().get(0).getRuleId());
        }
    }
}
