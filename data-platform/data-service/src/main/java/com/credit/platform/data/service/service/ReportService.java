package com.credit.platform.data.service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * BI 报表服务 — 提供四类报表查询。
 *
 * <p>报表类型:
 * <ol>
 *   <li>business — 经营分析看板（T+1）</li>
 *   <li>risk — 风控监控看板（实时）</li>
 *   <li>channel — 渠道分析看板（T+1）</li>
 *   <li>quality — 数据质量看板（实时）</li>
 * </ol>
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

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

    /** 经营分析看板 */
    private Map<String, Object> businessReport(String date) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("reportType", "经营分析看板");
        report.put("refreshFrequency", "T+1");
        report.put("statDate", date);

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("totalApplications", 1250);
        metrics.put("approvedCount", 875);
        metrics.put("rejectedCount", 375);
        metrics.put("approveRate", "70.0%");
        metrics.put("totalLoanAmount", 87500000.00);
        metrics.put("avgLoanAmount", 100000.00);
        metrics.put("nplRate", "1.8%");
        report.put("metrics", metrics);

        // 趋势数据（近 7 天）
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

    /** 风控监控看板 */
    private Map<String, Object> riskReport(String date) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("reportType", "风控监控看板");
        report.put("refreshFrequency", "实时");
        report.put("statDate", date);

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

    /** 渠道分析看板 */
    private Map<String, Object> channelReport(String date) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("reportType", "渠道分析看板");
        report.put("refreshFrequency", "T+1");
        report.put("statDate", date);

        report.put("channels", List.of(
                Map.of("channel", "ONLINE", "applications", 800, "approveRate", "75.0%", "conversionRate", "12.5%"),
                Map.of("channel", "OFFLINE", "applications", 300, "approveRate", "65.0%", "conversionRate", "20.0%"),
                Map.of("channel", "PARTNER", "applications", 150, "approveRate", "70.0%", "conversionRate", "15.0%")
        ));
        return report;
    }

    /** 数据质量看板 */
    private Map<String, Object> qualityReport(String date) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("reportType", "数据质量看板");
        report.put("refreshFrequency", "实时");
        report.put("statDate", date);

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
}
