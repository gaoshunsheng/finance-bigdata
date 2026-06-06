package com.credit.platform.admin.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.credit.platform.admin.model.ApiResponse;
import com.credit.platform.admin.model.RuleEntity;
import com.credit.platform.admin.service.ExperimentAdminService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * 实验管理 Controller — AB 实验 CRUD。
 * <p>
 * API 路径:
 * <ul>
 *   <li>GET    /api/v1/experiments                — 列出所有实验</li>
 *   <li>POST   /api/v1/experiments                — 创建实验</li>
 *   <li>GET    /api/v1/experiments/{id}           — 获取最新版本</li>
 *   <li>PUT    /api/v1/experiments/{id}           — 更新实验</li>
 *   <li>DELETE /api/v1/experiments/{id}           — 删除实验</li>
 *   <li>GET    /api/v1/experiments/{id}/versions  — 版本历史</li>
 *   <li>POST   /api/v1/experiments/{id}/versions  — 创建新版本</li>
 *   <li>POST   /api/v1/experiments/{id}/publish   — 发布实验</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/api/v1/experiments")
public class ExperimentController {

    private final ExperimentAdminService service;

    public ExperimentController(ExperimentAdminService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list(
            @RequestParam(required = false) Map<String, Object> params) {
        List<Map<String, Object>> result = service.listExperiments(params).stream()
            .map(RuleEntity::toSummary).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(
            @Valid @RequestBody CreateRequest request) {
        RuleEntity entity = service.createExperiment(request.name(), request.content(), request.description());
        return ResponseEntity.ok(ApiResponse.success(entity.toSummary()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RuleEntity>> get(@PathVariable("id") String id) {
        return ResponseEntity.ok(ApiResponse.success(service.getExperiment(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> update(
            @PathVariable("id") String id, @RequestBody UpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.updateExperiment(id, request.content()).toSummary()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable("id") String id) {
        service.deleteExperiment(id);
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
        return ResponseEntity.ok(ApiResponse.success(service.publishExperiment(id).toSummary()));
    }

    // ========== DTO ==========

    public record CreateRequest(
        @NotBlank(message = "实验名称不能为空") String name,
        @NotBlank(message = "实验内容不能为空") String content,
        String description
    ) {}

    public record UpdateRequest(String content) {}
}
