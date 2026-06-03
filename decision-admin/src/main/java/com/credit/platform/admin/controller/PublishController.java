package com.credit.platform.admin.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.credit.platform.admin.model.ApiResponse;
import com.credit.platform.admin.model.RuleEntity;
import com.credit.platform.admin.service.RuleAdminService;

/**
 * 发布管理 Controller — 推进/驳回/回滚。
 */
@RestController
@RequestMapping("/api/v1/publish")
public class PublishController {

    private final RuleAdminService service;

    public PublishController(RuleAdminService service) {
        this.service = service;
    }

    /** 推进到下一状态 */
    @PostMapping("/{type}/{id}/promote")
    public ResponseEntity<ApiResponse<Map<String, Object>>> promote(
            @PathVariable("type") String type, @PathVariable("id") String id) {
        return ResponseEntity.ok(ApiResponse.success(service.promote(type, id).toSummary()));
    }

    /** 驳回 */
    @PostMapping("/{type}/{id}/reject")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reject(
            @PathVariable("type") String type, @PathVariable("id") String id,
            @RequestBody RejectRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.reject(type, id, request.reason()).toSummary()));
    }

    /** 回滚到指定版本 */
    @PostMapping("/{type}/{id}/rollback/{version}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> rollback(
            @PathVariable("type") String type, @PathVariable("id") String id,
            @PathVariable("version") int version) {
        return ResponseEntity.ok(ApiResponse.success(service.rollback(type, id, version).toSummary()));
    }

    public record RejectRequest(String reason) {}
}
