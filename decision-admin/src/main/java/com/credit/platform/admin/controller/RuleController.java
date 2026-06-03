package com.credit.platform.admin.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.credit.platform.admin.model.ApiResponse;
import com.credit.platform.admin.model.RuleEntity;
import com.credit.platform.admin.service.RuleAdminService;

/**
 * 规则管理 Controller — 条件规则 CRUD。
 * <p>
 * API 路径:
 * <ul>
 *   <li>GET    /api/v1/rules           — 列出所有规则</li>
 *   <li>POST   /api/v1/rules           — 创建规则</li>
 *   <li>GET    /api/v1/rules/{id}      — 获取最新版本</li>
 *   <li>PUT    /api/v1/rules/{id}      — 更新规则</li>
 *   <li>DELETE /api/v1/rules/{id}/{v}  — 删除指定版本</li>
 *   <li>GET    /api/v1/rules/{id}/versions — 版本历史</li>
 *   <li>POST   /api/v1/rules/{id}/versions — 创建新版本</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/api/v1/rules")
public class RuleController {

    private static final String TYPE = "RULE";
    private final RuleAdminService service;

    public RuleController(RuleAdminService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list() {
        List<Map<String, Object>> result = service.list(TYPE).stream()
            .map(RuleEntity::toSummary).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(
            @RequestBody CreateRequest request) {
        RuleEntity entity = service.create(TYPE, request.name(), request.content(), request.description());
        return ResponseEntity.ok(ApiResponse.success(entity.toSummary()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RuleEntity>> get(@PathVariable("id") String id) {
        return ResponseEntity.ok(ApiResponse.success(service.getLatest(TYPE, id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> update(
            @PathVariable("id") String id, @RequestBody UpdateRequest request) {
        RuleEntity entity = service.update(TYPE, id, request.content());
        return ResponseEntity.ok(ApiResponse.success(entity.toSummary()));
    }

    @DeleteMapping("/{id}/{version}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable("id") String id, @PathVariable("version") int version) {
        service.delete(TYPE, id, version);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @GetMapping("/{id}/versions")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> versions(
            @PathVariable("id") String id) {
        List<Map<String, Object>> result = service.listVersions(TYPE, id).stream()
            .map(RuleEntity::toSummary).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/{id}/versions")
    public ResponseEntity<ApiResponse<Map<String, Object>>> newVersion(
            @PathVariable("id") String id) {
        RuleEntity entity = service.createNewVersion(TYPE, id);
        return ResponseEntity.ok(ApiResponse.success(entity.toSummary()));
    }

    // ========== DTO ==========

    public record CreateRequest(String name, String content, String description) {}
    public record UpdateRequest(String content) {}
}
