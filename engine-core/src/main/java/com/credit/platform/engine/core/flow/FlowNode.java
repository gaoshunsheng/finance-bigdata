package com.credit.platform.engine.core.flow;

import java.util.Collections;
import java.util.Map;

/**
 * DAG 决策流节点定义。
 * <p>
 * 9 种节点类型: DATA_PREP / RULE_SET / SCORECARD / MODEL / DECISION / SUB_FLOW / AB_SPLIT / ACTION / SCRIPT
 * </p>
 */
public final class FlowNode {

    /** 节点类型枚举 */
    public enum Type {
        DATA_PREP, RULE_SET, SCORECARD, MODEL, DECISION,
        SUB_FLOW, AB_SPLIT, ACTION, SCRIPT
    }

    private final String id;
    private final Type type;
    private final Map<String, Object> config;

    public FlowNode(String id, Type type, Map<String, Object> config) {
        this.id = id;
        this.type = type;
        this.config = config != null
            ? Collections.unmodifiableMap(config) : Collections.emptyMap();
    }

    public String getId() { return id; }
    public Type getType() { return type; }
    public Map<String, Object> getConfig() { return config; }

    @SuppressWarnings("unchecked")
    public <T> T getConfig(String key, T defaultValue) {
        Object val = config.get(key);
        if (val == null) return defaultValue;
        try { return (T) val; } catch (ClassCastException e) { return defaultValue; }
    }

    @Override
    public String toString() {
        return "FlowNode{id='" + id + "', type=" + type + '}';
    }
}
