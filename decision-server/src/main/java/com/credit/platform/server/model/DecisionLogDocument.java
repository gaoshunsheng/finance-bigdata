package com.credit.platform.server.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 决策日志 ES 文档 — 持久化每次决策执行的完整记录。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DecisionLogDocument {

    @JsonProperty("id")
    private String id;

    @JsonProperty("traceId")
    private String traceId;

    @JsonProperty("customerId")
    private String customerId;

    @JsonProperty("decisionResult")
    private String decisionResult;

    @JsonProperty("score")
    private double score;

    @JsonProperty("riskLevel")
    private String riskLevel;

    @JsonProperty("rejectReason")
    private String rejectReason;

    @JsonProperty("rulesExecuted")
    private List<String> rulesExecuted;

    @JsonProperty("executionTimeMs")
    private long executionTimeMs;

    @JsonProperty("inputSnapshot")
    private String inputSnapshot;

    @JsonProperty("outputSnapshot")
    private String outputSnapshot;

    @JsonProperty("timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;

    public DecisionLogDocument() {
        this.timestamp = LocalDateTime.now();
    }

    // ========== Getters / Setters ==========

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getDecisionResult() { return decisionResult; }
    public void setDecisionResult(String decisionResult) { this.decisionResult = decisionResult; }

    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }

    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }

    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }

    public List<String> getRulesExecuted() { return rulesExecuted; }
    public void setRulesExecuted(List<String> rulesExecuted) { this.rulesExecuted = rulesExecuted; }

    public long getExecutionTimeMs() { return executionTimeMs; }
    public void setExecutionTimeMs(long executionTimeMs) { this.executionTimeMs = executionTimeMs; }

    public String getInputSnapshot() { return inputSnapshot; }
    public void setInputSnapshot(String inputSnapshot) { this.inputSnapshot = inputSnapshot; }

    public String getOutputSnapshot() { return outputSnapshot; }
    public void setOutputSnapshot(String outputSnapshot) { this.outputSnapshot = outputSnapshot; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
