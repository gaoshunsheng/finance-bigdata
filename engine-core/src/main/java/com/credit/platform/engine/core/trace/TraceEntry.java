package com.credit.platform.engine.core.trace;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 追踪条目 — 记录决策引擎中单个节点的执行细节。
 * <p>
 * 每个节点的执行产生一个 TraceEntry，包含:
 * <ul>
 *   <li>身份信息: traceId, nodeId, nodeType</li>
 *   <li>时间信息: startTime, endTime, durationMs</li>
 *   <li>数据快照: inputSnapshot (变量值), outputSnapshot (结果)</li>
 *   <li>扩展信息: details (节点类型特定的附加数据)</li>
 * </ul>
 * 不可变对象，线程安全。
 * </p>
 *
 * <pre>
 * TraceEntry entry = TraceEntry.builder()
 *     .traceId("trace-001")
 *     .nodeId("scorecard_01")
 *     .nodeType(NodeType.SCORECARD)
 *     .inputSnapshot(Map.of("age", 25, "income", 50000))
 *     .outputSnapshot(Map.of("score", 720, "result", "PASS"))
 *     .durationMs(12)
 *     .build();
 * </pre>
 */
public final class TraceEntry {

    private final String traceId;
    private final String nodeId;
    private final NodeType nodeType;
    private final long startTimeMs;
    private final long endTimeMs;
    private final long durationMs;
    private final Map<String, Object> inputSnapshot;
    private final Map<String, Object> outputSnapshot;
    private final Map<String, Object> details;
    private final boolean success;
    private final String errorMessage;

    private TraceEntry(Builder builder) {
        this.traceId = Objects.requireNonNull(builder.traceId, "traceId must not be null");
        this.nodeId = Objects.requireNonNull(builder.nodeId, "nodeId must not be null");
        this.nodeType = Objects.requireNonNull(builder.nodeType, "nodeType must not be null");
        this.startTimeMs = builder.startTimeMs;
        this.endTimeMs = builder.endTimeMs;
        this.durationMs = builder.durationMs;
        this.inputSnapshot = Collections.unmodifiableMap(
            new LinkedHashMap<>(builder.inputSnapshot));
        this.outputSnapshot = Collections.unmodifiableMap(
            new LinkedHashMap<>(builder.outputSnapshot));
        this.details = Collections.unmodifiableMap(
            new LinkedHashMap<>(builder.details));
        this.success = builder.success;
        this.errorMessage = builder.errorMessage;
    }

    // ========== Getters ==========

    public String getTraceId() { return traceId; }
    public String getNodeId() { return nodeId; }
    public NodeType getNodeType() { return nodeType; }
    public long getStartTimeMs() { return startTimeMs; }
    public long getEndTimeMs() { return endTimeMs; }
    public long getDurationMs() { return durationMs; }
    public Map<String, Object> getInputSnapshot() { return inputSnapshot; }
    public Map<String, Object> getOutputSnapshot() { return outputSnapshot; }
    public Map<String, Object> getDetails() { return details; }
    public boolean isSuccess() { return success; }
    public String getErrorMessage() { return errorMessage; }

    // ========== Builder ==========

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 从开始标记创建构建器。
     * <p>
     * 自动填充 traceId、nodeId、nodeType、startTimeMs。
     * </p>
     *
     * @param traceId  追踪 ID
     * @param nodeId   节点 ID
     * @param nodeType 节点类型
     * @return 已填充基础信息的构建器
     */
    public static Builder fromStart(String traceId, String nodeId, NodeType nodeType) {
        return builder()
            .traceId(traceId)
            .nodeId(nodeId)
            .nodeType(nodeType)
            .startTimeMs(System.currentTimeMillis());
    }

    /** 构建器 */
    public static class Builder {
        private String traceId;
        private String nodeId;
        private NodeType nodeType;
        private long startTimeMs;
        private long endTimeMs;
        private long durationMs;
        private Map<String, Object> inputSnapshot = new LinkedHashMap<>();
        private Map<String, Object> outputSnapshot = new LinkedHashMap<>();
        private Map<String, Object> details = new LinkedHashMap<>();
        private boolean success = true;
        private String errorMessage;

        public Builder traceId(String traceId) { this.traceId = traceId; return this; }
        public Builder nodeId(String nodeId) { this.nodeId = nodeId; return this; }
        public Builder nodeType(NodeType nodeType) { this.nodeType = nodeType; return this; }
        public Builder startTimeMs(long startTimeMs) { this.startTimeMs = startTimeMs; return this; }
        public Builder endTimeMs(long endTimeMs) { this.endTimeMs = endTimeMs; return this; }
        public Builder durationMs(long durationMs) { this.durationMs = durationMs; return this; }
        public Builder success(boolean success) { this.success = success; return this; }
        public Builder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }

        public Builder inputSnapshot(Map<String, Object> snapshot) {
            this.inputSnapshot = snapshot != null ? new LinkedHashMap<>(snapshot) : new LinkedHashMap<>();
            return this;
        }

        public Builder outputSnapshot(Map<String, Object> snapshot) {
            this.outputSnapshot = snapshot != null ? new LinkedHashMap<>(snapshot) : new LinkedHashMap<>();
            return this;
        }

        public Builder details(Map<String, Object> details) {
            this.details = details != null ? new LinkedHashMap<>(details) : new LinkedHashMap<>();
            return this;
        }

        /**
         * 向 details 中添加单条信息。
         *
         * @param key   键
         * @param value 值
         * @return 构建器自身
         */
        public Builder addDetail(String key, Object value) {
            this.details.put(key, value);
            return this;
        }

        /**
         * 结束追踪 — 自动设置 endTimeMs 和 durationMs。
         *
         * @return 构建器自身
         */
        public Builder end() {
            this.endTimeMs = System.currentTimeMillis();
            if (this.startTimeMs > 0) {
                this.durationMs = this.endTimeMs - this.startTimeMs;
            }
            return this;
        }

        public TraceEntry build() {
            return new TraceEntry(this);
        }
    }

    @Override
    public String toString() {
        return "TraceEntry{traceId='" + traceId + "', nodeId='" + nodeId
            + "', type=" + nodeType + ", duration=" + durationMs + "ms"
            + ", success=" + success + '}';
    }
}
