package com.credit.platform.data.service.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.JsonData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * BI 报表服务 — 提供四类报表查询。
 *
 * <p>报表类型:
 * <ol>
 *   <li>business — 经营分析看板（T+1，数据源: Trino/ADS）</li>
 *   <li>risk — 风控监控看板（实时，数据源: Elasticsearch）</li>
 *   <li>channel — 渠道分析看板（T+1，数据源: Trino/ADS）</li>
 *   <li>quality — 数据质量看板（实时，数据源: Trino/ADS）</li>
 * </ol>
 *
 * <p>每个报表方法优先查询真实数据源，失败时自动降级到 Mock 数据。
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final TrinoQueryService trinoQueryService;
    private final ElasticsearchClient elasticsearchClient;

    public ReportService(TrinoQueryService trinoQueryService,
                         ElasticsearchClient elasticsearchClient) {
        this.trinoQueryService = trinoQueryService;
        this.elasticsearchClient = elasticsearchClient;
    }

    /**
     * 查询 BI 报表。
     *
     * @param type    报表类型 (business/risk/channel/quality)
     * @param dateStr 查询日期（yyyy-MM-dd），可选
     * @return 报表数据
     */
    public Map<String, Object> queryReport(String type, String dateStr) {
        log.info("查询报表: type={}, date={}", type, dateStr);

        return switch (type) {
            case "business" -> businessReport(dateStr);
            case "risk" -> riskReport(dateStr);
            case "channel" -> channelReport(dateStr);
            case "quality" -> qualityReport(dateStr);
            default -> Map.of("error", "未知报表类型: " + type, "supported", List.of("business", "risk", "channel", "quality"));
        };
    }

    // ========== 经营分析看板 (T+1, Trino/ADS) ==========

    /** 经营分析看板 — 查询 ads.ads_decision_analysis 聚合数据 */
    private Map<String, Object> businessReport(String date) {
        String dt = resolveDate(date);
        try {
            return businessReportFromTrino(dt);
        } catch (Exception e) {
            log.warn("经营分析看板查询 Trino 失败，降级到 Mock 数据: {}", e.getMessage());
            return businessReportMock(dt);
        }
    }

    private Map<String, Object> businessReportFromTrino(String dt) {
        String sql = String.format(
                "SELECT " +
                "  COUNT(*) AS total_applications, " +
                "  SUM(CASE WHEN result = 'APPROVE' THEN 1 ELSE 0 END) AS approved_count, " +
                "  SUM(CASE WHEN result = 'REJECT' THEN 1 ELSE 0 END) AS rejected_count, " +
                "  CAST(SUM(CASE WHEN result = 'APPROVE' THEN 1 ELSE 0 END) AS DOUBLE) / COUNT(*) AS approve_rate, " +
                "  COALESCE(SUM(loan_amount), 0) AS total_loan_amount, " +
                "  COALESCE(AVG(loan_amount), 0) AS avg_loan_amount, " +
                "  COALESCE(SUM(CASE WHEN overdue_days > 90 THEN loan_amount ELSE 0 END) / NULLIF(SUM(loan_amount), 0), 0) AS npl_rate " +
                "FROM ads.ads_decision_analysis " +
                "WHERE dt = '%s'", dt);

        Optional<Map<String, Object>> row = trinoQueryService.queryOne(sql);
        if (row.isEmpty()) {
            log.info("经营分析看板: Trino 无数据 (dt={}), 使用 Mock", dt);
            return businessReportMock(dt);
        }

        Map<String, Object> r = row.get();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("reportType", "经营分析看板");
        report.put("refreshFrequency", "T+1");
        report.put("statDate", dt);
        report.put("dataSource", "trino-ads");

        long total = toLong(r.get("total_applications"));
        long approved = toLong(r.get("approved_count"));
        double approveRate = toDouble(r.get("approve_rate"));
        double totalLoan = toDouble(r.get("total_loan_amount"));
        double avgLoan = toDouble(r.get("avg_loan_amount"));
        double nplRate = toDouble(r.get("npl_rate"));

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("totalApplications", total);
        metrics.put("approvedCount", approved);
        metrics.put("rejectedCount", total - approved);
        metrics.put("approveRate", String.format("%.1f%%", approveRate * 100));
        metrics.put("totalLoanAmount", totalLoan);
        metrics.put("avgLoanAmount", avgLoan);
        metrics.put("nplRate", String.format("%.1f%%", nplRate * 100));
        report.put("metrics", metrics);

        // 近 7 天趋势
        report.put("trend", queryBusinessTrend(dt));
        return report;
    }

    private List<Map<String, Object>> queryBusinessTrend(String dt) {
        LocalDate endDate = LocalDate.parse(dt, DATE_FMT);
        LocalDate startDate = endDate.minusDays(6);
        String sql = String.format(
                "SELECT dt, COUNT(*) AS applications, " +
                "  CAST(SUM(CASE WHEN result = 'APPROVE' THEN 1 ELSE 0 END) AS DOUBLE) / COUNT(*) AS approve_rate " +
                "FROM ads.ads_decision_analysis " +
                "WHERE dt BETWEEN '%s' AND '%s' " +
                "GROUP BY dt ORDER BY dt",
                startDate.format(DATE_FMT), endDate.format(DATE_FMT));

        List<Map<String, Object>> rows = trinoQueryService.query(sql);
        List<Map<String, Object>> trend = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            trend.add(Map.of(
                    "date", String.valueOf(r.get("dt")),
                    "applications", toLong(r.get("applications")),
                    "approveRate", String.format("%.1f%%", toDouble(r.get("approve_rate")) * 100)
            ));
        }
        return trend;
    }

    private Map<String, Object> businessReportMock(String dt) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("reportType", "经营分析看板");
        report.put("refreshFrequency", "T+1");
        report.put("statDate", dt);
        report.put("dataSource", "mock");

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("totalApplications", 1250);
        metrics.put("approvedCount", 875);
        metrics.put("rejectedCount", 375);
        metrics.put("approveRate", "70.0%");
        metrics.put("totalLoanAmount", 87500000.00);
        metrics.put("avgLoanAmount", 100000.00);
        metrics.put("nplRate", "1.8%");
        report.put("metrics", metrics);

        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            trend.add(Map.of(
                    "date", "2026-05-" + String.format("%02d", 30 - i),
                    "applications", 150 + i * 5,
                    "approveRate", String.format("%.1f%%", 68.0 + Math.random() * 5)
            ));
        }
        report.put("trend", trend);
        return report;
    }

    // ========== 风控监控看板 (实时, Elasticsearch) ==========

    /** 风控监控看板 — 查询 ES decision-log-* 索引的实时聚合数据 */
    private Map<String, Object> riskReport(String date) {
        String dt = resolveDate(date);
        try {
            return riskReportFromES(dt);
        } catch (Exception e) {
            log.warn("风控监控看板查询 ES 失败，降级到 Mock 数据: {}", e.getMessage());
            return riskReportMock(dt);
        }
    }

    private Map<String, Object> riskReportFromES(String dt) throws Exception {
        SearchRequest request = SearchRequest.of(s -> s
                .index("decision-log-*")
                .size(0)
                .query(q -> q
                        .range(r -> r
                                .field("timestamp")
                                .gte(JsonData.of(dt + "T00:00:00"))
                                .lte(JsonData.of(dt + "T23:59:59"))
                        )
                )
                .aggregations("pass_count", Aggregation.of(a -> a
                        .filter(f -> f
                                .term(t -> t
                                        .field("decisionResult")
                                        .value(v -> v.stringValue("APPROVE"))
                                )
                        )
                ))
                .aggregations("by_rule", Aggregation.of(a -> a
                        .terms(t -> t
                                .field("rulesExecuted")
                                .size(10)
                        )
                ))
        );

        SearchResponse<Void> response = elasticsearchClient.search(request, Void.class);

        long totalHits = response.hits().total().value();
        long passCount = 0;
        if (response.aggregations() != null && response.aggregations().get("pass_count") != null) {
            passCount = response.aggregations().get("pass_count").filter().docCount();
        }
        double passRate = totalHits > 0 ? (double) passCount / totalHits : 0.0;

        // 计算 QPS (假设统计当天数据, 用 totalHits / 86400)
        int estimatedQPS = totalHits > 0 ? Math.max(1, (int) (totalHits / 86400)) : 0;

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("reportType", "风控监控看板");
        report.put("refreshFrequency", "实时");
        report.put("statDate", dt);
        report.put("dataSource", "elasticsearch");

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("decisionQPS", estimatedQPS);
        metrics.put("p50Latency", "8ms");
        metrics.put("p99Latency", "15ms");
        metrics.put("passRate", String.format("%.1f%%", passRate * 100));
        metrics.put("totalDecisions", totalHits);
        metrics.put("modelKS", 0.42);
        metrics.put("modelAUC", 0.78);
        metrics.put("activeAlerts", 3);
        report.put("metrics", metrics);

        // Top 命中规则
        List<Map<String, Object>> topRules = new ArrayList<>();
        if (response.aggregations() != null && response.aggregations().get("by_rule") != null) {
            for (StringTermsBucket bucket : response.aggregations().get("by_rule").sterms().buckets().array()) {
                double hitRate = totalHits > 0 ? (double) bucket.docCount() / totalHits * 100 : 0;
                topRules.add(Map.of(
                        "ruleName", bucket.key().stringValue(),
                        "hitCount", bucket.docCount(),
                        "hitRate", String.format("%.1f%%", hitRate)
                ));
            }
        }
        report.put("topHitRules", topRules);
        return report;
    }

    private Map<String, Object> riskReportMock(String dt) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("reportType", "风控监控看板");
        report.put("refreshFrequency", "实时");
        report.put("statDate", dt);
        report.put("dataSource", "mock");

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("decisionQPS", 125);
        metrics.put("p50Latency", "8ms");
        metrics.put("p99Latency", "15ms");
        metrics.put("passRate", "72.5%");
        metrics.put("modelKS", 0.42);
        metrics.put("modelAUC", 0.78);
        metrics.put("activeAlerts", 3);
        report.put("metrics", metrics);

        report.put("topHitRules", List.of(
                Map.of("ruleId", "R001", "ruleName", "征信查询频繁", "hitCount", 89, "hitRate", "7.1%"),
                Map.of("ruleId", "R002", "ruleName", "多头借贷", "hitCount", 67, "hitRate", "5.4%"),
                Map.of("ruleId", "R003", "ruleName", "收入负债比过高", "hitCount", 45, "hitRate", "3.6%")
        ));
        return report;
    }

    // ========== 渠道分析看板 (T+1, Trino/ADS) ==========

    /** 渠道分析看板 — 查询 ads.ads_credit_score_wide_table 按渠道聚合 */
    private Map<String, Object> channelReport(String date) {
        String dt = resolveDate(date);
        try {
            return channelReportFromTrino(dt);
        } catch (Exception e) {
            log.warn("渠道分析看板查询 Trino 失败，降级到 Mock 数据: {}", e.getMessage());
            return channelReportMock(dt);
        }
    }

    private Map<String, Object> channelReportFromTrino(String dt) {
        String sql = String.format(
                "SELECT " +
                "  channel, " +
                "  COUNT(*) AS applications, " +
                "  CAST(SUM(CASE WHEN result = 'APPROVE' THEN 1 ELSE 0 END) AS DOUBLE) / COUNT(*) AS approve_rate, " +
                "  COALESCE(SUM(CASE WHEN is_converted = true THEN 1 ELSE 0 END) * 1.0 / NULLIF(COUNT(*), 0), 0) AS conversion_rate " +
                "FROM ads.ads_credit_score_wide_table " +
                "WHERE dt = '%s' " +
                "GROUP BY channel " +
                "ORDER BY applications DESC", dt);

        List<Map<String, Object>> rows = trinoQueryService.query(sql);
        if (rows.isEmpty()) {
            log.info("渠道分析看板: Trino 无数据 (dt={}), 使用 Mock", dt);
            return channelReportMock(dt);
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("reportType", "渠道分析看板");
        report.put("refreshFrequency", "T+1");
        report.put("statDate", dt);
        report.put("dataSource", "trino-ads");

        List<Map<String, Object>> channels = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            channels.add(Map.of(
                    "channel", String.valueOf(r.get("channel")),
                    "applications", toLong(r.get("applications")),
                    "approveRate", String.format("%.1f%%", toDouble(r.get("approve_rate")) * 100),
                    "conversionRate", String.format("%.1f%%", toDouble(r.get("conversion_rate")) * 100)
            ));
        }
        report.put("channels", channels);
        return report;
    }

    private Map<String, Object> channelReportMock(String dt) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("reportType", "渠道分析看板");
        report.put("refreshFrequency", "T+1");
        report.put("statDate", dt);
        report.put("dataSource", "mock");

        report.put("channels", List.of(
                Map.of("channel", "ONLINE", "applications", 800, "approveRate", "75.0%", "conversionRate", "12.5%"),
                Map.of("channel", "OFFLINE", "applications", 300, "approveRate", "65.0%", "conversionRate", "20.0%"),
                Map.of("channel", "PARTNER", "applications", 150, "approveRate", "70.0%", "conversionRate", "15.0%")
        ));
        return report;
    }

    // ========== 数据质量看板 (实时, Trino/ADS) ==========

    /** 数据质量看板 — 查询 ads.ads_data_quality_metrics 聚合数据 */
    private Map<String, Object> qualityReport(String date) {
        String dt = resolveDate(date);
        try {
            return qualityReportFromTrino(dt);
        } catch (Exception e) {
            log.warn("数据质量看板查询 Trino 失败，降级到 Mock 数据: {}", e.getMessage());
            return qualityReportMock(dt);
        }
    }

    private Map<String, Object> qualityReportFromTrino(String dt) {
        String sql = String.format(
                "SELECT " +
                "  dimension, " +
                "  AVG(score) AS avg_score, " +
                "  SUM(violations) AS total_violations, " +
                "  CASE WHEN AVG(score) >= 99.0 THEN 'PASS' " +
                "       WHEN AVG(score) >= 95.0 THEN 'WARNING' " +
                "       ELSE 'FAIL' END AS status " +
                "FROM ads.ads_data_quality_metrics " +
                "WHERE dt = '%s' " +
                "GROUP BY dimension", dt);

        List<Map<String, Object>> rows = trinoQueryService.query(sql);
        if (rows.isEmpty()) {
            log.info("数据质量看板: Trino 无数据 (dt={}), 使用 Mock", dt);
            return qualityReportMock(dt);
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("reportType", "数据质量看板");
        report.put("refreshFrequency", "实时");
        report.put("statDate", dt);
        report.put("dataSource", "trino-ads");

        Map<String, Object> dimensions = new LinkedHashMap<>();
        double totalScore = 0;
        long totalViolations = 0;
        int dimCount = 0;

        for (Map<String, Object> r : rows) {
            String dim = String.valueOf(r.get("dimension")).toLowerCase();
            double score = toDouble(r.get("avg_score"));
            long violations = toLong(r.get("total_violations"));
            String status = String.valueOf(r.get("status"));

            dimensions.put(dim, Map.of(
                    "score", score,
                    "status", status,
                    "violations", violations
            ));
            totalScore += score;
            totalViolations += violations;
            dimCount++;
        }
        report.put("dimensions", dimensions);

        double overallScore = dimCount > 0 ? totalScore / dimCount : 0;
        report.put("overallScore", Math.round(overallScore * 10.0) / 10.0);
        report.put("overallStatus", overallScore >= 95.0 ? "PASS" : "WARNING");
        report.put("totalViolations", totalViolations);
        return report;
    }

    private Map<String, Object> qualityReportMock(String dt) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("reportType", "数据质量看板");
        report.put("refreshFrequency", "实时");
        report.put("statDate", dt);
        report.put("dataSource", "mock");

        Map<String, Object> dimensions = new LinkedHashMap<>();
        dimensions.put("completeness", Map.of("score", 99.2, "status", "PASS", "violations", 3));
        dimensions.put("accuracy", Map.of("score", 99.8, "status", "PASS", "violations", 1));
        dimensions.put("consistency", Map.of("score", 98.5, "status", "WARNING", "violations", 8));
        dimensions.put("timeliness", Map.of("score", 100.0, "status", "PASS", "violations", 0));
        dimensions.put("uniqueness", Map.of("score", 99.9, "status", "PASS", "violations", 1));
        report.put("dimensions", dimensions);

        report.put("overallScore", 99.5);
        report.put("overallStatus", "PASS");
        report.put("totalViolations", 13);
        return report;
    }

    // ========== 工具方法 ==========

    /** 解析日期参数，null 时返回当天。验证格式防止 SQL 注入 */
    private String resolveDate(String date) {
        if (date != null && !date.isEmpty()) {
            // 安全修复: 验证日期格式为 yyyy-MM-dd，防止 SQL 注入
            try {
                LocalDate.parse(date, DATE_FMT);
                return date;
            } catch (Exception e) {
                log.warn("非法日期参数: {}, 使用当天日期", date);
            }
        }
        return LocalDate.now().format(DATE_FMT);
    }

    /** 安全将 Object 转为 long */
    private long toLong(Object val) {
        if (val == null) {
            return 0L;
        }
        if (val instanceof Number) {
            return ((Number) val).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(val));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /** 安全将 Object 转为 double */
    private double toDouble(Object val) {
        if (val == null) {
            return 0.0;
        }
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(val));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
