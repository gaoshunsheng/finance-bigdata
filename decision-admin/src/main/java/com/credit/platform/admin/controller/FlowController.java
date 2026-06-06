package com.credit.platform.admin.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.credit.platform.admin.model.ApiResponse;
import com.credit.platform.admin.model.RuleEntity;
import com.credit.platform.admin.service.FlowAdminService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * 决策流管理 Controller — 决策流 DAG CRUD。
 * <p>
 * API 路径:
 * <ul>
 *   <li>GET    /api/v1/flows                — 列出所有决策流</li>
 *   <li>POST   /api/v1/flows                — 创建决策流</li>
 *   <li>GET    /api/v1/flows/{id}           — 获取最新版本</li>
 *   <li>PUT    /api/v1/flows/{id}           — 更新决策流</li>
 *   <li>DELETE /api/v1/flows/{id}           — 删除决策流</li>
 *   <li>GET    /api/v1/flows/{id}/versions  — 版本历史</li>
 *   <li>POST   /api/v1/flows/{id}/versions  — 创建新版本</li>
 *   <li>POST   /api/v1/flows/{id}/publish   — 发布决策流</li>
 *   <li>GET    /api/v1/flows/{id}/validate  — 校验 DAG 无环</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/api/v1/flows")
public class FlowController {

    private final FlowAdminService service;

    public FlowController(FlowAdminService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list(
            @RequestParam(required = false) Map<String, Object> params) {
        List<Map<String, Object>> result = service.listFlows(params).stream()
            .map(RuleEntity::toSummary).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(
            @Valid @RequestBody CreateRequest request) {
        RuleEntity entity = service.createFlow(request.name(), request.content(), request.description());
        return ResponseEntity.ok(ApiResponse.success(entity.toSummary()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RuleEntity>> get(@PathVariable("id") String id) {
        return ResponseEntity.ok(ApiResponse.success(service.getFlow(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> update(
            @PathVariable("id") String id, @RequestBody UpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.updateFlow(id, request.content()).toSummary()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable("id") String id) {
        service.deleteFlow(id);
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
        return ResponseEntity.ok(ApiResponse.success(service.publishFlow(id).toSummary()));
    }

    /**
     * 校验决策流 DAG 是否有环。
     */
    @PostMapping("/{id}/validate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> validate(
            @PathVariable("id") String id) {
        RuleEntity flow = service.getFlow(id);
        service.detectCycle(flow.getContent());
        Map<String, Object> result = Map.of(
            "id", id,
            "valid", true,
            "message", "DAG validation passed: no cycles detected"
        );
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ========== DTO ==========

    public record CreateRequest(
        @NotBlank(message = "决策流名称不能为空") String name,
        @NotBlank(message = "决策流内容不能为空") String content,
        String description
    ) {}

    public record UpdateRequest(String content) {}
}
