package com.credit.platform.server.adapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 外部 API 适配器注册中心 — 管理所有适配器的注册和路由。
 * <p>
 * 核心职责：
 * <ol>
 *   <li>注册/注销适配器</li>
 *   <li>将变量 ID 路由到对应的适配器</li>
 *   <li>并行调用多个适配器获取变量</li>
 *   <li>收集各适配器的健康状态</li>
 * </ol>
 * </p>
 */
@Component
public class AdapterRegistry {

    private static final Logger log = LoggerFactory.getLogger(AdapterRegistry.class);

    /** adapterType → adapter */
    private final Map<String, ExternalApiAdapter> adapters = new ConcurrentHashMap<>();

    /** varId → adapterType 的路由表 */
    private final Map<String, String> variableRouting = new ConcurrentHashMap<>();

    /**
     * 注册一个外部 API 适配器。
     */
    public void register(ExternalApiAdapter adapter) {
        adapters.put(adapter.getAdapterType(), adapter);

        // 建立变量路由
        for (String varId : adapter.getSupportedVariables()) {
            variableRouting.put(varId, adapter.getAdapterType());
        }

        log.info("Registered external API adapter: {} (supports {} variables)",
            adapter.getAdapterType(), adapter.getSupportedVariables().size());
    }

    /**
     * 注销一个适配器。
     */
    public void unregister(String adapterType) {
        ExternalApiAdapter removed = adapters.remove(adapterType);
        if (removed != null) {
            for (String varId : removed.getSupportedVariables()) {
                variableRouting.remove(varId);
            }
            log.info("Unregistered external API adapter: {}", adapterType);
        }
    }

    /**
     * 获取指定类型的适配器。
     */
    public ExternalApiAdapter getAdapter(String adapterType) {
        return adapters.get(adapterType);
    }

    /**
     * 从指定类型适配器获取变量值。
     */
    public Map<String, Object> fetch(String adapterType, Set<String> varIds, Map<String, Object> contextVars) {
        ExternalApiAdapter adapter = adapters.get(adapterType);
        if (adapter == null) {
            log.warn("No adapter registered for type: {}", adapterType);
            return Collections.emptyMap();
        }
        return adapter.fetch(varIds, contextVars);
    }

    /**
     * 将变量按适配器分组。
     *
     * @param varIds 需要获取的变量 ID 集合
     * @return adapterType → 该适配器需要获取的变量 ID 集合
     */
    public Map<String, Set<String>> groupByAdapter(Set<String> varIds) {
        Map<String, Set<String>> groups = new HashMap<>();
        Set<String> unmatched = new java.util.HashSet<>();

        for (String varId : varIds) {
            String adapterType = variableRouting.get(varId);
            if (adapterType != null) {
                groups.computeIfAbsent(adapterType, k -> new java.util.HashSet<>()).add(varId);
            } else {
                unmatched.add(varId);
            }
        }

        if (!unmatched.isEmpty()) {
            log.debug("Variables without adapter routing: {}", unmatched);
        }

        return groups;
    }

    /**
     * 并行调用多个适配器获取变量值。
     * <p>
     * 使用 CompletableFuture.allOf 并行调用，总耗时 = 最慢的适配器耗时。
     * </p>
     *
     * @param varIds      需要获取的变量 ID 集合
     * @param contextVars 上下文变量
     * @param executor    线程池（用于并行调用）
     * @return 所有变量名 → 变量值的合并结果
     */
    public Map<String, Object> fetchParallel(Set<String> varIds,
                                               Map<String, Object> contextVars,
                                               java.util.concurrent.Executor executor) {
        if (varIds == null || varIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Set<String>> groups = groupByAdapter(varIds);
        if (groups.isEmpty()) {
            return Collections.emptyMap();
        }

        // 单个适配器 → 直接调用
        if (groups.size() == 1) {
            Map.Entry<String, Set<String>> entry = groups.entrySet().iterator().next();
            return fetch(entry.getKey(), entry.getValue(), contextVars);
        }

        // 多个适配器 → 并行调用
        Map<String, Object> result = new ConcurrentHashMap<>();
        List<java.util.concurrent.CompletableFuture<Void>> futures = new ArrayList<>();

        for (Map.Entry<String, Set<String>> entry : groups.entrySet()) {
            String adapterType = entry.getKey();
            Set<String> adapterVarIds = entry.getValue();

            java.util.concurrent.CompletableFuture<Void> future =
                java.util.concurrent.CompletableFuture.runAsync(() -> {
                    try {
                        Map<String, Object> data = fetch(adapterType, adapterVarIds, contextVars);
                        result.putAll(data);
                    } catch (Exception e) {
                        log.error("[{}] Parallel fetch failed: {}", adapterType, e.getMessage());
                    }
                }, executor);

            futures.add(future);
        }

        // 等待所有适配器完成（最慢的决定总耗时）
        try {
            java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0]))
                .get(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Parallel fetch timed out or failed: {}", e.getMessage());
        }

        return result;
    }

    /**
     * 获取所有已注册适配器的健康状态。
     */
    public Map<String, Boolean> getHealthStatus() {
        Map<String, Boolean> status = new HashMap<>();
        for (Map.Entry<String, ExternalApiAdapter> entry : adapters.entrySet()) {
            status.put(entry.getKey(), entry.getValue().isHealthy());
        }
        return status;
    }

    /**
     * 获取所有已注册的适配器类型。
     */
    public Set<String> getRegisteredTypes() {
        return Collections.unmodifiableSet(adapters.keySet());
    }

    /**
     * 获取注册的适配器数量。
     */
    public int size() {
        return adapters.size();
    }
}
