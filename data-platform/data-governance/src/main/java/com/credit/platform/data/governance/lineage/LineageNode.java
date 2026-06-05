package com.credit.platform.data.governance.lineage;

/**
 * 数据血缘节点 — 代表一个数据资产（表或字段）。
 */
public class LineageNode {

    private String nodeId;      // 格式: database.table 或 database.table.column
    private String name;
    private NodeType type;      // TABLE / COLUMN
    private String layer;       // ODS / DWD / DWS / ADS / EXTERNAL
    private String source;      // HIVE / MYSQL / HBASE / KAFKA / API

    public enum NodeType {
        TABLE, COLUMN
    }

    public LineageNode() {
    }

    public LineageNode(String nodeId, String name, NodeType type, String layer, String source) {
        this.nodeId = nodeId;
        this.name = name;
        this.type = type;
        this.layer = layer;
        this.source = source;
    }

    // Getters and Setters
    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public NodeType getType() { return type; }
    public void setType(NodeType type) { this.type = type; }
    public String getLayer() { return layer; }
    public void setLayer(String layer) { this.layer = layer; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    @Override
    public String toString() {
        return String.format("LineageNode{%s, type=%s, layer=%s}", nodeId, type, layer);
    }
}
