package com.credit.platform.admin.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

/**
 * 审计日志仓库 — 内存实现，后续替换为 Elasticsearch。
 * <p>
 * 审计日志只增不改不删，保留 5 年。
 * </p>
 */
@Repository
public class AuditLogRepository {

    private final ConcurrentHashMap<Long, AuditLog> store = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong(0);

    /**
     * 保存审计日志。
     */
    public AuditLog save(AuditLog log) {
        Objects.requireNonNull(log);
        if (log.getId() == null) {
            log.setId(idSequence.incrementAndGet());
        }
        store.put(log.getId(), log);
        return log;
    }

    /**
     * 按 ID 查找。
     */
    public AuditLog findById(Long id) {
        return store.get(id);
    }

    /**
     * 查询指定操作人的审计日志。
     */
    public List<AuditLog> findByOperator(String operator) {
        return store.values().stream()
            .filter(log -> operator.equals(log.getOperator()))
            .sorted((a, b) -> b.getOperatedAt().compareTo(a.getOperatedAt()))
            .collect(Collectors.toList());
    }

    /**
     * 查询指定目标的审计日志。
     */
    public List<AuditLog> findByTarget(String targetType, String targetId) {
        return store.values().stream()
            .filter(log -> targetType.equals(log.getTargetType()) && targetId.equals(log.getTargetId()))
            .sorted((a, b) -> b.getOperatedAt().compareTo(a.getOperatedAt()))
            .collect(Collectors.toList());
    }

    /**
     * 查询指定操作类型的审计日志。
     */
    public List<AuditLog> findByAction(String action) {
        return store.values().stream()
            .filter(log -> action.equals(log.getAction()))
            .sorted((a, b) -> b.getOperatedAt().compareTo(a.getOperatedAt()))
            .collect(Collectors.toList());
    }

    /**
     * 查询所有审计日志（按时间倒序，分页）。
     */
    public List<AuditLog> findAll(int page, int size) {
        return store.values().stream()
            .sorted((a, b) -> b.getOperatedAt().compareTo(a.getOperatedAt()))
            .skip((long) page * size)
            .limit(size)
            .collect(Collectors.toList());
    }

    /**
     * 总数。
     */
    public long count() {
        return store.size();
    }
}
