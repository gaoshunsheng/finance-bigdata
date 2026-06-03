package com.credit.platform.admin.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.credit.platform.admin.model.ApiResponse;
import com.credit.platform.admin.model.GrayscaleConfig;
import com.credit.platform.admin.model.RuleEntity;
import com.credit.platform.admin.model.VersionDiff;
import com.credit.platform.admin.service.RulePublishService;

/**
 * 发布管理 Controller — 完整发布生命周期 API。
 * <p>
 * 支持操作: 推进测试/提交审批/审批通过/审批驳回/撤回审批/灰度发布/灰度调整/回滚/版本对比。
 * </p>
 */
@RestController
@RequestMapping("/api/v1/publish")
public class PublishController {

    private final RulePublishService publishService;

    public PublishController(RulePublishService publishService) {
        this.publishService = publishService;
    }

    // ==================== 测试 ====================

    /** 推进到测试: DRAFT → TESTING */
    @PostMapping("/{type}/{id}/promote-to-testing")
    public ResponseEntity<ApiResponse<Map<String, Object>>> promoteToTesting(
            @PathVariable("type") String type,
            @PathVariable("id") String id,
            @RequestBody OperatorRequest request) {
        RuleEntity entity = publishService.promoteToTesting(type, id, request.operator());
        return ResponseEntity.ok(ApiResponse.success(entity.toSummary()));
    }

    // ==================== 审批 ====================

    /** 提交审批: TESTING → PENDING_REVIEW */
    @PostMapping("/{type}/{id}/submit-approval")
    public ResponseEntity<ApiResponse<Map<String, Object>>> submitForApproval(
            @PathVariable("type") String type,
            @PathVariable("id") String id,
            @RequestBody ApprovalRequest request) {
        RuleEntity entity = publishService.submitForApproval(type, id, request.operator(), request.comment());
        return ResponseEntity.ok(ApiResponse.success(entity.toSummary()));
    }

    /** 审批通过: PENDING_REVIEW → APPROVED */
    @PostMapping("/{type}/{id}/approve")
    public ResponseEntity<ApiResponse<Map<String, Object>>> approve(
            @PathVariable("type") String type,
            @PathVariable("id") String id,
            @RequestBody ApprovalRequest request) {
        RuleEntity entity = publishService.approve(type, id, request.operator(), request.comment());
        return ResponseEntity.ok(ApiResponse.success(entity.toSummary()));
    }

    /** 审批驳回: PENDING_REVIEW → DRAFT */
    @PostMapping("/{type}/{id}/reject")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reject(
            @PathVariable("type") String type,
            @PathVariable("id") String id,
            @RequestBody RejectRequest request) {
        RuleEntity entity = publishService.reject(type, id, request.operator(), request.reason());
        return ResponseEntity.ok(ApiResponse.success(entity.toSummary()));
    }

    /** 撤回审批: PENDING_REVIEW → TESTING */
    @PostMapping("/{type}/{id}/withdraw")
    public ResponseEntity<ApiResponse<Map<String, Object>>> withdraw(
            @PathVariable("type") String type,
            @PathVariable("id") String id,
            @RequestBody ApprovalRequest request) {
        RuleEntity entity = publishService.withdrawApproval(type, id, request.operator(), request.comment());
        return ResponseEntity.ok(ApiResponse.success(entity.toSummary()));
    }

    /** 查询审批历史 */
    @GetMapping("/{type}/{id}/approval-history")
    public ResponseEntity<ApiResponse<List<?>>> getApprovalHistory(
            @PathVariable("type") String type,
            @PathVariable("id") String id) {
        return ResponseEntity.ok(ApiResponse.success(publishService.getApprovalHistory(type, id)));
    }

    /** 查询待审批列表 */
    @GetMapping("/pending-approvals")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getPendingApprovals() {
        List<RuleEntity> entities = publishService.getPendingApprovals();
        List<Map<String, Object>> summaries = entities.stream().map(RuleEntity::toSummary).toList();
        return ResponseEntity.ok(ApiResponse.success(summaries));
    }

    // ==================== 灰度 ====================

    /** 开始灰度: APPROVED → GRAYSCALE */
    @PostMapping("/{type}/{id}/grayscale/start")
    public ResponseEntity<ApiResponse<GrayscaleConfig>> startGrayscale(
            @PathVariable("type") String type,
            @PathVariable("id") String id,
            @RequestBody GrayscaleStartRequest request) {
        GrayscaleConfig config = publishService.startGrayscale(
            type, id, request.percentage(), request.operator());
        return ResponseEntity.ok(ApiResponse.success(config));
    }

    /** 灰度阶梯提升 */
    @PostMapping("/{type}/{id}/grayscale/ramp-up")
    public ResponseEntity<ApiResponse<GrayscaleConfig>> rampUpGrayscale(
            @PathVariable("type") String type,
            @PathVariable("id") String id,
            @RequestBody OperatorRequest request) {
        GrayscaleConfig config = publishService.rampUpGrayscale(type, id, request.operator());
        return ResponseEntity.ok(ApiResponse.success(config));
    }

