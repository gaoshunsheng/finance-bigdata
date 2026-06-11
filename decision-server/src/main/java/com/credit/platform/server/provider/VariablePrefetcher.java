package com.credit.platform.server.provider;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.credit.platform.engine.core.variable.PrefetchResult;
import com.credit.platform.engine.core.variable.VariableDefinition;
import com.credit.platform.engine.core.variable.VariableLayer;
import com.credit.platform.engine.core.variable.VariableRegistry;

/**
 * 变量预取器 — 在决策执行前预加载 L2 缓存变量。
 *
 * <p>核心职责:
 * <ol>
 *   <li>从 VariableRegistry 中筛选出 L2 CACHED 层的变量</li>
 *   <li>调用 CachedVariableProvider 批量获取这些变量</li>
 *   <li>将结果包装为 {@link PrefetchResult} 供决策流程使用</li>
 * </ol>
 *
 * <p>使用场景:
 * <ul>
 *   <li>DATA_PREP DAG 节点在决策流执行前调用</li>
 *   <li>减少决策执行时的 L2 变量获取延迟</li>
 *   <li>支持同步/异步两种模式</li>
 * </ul>
 *
 * <pre>
 * // 同步模式
 * PrefetchResult result = prefetcher.prefetch(requiredVarIds, customerId);
 *
 * // 异步模式
 * CompletableFuture&lt;PrefetchResult&gt; future = prefetcher.prefetchAsync(requiredVarIds, customerId);
 * </pre>
 */
public class VariablePrefetcher {

    private static final Logger log = LoggerFactory.getLogger(VariablePrefetcher.class);

    private final VariableRegistry registry;
    private final CachedVariableProvider cachedProvider;
    private final Executor executor;

    public VariablePrefetcher(VariableRegistry registry,
                              CachedVariableProvider cachedProvider,
                              Executor executor) {
        this.registry = registry;
        this.cachedProvider = cachedProvider;
        this.executor = executor;
    }

    /**
     * 同步预取 L2 缓存变量。
     *
     * @param requiredVarIds 决策流程所需的全部变量 ID
     * @param customerId     客户 ID
     * @return 预取结果
     */
    public PrefetchResult prefetch(Set<String> requiredVarIds, String customerId) {
        if (requiredVarIds == null || requiredVarIds.isEmpty() || customerId == null) {
            log.debug("预取跳过: 无变量或无 customerId");
            return PrefetchResult.empty();
        }

        // 筛选 L2 CACHED 层变量
        Set<String> cachedVarIds = filterCachedVariables(requiredVarIds);
        if (cachedVarIds.isEmpty()) {
            log.debug("预取跳过: 无 L2 CACHED 变量 (总共 {} 个变量)", requiredVarIds.size());
            return PrefetchResult.empty();
        }

        long start = System.currentTimeMillis();
        log.info("开始预取 L2 变量: customerId={}, L2变量数={}/{}", customerId, cachedVarIds.size(), requiredVarIds.size());

        Map<String, Object> variables;
        Set<String> failed = new HashSet<>();
        try {
            variables = cachedProvider.queryWithFallback(cachedVarIds, customerId);
        } catch (Exception e) {
            log.error("L2 变量预取异常: customerId={}, error={}", customerId, e.getMessage());
            variables = new HashMap<>();
            failed.addAll(cachedVarIds);
        }

        // 记录未命中的变量
        for (String varId : cachedVarIds) {
            if (!variables.containsKey(varId)) {
                failed.add(varId);
            }
        }

        long elapsed = System.currentTimeMillis() - start;

        PrefetchResult result;
        if (failed.isEmpty()) {
            result = PrefetchResult.success(variables, elapsed);
        } else if (!variables.isEmpty()) {
            result = PrefetchResult.partial(variables, failed, elapsed);
        } else {
            result = PrefetchResult.failure(failed, elapsed);
        }

        log.info("预取完成: customerId={}, 成功={}, 失败={}, 耗时={}ms",
            customerId, result.getSuccessCount(), result.getFailureCount(), elapsed);

        return result;
    }

    /**
     * 异步预取 L2 缓存变量。
     *
     * @param requiredVarIds 决策流程所需的全部变量 ID
     * @param customerId     客户 ID
     * @return 异步预取结果
     */
    public CompletableFuture<PrefetchResult> prefetchAsync(Set<String> requiredVarIds, String customerId) {
        return CompletableFuture.supplyAsync(() -> prefetch(requiredVarIds, customerId), executor);
    }

    /**
     * 将预取结果注入到请求上下文参数中。
     *
     * <p>在 VariableEngine.resolve() 之前调用此方法，
     * 可以让 L0 层自动包含预取的变量值，避免 L2 层重复查询。
     *
     * @param params   原始请求参数
     * @param prefetch 预取结果
     * @return 合并后的请求参数
     */
    public Map<String, Object> injectPrefetchResult(Map<String, Object> params, PrefetchResult prefetch) {
        if (prefetch == null || prefetch.getVariables().isEmpty()) {
            return params;
        }

        Map<String, Object> merged = new HashMap<>(params);
        merged.putAll(prefetch.getVariables());
        log.debug("注入预取变量: {} 个变量合并到请求参数", prefetch.getSuccessCount());
        return merged;
    }

    /**
     * 从变量集合中筛选 L2 CACHED 层的变量。
     */
    private Set<String> filterCachedVariables(Set<String> varIds) {
        Set<String> cached = new HashSet<>();
        for (String varId : varIds) {
            VariableDefinition def = registry.get(varId);
            if (def != null && def.getLayer() == VariableLayer.CACHED) {
                cached.add(varId);
            }
        }
        return cached;
    }
}
