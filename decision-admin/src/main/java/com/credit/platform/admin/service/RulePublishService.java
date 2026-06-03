package com.credit.platform.admin.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.credit.platform.admin.model.GrayscaleConfig;
import com.credit.platform.admin.model.PublishStatus;
import com.credit.platform.admin.model.RuleEntity;
import com.credit.platform.admin.model.VersionDiff;

/**
 * 规则发布服务 — 编排完整发布生命周期。
 * <p>
 * 生命周期: DRAFT → TESTING → PENDING_REVIEW → APPROVED → GRAYSCALE → RELEASED
 * <ul>
 *   <li>审批流程由 {@link ApprovalService} 管理</li>
 *   <li>灰度发布由 {@link GrayscalePublishService} 管理</li>
 *   <li>版本对比由 {@link VersionDiffService} 管理</li>
 * </ul>
 * </p>
 */
@Service
public class RulePublishService {

    private final RuleRepository repository;
    private final ApprovalService approvalService;
    private final GrayscalePublishService grayscaleService;
    private final VersionDiffService diffService;

    public RulePublishService(RuleRepository repository,
                               ApprovalService approvalService,
                               GrayscalePublishService grayscaleService,
                               VersionDiffService diffService) {
        this.repository = Objects.requireNonNull(repository);
        this.approvalService = Objects.requireNonNull(approvalService);
        this.grayscaleService = Objects.requireNonNull(grayscaleService);
        this.diffService = Objects.requireNonNull(diffService);
    }

    // ==================== 发布流程 ====================

    /**
     * 完整发布流程 — 一步到位: 提交测试 → 提交审批 → 审批通过 → 开始灰度 → 全量发布。
     * <p>
     * 仅用于自动化测试或紧急发布场景。
     * </p>
     */
    public RuleEntity fullPublish(String type, String id, String operator) {
        // DRAFT → TESTING
        promoteToTesting(type, id, operator);
        // TESTING → PENDING_REVIEW
        submitForApproval(type, id, operator, "Full publish by " + operator);
        // PENDING_REVIEW → APPROVED
        approve(type, id, operator, "Auto-approved in full publish");
        // APPROVED → GRAYSCALE (100%)
        startGrayscale(type, id, 100, operator);
        // GRAYSCALE → RELEASED (100% triggers auto-release)
        return repository.findLatest(type, id).orElseThrow();
    }

    /**
     * 推进到测试状态: DRAFT → TESTING。
     */
    public RuleEntity promoteToTesting(String type, String id, String operator) {
        RuleEntity entity = getLatest(type, id);
        assertStatus(entity, PublishStatus.DRAFT, "promote to TESTING");
        entity.setStatus(PublishStatus.TESTING);
        entity.setUpdatedBy(operator);
        return repository.save(entity);
    }

    /**
     * 提交审批: TESTING → PENDING_REVIEW。
     */
    public RuleEntity submitForApproval(String type, String id, String submitter, String comment) {
        return approvalService.submitForApproval(type, id, submitter, comment);
    }

    /**
     * 审批通过: PENDING_REVIEW → APPROVED。
     */
    public RuleEntity approve(String type, String id, String approver, String comment) {
        return approvalService.approve(type, id, approver, comment);
    }

    /**
     * 审批驳回: PENDING_REVIEW → DRAFT。
     */
    public RuleEntity reject(String type, String id, String rejecter, String reason) {
        return approvalService.reject(type, id, rejecter, reason);
    }

    /**
     * 撤回审批: PENDING_REVIEW → TESTING。
     */
    public RuleEntity withdrawApproval(String type, String id, String withdrawer, String reason) {
        return approvalService.withdraw(type, id, withdrawer, reason);
    }

    /**
     * 开始灰度发布: APPROVED → GRAYSCALE。
     */
    public GrayscaleConfig startGrayscale(String type, String id, int percentage, String operator) {
        return grayscaleService.startGrayscale(type, id, percentage, operator);
    }

    /**
     * 按阶梯提升灰度。
     */
    public GrayscaleConfig rampUpGrayscale(String type, String id, String operator) {
        return grayscaleService.rampUp(type, id, operator);
    }

