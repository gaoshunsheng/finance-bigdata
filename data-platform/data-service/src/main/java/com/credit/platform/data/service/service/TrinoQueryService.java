package com.credit.platform.data.service.service;

import com.credit.platform.data.service.config.TrinoConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.*;

/**
 * Trino 查询服务 — 封装 Trino JDBC 查询逻辑。
 *
 * <p>提供两类查询方法:
 * <ul>
 *   <li>{@link #query(String)} — 返回多行结果</li>
 *   <li>{@link #queryOne(String)} — 返回单行结果</li>
 * </ul>
 *
 * <p>所有 SQLException 均被捕获并返回空结果，确保上游调用者不受影响。
 */
@Service
public class TrinoQueryService {

    private static final Logger log = LoggerFactory.getLogger(TrinoQueryService.class);

    private final TrinoConfig trinoConfig;

    public TrinoQueryService(TrinoConfig trinoConfig) {
        this.trinoConfig = trinoConfig;
    }

    /**
     * 执行 SQL 查询，返回所有结果行。
     *
     * @param sql SQL 查询语句
     * @return 结果列表，每行为 Map&lt;列名, 值&gt;；查询失败返回空列表
     */
    public List<Map<String, Object>> query(String sql) {
        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = trinoConfig.createConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();

            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= colCount; i++) {
                    row.put(meta.getColumnLabel(i), rs.getObject(i));
                }
                results.add(row);
            }

            log.info("Trino 查询成功: sql={}, 行数={}", abbreviateSql(sql), results.size());
        } catch (SQLException e) {
            log.warn("Trino 查询失败: sql={}, error={}", abbreviateSql(sql), e.getMessage());
        }
        return results;
    }

    /**
     * 执行 SQL 查询，返回第一行结果。
     *
     * @param sql SQL 查询语句
     * @return Optional 包含第一行结果；查询失败或无结果返回 Optional.empty()
     */
    public Optional<Map<String, Object>> queryOne(String sql) {
        List<Map<String, Object>> results = query(sql);
        if (results.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(results.get(0));
    }

    /**
     * 截断 SQL 用于日志输出。
     */
    private String abbreviateSql(String sql) {
        if (sql == null) {
            return "null";
        }
        String oneLine = sql.replaceAll("\\s+", " ").trim();
        if (oneLine.length() > 120) {
            return oneLine.substring(0, 120) + "...";
        }
        return oneLine;
    }
}
