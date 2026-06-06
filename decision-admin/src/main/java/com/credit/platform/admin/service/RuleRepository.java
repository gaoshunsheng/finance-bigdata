package com.credit.platform.admin.service;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.credit.platform.admin.mapper.RuleMapper;
import com.credit.platform.admin.model.PublishStatus;
import com.credit.platform.admin.model.RuleEntity;
import org.springframework.stereotype.Repository;

/**
 * 规则实体仓库 — MyBatis-Plus + MySQL 持久化实现。
 * <p>
 * 复合主键: (type, id, version) — 对应 rule_entity 表。
 * 保持与内存版本相同的 API 接口，上层服务无需修改。
 * </p>
 */
@Repository
public class RuleRepository {

    private final RuleMapper ruleMapper;
    private final AtomicInteger idCounter = new AtomicInteger(0);

    public RuleRepository(RuleMapper ruleMapper) {
        this.ruleMapper = ruleMapper;
    }

    /**
     * 生成唯一 ID。
     */
    public String nextId(String type) {
        return type.toLowerCase() + "-" + System.currentTimeMillis() + "-" + idCounter.incrementAndGet();
    }

    /**
     * 存储实体 — INSERT or UPDATE。
     */
    public RuleEntity save(RuleEntity entity) {
        Objects.requireNonNull(entity);
        entity.setUpdatedAt(java.time.LocalDateTime.now());

        // 检查是否已存在（按复合主键查找）
        Optional<RuleEntity> existing = ruleMapper.findByTypeAndIdAndVersion(
                entity.getType(), entity.getId(), entity.getVersion());

        if (existing.isPresent()) {
            // UPDATE — 使用 LambdaQueryWrapper 定位复合主键行
            ruleMapper.update(entity, new LambdaQueryWrapper<RuleEntity>()
                    .eq(RuleEntity::getType, entity.getType())
                    .eq(RuleEntity::getId, entity.getId())
                    .eq(RuleEntity::getVersion, entity.getVersion()));
        } else {
            // INSERT
            ruleMapper.insert(entity);
        }
        return entity;
    }

    /**
     * 按 type+id+version 查找。
     */
    public Optional<RuleEntity> find(String type, String id, int version) {
        return ruleMapper.findByTypeAndIdAndVersion(type, id, version);
    }

    /**
     * 查找指定 type+id 的最新版本。
     */
    public Optional<RuleEntity> findLatest(String type, String id) {
        return ruleMapper.findLatest(type, id);
    }

    /**
     * 查找指定 type+id 的所有版本。
     */
    public List<RuleEntity> findVersions(String type, String id) {
        return ruleMapper.findVersions(type, id);
    }

    /**
     * 按类型列出所有最新版本。
     */
    public List<RuleEntity> listByType(String type) {
        return ruleMapper.listLatestByType(type);
    }

    /**
     * 按状态过滤。
     */
    public List<RuleEntity> findByTypeAndStatus(String type, PublishStatus status) {
        return ruleMapper.findByTypeAndStatus(type, status.name());
    }

    /**
     * 删除指定版本。
     */
    public boolean delete(String type, String id, int version) {
        return ruleMapper.deleteByTypeAndIdAndVersion(type, id, version) > 0;
    }

    /**
     * 统计总数。
     */
    public long count() {
        return ruleMapper.countAll();
    }
}
