package com.credit.platform.server.provider;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.credit.platform.engine.core.variable.VariableLayer;
import com.credit.platform.engine.core.variable.VariableProvider;
import com.credit.platform.engine.core.variable.VariableResolveContext;
import com.credit.platform.server.adapter.AdapterRegistry;

/**
 * 外部 API 变量提供者 — 实现 VariableProvider 接口的 L1 (EXTERNAL) 层。
 * <p>
 * 通过 {@link AdapterRegistry} 将变量路由到对应的外部 API 适配器，
 * 使用 {@link AdapterRegistry#fetchParallel} 并行调用多个适配器，
 * 总耗时 = max(各适配器耗时)。
 * </p>
 *
 * <p>在 Spring 配置中注册：</p>
 * <pre>
 * &#64;Bean
 * public VariableProvider externalApiProvider(AdapterRegistry registry, Executor externalApiExecutor) {
 *     return new ExternalApiProvider(registry, externalApiExecutor);
 * }
 * </pre>
 */
public class ExternalApiProvider implements VariableProvider {

    private static final Logger log = LoggerFactory.getLogger(ExternalApiProvider.class);

    private final AdapterRegistry adapterRegistry;
    private final Executor executor;

    /**
     * @param adapterRegistry 适配器注册中心
     * @param executor        外部 API 调用线程池（用于并行调用多个适配器）
     */
    public ExternalApiProvider(AdapterRegistry adapterRegistry, Executor executor) {
        this.adapterRegistry = adapterRegistry;
        this.executor = executor;
    }

    @Override
    public Map<String, Object> provide(Set<String> varIds, VariableResolveContext context) {
        if (varIds == null || varIds.isEmpty()) {
            return Map.of();
        }

        log.debug("[L1-EXTERNAL] Resolving {} variables: {}", varIds.size(), varIds);

        long startMs = System.currentTimeMillis();
        try {
            Map<String, Object> result = adapterRegistry.fetchParallel(
                varIds, context.getAllResolved(), executor);

            long durationMs = System.currentTimeMillis() - startMs;
            log.debug("[L1-EXTERNAL] Resolved {} variables in {}ms", result.size(), durationMs);

            return result;
        } catch (Exception e) {
            log.error("[L1-EXTERNAL] Failed to resolve variables: {}", e.getMessage());
            return Map.of();
        }
    }
}
