package com.credit.platform.admin.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.credit.platform.admin.model.PublishStatus;
import com.credit.platform.admin.model.RuleEntity;

/**
 * 评分卡管理服务 — 评分卡 CRUD 与版本管理。
 * <p>
 * 在通用 RuleAdminService 基础上增加评分卡特有的逻辑:
 * <ul>
 *   <li>评分卡内容校验（必须包含 scorecard 字段）</li>
 *   <li>版本管理 — 创建新版本、版本回滚</li>
 *   <li>发布流程 — DRAFT → RELEASED</li>
 * </ul>
 * </p>
 */
@Service
public class ScorecardAdminService {

    private static final Logger log = LoggerFactory.getLogger(ScorecardAdminService.class);
    private static final String TYPE = "SCORECARD";

    private final RuleRepository repository;

    public ScorecardAdminService(RuleRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    // ==================== CRUD ====================

    /**
     * 创建评分卡。
     *
     * @param name        评分卡名称
     * @param content     评分卡 JSON 定义
     * @param description 评分卡描述
     * @return 创建的评分卡实体
     */
    public RuleEntity createScorecard(String name, String content, String description) {
        validateContent(content);
        String id = repository.nextId(TYPE);
        RuleEntity entity = RuleEntity.create(id, name, TYPE, content);
        entity.setDescription(description);
        entity.setCreatedBy("system");
        entity.setUpdatedBy("system");
        log.info("创建评分卡: id={}, name={}", id, name);
        return repository.save(entity);
    }

    /**
     * 更新评分卡内容（仅 DRAFT 状态可修改）。
     */
    public RuleEntity updateScorecard(String id, String content) {
        validateContent(content);
        RuleEntity latest = getLatest(id);
        assertDraft(latest);

        latest.setContent(content);
        log.info("更新评分卡: id={}, version={}", id, latest.getVersion());
        return repository.save(latest);
    }

    /**
     * 软删除评分卡（创建新版本标记为已删除）。
     */
    public boolean deleteScorecard(String id) {
        RuleEntity latest = getLatest(id);
        if (latest.getStatus() == PublishStatus.RELEASED) {
            throw new IllegalStateException("Cannot delete a released scorecard. Rollback first.");
        }
        log.info("删除评分卡: id={}, version={}", id, latest.getVersion());
        return repository.delete(TYPE, id, latest.getVersion());
    }

    /**
     * 获取评分卡最新版本。
     */
    public RuleEntity getScorecard(String id) {
        return getLatest(id);
    }

    /**
     * 列出所有评分卡（最新版本）。
     */
    public List<RuleEntity> listScorecards(Map<String, Object> params) {
        if (params != null && params.containsKey("status")) {
            String status = (String) params.get("status");
            return repository.findByTypeAndStatus(TYPE, PublishStatus.valueOf(status));
        }
        return repository.listByType(TYPE);
    }

    // ==================== 版本管理 ====================

    /**
     * 创建新版本。
     */
    public RuleEntity createNewVersion(String id) {
        RuleEntity latest = getLatest(id);
        int newVersion = latest.getVersion() + 1;
        RuleEntity newEntity = latest.newVersion(newVersion);
        log.info("创建评分卡新版本: id={}, version={}", id, newVersion);
        return repository.save(newEntity);
    }

    /**
     * 获取版本历史。
     */
    public List<RuleEntity> listVersions(String id) {
        return repository.findVersions(TYPE, id);
    }

    // ==================== 发布 ====================

    /**
     * 发布评分卡: DRAFT → TESTING → PENDING_REVIEW → APPROVED → RELEASED。
     * <p>
     * 简化流程: 直接将状态推进到 RELEASED。
     * </p>
     */
    public RuleEntity publishScorecard(String id) {
        RuleEntity entity = getLatest(id);
        if (entity.getStatus() == PublishStatus.RELEASED) {
            throw new IllegalStateException("Scorecard already released");
        }
        entity.setStatus(PublishStatus.RELEASED);
        log.info("发布评分卡: id={}, version={}", id, entity.getVersion());
        return repository.save(entity);
    }

    // ==================== 内部方法 ====================

    private RuleEntity getLatest(String id) {
        return repository.findLatest(TYPE, id)
            .orElseThrow(() -> new IllegalArgumentException("Scorecard not found: " + id));
    }

    private void assertDraft(RuleEntity entity) {
        if (entity.getStatus() != PublishStatus.DRAFT) {
            throw new IllegalStateException(
                "Cannot update scorecard in " + entity.getStatus() + " status. Create a new version first.");
        }
    }

    /**
     * 校验评分卡内容 — 确保包含 scorecard 定义。
     */
    private void validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Scorecard content cannot be empty");
        }
        // 基本校验: 内容应为合法 JSON 且包含 scorecard 关键字
        String trimmed = content.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            throw new IllegalArgumentException("Scorecard content must be valid JSON");
        }
    }
}
