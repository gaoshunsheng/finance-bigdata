package com.credit.platform.admin.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.credit.platform.admin.model.ApiResponse;
import com.credit.platform.admin.security.AuditLog;
import com.credit.platform.admin.security.AuditLogService;

/**
 * 审计日志 Controller — 查询审计历史。
 * <p>
 * 需要 APPROVER 或 ADMIN 角色。
 * </p>
 */
@RestController
@RequestMapping("/api/v1/audit")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    /**
     * 查询指定目标的审计历史。
     */
    @GetMapping("/{type}/{id}")
    public ResponseEntity<ApiResponse<List<AuditLog>>> getTargetHistory(
            @PathVariable("type") String type,
            @PathVariable("id") String id) {
        return ResponseEntity.ok(ApiResponse.success(auditLogService.getTargetHistory(type, id)));
    }

    /**
     * 查询指定操作人的审计日志。
     */
    @GetMapping("/operator/{operator}")
    public ResponseEntity<ApiResponse<List<AuditLog>>> getOperatorHistory(
            @PathVariable("operator") String operator) {
        return ResponseEntity.ok(ApiResponse.success(auditLogService.getOperatorHistory(operator)));
    }

    /**
     * 查询所有审计日志（分页）。
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<AuditLog>>> list(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(auditLogService.list(page, size)));
    }

    /**
     * 按操作类型查询。
     */
    @GetMapping("/action/{action}")
    public ResponseEntity<ApiResponse<List<AuditLog>>> findByAction(
            @PathVariable("action") String action) {
        return ResponseEntity.ok(ApiResponse.success(auditLogService.findByAction(action)));
    }

    /**
     * 审计日志统计。
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> stats() {
        return ResponseEntity.ok(ApiResponse.success(
            Map.of("totalLogs", auditLogService.count())));
    }
}
