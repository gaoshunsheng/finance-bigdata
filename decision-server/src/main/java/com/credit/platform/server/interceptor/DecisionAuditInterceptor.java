package com.credit.platform.server.interceptor;

import com.credit.platform.server.model.DecisionLogDocument;
import com.credit.platform.server.repository.DecisionLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * 决策执行审计拦截器 — 记录所有决策请求和响应。
 * <p>
 * 审计日志保留 5 年，记录内容包括:
 * <ul>
 *   <li>请求时间、客户端IP、请求路径</li>
 *   <li>决策ID、输入数据摘要</li>
 *   <li>决策结果、耗时</li>
 *   <li>操作人员 (从 Authorization header 提取)</li>
 * </ul>
 * </p>
 */
@Component
public class DecisionAuditInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(DecisionAuditInterceptor.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String START_TIME_ATTR = "auditStartTime";
    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");

    private final DecisionLogRepository decisionLogRepository;

    public DecisionAuditInterceptor(DecisionLogRepository decisionLogRepository) {
        this.decisionLogRepository = decisionLogRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME_ATTR, System.nanoTime());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                 Object handler, Exception ex) {
        try {
            Long startNanos = (Long) request.getAttribute(START_TIME_ATTR);
            long durationMs = startNanos != null ? (System.nanoTime() - startNanos) / 1_000_000 : -1;

            Map<String, Object> auditEntry = new LinkedHashMap<>();
            auditEntry.put("timestamp", LocalDateTime.now(BEIJING).toString());
            auditEntry.put("type", "DECISION_EXECUTION");
            auditEntry.put("method", request.getMethod());
            auditEntry.put("path", request.getRequestURI());
            auditEntry.put("clientIp", getClientIp(request));
            auditEntry.put("statusCode", response.getStatus());
            auditEntry.put("durationMs", durationMs);
            auditEntry.put("operator", extractOperator(request));
            auditEntry.put("userAgent", request.getHeader("User-Agent"));

            if (ex != null) {
                auditEntry.put("error", ex.getMessage());
            }

            // 保留策略标记: 5年
            auditEntry.put("retention", "5Y");

            logger.info("AUDIT: {}", objectMapper.writeValueAsString(auditEntry));

            // 持久化审计日志到 ES
            DecisionLogDocument auditDoc = new DecisionLogDocument();
            auditDoc.setId(UUID.randomUUID().toString().replace("-", ""));
            auditDoc.setTraceId(request.getRequestId());
            auditDoc.setDecisionResult("AUDIT");
            auditDoc.setExecutionTimeMs(durationMs);
            try {
                auditDoc.setInputSnapshot(objectMapper.writeValueAsString(auditEntry));
            } catch (Exception ignored) {}
            decisionLogRepository.save(auditDoc);
        } catch (Exception e) {
            // 审计日志写入失败不应影响主流程
            logger.error("Failed to write audit log: {}", e.getMessage());
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        // 多级代理取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private String extractOperator(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return "token:" + auth.substring(7, Math.min(auth.length(), 19)) + "...";
        }
        return "anonymous";
    }
}
