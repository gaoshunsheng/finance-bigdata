package com.credit.platform.admin.security;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 审计日志 — 记录所有配置变更操作。
 * <p>
 * 审计日志保留 5 年，不可修改/删除。
 * </p>
 */
public class AuditLog {

    private Long id;
    /** 操作人 */
    private String operator;
    /** 操作类型: CREATE / UPDATE / DELETE / PUBLISH / APPROVE / REJECT / ROLLBACK / GRAYSCALE / LOGIN */
    private String action;
    /** 目标类型: RULE / SCORECARD / DECISION_TABLE / DECISION_TREE / FLOW / VARIABLE / USER */
    private String targetType;
    /** 目标 ID */
    private String targetId;
    /** 目标版本 */
    private Integer targetVersion;
    /** 变更前内容 */
    private String beforeSnapshot;
    /** 变更后内容 */
    private String afterSnapshot;
    /** 操作详情 */
    private String details;
    /** 操作 IP */
    private String ipAddress;
    /** 操作时间 */
    private LocalDateTime operatedAt;

    public AuditLog() {
        this.operatedAt = LocalDateTime.now();
    }

    /**
     * 创建审计日志。
     */
    public static AuditLog of(String operator, String action, String targetType,
                               String targetId, String details) {
        AuditLog log = new AuditLog();
        log.operator = Objects.requireNonNull(operator);
        log.action = Objects.requireNonNull(action);
        log.targetType = targetType;
        log.targetId = targetId;
        log.details = details;
        return log;
    }

    /**
     * 创建带版本和快照的审计日志。
     */
    public static AuditLog of(String operator, String action, String targetType,
                               String targetId, Integer targetVersion,
                               String beforeSnapshot, String afterSnapshot,
                               String details) {
        AuditLog log = of(operator, action, targetType, targetId, details);
        log.targetVersion = targetVersion;
        log.beforeSnapshot = beforeSnapshot;
        log.afterSnapshot = afterSnapshot;
        return log;
    }

    // ========== Getters & Setters ==========

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }
    public Integer getTargetVersion() { return targetVersion; }
    public void setTargetVersion(Integer targetVersion) { this.targetVersion = targetVersion; }
    public String getBeforeSnapshot() { return beforeSnapshot; }
    public void setBeforeSnapshot(String beforeSnapshot) { this.beforeSnapshot = beforeSnapshot; }
    public String getAfterSnapshot() { return afterSnapshot; }
    public void setAfterSnapshot(String afterSnapshot) { this.afterSnapshot = afterSnapshot; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public LocalDateTime getOperatedAt() { return operatedAt; }
    public void setOperatedAt(LocalDateTime operatedAt) { this.operatedAt = operatedAt; }
}
