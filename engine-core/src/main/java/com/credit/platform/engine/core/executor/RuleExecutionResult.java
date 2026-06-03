package com.credit.platform.engine.core.executor;

import com.credit.platform.engine.core.compiler.CompiledConditionRule;
import com.credit.platform.engine.core.compiler.model.RuleAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 规则执行结果。
 * <p>
 * 记录规则集执行后命中的规则、触发的动作、以及执行耗时。
 * 不可变对象，每次执行创建新的结果实例。
 * </p>
 */
public final class RuleExecutionResult {

    private final String ruleSetId;
    private final boolean hit;
    private final List<CompiledConditionRule> matchedRules;
    private final List<RuleAction> triggeredActions;
    private final long durationMs;
    private final int totalRulesEvaluated;

    private RuleExecutionResult(Builder builder) {
        this.ruleSetId = builder.ruleSetId;
        this.hit = builder.hit;
        this.matchedRules = Collections.unmodifiableList(new ArrayList<>(builder.matchedRules));
        this.triggeredActions = Collections.unmodifiableList(new ArrayList<>(builder.triggeredActions));
        this.durationMs = builder.durationMs;
        this.totalRulesEvaluated = builder.totalRulesEvaluated;
    }

    public String getRuleSetId() { return ruleSetId; }
    public boolean isHit() { return hit; }
    public List<CompiledConditionRule> getMatchedRules() { return matchedRules; }
    public List<RuleAction> getTriggeredActions() { return triggeredActions; }
    public long getDurationMs() { return durationMs; }
    public int getTotalRulesEvaluated() { return totalRulesEvaluated; }

    public static Builder builder() { return new Builder(); }

    /** 构建器 */
    public static class Builder {
        private String ruleSetId;
        private boolean hit;
        private List<CompiledConditionRule> matchedRules = new ArrayList<>();
        private List<RuleAction> triggeredActions = new ArrayList<>();
        private long durationMs;
        private int totalRulesEvaluated;

        public Builder ruleSetId(String ruleSetId) { this.ruleSetId = ruleSetId; return this; }
        public Builder hit(boolean hit) { this.hit = hit; return this; }
        public Builder matchedRules(List<CompiledConditionRule> rules) {
            this.matchedRules = rules != null ? rules : Collections.emptyList();
            return this;
        }
        public Builder triggeredActions(List<RuleAction> actions) {
            this.triggeredActions = actions != null ? actions : Collections.emptyList();
            return this;
        }
        public Builder durationMs(long durationMs) { this.durationMs = durationMs; return this; }
        public Builder totalRulesEvaluated(int count) { this.totalRulesEvaluated = count; return this; }
        public RuleExecutionResult build() { return new RuleExecutionResult(this); }
    }

    @Override
    public String toString() {
        return "RuleExecutionResult{ruleSetId='" + ruleSetId + "', hit=" + hit
            + ", matched=" + matchedRules.size() + ", actions=" + triggeredActions.size()
            + ", duration=" + durationMs + "ms}";
    }
}
