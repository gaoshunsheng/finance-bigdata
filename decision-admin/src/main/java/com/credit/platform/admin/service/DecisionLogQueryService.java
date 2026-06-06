package com.credit.platform.admin.service;

import java.time.LocalDateTime;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.credit.platform.admin.model.ApiResponse;

/**
 * 决策日志查询服务 — 查询决策执行日志。
 * <p>
 * 当前实现基于 audit_log 表提供查询能力。
 * 后续可切换到 Elasticsearch 实现以支持更复杂的全文检索和聚合分析。
 * </p>
 * <p>
 * 核心功能:
 * <ul>
 *   <li>分页查询决策日志</li>
 *   <li>查询单条日志详情</li>
 *   <li>聚合统计 — 按日期范围统计 pass/reject/review 数量</li>
 * </ul>
 * </p>
 */
@Service
public class DecisionLogQueryService {

    private static final Logger log = LoggerFactory.getLogger(DecisionLogQueryService.class);

    private final JdbcTemplate jdbcTemplate;

    public DecisionLogQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    /**
     * 分页查询决策日志。
     *
     * @param params 查询参数（支持 operator, action, targetType, targetId, startDate, endDate）
     * @param page   页码（从 1 开始）
     * @param size   每页大小
     * @return 分页结果
     */
    public Map<String, Object> queryLogs(Map<String, Object> params, int page, int size) {
        StringBuilder sql = new StringBuilder("SELECT * FROM audit_log WHERE 1=1");
        List<Object> args = new ArrayList<>();

        if (params != null) {
            appendCondition(sql, args, params, "operator", "operator");
            appendCondition(sql, args, params, "action", "action");
            appendCondition(sql, args, params, "targetType", "target_type");
            appendCondition(sql, args, params, "targetId", "target_id");
            if (params.containsKey("startDate")) {
                sql.append(" AND operated_at >= ?");
                args.add(LocalDateTime.parse(params.get("startDate").toString()));
            }
            if (params.containsKey("endDate")) {
                sql.append(" AND operated_at <= ?");
                args.add(LocalDateTime.parse(params.get("endDate").toString()));
            }
        }

        // Count
        String countSql = "SELECT COUNT(*) FROM (" + sql + ") t";
        long total = jdbcTemplate.queryForObject(countSql, Long.class, args.toArray());

        // Query with pagination
        sql.append(" ORDER BY operated_at DESC LIMIT ? OFFSET ?");
        args.add(size);
        args.add((page - 1) * size);

        List<Map<String, Object>> records = jdbcTemplate.queryForList(sql.toString(), args.toArray());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        result.put("totalPages", (total + size - 1) / size);

        log.debug("查询决策日志: page={}, size={}, total={}", page, size, total);
        return result;
    }

    /**
     * 查询单条日志详情。
     *
     * @param decisionId 日志 ID
     * @return 日志详情
     */
    public Map<String, Object> getLogDetail(String decisionId) {
        String sql = "SELECT * FROM audit_log WHERE id = ?";
        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, Long.parseLong(decisionId));
        if (results.isEmpty()) {
            throw new IllegalArgumentException("Decision log not found: " + decisionId);
        }
        return results.get(0);
    }

    /**
     * 聚合统计 — 按日期范围统计各操作类型的数量。
     *
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 聚合统计结果
     */
    public List<Map<String, Object>> getStats(LocalDateTime startDate, LocalDateTime endDate) {
        String sql = "SELECT DATE(operated_at) as date, action, COUNT(*) as count " +
            "FROM audit_log " +
            "WHERE operated_at >= ? AND operated_at <= ? " +
            "GROUP BY DATE(operated_at), action " +
            "ORDER BY date DESC";

        List<Map<String, Object>> stats = jdbcTemplate.queryForList(sql, startDate, endDate);
        log.debug("查询决策统计: startDate={}, endDate={}, rows={}", startDate, endDate, stats.size());
        return stats;
    }

    private void appendCondition(StringBuilder sql, List<Object> args,
                                  Map<String, Object> params, String paramKey, String column) {
        if (params.containsKey(paramKey) && params.get(paramKey) != null) {
            sql.append(" AND ").append(column).append(" = ?");
            args.add(params.get(paramKey));
        }
    }
}
