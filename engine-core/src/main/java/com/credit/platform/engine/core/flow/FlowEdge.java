package com.credit.platform.engine.core.flow;

import java.util.Objects;

/**
 * DAG 决策流边定义 — 节点之间的连接和条件。
 * <p>
 * 三种类型:
 * <ul>
 *   <li>无条件: condition 为 null，无条件到达下一节点</li>
 * *   <li>表达式: Aviator 表达式为 true 时到达</li>
 * *   <li>枚举: 节点返回值匹配时到达</li>
 * </ul>
 * </p>
 */
public final class FlowEdge {

    private final String from;
    private final String to;
    private final String condition;  // null = 无条件

    public FlowEdge(String from, String to, String condition) {
        this.from = Objects.requireNonNull(from);
        this.to = Objects.requireNonNull(to);
        this.condition = condition;
    }

    public FlowEdge(String from, String to) {
        this(from, to, null);
    }

    public String getFrom() { return from; }
    public String getTo() { return to; }
    public String getCondition() { return condition; }

    public boolean isUnconditional() { return condition == null || condition.isEmpty(); }

    @Override
    public String toString() {
        return from + " → " + to + (condition != null ? " [" + condition + "]" : "");
    }
}
