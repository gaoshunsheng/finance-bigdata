package com.credit.platform.data.governance.metadata;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 表级元数据模型 — 描述一个数据资产（表）的完整信息。
 */
public class TableMetadata {

    private String tableId;
    private String tableName;
    private String database;
    private String source;         // HIVE / MYSQL / HBASE
    private String layer;          // ODS / DWD / DWS / ADS
    private String description;
    private String owner;
    private List<String> tags = new ArrayList<>();
    private List<ColumnMetadata> columns = new ArrayList<>();
    private String sensitivityLevel; // PUBLIC / INTERNAL / CONFIDENTIAL / TOP_SECRET
    private long rowCount;
    private long sizeInBytes;
    private String partitionColumn;
    private LocalDateTime lastModifiedTime;
    private LocalDateTime discoveredTime;

    // Getters and Setters
    public String getTableId() { return tableId; }
    public void setTableId(String tableId) { this.tableId = tableId; }
    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public String getDatabase() { return database; }
    public void setDatabase(String database) { this.database = database; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getLayer() { return layer; }
    public void setLayer(String layer) { this.layer = layer; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public List<ColumnMetadata> getColumns() { return columns; }
    public void setColumns(List<ColumnMetadata> columns) { this.columns = columns; }
    public String getSensitivityLevel() { return sensitivityLevel; }
    public void setSensitivityLevel(String sensitivityLevel) { this.sensitivityLevel = sensitivityLevel; }
    public long getRowCount() { return rowCount; }
    public void setRowCount(long rowCount) { this.rowCount = rowCount; }
    public long getSizeInBytes() { return sizeInBytes; }
    public void setSizeInBytes(long sizeInBytes) { this.sizeInBytes = sizeInBytes; }
    public String getPartitionColumn() { return partitionColumn; }
    public void setPartitionColumn(String partitionColumn) { this.partitionColumn = partitionColumn; }
    public LocalDateTime getLastModifiedTime() { return lastModifiedTime; }
    public void setLastModifiedTime(LocalDateTime lastModifiedTime) { this.lastModifiedTime = lastModifiedTime; }
    public LocalDateTime getDiscoveredTime() { return discoveredTime; }
    public void setDiscoveredTime(LocalDateTime discoveredTime) { this.discoveredTime = discoveredTime; }

    public void addTag(String tag) { this.tags.add(tag); }
    public void addColumn(ColumnMetadata column) { this.columns.add(column); }

    @Override
    public String toString() {
        return String.format("TableMetadata{%s.%s, source=%s, layer=%s, columns=%d}",
                database, tableName, source, layer, columns.size());
    }
}
