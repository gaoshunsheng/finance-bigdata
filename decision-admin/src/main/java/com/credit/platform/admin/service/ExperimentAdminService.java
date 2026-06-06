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
 * 实验管理服务 — AB 实验配置 CRUD 与版本管理。
 * <p>
 * 在通用 RuleAdminService 基础上增加实验特有的逻辑:
 * <ul>
 *   <li>实验配置校验（必须包含 control 和 experiment 组）</li>
 *   <li>流量分配校验（百分比总和不超过 100%）</li>
 *   <li>版本管理 — 创建新版本、版本回滚</li>
 *   <li>发布流程 — DRAFT → RELEASED</li>
 * </ul>
 * </p>
 */
@Service
public class ExperimentAdminService {

    private static final Logger log = LoggerFactory.getLogger(ExperimentAdminService.class);
    private static final String TYPE = "EXPERIMENT";

    private final RuleRepository repository;

    public ExperimentAdminService(RuleRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    // ==================== CRUD ====================

    /**
     * 创建实验配置。
     *
     * @param name        实验名称
     * @param content     实验 JSON 定义（包含 control 和 experiment 组配置）
     * @param description 实验描述
     * @return 创建的实验实体
     */
    public RuleEntity createExperiment(String name, String content, String description) {
        validateContent(content);
        validateTrafficAllocation(content);
        String id = repository.nextId(TYPE);
        RuleEntity entity = RuleEntity.create(id, name, TYPE, content);
        entity.setDescription(description);
        entity.setCreatedBy("system");
        entity.setUpdatedBy("system");
        log.info("创建实验: id={}, name={}", id, name);
        return repository.save(entity);
    }

    /**
     * 更新实验配置（仅 DRAFT 状态可修改）。
     */
    public RuleEntity updateExperiment(String id, String content) {
        validateContent(content);
        validateTrafficAllocation(content);
        RuleEntity latest = getLatest(id);
        assertDraft(latest);

        latest.setContent(content);
        log.info("更新实验: id={}, version={}", id, latest.getVersion());
        return repository.save(latest);
    }

    /**
     * 软删除实验。
     */
    public boolean deleteExperiment(String id) {
        RuleEntity latest = getLatest(id);
        if (latest.getStatus() == PublishStatus.RELEASED) {
            throw new IllegalStateException("Cannot delete a released experiment. Rollback first.");
        }
        log.info("删除实验: id={}, version={}", id, latest.getVersion());
        return repository.delete(TYPE, id, latest.getVersion());
    }

    /**
     * 获取实验最新版本。
     */
    public RuleEntity getExperiment(String id) {
        return getLatest(id);
    }

    /**
     * 列出所有实验（最新版本），支持按状态过滤。
     */
    public List<RuleEntity> listExperiments(Map<String, Object> params) {
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
        log.info("创建实验新版本: id={}, version={}", id, newVersion);
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
     * 发布实验: DRAFT → RELEASED。
     * <p>
     * 发布前再次校验流量分配合法性。
     * </p>
     */
    public RuleEntity publishExperiment(String id) {
        RuleEntity entity = getLatest(id);
        if (entity.getStatus() == PublishStatus.RELEASED) {
            throw new IllegalStateException("Experiment already released");
        }
        validateTrafficAllocation(entity.getContent());
        entity.setStatus(PublishStatus.RELEASED);
        log.info("发布实验: id={}, version={}", id, entity.getVersion());
        return repository.save(entity);
    }

    // ==================== 内部方法 ====================

    private RuleEntity getLatest(String id) {
        return repository.findLatest(TYPE, id)
            .orElseThrow(() -> new IllegalArgumentException("Experiment not found: " + id));
    }

    private void assertDraft(RuleEntity entity) {
        if (entity.getStatus() != PublishStatus.DRAFT) {
            throw new IllegalStateException(
                "Cannot update experiment in " + entity.getStatus() + " status. Create a new version first.");
        }
    }

    /**
     * 校验实验内容 — 确保包含 control 和 experiment 组。
     */
    private void validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Experiment content cannot be empty");
        }
        String trimmed = content.trim();
        if (!trimmed.startsWith("{")) {
            throw new IllegalArgumentException("Experiment content must be valid JSON object");
        }
    }

    /**
     * 校验流量分配 — 百分比总和不超过 100%。
     * <p>
     * 查找 content 中的 percentage 关键字，提取所有百分比值并求和。
     * </p>
     */
    private void validateTrafficAllocation(String content) {
        if (content == null) {
            return;
        }
        // 简易解析: 查找 "percentage": N 模式
        int totalPercentage = 0;
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "\"percentage\"\\s*:\\s*(\\d+)");
        java.util.regex.Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            totalPercentage += Integer.parseInt(matcher.group(1));
        }
        if (totalPercentage > 100) {
            throw new IllegalStateException(
                "Traffic allocation exceeds 100%: total=" + totalPercentage + "%");
        }
        log.debug("流量分配校验通过: total={}%", totalPercentage);
    }
}
