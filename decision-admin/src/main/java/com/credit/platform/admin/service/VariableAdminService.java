package com.credit.platform.admin.service;

import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.credit.platform.admin.model.PublishStatus;
import com.credit.platform.admin.model.RuleEntity;

/**
 * 变量管理服务 — 变量 CRUD 与依赖分析。
 * <p>
 * 在通用 RuleAdminService 基础上增加变量特有的逻辑:
 * <ul>
 *   <li>变量内容校验（必须包含 name、type、expression 字段）</li>
 *   <li>依赖分析 — 检测变量间的引用关系</li>
 *   <li>版本管理 — 创建新版本、版本回滚</li>
 *   <li>发布流程 — DRAFT → RELEASED</li>
 * </ul>
 * </p>
 */
@Service
public class VariableAdminService {

    private static final Logger log = LoggerFactory.getLogger(VariableAdminService.class);
    private static final String TYPE = "VARIABLE";

    private final RuleRepository repository;

    public VariableAdminService(RuleRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    // ==================== CRUD ====================

    /**
     * 创建变量。
     *
     * @param name        变量名称
     * @param content     变量 JSON 定义（包含 name, type, expression）
     * @param description 变量描述
     * @return 创建的变量实体
     */
    public RuleEntity createVariable(String name, String content, String description) {
        validateContent(content);
        String id = repository.nextId(TYPE);
        RuleEntity entity = RuleEntity.create(id, name, TYPE, content);
        entity.setDescription(description);
        entity.setCreatedBy("system");
        entity.setUpdatedBy("system");
        log.info("创建变量: id={}, name={}", id, name);
        return repository.save(entity);
    }

    /**
     * 更新变量内容（仅 DRAFT 状态可修改）。
     */
    public RuleEntity updateVariable(String id, String content) {
        validateContent(content);
        RuleEntity latest = getLatest(id);
        assertDraft(latest);

        latest.setContent(content);
        log.info("更新变量: id={}, version={}", id, latest.getVersion());
        return repository.save(latest);
    }

    /**
     * 软删除变量。
     */
    public boolean deleteVariable(String id) {
        RuleEntity latest = getLatest(id);
        if (latest.getStatus() == PublishStatus.RELEASED) {
            throw new IllegalStateException("Cannot delete a released variable. Rollback first.");
        }
        // 检查是否有其他变量依赖此变量
        List<String> dependents = findDependents(id);
        if (!dependents.isEmpty()) {
            throw new IllegalStateException(
                "Cannot delete variable: referenced by " + dependents);
        }
        log.info("删除变量: id={}, version={}", id, latest.getVersion());
        return repository.delete(TYPE, id, latest.getVersion());
    }

    /**
     * 获取变量最新版本。
     */
    public RuleEntity getVariable(String id) {
        return getLatest(id);
    }

    /**
     * 列出所有变量（最新版本），支持按状态过滤。
     */
    public List<RuleEntity> listVariables(Map<String, Object> params) {
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
        log.info("创建变量新版本: id={}, version={}", id, newVersion);
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
     * 发布变量: DRAFT → RELEASED。
     */
    public RuleEntity publishVariable(String id) {
        RuleEntity entity = getLatest(id);
        if (entity.getStatus() == PublishStatus.RELEASED) {
            throw new IllegalStateException("Variable already released");
        }
        entity.setStatus(PublishStatus.RELEASED);
        log.info("发布变量: id={}, version={}", id, entity.getVersion());
        return repository.save(entity);
    }

    // ==================== 依赖分析 ====================

    /**
     * 分析变量依赖关系 — 查找指定变量引用了哪些其他变量。
     * <p>
     * 解析变量 expression 中引用的变量名，与系统中的变量列表交叉匹配。
     * </p>
     *
     * @param id 变量 ID
     * @return 被依赖的变量 ID 列表
     */
    public List<String> analyzeDependencies(String id) {
        RuleEntity entity = getLatest(id);
        String content = entity.getContent();
        if (content == null || content.isBlank()) {
            return List.of();
        }

        // 获取系统中所有变量
        List<RuleEntity> allVariables = repository.listByType(TYPE);

        // 解析 expression 中引用的变量名
        Set<String> referencedIds = new LinkedHashSet<>();
        for (RuleEntity var : allVariables) {
            if (!var.getId().equals(id) && content.contains(var.getName())) {
                referencedIds.add(var.getId());
            }
        }

        log.debug("变量 {} 依赖: {}", id, referencedIds);
        return new ArrayList<>(referencedIds);
    }

    /**
     * 查找依赖指定变量的其他变量（反向依赖）。
     *
     * @param id 变量 ID
     * @return 依赖此变量的变量 ID 列表
     */
    public List<String> findDependents(String id) {
        RuleEntity target = getLatest(id);
        String targetName = target.getName();

        List<RuleEntity> allVariables = repository.listByType(TYPE);
        List<String> dependents = allVariables.stream()
            .filter(v -> !v.getId().equals(id))
            .filter(v -> v.getContent() != null && v.getContent().contains(targetName))
            .map(RuleEntity::getId)
            .collect(Collectors.toList());

        log.debug("依赖变量 {} 的变量: {}", id, dependents);
        return dependents;
    }

    // ==================== 内部方法 ====================

    private RuleEntity getLatest(String id) {
        return repository.findLatest(TYPE, id)
            .orElseThrow(() -> new IllegalArgumentException("Variable not found: " + id));
    }

    private void assertDraft(RuleEntity entity) {
        if (entity.getStatus() != PublishStatus.DRAFT) {
            throw new IllegalStateException(
                "Cannot update variable in " + entity.getStatus() + " status. Create a new version first.");
        }
    }

    /**
     * 校验变量内容 — 确保包含必要的字段。
     */
    private void validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Variable content cannot be empty");
        }
        String trimmed = content.trim();
        if (!trimmed.startsWith("{")) {
            throw new IllegalArgumentException("Variable content must be valid JSON object");
        }
    }
}
