package com.credit.platform.engine.core.executor;

import com.credit.platform.engine.core.compiler.CompiledConditionRule;
import com.credit.platform.engine.core.compiler.CompiledRuleSet;
import com.credit.platform.engine.core.compiler.model.RuleAction;
import java.util.List;

/**
 * 规则执行器 — 对执行上下文求值已编译的规则。
 * <p>
 * 核心入口方法:
 * <ul>
 *   <li>{@link #execute(CompiledRuleSet, ExecutionContext)} — 执行规则集</li>
 *   <li>{@link #execute(CompiledConditionRule, ExecutionContext)} — 执行单条条件规则</li>
 * </ul>
 * 执行器无状态，线程安全，可复用。
 * </p>
 */
public class RuleExecutor {

    /**
     * 执行规则集。
     *
     * @param ruleSet 编译后的规则集
     * @param context 执行上下文（每次请求独立）
     * @return 执行结果
     */
    public RuleExecutionResult execute(CompiledRuleSet ruleSet, ExecutionContext context) {
        long start = System.nanoTime();

        List<CompiledConditionRule> matched = ruleSet.evaluate(context.getAllVariables());
        List<RuleAction> actions = ruleSet.collectActions(matched);

        long durationMs = (System.nanoTime() - start) / 1_000_000;

        return RuleExecutionResult.builder()
            .ruleSetId(ruleSet.getRuleId())
            .hit(!matched.isEmpty())
            .matchedRules(matched)
            .triggeredActions(actions)
            .durationMs(durationMs)
            .totalRulesEvaluated(ruleSet.getRules().size())
            .build();
    }

    /**
     * 执行单条条件规则。
     *
     * @param rule 编译后的条件规则
     * @param context 执行上下文
     * @return 规则是否命中
     */
    public boolean evaluate(CompiledConditionRule rule, ExecutionContext context) {
        return rule.evaluate(context.getAllVariables());
    }
}
