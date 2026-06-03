package com.credit.platform.engine.core.variable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 变量解析上下文 — 记录变量获取过程中的中间状态。
 * <p>
 * 每次请求创建一个，按 L0 → L1 → L2 → L3 顺序填充变量。
 * </p>
 */
public final class VariableResolveContext {

    private final String requestId;
    private final Map<String, Object> requestParams;  // L0 输入
    private final Map<String, Object> resolved;         // 已解析的变量

    public VariableResolveContext(String requestId, Map<String, Object> requestParams) {
        this.requestId = requestId;
        this.requestParams = Collections.unmodifiableMap(new HashMap<>(requestParams));
        this.resolved = new HashMap<>(requestParams);
    }

    public String getRequestId() { return requestId; }
    public Map<String, Object> getRequestParams() { return requestParams; }

    /**
     * 获取已解析的变量值。
     */
    public Object get(String varId) { return resolved.get(varId); }

    /**
     * 获取所有已解析变量。
     */
    public Map<String, Object> getAllResolved() {
        return Collections.unmodifiableMap(resolved);
    }

    /**
     * 添加已解析的变量。
     */
    public void putResolved(String varId, Object value) { resolved.put(varId, value); }

    /**
     * 批量添加已解析的变量。
     */
    public void putAllResolved(Map<String, Object> vars) {
        if (vars != null) resolved.putAll(vars);
    }

    /**
     * 变量是否已解析。
     */
    public boolean isResolved(String varId) { return resolved.containsKey(varId); }
}
