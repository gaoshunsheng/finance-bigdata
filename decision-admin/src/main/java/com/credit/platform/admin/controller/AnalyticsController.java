package com.credit.platform.admin.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.credit.platform.admin.model.ApiResponse;
import com.credit.platform.admin.service.AnalyticsService;

/**
 * 分析统计 Controller — 聚合分析决策执行数据。
 * <p>
 * API 路径:
 * <ul>
 *   <li>GET /api/v1/analytics/pass-rate        — 通过率趋势</li>
 *   <li>GET /api/v1/analytics/hit-rank         — 规则命中排行</li>
 *   <li>GET /api/v1/analytics/score-distribution — 评分分布直方图</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /**
     * 概览统计 — 今日决策总量、通过率、拒绝率、P99 耗时。
     */
    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOverview() {
        Map<String, Object> overview = analyticsService.getOverview();
        return ResponseEntity.ok(ApiResponse.success(overview));
    }

    /**
     * 通过率趋势 — 按日/周/月维度统计通过率。
     *
     * @param startDate   开始日期 (ISO format, 默认 30 天前)
     * @param endDate     结束日期 (ISO format, 默认当前)
     * @param granularity 时间粒度: daily / weekly / monthly (默认 daily)
     */
    @GetMapping("/pass-rate")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getPassRate(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "daily") String granularity) {

        LocalDateTime end = endDate != null
            ? LocalDateTime.parse(endDate)
            : LocalDateTime.now();
        LocalDateTime start = startDate != null
            ? LocalDateTime.parse(startDate)
            : end.minusDays(30);

        List<Map<String, Object>> trend = analyticsService.getPassRateTrend(start, end, granularity);
        return ResponseEntity.ok(ApiResponse.success(trend));
    }

    /**
     * 规则命中排行 — Top-N 规则按命中次数排序。
     *
     * @param topN      排行数量（默认 10）
     * @param startDate 开始日期 (ISO format, 可选)
     * @param endDate   结束日期 (ISO format, 可选)
     */
    @GetMapping("/hit-rank")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getHitRank(
            @RequestParam(defaultValue = "10") int topN,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        LocalDateTime start = startDate != null ? LocalDateTime.parse(startDate) : null;
        LocalDateTime end = endDate != null ? LocalDateTime.parse(endDate) : null;

        List<Map<String, Object>> rank = analyticsService.getHitRank(topN, start, end);
        return ResponseEntity.ok(ApiResponse.success(rank));
    }

    /**
     * 评分分布直方图 — 统计评分区间分布。
     *
     * @param startDate 开始日期 (ISO format, 可选)
     * @param endDate   结束日期 (ISO format, 可选)
     */
    @GetMapping("/score-distribution")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getScoreDistribution(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        LocalDateTime start = startDate != null ? LocalDateTime.parse(startDate) : null;
        LocalDateTime end = endDate != null ? LocalDateTime.parse(endDate) : null;

        List<Map<String, Object>> distribution = analyticsService.getScoreDistribution(start, end);
        return ResponseEntity.ok(ApiResponse.success(distribution));
    }
}
