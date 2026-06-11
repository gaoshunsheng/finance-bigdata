package com.credit.platform.engine.core.variable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 预取结果 — VariablePrefetcher 预加载 L2 变量的结果。
 *
 * <p>包含:
 * <ul>
 *   <li>{@code variables} — 成功获取的变量值</li>
 *   <li>{@code failed} — 获取失败的变量 ID 集合</li>
 *   <li>{@code latencyMs} — 预取总耗时（毫秒）</li>
 * </ul>
 *
 * <p>注意：此类定义在 engine-core 中，不依赖 Redis/HBase，
 * 具体的存储实现在 decision-server 层。
 */
public final class PrefetchResult {

    private final Map<String, Object> variables;
    private final Set<String> failed;
    private final long latencyMs;

    private PrefetchResult(Map<String, Object> variables, Set<String> failed, long latencyMs) {
        this.variables = Collections.unmodifiableMap(new HashMap<>(variables));
        this.failed = Collections.unmodifiableSet(failed);
        this.latencyMs = latencyMs;
    }

    /**
     * 全部成功的预取结果。
     */
    public static PrefetchResult success(Map<String, Object> variables, long latencyMs) {
        return new PrefetchResult(variables, Set.of(), latencyMs);
    }

    /**
     * 部分成功的预取结果。
     */
    public static PrefetchResult partial(Map<String, Object> variables, Set<String> failed, long latencyMs) {
        return new PrefetchResult(variables, failed, latencyMs);
    }

    /**
     * 全部失败的预取结果。
     */
    public static PrefetchResult failure(Set<String> failedVarIds, long latencyMs) {
        return new PrefetchResult(Map.of(), failedVarIds, latencyMs);
    }

    /**
     * 空预取结果 — 无 L2 变量需要预取。
     */
    public static PrefetchResult empty() {
        return new PrefetchResult(Map.of(), Set.of(), 0);
    }

    /** 获取成功预取的变量值 */
    public Map<String, Object> getVariables() { return variables; }

    /** 获取失败的变量 ID 集合 */
    public Set<String> getFailed() { return failed; }

    /** 获取预取耗时（毫秒） */
    public long getLatencyMs() { return latencyMs; }

    /** 是否全部成功 */
    public boolean isAllSuccess() { return failed.isEmpty(); }

    /** 是否有部分失败 */
    public boolean hasPartialFailure() { return !failed.isEmpty() && !variables.isEmpty(); }

    /** 成功获取的变量数量 */
    public int getSuccessCount() { return variables.size(); }

    /** 失败的变量数量 */
    public int getFailureCount() { return failed.size(); }

    @Override
    public String toString() {
        return "PrefetchResult{" +
            "success=" + variables.size() +
            ", failed=" + failed.size() +
            ", latencyMs=" + latencyMs +
            '}';
    }
}
