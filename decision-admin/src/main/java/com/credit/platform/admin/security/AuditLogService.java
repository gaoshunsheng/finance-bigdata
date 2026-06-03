package com.credit.platform.admin.security;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

/**
 * 审计日志服务 — 记录所有配置变更操作。
 * <p>
 * 审计日志只增不删不改，保留 5 年。
 * </p>
 */
@Service
public class AuditLogService {

    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    /**
     * 记录操作审计日志。
     */
    public AuditLog log(String operator, String action, String targetType,
                         String targetId, String details) {
        AuditLog log = AuditLog.of(operator, action, targetType, targetId, details);
        return repository.save(log);
    }

    /**
     * 记录带版本和快照的操作审计日志。
     */
    public AuditLog log(String operator, String action, String targetType,
                         String targetId, Integer targetVersion,
                         String before, String after, String details) {
        AuditLog log = AuditLog.of(operator, action, targetType, targetId,
            targetVersion, before, after, details);
        return repository.save(log);
    }

    /**
     * 记录登录审计。
     */
    public AuditLog logLogin(String operator, String ipAddress) {
        AuditLog log = AuditLog.of(operator, "LOGIN", "USER", operator, "User logged in");
        log.setIpAddress(ipAddress);
        return repository.save(log);
    }

    /**
     * 查询指定目标的审计历史。
     */
    public List<AuditLog> getTargetHistory(String targetType, String targetId) {
        return repository.findByTarget(targetType, targetId);
    }

    /**
     * 查询指定操作人的审计日志。
     */
    public List<AuditLog> getOperatorHistory(String operator) {
        return repository.findByOperator(operator);
    }

    /**
     * 查询所有审计日志（分页）。
     */
    public List<AuditLog> list(int page, int size) {
        return repository.findAll(page, size);
    }

    /**
     * 按操作类型查询。
     */
    public List<AuditLog> findByAction(String action) {
        return repository.findByAction(action);
    }

    /**
     * 总数。
     */
    public long count() {
        return repository.count();
    }
}
