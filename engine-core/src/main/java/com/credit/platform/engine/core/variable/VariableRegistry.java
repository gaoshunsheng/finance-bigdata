package com.credit.platform.engine.core.variable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 变量注册表 — 管理所有已注册的变量定义。
 * <p>
 * 线程安全 (ConcurrentHashMap)。支持按 ID、层级、类别查询。
 * </p>
 */
public final class VariableRegistry {

    private final Map<String, VariableDefinition> variables = new ConcurrentHashMap<>();

    /**
     * 注册一个变量定义。
     *
     * @param definition 变量定义
     */
    public void register(VariableDefinition definition) {
        variables.put(definition.getVarId(), definition);
    }

    /**
     * 批量注册变量定义。
     */
    public void registerAll(Collection<VariableDefinition> definitions) {
        for (VariableDefinition def : definitions) {
            register(def);
        }
    }

    /**
     * 获取变量定义。
     *
     * @param varId 变量 ID
     * @return 变量定义，不存在时返回 null
     */
    public VariableDefinition get(String varId) {
        return variables.get(varId);
    }

    /**
     * 按层级获取变量定义列表。
     */
    public Set<VariableDefinition> getByLayer(VariableLayer layer) {
        Set<VariableDefinition> result = new HashSet<>();
        for (VariableDefinition def : variables.values()) {
            if (def.getLayer() == layer) {
                result.add(def);
            }
        }
        return result;
    }

    /**
     * 按业务域获取变量定义列表。
     */
    public Set<VariableDefinition> getByCategory(String category) {
        Set<VariableDefinition> result = new HashSet<>();
        for (VariableDefinition def : variables.values()) {
            if (category.equals(def.getCategory())) {
                result.add(def);
            }
        }
        return result;
    }

    /**
     * 分析给定变量 ID 的完整依赖树（递归展开所有依赖）。
     *
     * @param varIds 起始变量 ID 集合
     * @return 包含所有传递依赖的变量 ID 集合
     */
    public Set<String> resolveDependencies(Set<String> varIds) {
        Set<String> resolved = new HashSet<>();
        Set<String> toProcess = new HashSet<>(varIds);

        while (!toProcess.isEmpty()) {
            Set<String> next = new HashSet<>();
            for (String varId : toProcess) {
                if (resolved.add(varId)) {
                    VariableDefinition def = variables.get(varId);
                    if (def != null && !def.getDependencies().isEmpty()) {
                        for (String dep : def.getDependencies()) {
                            if (!resolved.contains(dep)) {
                                next.add(dep);
                            }
                        }
                    }
                }
            }
            toProcess = next;
        }

        return resolved;
    }

    /**
     * 检测循环依赖。
     *
     * @return 存在循环依赖的变量 ID 集合，空集合表示无循环
     */
    public Set<String> detectCircularDependencies() {
        Set<String> circular = new HashSet<>();
        for (VariableDefinition def : variables.values()) {
            if (hasCycle(def.getVarId(), new HashSet<>())) {
                circular.add(def.getVarId());
            }
        }
        return circular;
    }

    private boolean hasCycle(String varId, Set<String> visiting) {
        if (visiting.contains(varId)) return true;
        VariableDefinition def = variables.get(varId);
        if (def == null || def.getDependencies().isEmpty()) return false;

        visiting.add(varId);
        for (String dep : def.getDependencies()) {
            if (hasCycle(dep, visiting)) {
                return true;
            }
        }
        visiting.remove(varId);
        return false;
    }

    /**
     * 返回注册变量总数。
     */
    public int size() { return variables.size(); }

    /**
     * 返回所有已注册变量定义的只读视图。
     */
    public Collection<VariableDefinition> getAll() {
        return Collections.unmodifiableCollection(variables.values());
    }

    /**
     * 清除所有注册变量（用于测试或热重载）。
     */
    public void clear() { variables.clear(); }
}
