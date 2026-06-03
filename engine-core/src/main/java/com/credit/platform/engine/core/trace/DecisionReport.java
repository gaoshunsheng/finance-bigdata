package com.credit.platform.engine.core.trace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 决策报告 — 一次完整决策的可解释性报告。
 * <p>
 * 由 {@link TraceReporter} 从 {@link DecisionTrace} 生成，包含:
 * <ul>
 *   <li>决策身份: traceId, decisionId, strategyId</li>
 *   <li>决策结论: finalResult, finalScore, rejectReason, rejectCode</li>
 *   <li>执行路径: 决策流经过的所有节点</li>
 *   <li>节点详情: 每个节点的执行明细</li>
 *   <li>贡献因子: 按影响度排序的关键因素</li>
 * </ul>
 * 不可变对象。
 * </p>
 */
public final class DecisionReport {

    private final String traceId;
    private final String decisionId;
    private final String strategyId;
    private final String finalResult;
    private final Integer finalScore;
    private final String rejectReason;
    private final String rejectCode;
    private final long totalDurationMs;
    private final List<String> decisionPath;
    private final List<NodeReport> nodeReports;
    private final List<ContributionFactor> topFactors;
    private final Map<String, Object> summary;

    private DecisionReport(Builder builder) {
        this.traceId = builder.traceId;
        this.decisionId = builder.decisionId;
        this.strategyId = builder.strategyId;
        this.finalResult = builder.finalResult;
        this.finalScore = builder.finalScore;
        this.rejectReason = builder.rejectReason;
        this.rejectCode = builder.rejectCode;
        this.totalDurationMs = builder.totalDurationMs;
        this.decisionPath = Collections.unmodifiableList(new ArrayList<>(builder.decisionPath));
        this.nodeReports = Collections.unmodifiableList(new ArrayList<>(builder.nodeReports));
        this.topFactors = Collections.unmodifiableList(new ArrayList<>(builder.topFactors));
        this.summary = Collections.unmodifiableMap(new LinkedHashMap<>(builder.summary));
    }

    // ========== Getters ==========

    public String getTraceId() { return traceId; }
    public String getDecisionId() { return decisionId; }
    public String getStrategyId() { return strategyId; }
    public String getFinalResult() { return finalResult; }
    public Integer getFinalScore() { return finalScore; }
    public String getRejectReason() { return rejectReason; }
    public String getRejectCode() { return rejectCode; }
    public long getTotalDurationMs() { return totalDurationMs; }
    public List<String> getDecisionPath() { return decisionPath; }
    public List<NodeReport> getNodeReports() { return nodeReports; }
    public List<ContributionFactor> getTopFactors() { return topFactors; }
    public Map<String, Object> getSummary() { return summary; }

    // ========== 内嵌类 ==========

    /**
     * 单个节点的执行报告。
     */
    public static final class NodeReport {
        private final String nodeId;
        private final NodeType nodeType;
        private final long durationMs;
        private final boolean success;
        private final String errorMessage;
        private final Map<String, Object> inputSnapshot;
        private final Map<String, Object> outputSnapshot;
        private final Map<String, Object> details;

        public NodeReport(String nodeId, NodeType nodeType, long durationMs,
                          boolean success, String errorMessage,
                          Map<String, Object> inputSnapshot,
                          Map<String, Object> outputSnapshot,
                          Map<String, Object> details) {
            this.nodeId = nodeId;
            this.nodeType = nodeType;
            this.durationMs = durationMs;
            this.success = success;
            this.errorMessage = errorMessage;
            this.inputSnapshot = Collections.unmodifiableMap(new LinkedHashMap<>(inputSnapshot));
            this.outputSnapshot = Collections.unmodifiableMap(new LinkedHashMap<>(outputSnapshot));
            this.details = Collections.unmodifiableMap(new LinkedHashMap<>(details));
        }

        public String getNodeId() { return nodeId; }
        public NodeType getNodeType() { return nodeType; }
        public long getDurationMs() { return durationMs; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
        public Map<String, Object> getInputSnapshot() { return inputSnapshot; }
        public Map<String, Object> getOutputSnapshot() { return outputSnapshot; }
        public Map<String, Object> getDetails() { return details; }

        @Override
        public String toString() {
            return "NodeReport{id='" + nodeId + "', type=" + nodeType
                + ", duration=" + durationMs + "ms, success=" + success + '}';
        }
    }

    /**
     * 贡献因子 — 按影响度排序的决策关键因素。
     */
    public static final class ContributionFactor {
        private final String factor;
        private final String description;
        private final double influence;
        private final String direction;

        public ContributionFactor(String factor, String description,
                                   double influence, String direction) {
            this.factor = factor;
            this.description = description;
            this.influence = influence;
            this.direction = direction;
        }

        public String getFactor() { return factor; }
        public String getDescription() { return description; }
        public double getInfluence() { return influence; }
        public String getDirection() { return direction; }

        @Override
        public String toString() {
            return factor + " (" + direction + ", influence=" + influence + ')';
        }
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
        private String finalResult;
        private Integer finalScore;
        private String rejectReason;
        private String rejectCode;
        private long totalDurationMs;
        private List<String> decisionPath = new ArrayList<>();
        private List<NodeReport> nodeReports = new ArrayList<>();
        private List<ContributionFactor> topFactors = new ArrayList<>();
        private Map<String, Object> summary = new LinkedHashMap<>();

        public Builder traceId(String traceId) { this.traceId = traceId; return this; }
        public Builder decisionId(String decisionId) { this.decisionId = decisionId; return this; }
        public Builder strategyId(String strategyId) { this.strategyId = strategyId; return this; }
        public Builder finalResult(String result) { this.finalResult = result; return this; }
        public Builder finalScore(Integer score) { this.finalScore = score; return this; }
        public Builder rejectReason(String reason) { this.rejectReason = reason; return this; }
        public Builder rejectCode(String code) { this.rejectCode = code; return this; }
        public Builder totalDurationMs(long ms) { this.totalDurationMs = ms; return this; }
        public Builder decisionPath(List<String> path) { this.decisionPath = path; return this; }
        public Builder nodeReports(List<NodeReport> reports) { this.nodeReports = reports; return this; }
        public Builder topFactors(List<ContributionFactor> factors) { this.topFactors = factors; return this; }
        public Builder summary(Map<String, Object> summary) { this.summary = summary; return this; }
        public DecisionReport build() { return new DecisionReport(this); }
    }

    @Override
    public String toString() {
        return "DecisionReport{traceId='" + traceId + "', result=" + finalResult
            + ", score=" + finalScore + ", nodes=" + nodeReports.size()
            + ", duration=" + totalDurationMs + "ms}";
    }
}
