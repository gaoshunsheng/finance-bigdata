package com.credit.platform.data.governance.quality;

/**
 * 数据质量规则定义。
 */
public class QualityRule {

    private String ruleId;
    private String ruleName;
    private QualityDimension dimension;
    private String targetTable;
    private String targetColumn;
    private String expression;    // 规则表达式（如 "NOT NULL", ">= 0", "REGEX: ^1[3-9]\\d{9}$"）
    private double threshold;     // 阈值（如空值率上限 0.05）
    private Severity severity;    // 严重程度

    public enum QualityDimension {
        COMPLETENESS,   // 完整性
        ACCURACY,       // 准确性
        CONSISTENCY,    // 一致性
        TIMELINESS,     // 及时性
        UNIQUENESS      // 唯一性
    }

    public enum Severity {
        INFO, WARNING, CRITICAL
    }

    public QualityRule() {
    }

    public QualityRule(String ruleId, String ruleName, QualityDimension dimension,
                       String targetTable, String targetColumn, String expression,
                       double threshold, Severity severity) {
        this.ruleId = ruleId;
        this.ruleName = ruleName;
        this.dimension = dimension;
        this.targetTable = targetTable;
        this.targetColumn = targetColumn;
        this.expression = expression;
        this.threshold = threshold;
        this.severity = severity;
    }

    // Getters
    public String getRuleId() { return ruleId; }
    public String getRuleName() { return ruleName; }
    public QualityDimension getDimension() { return dimension; }
    public String getTargetTable() { return targetTable; }
    public String getTargetColumn() { return targetColumn; }
    public String getExpression() { return expression; }
    public double getThreshold() { return threshold; }
    public Severity getSeverity() { return severity; }

    // Setters
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    public void setDimension(QualityDimension dimension) { this.dimension = dimension; }
    public void setTargetTable(String targetTable) { this.targetTable = targetTable; }
    public void setTargetColumn(String targetColumn) { this.targetColumn = targetColumn; }
    public void setExpression(String expression) { this.expression = expression; }
    public void setThreshold(double threshold) { this.threshold = threshold; }
    public void setSeverity(Severity severity) { this.severity = severity; }
}
