package com.credit.platform.admin.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.credit.platform.admin.model.PublishStatus;
import com.credit.platform.admin.model.RuleEntity;

/**
 * 规则管理服务 — 统一 CRUD 操作。
 * <p>
 * 支持类型: RULE, SCORECARD, DECISION_TABLE, DECISION_TREE, FLOW, VARIABLE
 * </p>
 */
@Service
public class RuleAdminService {

    private final RuleRepository repository;

    public RuleAdminService(RuleRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    // ==================== CRUD ====================

    /**
     * 创建新规则。
     */
    public RuleEntity create(String type, String name, String content, String description) {
        String id = repository.nextId(type);
        RuleEntity entity = RuleEntity.create(id, name, type, content);
        entity.setDescription(description);
        entity.setCreatedBy("system");
        entity.setUpdatedBy("system");
        return repository.save(entity);
    }

    /**
     * 更新规则内容（创建新版本）。
     */
    public RuleEntity update(String type, String id, String content) {
        RuleEntity latest = repository.findLatest(type, id)
            .orElseThrow(() -> new IllegalArgumentException(
                type + " not found: " + id));

        // 只有 DRAFT 状态才能直接修改
        if (latest.getStatus() != PublishStatus.DRAFT) {
            throw new IllegalStateException(
                "Cannot update rule in " + latest.getStatus() + " status. Create a new version first.");
        }

        latest.setContent(content);
        return repository.save(latest);
    }

    /**
     * 创建新版本。
     */
    public RuleEntity createNewVersion(String type, String id) {
        RuleEntity latest = repository.findLatest(type, id)
            .orElseThrow(() -> new IllegalArgumentException(
                type + " not found: " + id));

        int newVersion = latest.getVersion() + 1;
        RuleEntity newEntity = latest.newVersion(newVersion);
        return repository.save(newEntity);
    }

    /**
     * 获取最新版本。
     */
    public RuleEntity getLatest(String type, String id) {
        return repository.findLatest(type, id)
            .orElseThrow(() -> new IllegalArgumentException(
                type + " not found: " + id));
    }

    /**
     * 获取指定版本。
     */
    public RuleEntity getVersion(String type, String id, int version) {
        return repository.find(type, id, version)
            .orElseThrow(() -> new IllegalArgumentException(
                type + ":" + id + " v" + version + " not found"));
    }

    /**
     * 列出指定类型的所有规则（最新版本）。
     */
    public List<RuleEntity> list(String type) {
        return repository.listByType(type);
    }

    /**
     * 列出所有版本。
     */
    public List<RuleEntity> listVersions(String type, String id) {
        return repository.findVersions(type, id);
    }

    /**
     * 删除指定版本。
     */
    public boolean delete(String type, String id, int version) {
        return repository.delete(type, id, version);
    }

    // ==================== 发布流程 ====================

    /**
     * 推进到下一状态。
     * <p>
     * DRAFT → TESTING → PENDING_REVIEW → APPROVED → GRAYSCALE → RELEASED
     * </p>
     */
    public RuleEntity promote(String type, String id) {
        RuleEntity entity = getLatest(type, id);
        PublishStatus next = nextStatus(entity.getStatus());
        entity.setStatus(next);
        return repository.save(entity);
    }

    /**
     * 驳回 — 回到 DRAFT。
     */
    public RuleEntity reject(String type, String id, String reason) {
        RuleEntity entity = getLatest(type, id);
        if (entity.getStatus() != PublishStatus.PENDING_REVIEW) {
            throw new IllegalStateException("Can only reject rules in PENDING_REVIEW status");
        }
        entity.setStatus(PublishStatus.DRAFT);
        entity.getAttributes().put("rejectReason", reason);
        return repository.save(entity);
    }

    /**
     * 回滚到指定版本。
     */
    public RuleEntity rollback(String type, String id, int targetVersion) {
        RuleEntity target = getVersion(type, id, targetVersion);
        RuleEntity newEntity = target.newVersion(
            getLatest(type, id).getVersion() + 1);
        newEntity.setStatus(PublishStatus.DRAFT);
        newEntity.getAttributes().put("rollbackFrom", targetVersion);
        return repository.save(newEntity);
    }

    // ==================== 内部方法 ====================

    private PublishStatus nextStatus(PublishStatus current) {
        return switch (current) {
            case DRAFT -> PublishStatus.TESTING;
            case TESTING -> PublishStatus.PENDING_REVIEW;
            case PENDING_REVIEW -> PublishStatus.APPROVED;
            case APPROVED -> PublishStatus.GRAYSCALE;
            case GRAYSCALE -> PublishStatus.RELEASED;
            case RELEASED -> throw new IllegalStateException("Already released");
            case ROLLED_BACK -> PublishStatus.DRAFT;
        };
    }
}
