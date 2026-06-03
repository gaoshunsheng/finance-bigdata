package com.credit.platform.engine.core.compiler;

import com.credit.platform.engine.core.compiler.model.HitPolicy;
import com.credit.platform.engine.core.compiler.model.RuleAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 编译后的规则集 — 多条条件规则的容器，统一命中策略。
 * <p>
 * 不可变对象，线程安全。支持三种命中策略：
 * <ul>
 *   <li>{@link HitPolicy#FIRST_HIT} — 首条命中即返回</li>
 *   <li>{@link HitPolicy#ALL} — 全量执行，收集所有命中结果</li>
 *   <li>{@link HitPolicy#PRIORITY} — 按优先级排序，命中即停</li>
 * </ul>
 * </p>
 */
public final class CompiledRuleSet implements CompiledRule {

    private static final String RULE_TYPE = "RULE_SET";

    private final String ruleId;
    private final String name;
    private final int version;
    private final HitPolicy hitPolicy;
    private final List<CompiledConditionRule> rules;
    private final Set<String> referencedFields;

    public CompiledRuleSet(String ruleId, String name, int version,
                           HitPolicy hitPolicy, List<CompiledConditionRule> rules) {
        this.ruleId = Objects.requireNonNull(ruleId, "ruleId must not be null");
        this.name = name;
        this.version = version;
        this.hitPolicy = Objects.requireNonNull(hitPolicy, "hitPolicy must not be null");

        // PRIORITY 模式按优先级降序排列（数值越大优先级越高）
        if (hitPolicy == HitPolicy.PRIORITY) {
            List<CompiledConditionRule> sorted = new ArrayList<>(
                Objects.requireNonNull(rules, "rules must not be null"));
            sorted.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
            this.rules = Collections.unmodifiableList(sorted);
        } else {
            this.rules = Collections.unmodifiableList(
                new ArrayList<>(Objects.requireNonNull(rules, "rules must not be null")));
        }

        // 收集所有引用字段
        Set<String> fields = new HashSet<>();
        for (CompiledConditionRule rule : this.rules) {
            fields.addAll(rule.getReferencedFields());
        }
        this.referencedFields = Collections.unmodifiableSet(fields);
    }

    /**
     * 执行规则集，按命中策略返回匹配的规则。
     *
     * @param variables 变量上下文
     * @return 命中的规则列表（按策略可能包含 0-N 条）
     */
    public List<CompiledConditionRule> evaluate(Map<String, Object> variables) {
        List<CompiledConditionRule> matched = new ArrayList<>();

        for (CompiledConditionRule rule : rules) {
            if (rule.evaluate(variables)) {
                matched.add(rule);
                if (hitPolicy == HitPolicy.FIRST_HIT || hitPolicy == HitPolicy.PRIORITY) {
                    break;
                }
            }
        }

        return matched;
    }

    /**
     * 从命中的规则中提取所有动作。
     *
     * @param matchedRules 命中规则列表
     * @return 动作列表
     */
    public List<RuleAction> collectActions(List<CompiledConditionRule> matchedRules) {
        return matchedRules.stream()
            .flatMap(r -> r.getActions().stream())
            .collect(Collectors.toList());
    }

    @Override
    public String getRuleId() { return ruleId; }
    @Override
    public String getName() { return name; }
    @Override
    public int getVersion() { return version; }
    @Override
    public String getRuleType() { return RULE_TYPE; }

    public HitPolicy getHitPolicy() { return hitPolicy; }
    public List<CompiledConditionRule> getRules() { return rules; }
    public Set<String> getReferencedFields() { return referencedFields; }

    @Override
    public String toString() {
        return "CompiledRuleSet{id='" + ruleId + "', policy=" + hitPolicy
            + ", rules=" + rules.size() + '}';
    }
}