    /**
     * 调整灰度百分比。
     */
    public GrayscaleConfig adjustGrayscale(String type, String id, int percentage, String operator) {
        return grayscaleService.adjustGrayscale(type, id, percentage, operator);
    }

    /**
     * 暂停灰度。
     */
    public GrayscaleConfig pauseGrayscale(String type, String id, String operator) {
        return grayscaleService.pause(type, id, operator);
    }

    /**
     * 恢复灰度。
     */
    public GrayscaleConfig resumeGrayscale(String type, String id, String operator) {
        return grayscaleService.resume(type, id, operator);
    }

    // ==================== 回滚 ====================

    /**
     * 一键回滚 — 回退到指定历史版本。
     * <p>
     * 创建新版本副本，继承目标版本内容，状态设为 DRAFT。
     * 如果当前版本在灰度中，先回滚灰度。
     * </p>
     *
     * @param type          规则类型
     * @param id            规则 ID
     * @param targetVersion 目标版本号
     * @param operator      操作人
     * @return 新版本的 RuleEntity
     */
    public RuleEntity rollback(String type, String id, int targetVersion, String operator) {
        RuleEntity current = getLatest(type, id);

        // 如果在灰度中，先回滚灰度
        if (current.getStatus() == PublishStatus.GRAYSCALE) {
            grayscaleService.rollbackGrayscale(type, id, operator);
        }

        // 获取目标版本
        RuleEntity target = repository.find(type, id, targetVersion)
            .orElseThrow(() -> new IllegalArgumentException(
                type + ":" + id + " v" + targetVersion + " not found"));

        // 创建新版本
        RuleEntity newEntity = target.newVersion(current.getVersion() + 1);
        newEntity.setStatus(PublishStatus.DRAFT);
        newEntity.setUpdatedBy(operator);
        newEntity.getAttributes().put("rollbackFrom", targetVersion);
        newEntity.getAttributes().put("rollbackBy", operator);
        newEntity.getAttributes().put("rollbackAt", java.time.LocalDateTime.now().toString());

        return repository.save(newEntity);
    }

    /**
     * 灰度回滚 — 仅回滚灰度，将规则回到 APPROVED 状态。
     */
    public RuleEntity rollbackGrayscale(String type, String id, String operator) {
        return grayscaleService.rollbackGrayscale(type, id, operator);
    }

    // ==================== 版本对比 ====================

    /**
     * 版本对比。
     */
    public VersionDiff diff(String type, String id, int sourceVersion, int targetVersion) {
        return diffService.diff(type, id, sourceVersion, targetVersion);
    }

    /**
     * 最新两个版本对比。
     */
    public VersionDiff diffLatest(String type, String id) {
        return diffService.diffLatest(type, id);
    }

    // ==================== 查询 ====================

    /**
     * 获取发布历史 — 所有版本列表。
     */
    public List<RuleEntity> getPublishHistory(String type, String id) {
        return repository.findVersions(type, id);
    }

    /**
     * 获取灰度配置。
     */
    public GrayscaleConfig getGrayscaleConfig(String type, String id) {
        return grayscaleService.getGrayscaleConfig(type, id);
    }

    /**
     * 获取审批历史。
     */
    public List<?> getApprovalHistory(String type, String id) {
        return approvalService.getApprovalHistory(type, id);
    }

    /**
     * 获取待审批列表。
     */
    public List<RuleEntity> getPendingApprovals() {
        return approvalService.getPendingApprovals("all");
    }

    // ==================== 内部方法 ====================

    private RuleEntity getLatest(String type, String id) {
        return repository.findLatest(type, id)
            .orElseThrow(() -> new IllegalArgumentException(type + " not found: " + id));
    }

    private void assertStatus(RuleEntity entity, PublishStatus expected, String operation) {
        if (entity.getStatus() != expected) {
            throw new IllegalStateException(
                "Cannot " + operation + ": current status is " + entity.getStatus()
                    + ", expected " + expected);
        }
    }
}
