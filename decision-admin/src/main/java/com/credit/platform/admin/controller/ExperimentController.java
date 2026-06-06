package com.credit.platform.admin.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.credit.platform.admin.model.ApiResponse;
import com.credit.platform.admin.model.RuleEntity;
import com.credit.platform.admin.service.RuleAdminService;

/**
 * 实验管理 Controller — 实验 CRUD。
 */
@RestController
@RequestMapping("/api/v1/experiments")
public class ExperimentController {

    private static final String TYPE = "EXPERIMENT";
    private final RuleAdminService service;

    public ExperimentController(RuleAdminService service) {
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
            @RequestBody RuleController.CreateRequest request) {
        RuleEntity entity = service.create(TYPE, request.name(), request.content(), request.description());
        return ResponseEntity.ok(ApiResponse.success(entity.toSummary()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RuleEntity>> get(@PathVariable("id") String id) {
        return ResponseEntity.ok(ApiResponse.success(service.getLatest(TYPE, id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> update(
            @PathVariable("id") String id, @RequestBody RuleController.UpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.update(TYPE, id, request.content()).toSummary()));
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
        return ResponseEntity.ok(ApiResponse.success(service.createNewVersion(TYPE, id).toSummary()));
    }
}
