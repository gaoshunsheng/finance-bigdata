package com.credit.platform.admin.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.credit.platform.admin.model.ApiResponse;
import com.credit.platform.admin.service.DecisionLogQueryService;

/**
 * 决策日志 Controller — 查询决策执行日志。
 * <p>
 * API 路径:
 * <ul>
 *   <li>GET /api/v1/logs           — 分页查询决策日志</li>
 *   <li>GET /api/v1/logs/{id}      — 查询单条日志详情</li>
 *   <li>GET /api/v1/logs/stats     — 聚合统计（按日期范围）</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/api/v1/logs")
public class DecisionLogController {

    private final DecisionLogQueryService logQueryService;

    public DecisionLogController(DecisionLogQueryService logQueryService) {
        this.logQueryService = logQueryService;
    }

    /**
     * 分页查询决策日志。
     * <p>
     * 支持过滤参数: operator, action, targetType, targetId, startDate, endDate
     * </p>
     *
     * @param operator  操作人过滤
     * @param action    操作类型过滤
     * @param targetType 目标类型过滤
     * @param targetId  目标 ID 过滤
     * @param startDate 开始日期 (ISO format)
     * @param endDate   结束日期 (ISO format)
     * @param page      页码（从 1 开始，默认 1）
     * @param size      每页大小（默认 20）
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> list(
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        Map<String, Object> params = new java.util.LinkedHashMap<>();
        if (operator != null) params.put("operator", operator);
        if (action != null) params.put("action", action);
        if (targetType != null) params.put("targetType", targetType);
        if (targetId != null) params.put("targetId", targetId);
        if (startDate != null) params.put("startDate", startDate);
        if (endDate != null) params.put("endDate", endDate);

        Map<String, Object> result = logQueryService.queryLogs(params, page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 查询单条日志详情。
     */
    @GetMapping("/{decisionId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getLog(
            @PathVariable("decisionId") String decisionId) {
        Map<String, Object> log = logQueryService.getLogDetail(decisionId);
        return ResponseEntity.ok(ApiResponse.success(log));
    }

    /**
     * 聚合统计 — 按日期范围统计 pass/reject/review 数量。
     *
     * @param startDate 开始日期 (ISO format, 默认 7 天前)
     * @param endDate   结束日期 (ISO format, 默认当前)
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getStats(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        LocalDateTime end = endDate != null
            ? LocalDateTime.parse(endDate)
            : LocalDateTime.now();
        LocalDateTime start = startDate != null
            ? LocalDateTime.parse(startDate)
            : end.minusDays(7);

        List<Map<String, Object>> stats = logQueryService.getStats(start, end);
        return ResponseEntity.ok(ApiResponse.success(stats));
    }
}
