package com.credit.platform.engine.core.compiler;

import com.credit.platform.engine.core.compiler.model.ConditionNode;
import com.credit.platform.engine.core.compiler.model.RuleAction;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 编译后的条件规则 — IF 条件 THEN 动作。
 * <p>
 * 不可变对象，线程安全。由 {@link RuleCompiler} 编译生成，缓存于 Caffeine。
 * </p>
 *
 * <pre>
 * // 编译过程 (由 RuleCompiler 完成):
 * JSON → ConditionNode (AST) + RuleAction → CompiledConditionRule
 *
 * // 执行过程 (由 RuleExecutor 完成):
 * CompiledConditionRule.evaluate(variables) → true/false
 * </pre>
 */
public final class CompiledConditionRule implements CompiledRule {

    private static final String RULE_TYPE = "CONDITION";

    private final String ruleId;
    private final String name;
    private final int version;
    private final int priority;
    private final ConditionNode condition;
    private final List<RuleAction> actions;
    private final Set<String> referencedFields;

    public CompiledConditionRule(String ruleId, String name, int version,
                                  int priority, ConditionNode condition,
                                  List<RuleAction> actions) {
        this.ruleId = Objects.requireNonNull(ruleId, "ruleId must not be null");
        this.name = name;
        this.version = version;
        this.priority = priority;
        this.condition = Objects.requireNonNull(condition, "condition must not be null");
        this.actions = Collections.unmodifiableList(
            Objects.requireNonNull(actions, "actions must not be null"));
        this.referencedFields = Collections.unmodifiableSet(condition.getReferencedFields());
    }

    /**
     * 对条件节点求值。
     *
     * @param variables 变量上下文
     * @return 条件是否命中
     */
    public boolean evaluate(java.util.Map<String, Object> variables) {
        return condition.evaluate(variables);
    }

    @Override
    public String getRuleId() { return ruleId; }
    @Override
    public String getName() { return name; }
    @Override
    public int getVersion() { return version; }
    @Override
    public String getRuleType() { return RULE_TYPE; }

    public int getPriority() { return priority; }
    public ConditionNode getCondition() { return condition; }
    public List<RuleAction> getActions() { return actions; }
    public Set<String> getReferencedFields() { return referencedFields; }

    @Override
    public String toString() {
        return "CompiledConditionRule{id='" + ruleId + "', name='" + name
            + "', priority=" + priority + ", fields=" + referencedFields + '}';
    }
}
