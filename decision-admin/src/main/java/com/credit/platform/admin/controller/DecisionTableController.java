package com.credit.platform.admin.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.credit.platform.admin.model.ApiResponse;
import com.credit.platform.admin.model.RuleEntity;
import com.credit.platform.admin.service.DecisionTableAdminService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * 决策表管理 Controller — 决策表 CRUD。
 * <p>
 * API 路径:
 * <ul>
 *   <li>GET    /api/v1/tables                — 列出所有决策表</li>
 *   <li>POST   /api/v1/tables                — 创建决策表</li>
 *   <li>GET    /api/v1/tables/{id}           — 获取最新版本</li>
 *   <li>PUT    /api/v1/tables/{id}           — 更新决策表</li>
 *   <li>DELETE /api/v1/tables/{id}           — 删除决策表</li>
 *   <li>GET    /api/v1/tables/{id}/versions  — 版本历史</li>
 *   <li>POST   /api/v1/tables/{id}/versions  — 创建新版本</li>
 *   <li>POST   /api/v1/tables/{id}/publish   — 发布决策表</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/api/v1/tables")
public class DecisionTableController {

    private final DecisionTableAdminService service;

    public DecisionTableController(DecisionTableAdminService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list(
            @RequestParam(required = false) Map<String, Object> params) {
        List<Map<String, Object>> result = service.listDecisionTables(params).stream()
            .map(RuleEntity::toSummary).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(
            @Valid @RequestBody CreateRequest request) {
        RuleEntity entity = service.createDecisionTable(request.name(), request.content(), request.description());
        return ResponseEntity.ok(ApiResponse.success(entity.toSummary()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RuleEntity>> get(@PathVariable("id") String id) {
        return ResponseEntity.ok(ApiResponse.success(service.getDecisionTable(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> update(
            @PathVariable("id") String id, @RequestBody UpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.updateDecisionTable(id, request.content()).toSummary()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable("id") String id) {
        service.deleteDecisionTable(id);
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
        return ResponseEntity.ok(ApiResponse.success(service.publishDecisionTable(id).toSummary()));
    }

    // ========== DTO ==========

    public record CreateRequest(
        @NotBlank(message = "决策表名称不能为空") String name,
        @NotBlank(message = "决策表内容不能为空") String content,
        String description
    ) {}

    public record UpdateRequest(String content) {}
}
