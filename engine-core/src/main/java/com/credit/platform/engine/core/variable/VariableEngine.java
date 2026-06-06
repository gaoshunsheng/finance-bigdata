package com.credit.platform.engine.core.variable;

import com.credit.platform.engine.core.expression.ExpressionEngine;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 变量引擎 — 按层级协调变量获取。
 * <p>
 * 核心职责:
 * <ol>
 *   <li>接受所需变量 ID 集合</li>
 *   <li>按层级 (L0→L1→L2→L3) 获取变量</li>
 *   <li>委托给各层 {@link VariableProvider}</li>
 * </ol>
 * </p>
 *
 * <pre>
 * VariableEngine engine = new VariableEngine(registry)
 *     .setProvider(VariableLayer.EXTERNAL, externalProvider)
 *     .setProvider(VariableLayer.CACHED, cachedProvider)
 *     .setDerivedProvider(expressionEngine);
 *
 * VariableResolveContext ctx = engine.resolve(requiredVarIds, requestId, requestParams);
 * Map&lt;String, Object&gt; allVars = ctx.getAllResolved();
 * </pre>
 */
public class VariableEngine {

    private final VariableRegistry registry;
    private final Map<VariableLayer, VariableProvider> providers = new HashMap<>();
    private ExpressionEngine expressionEngine;

    public VariableEngine(VariableRegistry registry) {
        this.registry = registry;
    }

    /**
     * 设置指定层级的变量提供者。
     */
    public VariableEngine setProvider(VariableLayer layer, VariableProvider provider) {
        providers.put(layer, provider);
        return this;
    }

    /**
     * 设置表达式引擎（用于 L3 衍生变量计算）。
     */
    public VariableEngine setDerivedProvider(ExpressionEngine engine) {
        this.expressionEngine = engine;
        return this;
    }

    /**
     * 解析所需变量集合。
     * <p>
     * 按层级顺序: L0(输入) → L1(外部) → L2(缓存) → L3(衍生)
     * </p>
     *
     * @param requiredVarIds 需要的变量 ID 集合
     * @param requestId      请求 ID
     * @param requestParams  请求参数（L0 输入变量）
     * @return 变量解析上下文
     */
    public VariableResolveContext resolve(Set<String> requiredVarIds,
                                           String requestId,
                                           Map<String, Object> requestParams) {
        VariableResolveContext ctx = new VariableResolveContext(requestId, requestParams);

        // 展开依赖：将衍生变量的依赖也加入解析集
        Set<String> expandedVarIds = registry.resolveDependencies(requiredVarIds);

        // 按层级分类变量
        Map<VariableLayer, Set<String>> byLayer = classifyByLayer(expandedVarIds);

        // L0: 输入变量已由 requestParams 提供
        // L1: 外部变量
        resolveLayer(VariableLayer.EXTERNAL, byLayer.getOrDefault(VariableLayer.EXTERNAL, Set.of()), ctx);
        // L2: 缓存变量
        resolveLayer(VariableLayer.CACHED, byLayer.getOrDefault(VariableLayer.CACHED, Set.of()), ctx);
        // L3: 衍生变量
        resolveDerived(byLayer.getOrDefault(VariableLayer.DERIVED, Set.of()), ctx);

        return ctx;
    }

    private void resolveLayer(VariableLayer layer, Set<String> varIds, VariableResolveContext ctx) {
        if (varIds.isEmpty()) return;
        // 过滤已解析的变量
        Set<String> unresolved = new HashSet<>();
        for (String varId : varIds) {
            if (!ctx.isResolved(varId)) {
                unresolved.add(varId);
            }
        }
        if (unresolved.isEmpty()) return;

        VariableProvider provider = providers.get(layer);
        if (provider != null) {
            Map<String, Object> values = provider.provide(unresolved, ctx);
            ctx.putAllResolved(values);
        }
    }

    /**
     * 解析衍生变量 — 按依赖拓扑顺序计算。
     * <p>
     * 衍生变量之间存在依赖关系（如 var_a 依赖 var_b），
     * 必须按依赖顺序计算，否则表达式引用的变量值可能尚未就绪。
     * </p>
     */
    private void resolveDerived(Set<String> varIds, VariableResolveContext ctx) {
        if (varIds.isEmpty() || expressionEngine == null) return;

        // 拓扑排序：确保被依赖的变量先计算
        List<String> sorted = topologicalSort(varIds);

        for (String varId : sorted) {
            if (ctx.isResolved(varId)) continue;

            VariableDefinition def = registry.get(varId);
            if (def != null && def.isDerived() && def.getExpression() != null) {
                try {
                    Object value = expressionEngine.execute(def.getExpression(), ctx.getAllResolved());
                    ctx.putResolved(varId, value);
                } catch (Exception e) {
                    // 衍生变量计算失败不阻塞流程，值为 null
                    ctx.putResolved(varId, null);
                }
            }
        }
    }

    /**
     * 对衍生变量按依赖关系进行拓扑排序（Kahn 算法）。
     * 被依赖的变量排在前面，确保计算时其依赖值已就绪。
     */
    private List<String> topologicalSort(Set<String> varIds) {
        // 构建 DAG：仅考虑 varIds 集合内的依赖关系
        Map<String, Set<String>> deps = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();
        for (String varId : varIds) {
            deps.put(varId, new HashSet<>());
            inDegree.put(varId, 0);
        }
        for (String varId : varIds) {
            VariableDefinition def = registry.get(varId);
            if (def != null && def.getDependencies() != null) {
                for (String dep : def.getDependencies()) {
                    if (varIds.contains(dep)) {
                        // dep → varId（dep 必须先于 varId 计算）
                        deps.get(dep).add(varId);
                        inDegree.merge(varId, 1, Integer::sum);
                    }
                }
            }
        }

        // Kahn 算法
        java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>();
        for (Map.Entry<String, Integer> e : inDegree.entrySet()) {
            if (e.getValue() == 0) {
                queue.add(e.getKey());
            }
        }

        List<String> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            result.add(current);
            for (String dependent : deps.get(current)) {
                int newDegree = inDegree.merge(dependent, -1, Integer::sum);
                if (newDegree == 0) {
                    queue.add(dependent);
                }
            }
        }

        // 如果存在循环依赖，result 可能不完整；
        // 将剩余变量追加到末尾（降级为原始顺序）
        if (result.size() < varIds.size()) {
            for (String varId : varIds) {
                if (!result.contains(varId)) {
                    result.add(varId);
                }
            }
        }
        return result;
    }

    /**
     * 将变量 ID 按层级分类。
     */
    private Map<VariableLayer, Set<String>> classifyByLayer(Set<String> varIds) {
        Map<VariableLayer, Set<String>> result = new HashMap<>();
        for (VariableLayer layer : VariableLayer.values()) {
            result.put(layer, new HashSet<>());
        }
        for (String varId : varIds) {
            VariableDefinition def = registry.get(varId);
            VariableLayer layer = (def != null) ? def.getLayer() : VariableLayer.INPUT;
            result.get(layer).add(varId);
        }
        return result;
    }

    public VariableRegistry getRegistry() { return registry; }
}
