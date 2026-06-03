package com.credit.platform.engine.core.trace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 决策追踪容器 — 记录一次完整决策请求的所有追踪条目。
 * <p>
 * 每个决策请求创建一个 DecisionTrace 实例，包含:
 * <ul>
 *   <li>traceId — 追踪唯一标识</li>
 *   <li>decisionId — 决策唯一标识</li>
 *   <li>strategyId — 策略 ID</li>
 *   <li>entries — 按时间顺序记录的 TraceEntry 列表</li>
 *   <li>decisionPath — DAG 执行路径 (节点 ID 列表)</li>
 *   <li>最终决策结果、评分、耗时等</li>
 * </ul>
 * </p>
 *
 * <pre>
 * DecisionTrace trace = DecisionTrace.create("trace-001", "decision-001", "strategy-v1");
 * trace.addEntry(entry);
 * trace.setFinalResult(DecisionResult.PASS, 720, null, null);
 * </pre>
 */
public final class DecisionTrace {

    private final String traceId;
    private final String decisionId;
    private final String strategyId;
    private final long startTimeMs;
    private final List<TraceEntry> entries;
    private final List<String> decisionPath;
    private final Map<String, Object> metadata;

    // 最终决策结果 (延迟设置)
    private String finalResult;
    private Integer finalScore;
    private String rejectReason;
    private String rejectCode;
    private long totalDurationMs;

    private DecisionTrace(Builder builder) {
        this.traceId = Objects.requireNonNull(builder.traceId, "traceId must not be null");
        this.decisionId = builder.decisionId;
        this.strategyId = builder.strategyId;
        this.startTimeMs = builder.startTimeMs > 0 ? builder.startTimeMs : System.currentTimeMillis();
        this.entries = Collections.synchronizedList(new ArrayList<>(builder.entries));
        this.decisionPath = Collections.synchronizedList(new ArrayList<>(builder.decisionPath));
        this.metadata = Collections.synchronizedMap(new LinkedHashMap<>(builder.metadata));
        this.finalResult = builder.finalResult;
        this.finalScore = builder.finalScore;
        this.rejectReason = builder.rejectReason;
        this.rejectCode = builder.rejectCode;
        this.totalDurationMs = builder.totalDurationMs;
    }

    /**
     * 快速创建追踪容器。
     *
     * @param traceId    追踪 ID
     * @param decisionId 决策 ID
     * @param strategyId 策略 ID
     * @return 新的决策追踪容器
     */
    public static DecisionTrace create(String traceId, String decisionId, String strategyId) {
        return new Builder()
            .traceId(traceId)
            .decisionId(decisionId)
            .strategyId(strategyId)
            .startTimeMs(System.currentTimeMillis())
            .build();
    }

    // ========== 运行时操作 ==========

    /**
     * 添加追踪条目。
     *
     * @param entry 追踪条目
     */
    public void addEntry(TraceEntry entry) {
        entries.add(Objects.requireNonNull(entry));
    }

    /**
     * 添加决策路径节点。
     *
     * @param nodeId 节点 ID
     */
    public void addPathNode(String nodeId) {
        decisionPath.add(nodeId);
    }

    /**
     * 设置最终决策结果。
     *
     * @param result       决策结果 (PASS/REJECT/REVIEW/MANUAL)
     * @param score        评分 (可为null)
     * @param rejectReason 拒绝原因 (可为null)
     * @param rejectCode   拒绝码 (可为null)
     */
    public void setFinalResult(String result, Integer score,
                                String rejectReason, String rejectCode) {
        this.finalResult = result;
        this.finalScore = score;
        this.rejectReason = rejectReason;
        this.rejectCode = rejectCode;
        this.totalDurationMs = System.currentTimeMillis() - startTimeMs;
    }

    /**
     * 添加元数据。
     *
     * @param key   键
     * @param value 值
     */
    public void addMetadata(String key, Object value) {
        metadata.put(key, value);
    }

    // ========== Getters ==========

    public String getTraceId() { return traceId; }
    public String getDecisionId() { return decisionId; }
    public String getStrategyId() { return strategyId; }
    public long getStartTimeMs() { return startTimeMs; }

    /**
     * 获取所有追踪条目（只读副本）。
     *
     * @return 不可修改的追踪条目列表
     */
    public List<TraceEntry> getEntries() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }

    /**
     * 获取决策路径（只读副本）。
     *
     * @return 不可修改的节点 ID 列表
     */
    public List<String> getDecisionPath() {
        return Collections.unmodifiableList(new ArrayList<>(decisionPath));
    }

    public Map<String, Object> getMetadata() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
    public String getFinalResult() { return finalResult; }
    public Integer getFinalScore() { return finalScore; }
    public String getRejectReason() { return rejectReason; }
    public String getRejectCode() { return rejectCode; }
    public long getTotalDurationMs() { return totalDurationMs; }

    /**
     * 获取指定节点类型的追踪条目。
     *
     * @param nodeType 节点类型
     * @return 匹配的追踪条目列表
     */
    public List<TraceEntry> getEntriesByType(NodeType nodeType) {
        List<TraceEntry> result = new ArrayList<>();
        for (TraceEntry entry : entries) {
            if (entry.getNodeType() == nodeType) {
                result.add(entry);
            }
        }
        return result;
    }

    /**
     * 获取指定节点 ID 的追踪条目。
     *
     * @param nodeId 节点 ID
     * @return 匹配的追踪条目，不存在时返回 null
     */
    public TraceEntry getEntryByNodeId(String nodeId) {
        for (TraceEntry entry : entries) {
            if (entry.getNodeId().equals(nodeId)) {
                return entry;
            }
        }
        return null;
    }

    /**
     * 获取追踪条目总数。
     *
     * @return 条目数量
     */
    public int getEntryCount() {
        return entries.size();
    }

    // ========== Builder ==========

    public static Builder builder() {
        return new Builder();
    }

    /** 构建器 */
    public static class Builder {
        private String traceId;
        private String decisionId;
        private String strategyId;
        private long startTimeMs;
        private List<TraceEntry> entries = new ArrayList<>();
        private List<String> decisionPath = new ArrayList<>();
        private Map<String, Object> metadata = new LinkedHashMap<>();
        private String finalResult;
        private Integer finalScore;
        private String rejectReason;
        private String rejectCode;
        private long totalDurationMs;

        public Builder traceId(String traceId) { this.traceId = traceId; return this; }
        public Builder decisionId(String decisionId) { this.decisionId = decisionId; return this; }
        public Builder strategyId(String strategyId) { this.strategyId = strategyId; return this; }
        public Builder startTimeMs(long startTimeMs) { this.startTimeMs = startTimeMs; return this; }
        public Builder entries(List<TraceEntry> entries) { this.entries = entries; return this; }
        public Builder decisionPath(List<String> path) { this.decisionPath = path; return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }
        public Builder finalResult(String result) { this.finalResult = result; return this; }
        public Builder finalScore(Integer score) { this.finalScore = score; return this; }
        public Builder rejectReason(String reason) { this.rejectReason = reason; return this; }
        public Builder rejectCode(String code) { this.rejectCode = code; return this; }
        public Builder totalDurationMs(long ms) { this.totalDurationMs = ms; return this; }
        public DecisionTrace build() { return new DecisionTrace(this); }
    }

    @Override
    public String toString() {
        return "DecisionTrace{id='" + traceId + "', entries=" + entries.size()
            + ", path=" + decisionPath + ", result=" + finalResult
            + ", duration=" + totalDurationMs + "ms}";
    }
}
