package com.credit.platform.admin.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import com.credit.platform.admin.model.PublishStatus;
import com.credit.platform.admin.model.RuleEntity;

/**
 * 规则实体内存仓库 — 当前使用 ConcurrentHashMap 存储。
 * <p>
 * 后续替换为 MyBatis-Plus + MySQL 实现。
 * Key 格式: type:id:version → 例如 "RULE:rule-001:1"
 * </p>
 */
@Repository
public class RuleRepository {

    private final ConcurrentHashMap<String, RuleEntity> store = new ConcurrentHashMap<>();
    private final AtomicInteger idCounter = new AtomicInteger(0);

    /**
     * 生成唯一 ID。
     */
    public String nextId(String type) {
        return type.toLowerCase() + "-" + System.currentTimeMillis() + "-" + idCounter.incrementAndGet();
    }

    /**
     * 存储实体。
     */
    public RuleEntity save(RuleEntity entity) {
        Objects.requireNonNull(entity);
        String key = key(entity.getType(), entity.getId(), entity.getVersion());
        entity.setUpdatedAt(java.time.LocalDateTime.now());
        store.put(key, entity);
        return entity;
    }

    /**
     * 按 type+id+version 查找。
     */
    public Optional<RuleEntity> find(String type, String id, int version) {
        return Optional.ofNullable(store.get(key(type, id, version)));
    }

    /**
     * 查找指定 type+id 的最新版本。
     */
    public Optional<RuleEntity> findLatest(String type, String id) {
        return store.values().stream()
            .filter(e -> type.equals(e.getType()) && id.equals(e.getId()))
            .max((a, b) -> Integer.compare(a.getVersion(), b.getVersion()));
    }

    /**
     * 查找指定 type+id 的所有版本。
     */
    public List<RuleEntity> findVersions(String type, String id) {
        return store.values().stream()
            .filter(e -> type.equals(e.getType()) && id.equals(e.getId()))
            .sorted((a, b) -> Integer.compare(b.getVersion(), a.getVersion()))
            .collect(Collectors.toList());
    }

    /**
     * 按类型列出所有最新版本。
     */
    public List<RuleEntity> listByType(String type) {
        // 按 id 分组，取每组最新版本
        Map<String, RuleEntity> latest = new ConcurrentHashMap<>();
        store.values().stream()
            .filter(e -> type.equals(e.getType()))
            .forEach(e -> latest.merge(e.getId(), e,
                (a, b) -> a.getVersion() >= b.getVersion() ? a : b));
        return new ArrayList<>(latest.values());
    }

    /**
     * 按状态过滤。
     */
    public List<RuleEntity> findByTypeAndStatus(String type, PublishStatus status) {
        return listByType(type).stream()
            .filter(e -> e.getStatus() == status)
            .collect(Collectors.toList());
    }

    /**
     * 删除指定版本。
     */
    public boolean delete(String type, String id, int version) {
        return store.remove(key(type, id, version)) != null;
    }

    /**
     * 统计总数。
     */
    public long count() {
        return store.size();
    }

    private String key(String type, String id, int version) {
        return type + ":" + id + ":" + version;
    }
}
