package com.credit.platform.admin.security;

import java.util.List;
import java.util.Objects;

import com.credit.platform.admin.mapper.AuditLogMapper;
import org.springframework.stereotype.Repository;

/**
 * 审计日志仓库 — MyBatis-Plus + MySQL 持久化实现。
 * <p>
 * 审计日志只增不改不删，保留 5 年。
 * </p>
 */
@Repository
public class AuditLogRepository {

    private final AuditLogMapper auditLogMapper;

    public AuditLogRepository(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    /**
     * 保存审计日志 — INSERT only。
     */
    public AuditLog save(AuditLog log) {
        Objects.requireNonNull(log);
        auditLogMapper.insert(log);
        return log;
    }

    /**
     * 按 ID 查找。
     */
    public AuditLog findById(Long id) {
        return auditLogMapper.selectById(id);
    }

    /**
     * 查询指定操作人的审计日志。
     */
    public List<AuditLog> findByOperator(String operator) {
        return auditLogMapper.findByOperator(operator);
    }

    /**
     * 查询指定目标的审计日志。
     */
    public List<AuditLog> findByTarget(String targetType, String targetId) {
        return auditLogMapper.findByTarget(targetType, targetId);
    }

    /**
     * 查询指定操作类型的审计日志。
     */
    public List<AuditLog> findByAction(String action) {
        return auditLogMapper.findByAction(action);
    }

    /**
     * 查询所有审计日志（按时间倒序，分页）。
     */
    public List<AuditLog> findAll(int page, int size) {
        return auditLogMapper.findAllPaged(page * size, size);
    }

    /**
     * 总数。
     */
    public long count() {
        return auditLogMapper.selectCount(null);
    }
}
