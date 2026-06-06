package com.credit.platform.server.adapter;

import java.util.Map;
import java.util.Set;

/**
 * 外部 API 适配器接口 — 统一的外部数据源接入点。
 * <p>
 * 每种外部数据源（征信、工商、司法等）实现此接口。
 * {@link AbstractExternalApiAdapter} 提供超时/重试/降级/Mock 的通用模板。
 * </p>
 *
 * <pre>
 * // 通过 AdapterRegistry 使用
 * AdapterRegistry registry = ...;
 * Map&lt;String, Object&gt; data = registry.fetch("credit_bureau", varIds, context);
 * </pre>
 */
public interface ExternalApiAdapter {

    /**
     * 适配器类型标识，如 "credit_bureau", "business_registration"。
     * <p>
     * 与 {@link com.credit.platform.engine.core.variable.VariableDefinition#getCategory()} 对应。
     * </p>
     */
    String getAdapterType();

    /**
     * 该适配器能提供的变量 ID 前缀或集合。
     * <p>
     * 用于 {@link AdapterRegistry} 将变量路由到正确的适配器。
     * </p>
     */
    Set<String> getSupportedVariables();

    /**
     * 从外部数据源获取变量值。
     * <p>
     * 实现类应关注业务逻辑，超时/重试/降级由基类处理。
     * </p>
     *
     * @param varIds      需要获取的变量 ID 集合
     * @param contextVars 当前已知的上下文变量（如 customerId, idNumber 等）
     * @return 变量名 → 变量值映射
     */
    Map<String, Object> fetch(Set<String> varIds, Map<String, Object> contextVars);

    /**
     * 适配器是否健康（可用于熔断判断）。
     */
    boolean isHealthy();

    /**
     * 获取最近一次调用的耗时（毫秒），用于监控。
     */
    long getLastCallDurationMs();
}
