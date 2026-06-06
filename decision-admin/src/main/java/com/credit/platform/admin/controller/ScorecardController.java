package com.credit.platform.admin.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.credit.platform.admin.model.ApiResponse;
import com.credit.platform.admin.model.RuleEntity;
import com.credit.platform.admin.service.ScorecardAdminService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * 评分卡管理 Controller — 评分卡 CRUD。
 * <p>
 * API 路径:
 * <ul>
 *   <li>GET    /api/v1/scorecards                — 列出所有评分卡</li>
 *   <li>POST   /api/v1/scorecards                — 创建评分卡</li>
 *   <li>GET    /api/v1/scorecards/{id}           — 获取最新版本</li>
 *   <li>PUT    /api/v1/scorecards/{id}           — 更新评分卡</li>
 *   <li>DELETE /api/v1/scorecards/{id}           — 删除评分卡</li>
 *   <li>GET    /api/v1/scorecards/{id}/versions  — 版本历史</li>
 *   <li>POST   /api/v1/scorecards/{id}/versions  — 创建新版本</li>
 *   <li>POST   /api/v1/scorecards/{id}/publish   — 发布评分卡</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/api/v1/scorecards")
public class ScorecardController {

    private final ScorecardAdminService service;

    public ScorecardController(ScorecardAdminService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list(
            @RequestParam(required = false) Map<String, Object> params) {
        List<Map<String, Object>> result = service.listScorecards(params).stream()
            .map(RuleEntity::toSummary).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(
            @Valid @RequestBody CreateRequest request) {
        RuleEntity entity = service.createScorecard(request.name(), request.content(), request.description());
        return ResponseEntity.ok(ApiResponse.success(entity.toSummary()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RuleEntity>> get(@PathVariable("id") String id) {
        return ResponseEntity.ok(ApiResponse.success(service.getScorecard(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> update(
            @PathVariable("id") String id, @RequestBody UpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.updateScorecard(id, request.content()).toSummary()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable("id") String id) {
        service.deleteScorecard(id);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @GetMapping("/{id}/versions")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> versions(
            @PathVariable("id") String id) {
        List<Map<String, Object>> result = service.listVersions(id).stream()
            .map(RuleEntity::toSummary).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/{id}/versions")
    public ResponseEntity<ApiResponse<Map<String, Object>>> newVersion(
            @PathVariable("id") String id) {
        return ResponseEntity.ok(ApiResponse.success(service.createNewVersion(id).toSummary()));
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<ApiResponse<Map<String, Object>>> publish(
            @PathVariable("id") String id) {
        return ResponseEntity.ok(ApiResponse.success(service.publishScorecard(id).toSummary()));
    }

    // ========== DTO ==========

    public record CreateRequest(
        @NotBlank(message = "评分卡名称不能为空") String name,
        @NotBlank(message = "评分卡内容不能为空") String content,
        String description
    ) {}

    public record UpdateRequest(String content) {}
}
