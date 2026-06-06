package com.credit.platform.admin.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.credit.platform.admin.model.ApiResponse;
import com.credit.platform.admin.model.RuleEntity;
import com.credit.platform.admin.service.VariableAdminService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * 变量管理 Controller — 变量 CRUD 与依赖分析。
 * <p>
 * API 路径:
 * <ul>
 *   <li>GET    /api/v1/variables                  — 列出所有变量</li>
 *   <li>POST   /api/v1/variables                  — 创建变量</li>
 *   <li>GET    /api/v1/variables/{id}             — 获取最新版本</li>
 *   <li>PUT    /api/v1/variables/{id}             — 更新变量</li>
 *   <li>DELETE /api/v1/variables/{id}             — 删除变量</li>
 *   <li>GET    /api/v1/variables/{id}/versions    — 版本历史</li>
 *   <li>GET    /api/v1/variables/{id}/dependencies — 分析依赖</li>
 *   <li>GET    /api/v1/variables/{id}/dependents  — 查找反向依赖</li>
 *   <li>POST   /api/v1/variables/{id}/publish     — 发布变量</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/api/v1/variables")
public class VariableController {

    private final VariableAdminService service;

    public VariableController(VariableAdminService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list(
            @RequestParam(required = false) Map<String, Object> params) {
        List<Map<String, Object>> result = service.listVariables(params).stream()
            .map(RuleEntity::toSummary).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(
            @Valid @RequestBody CreateRequest request) {
        RuleEntity entity = service.createVariable(request.name(), request.content(), request.description());
        return ResponseEntity.ok(ApiResponse.success(entity.toSummary()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RuleEntity>> get(@PathVariable("id") String id) {
        return ResponseEntity.ok(ApiResponse.success(service.getVariable(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> update(
            @PathVariable("id") String id, @RequestBody UpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.updateVariable(id, request.content()).toSummary()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable("id") String id) {
        service.deleteVariable(id);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @GetMapping("/{id}/versions")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> versions(
            @PathVariable("id") String id) {
        List<Map<String, Object>> result = service.listVersions(id).stream()
            .map(RuleEntity::toSummary).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 分析变量依赖 — 返回此变量引用了哪些其他变量。
     */
    @GetMapping("/{id}/dependencies")
    public ResponseEntity<ApiResponse<List<String>>> dependencies(
            @PathVariable("id") String id) {
        return ResponseEntity.ok(ApiResponse.success(service.analyzeDependencies(id)));
    }

    /**
     * 查找反向依赖 — 返回哪些变量依赖此变量。
     */
    @GetMapping("/{id}/dependents")
    public ResponseEntity<ApiResponse<List<String>>> dependents(
            @PathVariable("id") String id) {
        return ResponseEntity.ok(ApiResponse.success(service.findDependents(id)));
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<ApiResponse<Map<String, Object>>> publish(
            @PathVariable("id") String id) {
        return ResponseEntity.ok(ApiResponse.success(service.publishVariable(id).toSummary()));
    }

    // ========== DTO ==========

    public record CreateRequest(
        @NotBlank(message = "变量名称不能为空") String name,
        @NotBlank(message = "变量内容不能为空") String content,
        String description
    ) {}

    public record UpdateRequest(String content) {}
}
