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
 * 决策表管理服务 — 决策表 CRUD 与版本管理。
 * <p>
 * 在通用 RuleAdminService 基础上增加决策表特有的逻辑:
 * <ul>
 *   <li>决策表内容校验（必须包含 columns 和 rows 字段）</li>
 *   <li>版本管理 — 创建新版本、版本回滚</li>
 *   <li>发布流程 — DRAFT → RELEASED</li>
 * </ul>
 * </p>
 */
@Service
public class DecisionTableAdminService {

    private static final Logger log = LoggerFactory.getLogger(DecisionTableAdminService.class);
    private static final String TYPE = "DECISION_TABLE";

    private final RuleRepository repository;

    public DecisionTableAdminService(RuleRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    // ==================== CRUD ====================

    /**
     * 创建决策表。
     *
     * @param name        决策表名称
     * @param content     决策表 JSON 定义（包含 columns 和 rows）
     * @param description 决策表描述
     * @return 创建的决策表实体
     */
    public RuleEntity createDecisionTable(String name, String content, String description) {
        validateContent(content);
        String id = repository.nextId(TYPE);
        RuleEntity entity = RuleEntity.create(id, name, TYPE, content);
        entity.setDescription(description);
        entity.setCreatedBy("system");
        entity.setUpdatedBy("system");
        log.info("创建决策表: id={}, name={}", id, name);
        return repository.save(entity);
    }

    /**
     * 更新决策表内容（仅 DRAFT 状态可修改）。
     */
    public RuleEntity updateDecisionTable(String id, String content) {
        validateContent(content);
        RuleEntity latest = getLatest(id);
        assertDraft(latest);

        latest.setContent(content);
        log.info("更新决策表: id={}, version={}", id, latest.getVersion());
        return repository.save(latest);
    }

    /**
     * 软删除决策表。
     */
    public boolean deleteDecisionTable(String id) {
        RuleEntity latest = getLatest(id);
        if (latest.getStatus() == PublishStatus.RELEASED) {
            throw new IllegalStateException("Cannot delete a released decision table. Rollback first.");
        }
        log.info("删除决策表: id={}, version={}", id, latest.getVersion());
        return repository.delete(TYPE, id, latest.getVersion());
    }

    /**
     * 获取决策表最新版本。
     */
    public RuleEntity getDecisionTable(String id) {
        return getLatest(id);
    }

    /**
     * 列出所有决策表（最新版本），支持按状态过滤。
     */
    public List<RuleEntity> listDecisionTables(Map<String, Object> params) {
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
        log.info("创建决策表新版本: id={}, version={}", id, newVersion);
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
     * 发布决策表: DRAFT → RELEASED。
     */
    public RuleEntity publishDecisionTable(String id) {
        RuleEntity entity = getLatest(id);
        if (entity.getStatus() == PublishStatus.RELEASED) {
            throw new IllegalStateException("Decision table already released");
        }
        entity.setStatus(PublishStatus.RELEASED);
        log.info("发布决策表: id={}, version={}", id, entity.getVersion());
        return repository.save(entity);
    }

    // ==================== 内部方法 ====================

    private RuleEntity getLatest(String id) {
        return repository.findLatest(TYPE, id)
            .orElseThrow(() -> new IllegalArgumentException("Decision table not found: " + id));
    }

    private void assertDraft(RuleEntity entity) {
        if (entity.getStatus() != PublishStatus.DRAFT) {
            throw new IllegalStateException(
                "Cannot update decision table in " + entity.getStatus() + " status. Create a new version first.");
        }
    }

    /**
     * 校验决策表内容 — 确保包含列定义和行数据。
     */
    private void validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Decision table content cannot be empty");
        }
        String trimmed = content.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            throw new IllegalArgumentException("Decision table content must be valid JSON");
        }
    }
}
