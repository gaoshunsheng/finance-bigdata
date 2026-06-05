package com.credit.platform.data.governance.lineage;

/**
 * 数据血缘边 — 描述两个节点之间的数据转换关系。
 */
public class LineageEdge {

    private String sourceId;
    private String targetId;
    private String transformType; // ETL / STREAMING / API / MANUAL
    private String transformName; // 转换名称（如 ETL 脚本名、Flink 作业名）
    private String description;

    public LineageEdge() {
    }

    public LineageEdge(String sourceId, String targetId, String transformType, String transformName) {
        this.sourceId = sourceId;
        this.targetId = targetId;
        this.transformType = transformType;
        this.transformName = transformName;
    }

    // Getters and Setters
    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }
    public String getTransformType() { return transformType; }
    public void setTransformType(String transformType) { this.transformType = transformType; }
    public String getTransformName() { return transformName; }
    public void setTransformName(String transformName) { this.transformName = transformName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    @Override
    public String toString() {
        return String.format("LineageEdge{%s → %s, type=%s, name=%s}",
                sourceId, targetId, transformType, transformName);
    }
}
