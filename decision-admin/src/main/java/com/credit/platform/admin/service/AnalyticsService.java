package com.credit.platform.admin.service;

import java.time.LocalDateTime;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 分析服务 — 聚合分析决策执行数据。
 * <p>
 * 当前实现基于 audit_log 表提供聚合分析能力。
 * 后续可切换到 Elasticsearch 实现以支持更复杂的聚合分析。
 * </p>
 * <p>
 * 核心功能:
 * <ul>
 *   <li>通过率趋势 — 按日/周/月维度统计通过率</li>
 *   <li>规则命中排行 — 按 hit count 排序的 Top-N 规则</li>
 *   <li>评分分布直方图 — 评分区间的分布统计</li>
 * </ul>
 * </p>
 */
@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    private final JdbcTemplate jdbcTemplate;

    public AnalyticsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    /**
     * 通过率趋势 — 按日/周/月维度统计通过率。
     * <p>
     * 统计 audit_log 中 APPROVE 和 REJECT 操作的比例。
     * </p>
     *
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @param granularity 时间粒度: daily / weekly / monthly
     * @return 通过率趋势数据
     */
    public List<Map<String, Object>> getPassRateTrend(LocalDateTime startDate, LocalDateTime endDate,
                                                       String granularity) {
        String dateFormat = switch (granularity != null ? granularity : "daily") {
            case "weekly" -> "%Y-%u";      // Year-Week
            case "monthly" -> "%Y-%m";     // Year-Month
            default -> "%Y-%m-%d";         // Year-Month-Day
        };

        String sql = "SELECT " +
            "DATE_FORMAT(operated_at, '" + dateFormat + "') AS period, " +
            "COUNT(*) AS total, " +
            "SUM(CASE WHEN action = 'APPROVE' THEN 1 ELSE 0 END) AS approved, " +
            "SUM(CASE WHEN action = 'REJECT' THEN 1 ELSE 0 END) AS rejected " +
            "FROM audit_log " +
            "WHERE operated_at >= ? AND operated_at <= ? " +
            "AND action IN ('APPROVE', 'REJECT') " +
            "GROUP BY period " +
            "ORDER BY period ASC";

        List<Map<String, Object>> rawStats = jdbcTemplate.queryForList(sql, startDate, endDate);

        // Calculate pass rate
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rawStats) {
            Map<String, Object> entry = new LinkedHashMap<>(row);
            long total = ((Number) row.get("total")).longValue();
            long approved = ((Number) row.get("approved")).longValue();
            double passRate = total > 0 ? (double) approved / total * 100 : 0;
            entry.put("passRate", Math.round(passRate * 100.0) / 100.0);
            result.add(entry);
        }

        log.debug("通过率趋势查询: granularity={}, rows={}", granularity, result.size());
        return result;
    }

    /**
     * 规则命中排行 — 按操作次数排序的 Top-N 目标。
     * <p>
     * 统计 audit_log 中每种 target_type + target_id 的操作次数。
     * </p>
     *
     * @param topN 排行数量（默认 10）
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 命中排行数据
     */
    public List<Map<String, Object>> getHitRank(int topN, LocalDateTime startDate, LocalDateTime endDate) {
        StringBuilder sql = new StringBuilder(
            "SELECT target_type, target_id, COUNT(*) AS hit_count " +
            "FROM audit_log " +
            "WHERE target_type IS NOT NULL AND target_id IS NOT NULL "
        );

        List<Object> args = new ArrayList<>();
        if (startDate != null) {
            sql.append(" AND operated_at >= ?");
            args.add(startDate);
        }
        if (endDate != null) {
            sql.append(" AND operated_at <= ?");
            args.add(endDate);
        }

        sql.append(" GROUP BY target_type, target_id");
        sql.append(" ORDER BY hit_count DESC");
        sql.append(" LIMIT ?");

        args.add(topN > 0 ? topN : 10);

        List<Map<String, Object>> result = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        log.debug("命中排行查询: topN={}, rows={}", topN, result.size());
        return result;
    }

    /**
     * 评分分布直方图 — 统计评分区间的分布。
     * <p>
     * 解析 audit_log 中 APPROVE 操作的 after_snapshot，提取 score 字段，
     * 按 [0-20), [20-40), [40-60), [60-80), [80-100] 分桶统计。
     * </p>
     *
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 评分分布数据
     */
    public List<Map<String, Object>> getScoreDistribution(LocalDateTime startDate, LocalDateTime endDate) {
        // 当前基于 audit_log 的 after_snapshot 字段解析评分
        // 后续切换 ES 时可使用 ES histogram aggregation
        String sql = "SELECT after_snapshot FROM audit_log " +
            "WHERE action = 'APPROVE' AND after_snapshot IS NOT NULL ";

        List<Object> args = new ArrayList<>();
        if (startDate != null) {
            sql += " AND operated_at >= ?";
            args.add(startDate);
        }
        if (endDate != null) {
            sql += " AND operated_at <= ?";
            args.add(endDate);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        int[] buckets = new int[5]; // [0-20), [20-40), [40-60), [60-80), [80-100]
        String[] labels = {"0-20", "20-40", "40-60", "60-80", "80-100"};

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args.toArray());
        for (Map<String, Object> row : rows) {
            String snapshot = (String) row.get("after_snapshot");
            if (snapshot == null) continue;

            // 尝试从 JSON 中提取 score 字段
            Double score = extractScore(snapshot);
            if (score != null) {
                int bucket = Math.min((int) (score / 20), 4);
                buckets[bucket]++;
            }
        }

        for (int i = 0; i < labels.length; i++) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("range", labels[i]);
            entry.put("count", buckets[i]);
            result.add(entry);
        }

        log.debug("评分分布查询: rows={}, buckets={}", rows.size(), Arrays.toString(buckets));
        return result;
    }

    /**
     * 概览统计 — 今日决策总量、通过率、拒绝率、P99 耗时。
     */
    public Map<String, Object> getOverview() {
        Map<String, Object> overview = new LinkedHashMap<>();
        LocalDateTime today = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime now = LocalDateTime.now();

        // 今日决策总量
        String totalSql = "SELECT COUNT(*) AS total FROM audit_log WHERE operated_at >= ?";
        List<Map<String, Object>> totalRows = jdbcTemplate.queryForList(totalSql, today);
        long total = totalRows.isEmpty() ? 0 : ((Number) totalRows.get(0).get("total")).longValue();
        overview.put("total", total);

        // 通过率 & 拒绝率
        String rateSql = "SELECT " +
            "IFNULL(SUM(CASE WHEN action = 'APPROVE' THEN 1 ELSE 0 END), 0) AS approved, " +
            "IFNULL(SUM(CASE WHEN action = 'REJECT' THEN 1 ELSE 0 END), 0) AS rejected " +
            "FROM audit_log WHERE operated_at >= ? AND action IN ('APPROVE', 'REJECT')";
        List<Map<String, Object>> rateRows = jdbcTemplate.queryForList(rateSql, today);
        if (!rateRows.isEmpty()) {
            Object approvedObj = rateRows.get(0).get("approved");
            Object rejectedObj = rateRows.get(0).get("rejected");
            long approved = approvedObj != null ? ((Number) approvedObj).longValue() : 0;
            long rejected = rejectedObj != null ? ((Number) rejectedObj).longValue() : 0;
            long rateTotal = approved + rejected;
            overview.put("passRate", rateTotal > 0 ? Math.round((double) approved / rateTotal * 10000) / 100.0 : 0);
            overview.put("rejectRate", rateTotal > 0 ? Math.round((double) rejected / rateTotal * 10000) / 100.0 : 0);
        } else {
            overview.put("passRate", 0);
            overview.put("rejectRate", 0);
        }

        // P99 耗时 (基于 audit_log 的 after_snapshot 解析 durationMs)
        String p99Sql = "SELECT after_snapshot FROM audit_log " +
            "WHERE operated_at >= ? AND after_snapshot IS NOT NULL AND after_snapshot LIKE '%durationMs%'";
        List<Map<String, Object>> p99Rows = jdbcTemplate.queryForList(p99Sql, today);
        List<Double> durations = new ArrayList<>();
        java.util.regex.Pattern durPattern = java.util.regex.Pattern.compile(
            "\"durationMs\"\\s*:\\s*([0-9]+\\.?[0-9]*)");
        for (Map<String, Object> row : p99Rows) {
            String snapshot = (String) row.get("after_snapshot");
            if (snapshot == null) continue;
            java.util.regex.Matcher m = durPattern.matcher(snapshot);
            if (m.find()) {
                try { durations.add(Double.parseDouble(m.group(1))); } catch (NumberFormatException ignored) {}
            }
        }
        if (!durations.isEmpty()) {
            Collections.sort(durations);
            int p99Idx = (int) Math.ceil(durations.size() * 0.99) - 1;
            overview.put("p99Latency", Math.round(durations.get(Math.max(0, p99Idx))));
        } else {
            overview.put("p99Latency", 0);
        }

        log.debug("概览统计: total={}, passRate={}, rejectRate={}, p99Latency={}",
            overview.get("total"), overview.get("passRate"), overview.get("rejectRate"), overview.get("p99Latency"));
        return overview;
    }

    /**
     * 从 JSON snapshot 中提取 score 值。
     */
    private Double extractScore(String json) {
        // 简易 JSON 解析: 查找 "score": N
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "\"score\"\\s*:\\s*([0-9]+\\.?[0-9]*)");
        java.util.regex.Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            try {
                return Double.parseDouble(matcher.group(1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