    /** 调整灰度百分比 */
    @PostMapping("/{type}/{id}/grayscale/adjust")
    public ResponseEntity<ApiResponse<GrayscaleConfig>> adjustGrayscale(
            @PathVariable("type") String type,
            @PathVariable("id") String id,
            @RequestBody GrayscaleAdjustRequest request) {
        GrayscaleConfig config = publishService.adjustGrayscale(
            type, id, request.percentage(), request.operator());
        return ResponseEntity.ok(ApiResponse.success(config));
    }

    /** 暂停灰度 */
    @PostMapping("/{type}/{id}/grayscale/pause")
    public ResponseEntity<ApiResponse<GrayscaleConfig>> pauseGrayscale(
            @PathVariable("type") String type,
            @PathVariable("id") String id,
            @RequestBody OperatorRequest request) {
        GrayscaleConfig config = publishService.pauseGrayscale(type, id, request.operator());
        return ResponseEntity.ok(ApiResponse.success(config));
    }

    /** 恢复灰度 */
    @PostMapping("/{type}/{id}/grayscale/resume")
    public ResponseEntity<ApiResponse<GrayscaleConfig>> resumeGrayscale(
            @PathVariable("type") String type,
            @PathVariable("id") String id,
            @RequestBody OperatorRequest request) {
        GrayscaleConfig config = publishService.resumeGrayscale(type, id, request.operator());
        return ResponseEntity.ok(ApiResponse.success(config));
    }

    /** 查询灰度配置 */
    @GetMapping("/{type}/{id}/grayscale")
    public ResponseEntity<ApiResponse<GrayscaleConfig>> getGrayscaleConfig(
            @PathVariable("type") String type,
            @PathVariable("id") String id) {
        GrayscaleConfig config = publishService.getGrayscaleConfig(type, id);
        return ResponseEntity.ok(ApiResponse.success(config));
    }

    // ==================== 回滚 ====================

    /** 回滚到指定版本 */
    @PostMapping("/{type}/{id}/rollback/{version}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> rollback(
            @PathVariable("type") String type,
            @PathVariable("id") String id,
            @PathVariable("version") int version,
            @RequestBody OperatorRequest request) {
        RuleEntity entity = publishService.rollback(type, id, version, request.operator());
        return ResponseEntity.ok(ApiResponse.success(entity.toSummary()));
    }

    /** 灰度回滚 */
    @PostMapping("/{type}/{id}/grayscale/rollback")
    public ResponseEntity<ApiResponse<Map<String, Object>>> rollbackGrayscale(
            @PathVariable("type") String type,
            @PathVariable("id") String id,
            @RequestBody OperatorRequest request) {
        RuleEntity entity = publishService.rollbackGrayscale(type, id, request.operator());
        return ResponseEntity.ok(ApiResponse.success(entity.toSummary()));
    }

    // ==================== 版本对比 ====================

    /** 版本对比 */
    @GetMapping("/{type}/{id}/diff")
    public ResponseEntity<ApiResponse<VersionDiff>> diff(
            @PathVariable("type") String type,
            @PathVariable("id") String id,
            @RequestParam("from") int fromVersion,
            @RequestParam("to") int toVersion) {
        VersionDiff diff = publishService.diff(type, id, fromVersion, toVersion);
        return ResponseEntity.ok(ApiResponse.success(diff));
    }

    /** 最新两版本对比 */
    @GetMapping("/{type}/{id}/diff-latest")
    public ResponseEntity<ApiResponse<VersionDiff>> diffLatest(
            @PathVariable("type") String type,
            @PathVariable("id") String id) {
        VersionDiff diff = publishService.diffLatest(type, id);
        return ResponseEntity.ok(ApiResponse.success(diff));
    }

    // ==================== 发布历史 ====================

    /** 查询发布历史 */
    @GetMapping("/{type}/{id}/history")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getPublishHistory(
            @PathVariable("type") String type,
            @PathVariable("id") String id) {
        List<RuleEntity> versions = publishService.getPublishHistory(type, id);
        List<Map<String, Object>> summaries = versions.stream().map(RuleEntity::toSummary).toList();
        return ResponseEntity.ok(ApiResponse.success(summaries));
    }

    // ==================== 通用推进 (兼容旧 API) ====================

    /** 推进到下一状态 (兼容旧 API) */
    @PostMapping("/{type}/{id}/promote")
    public ResponseEntity<ApiResponse<Map<String, Object>>> promote(
            @PathVariable("type") String type,
            @PathVariable("id") String id,
            @RequestBody OperatorRequest request) {
        RuleEntity entity = publishService.promoteToTesting(type, id, request.operator());
        return ResponseEntity.ok(ApiResponse.success(entity.toSummary()));
    }

    // ==================== Request DTOs ====================

    public record OperatorRequest(String operator) {}

    public record ApprovalRequest(String operator, String comment) {}

    public record RejectRequest(String operator, String reason) {}

    public record GrayscaleStartRequest(String operator, int percentage) {}

    public record GrayscaleAdjustRequest(String operator, int percentage) {}
}
