package com.credit.platform.data.service.controller;

import com.credit.platform.data.service.model.ApiResponse;
import com.credit.platform.data.service.service.ReportService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * BI 报表查询 API — 提供四类报表。
 *
 * <p>报表类型:
 * <ul>
 *   <li>business — 经营分析看板（T+1）</li>
 *   <li>risk — 风控监控看板（实时）</li>
 *   <li>channel — 渠道分析看板（T+1）</li>
 *   <li>quality — 数据质量看板（实时）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * 查询 BI 报表。
     *
     * @param type 报表类型 (business/risk/channel/quality)
     * @param date 查询日期（yyyy-MM-dd），可选，默认当天
     * @return 报表数据
     */
    @GetMapping("/{type}")
    public ApiResponse queryReport(
            @PathVariable String type,
            @RequestParam(value = "date", required = false) String date) {
        if (date != null && !date.isEmpty()) {
            try {
                DATE_FORMATTER.parse(date);
            } catch (DateTimeParseException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "日期格式无效，要求 yyyy-MM-dd: " + date);
            }
        }
        Map<String, Object> report = reportService.queryReport(type, date);
        return ApiResponse.ok(report, "报表查询成功");
    }
}
