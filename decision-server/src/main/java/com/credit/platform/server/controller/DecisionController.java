package com.credit.platform.server.controller;

import java.util.Map;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.credit.platform.engine.common.model.DecisionResponse;
import com.credit.platform.server.service.DecisionService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 决策引擎 REST API。
 * <p>
 * 核心接口:
 * <ul>
 *   <li>POST /api/v1/decision/execute — 执行决策</li>
 *   <li>GET /api/v1/decision/report/{decisionId} — 查询决策报告</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/api/v1/decision")
public class DecisionController {

    /** 合法渠道集合 */
    private static final Set<String> ALLOWED_CHANNELS = Set.of("APP", "WEB", "API", "PARTNER");

    private final DecisionService decisionService;

    public DecisionController(DecisionService decisionService) {
        this.decisionService = decisionService;
    }

    /**
     * 执行决策。
     * <p>
     * 请求体:
     * <pre>
     * {
     *   "strategyId": "STR_CREDIT_V3",
     *   "channel": "APP",
     *   "applicant": { "name": "张三", "age": 25, ... },
     *   "metadata": { "deviceFingerprint": "...", "ipAddress": "..." }
     * }
     * </pre>
     * </p>
     *
     * @param request 决策请求
     * @return 决策响应
     */
    @PostMapping("/execute")
    public ResponseEntity<DecisionResponse> execute(
            @Valid @RequestBody DecisionRequestDto request,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestHeader(value = "X-Channel", required = false) String headerChannel) {

        String channel = headerChannel != null ? headerChannel : request.getChannel();

        // 校验渠道合法性
        if (channel != null && !ALLOWED_CHANNELS.contains(channel.toUpperCase())) {
            return ResponseEntity.badRequest().body(
                DecisionResponse.error(null, null,
                    "Invalid channel: " + channel + ", allowed: " + ALLOWED_CHANNELS, 0L));
        }

        DecisionResponse response = decisionService.execute(
            request.getStrategyId(),
            channel,
            request.getApplicant(),
            request.getMetadata()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * 查询决策报告。
     *
     * @param decisionId 决策 ID
     * @return 决策报告 (JSON)
     */
    @GetMapping("/report/{decisionId}")
    public ResponseEntity<Map<String, Object>> getReport(@PathVariable("decisionId") String decisionId) {
        Map<String, Object> report = decisionService.getReport(decisionId);
        if (report == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(report);
    }

    /**
     * 健康检查。
     *
     * @return 服务状态
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "decision-server",
            "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * 决策请求 DTO。
     */
    public static class DecisionRequestDto {
        @NotBlank(message = "strategyId 不能为空")
        private String strategyId;
        private String channel;
        @NotNull(message = "applicant 不能为 null")
        private Map<String, Object> applicant;
        private Map<String, Object> metadata;

        public String getStrategyId() { return strategyId; }
        public void setStrategyId(String strategyId) { this.strategyId = strategyId; }
        public String getChannel() { return channel; }
        public void setChannel(String channel) { this.channel = channel; }
        public Map<String, Object> getApplicant() { return applicant; }
        public void setApplicant(Map<String, Object> applicant) { this.applicant = applicant; }
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }
}
