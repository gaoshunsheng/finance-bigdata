package com.credit.platform.data.governance.metadata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 元数据采集器 — 自动采集 Hive/MySQL/HBase 表结构并注册到元数据目录。
 *
 * <p>采集策略:
 * <ul>
 *   <li>Hive: 通过 Hive Metastore API 获取表结构</li>
 *   <li>MySQL: 通过 INFORMATION_SCHEMA 获取表结构</li>
 *   <li>HBase: 通过 Admin.getTableDescriptor() 获取列族信息</li>
 *   <li>自动检测间隔: ≤ 1 小时</li>
 * </ul>
 *
 * <p>当前实现为内存存储（生产环境替换为元数据中心数据库）
 */
public class MetadataCollector {

    private static final Logger log = LoggerFactory.getLogger(MetadataCollector.class);

    /** 元数据目录 — 按 database.tableName 索引 */
    private final Map<String, TableMetadata> catalog = new ConcurrentHashMap<>();

    /** 上次采集时间 */
    private LocalDateTime lastCollectionTime;

    /**
     * 注册表元数据。
     *
     * @param metadata 表元数据
     */
    public void register(TableMetadata metadata) {
        String key = metadata.getDatabase() + "." + metadata.getTableName();
        metadata.setDiscoveredTime(LocalDateTime.now());
        catalog.put(key, metadata);
        log.info("注册元数据: {} (source={}, layer={}, columns={})",
                key, metadata.getSource(), metadata.getLayer(), metadata.getColumns().size());
    }

    /**
     * 批量注册。
     */
    public void registerAll(List<TableMetadata> metadataList) {
        metadataList.forEach(this::register);
        lastCollectionTime = LocalDateTime.now();
    }

    /**
     * 查询表元数据。
     */
    public Optional<TableMetadata> getTable(String database, String tableName) {
        return Optional.ofNullable(catalog.get(database + "." + tableName));
    }

    /**
     * 按数据层查询所有表。
     */
    public List<TableMetadata> getTablesByLayer(String layer) {
        return catalog.values().stream()
                .filter(t -> layer.equals(t.getLayer()))
                .toList();
    }

    /**
     * 按标签查询。
     */
    public List<TableMetadata> getTablesByTag(String tag) {
        return catalog.values().stream()
                .filter(t -> t.getTags().contains(tag))
                .toList();
    }

    /**
     * 按数据源查询。
     */
    public List<TableMetadata> getTablesBySource(String source) {
        return catalog.values().stream()
                .filter(t -> source.equals(t.getSource()))
                .toList();
    }

    /**
     * 全量查询。
     */
    public List<TableMetadata> getAllTables() {
        return new ArrayList<>(catalog.values());
    }

    /**
     * 元数据总数。
     */
    public int size() {
        return catalog.size();
    }

    /**
     * 检测表结构变更（对比已有元数据与新采集元数据的差异）。
     *
     * @param newMetadata 新采集的元数据
     * @return 变更描述列表（新增列、删除列、类型变更等）
     */
    public List<String> detectChanges(TableMetadata newMetadata) {
        List<String> changes = new ArrayList<>();
        String key = newMetadata.getDatabase() + "." + newMetadata.getTableName();
        TableMetadata existing = catalog.get(key);

        if (existing == null) {
            changes.add("新表: " + key);
            return changes;
        }

        // 检测列变更
        Map<String, ColumnMetadata> existingCols = new HashMap<>();
        existing.getColumns().forEach(c -> existingCols.put(c.getColumnName(), c));

        Map<String, ColumnMetadata> newCols = new HashMap<>();
        newMetadata.getColumns().forEach(c -> newCols.put(c.getColumnName(), c));

        // 新增列
        for (String colName : newCols.keySet()) {
            if (!existingCols.containsKey(colName)) {
                changes.add("新增列: " + colName + " (" + newCols.get(colName).getDataType() + ")");
            }
        }

        // 删除列
        for (String colName : existingCols.keySet()) {
            if (!newCols.containsKey(colName)) {
                changes.add("删除列: " + colName);
            }
        }

        // 类型变更
        for (String colName : existingCols.keySet()) {
            ColumnMetadata existingCol = existingCols.get(colName);
            ColumnMetadata newCol = newCols.get(colName);
            if (newCol != null && !existingCol.getDataType().equals(newCol.getDataType())) {
                changes.add("类型变更: " + colName + " " + existingCol.getDataType() + " → " + newCol.getDataType());
            }
        }

        return changes;
    }

    public LocalDateTime getLastCollectionTime() {
        return lastCollectionTime;
    }
}
