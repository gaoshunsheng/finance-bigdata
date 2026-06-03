package com.credit.platform.engine.core.executor;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单次决策请求的执行上下文。
 * <p>
 * 每个请求创建独立的 ExecutionContext，确保线程安全。
 * 上下文包含：
 * <ul>
 *   <li>输入变量 — 来自请求参数（Layer 0）</li>
 *   <li>解析变量 — 外部 API / 缓存 / 衍生计算获取（Layer 1-3）</li>
 *   <li>追踪条目 — 记录每个节点的执行细节</li>
 * </ul>
 * </p>
 *
 * <pre>
 * ExecutionContext ctx = ExecutionContext.create(requestVariables);
 * ctx.setVariable("age", 25);
 * int age = ctx.getVariable("age", 0);
 * </pre>
 */
public final class ExecutionContext {

    private final String requestId;
    private final String strategyId;
    private final Map<String, Object> variables;
    private final Map<String, Object> metadata;

    private ExecutionContext(String requestId, String strategyId,
                              Map<String, Object> initialVariables,
                              Map<String, Object> metadata) {
        this.requestId = requestId;
        this.strategyId = strategyId;
        this.variables = new ConcurrentHashMap<>(initialVariables);
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
    }

    /**
     * 从请求变量创建执行上下文。
     *
     * @param initialVariables 初始变量（通常来自请求的 applicant 字段）
     * @return 新的执行上下文
     */
    public static ExecutionContext create(Map<String, Object> initialVariables) {
        return new ExecutionContext("unknown", "unknown", initialVariables, null);
    }

    /**
     * 创建带完整信息的执行上下文。
     *
     * @param requestId 请求 ID
     * @param strategyId 策略 ID
     * @param initialVariables 初始变量
     * @param metadata 元数据
     * @return 新的执行上下文
     */
    public static ExecutionContext create(String requestId, String strategyId,
                                           Map<String, Object> initialVariables,
                                           Map<String, Object> metadata) {
        return new ExecutionContext(requestId, strategyId, initialVariables, metadata);
    }

    /**
     * 获取变量值。
     *
     * @param key 变量名
     * @return 变量值，不存在时返回 null
     */
    public Object getVariable(String key) {
        return variables.get(key);
    }

    /**
     * 获取变量值，带类型转换和默认值。
     *
     * @param key 变量名
     * @param defaultValue 默认值
     * @param <T> 值类型
     * @return 变量值或默认值
     */
    @SuppressWarnings("unchecked")
    public <T> T getVariable(String key, T defaultValue) {
        Object value = variables.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return (T) value;
        } catch (ClassCastException e) {
            return defaultValue;
        }
    }

    /**
     * 设置变量值。
     *
     * @param key 变量名
     * @param value 变量值
     */
    public void setVariable(String key, Object value) {
        variables.put(key, value);
    }

    /**
     * 批量设置变量。
     *
     * @param vars 变量映射
     */
    public void setVariables(Map<String, Object> vars) {
        if (vars != null) {
            variables.putAll(vars);
        }
    }

    /**
     * 获取所有变量的只读视图。
     *
     * @return 不可修改变量映射
     */
    public Map<String, Object> getAllVariables() {
        return Collections.unmodifiableMap(variables);
    }

    /**
     * 变量是否存在。
     */
    public boolean hasVariable(String key) {
        return variables.containsKey(key);
    }

    public String getRequestId() { return requestId; }
    public String getStrategyId() { return strategyId; }
    public Map<String, Object> getMetadata() { return Collections.unmodifiableMap(metadata); }

    @Override
    public String toString() {
        return "ExecutionContext{requestId='" + requestId + "', strategyId='" + strategyId
            + "', vars=" + variables.size() + '}';
    }
}
