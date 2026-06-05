package com.credit.platform.data.governance.metadata;

/**
 * 列级元数据模型 — 描述表中一列的详细信息。
 */
public class ColumnMetadata {

    private String columnName;
    private String dataType;       // STRING / INTEGER / DECIMAL / DATE / TIMESTAMP
    private String description;
    private boolean nullable;
    private boolean isPrimaryKey;
    private String sensitivityLevel; // PUBLIC / INTERNAL / CONFIDENTIAL / TOP_SECRET
    private String maskStrategy;     // NONE / ID_CARD / MOBILE / BANK_CARD / NAME / EMAIL / CUSTOM
    private String sampleValue;

    public ColumnMetadata() {
    }

    public ColumnMetadata(String columnName, String dataType, String description) {
        this.columnName = columnName;
        this.dataType = dataType;
        this.description = description;
    }

    // Getters and Setters
    public String getColumnName() { return columnName; }
    public void setColumnName(String columnName) { this.columnName = columnName; }
    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isNullable() { return nullable; }
    public void setNullable(boolean nullable) { this.nullable = nullable; }
    public boolean isPrimaryKey() { return isPrimaryKey; }
    public void setPrimaryKey(boolean primaryKey) { isPrimaryKey = primaryKey; }
    public String getSensitivityLevel() { return sensitivityLevel; }
    public void setSensitivityLevel(String sensitivityLevel) { this.sensitivityLevel = sensitivityLevel; }
    public String getMaskStrategy() { return maskStrategy; }
    public void setMaskStrategy(String maskStrategy) { this.maskStrategy = maskStrategy; }
    public String getSampleValue() { return sampleValue; }
    public void setSampleValue(String sampleValue) { this.sampleValue = sampleValue; }
}
