package com.credit.platform.data.governance.quality;

import java.time.LocalDateTime;

/**
 * 数据质量违规记录 — 记录质量检测发现的问题。
 */
public class QualityViolation {

    private String violationId;
    private String ruleId;
    private String ruleName;
    private QualityRule.QualityDimension dimension;
    private String targetTable;
    private String targetColumn;
    private String violationDetail;
    private QualityRule.Severity severity;
    private LocalDateTime detectedTime;
    private String alertStatus;   // PENDING / SENT / ACKNOWLEDGED

    public QualityViolation() {
        this.detectedTime = LocalDateTime.now();
        this.alertStatus = "PENDING";
    }

    public QualityViolation(String ruleId, String ruleName, QualityRule.QualityDimension dimension,
                            String targetTable, String targetColumn, String violationDetail,
                            QualityRule.Severity severity) {
        this.violationId = "v_" + System.currentTimeMillis();
        this.ruleId = ruleId;
        this.ruleName = ruleName;
        this.dimension = dimension;
        this.targetTable = targetTable;
        this.targetColumn = targetColumn;
        this.violationDetail = violationDetail;
        this.severity = severity;
        this.detectedTime = LocalDateTime.now();
        this.alertStatus = "PENDING";
    }

    // Getters and Setters
    public String getViolationId() { return violationId; }
    public void setViolationId(String violationId) { this.violationId = violationId; }
    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    public QualityRule.QualityDimension getDimension() { return dimension; }
    public void setDimension(QualityRule.QualityDimension dimension) { this.dimension = dimension; }
    public String getTargetTable() { return targetTable; }
    public void setTargetTable(String targetTable) { this.targetTable = targetTable; }
    public String getTargetColumn() { return targetColumn; }
    public void setTargetColumn(String targetColumn) { this.targetColumn = targetColumn; }
    public String getViolationDetail() { return violationDetail; }
    public void setViolationDetail(String violationDetail) { this.violationDetail = violationDetail; }
    public QualityRule.Severity getSeverity() { return severity; }
    public void setSeverity(QualityRule.Severity severity) { this.severity = severity; }
    public LocalDateTime getDetectedTime() { return detectedTime; }
    public void setDetectedTime(LocalDateTime detectedTime) { this.detectedTime = detectedTime; }
    public String getAlertStatus() { return alertStatus; }
    public void setAlertStatus(String alertStatus) { this.alertStatus = alertStatus; }

    @Override
    public String toString() {
        return String.format("QualityViolation{%s, rule=%s, dim=%s, table=%s.%s, detail=%s}",
                violationId, ruleName, dimension, targetTable, targetColumn, violationDetail);
    }
}
