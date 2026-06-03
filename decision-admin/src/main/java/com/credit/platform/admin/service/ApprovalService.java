package com.credit.platform.admin.service;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.credit.platform.admin.model.ApprovalRecord;
import com.credit.platform.admin.model.ApprovalRecord.ApprovalAction;
import com.credit.platform.admin.model.PublishStatus;
import com.credit.platform.admin.model.RuleEntity;

/**
 * 审批服务 — 管理规则发布审批流程。
 * <p>
 * 完整审批流程: 提交审批 → 审批通过 / 审批驳回 / 撤回审批。
 * 每次操作生成 ApprovalRecord，支持完整审批历史追溯。
 * </p>
 */
@Service
public class ApprovalService {

    private final RuleRepository ruleRepository;
    private final ConcurrentHashMap<String, ApprovalRecord> approvalStore = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong(0);

    public ApprovalService(RuleRepository ruleRepository) {
        this.ruleRepository = Objects.requireNonNull(ruleRepository);
    }

    /**
     * 提交审批 — 将规则状态从 TESTING 推进到 PENDING_REVIEW，记录审批提交记录。
     *
     * @param type    规则类型
     * @param id      规则 ID
     * @param submitter 提交人
     * @param comment   提交说明
     * @return 更新后的 RuleEntity
     */
    public RuleEntity submitForApproval(String type, String id, String submitter, String comment) {
        RuleEntity entity = ruleRepository.findLatest(type, id)
            .orElseThrow(() -> new IllegalArgumentException(type + " not found: " + id));

        if (entity.getStatus() != PublishStatus.TESTING) {
            throw new IllegalStateException(
                "Cannot submit for approval: current status is " + entity.getStatus()
                    + ", expected TESTING");
        }

        // 推进状态
        entity.setStatus(PublishStatus.PENDING_REVIEW);
        entity.setUpdatedBy(submitter);
        ruleRepository.save(entity);

        // 记录审批提交
        ApprovalRecord record = ApprovalRecord.of(
            generateRecordId(), type, id, entity.getVersion(),
            ApprovalAction.SUBMIT, submitter, comment);
        approvalStore.put(record.getRecordId(), record);

        return entity;
    }

    /**
     * 审批通过 — 将规则状态从 PENDING_REVIEW 推进到 APPROVED。
     *
     * @param type     规则类型
     * @param id       规则 ID
     * @param approver 审批人
     * @param comment  审批意见
     * @return 更新后的 RuleEntity
     */
    public RuleEntity approve(String type, String id, String approver, String comment) {
        RuleEntity entity = ruleRepository.findLatest(type, id)
            .orElseThrow(() -> new IllegalArgumentException(type + " not found: " + id));

        if (entity.getStatus() != PublishStatus.PENDING_REVIEW) {
            throw new IllegalStateException(
                "Cannot approve: current status is " + entity.getStatus()
                    + ", expected PENDING_REVIEW");
        }

        entity.setStatus(PublishStatus.APPROVED);
        entity.setUpdatedBy(approver);
        entity.getAttributes().put("approvedBy", approver);
        entity.getAttributes().put("approvedAt", java.time.LocalDateTime.now().toString());
        ruleRepository.save(entity);

        ApprovalRecord record = ApprovalRecord.of(
            generateRecordId(), type, id, entity.getVersion(),
            ApprovalAction.APPROVE, approver, comment);
        approvalStore.put(record.getRecordId(), record);

        return entity;
    }

    /**
     * 审批驳回 — 将规则状态回退到 DRAFT。
     *
     * @param type     规则类型
     * @param id       规则 ID
     * @param rejecter 驳回人
     * @param reason   驳回原因
     * @return 更新后的 RuleEntity
     */
    public RuleEntity reject(String type, String id, String rejecter, String reason) {
        RuleEntity entity = ruleRepository.findLatest(type, id)
            .orElseThrow(() -> new IllegalArgumentException(type + " not found: " + id));

        if (entity.getStatus() != PublishStatus.PENDING_REVIEW) {
            throw new IllegalStateException(
                "Cannot reject: current status is " + entity.getStatus()
                    + ", expected PENDING_REVIEW");
        }

        entity.setStatus(PublishStatus.DRAFT);
        entity.setUpdatedBy(rejecter);
        entity.getAttributes().put("rejectReason", reason != null ? reason : "");
        entity.getAttributes().put("rejectedBy", rejecter);
        ruleRepository.save(entity);

        ApprovalRecord record = ApprovalRecord.of(
            generateRecordId(), type, id, entity.getVersion(),
            ApprovalAction.REJECT, rejecter, reason);
        approvalStore.put(record.getRecordId(), record);

        return entity;
    }

    /**
     * 撤回审批 — 将规则状态从 PENDING_REVIEW 回退到 TESTING。
     *
     * @param type      规则类型
     * @param id        规则 ID
     * @param withdrawer 撤回人
     * @param reason     撤回原因
     * @return 更新后的 RuleEntity
     */
    public RuleEntity withdraw(String type, String id, String withdrawer, String reason) {
        RuleEntity entity = ruleRepository.findLatest(type, id)
            .orElseThrow(() -> new IllegalArgumentException(type + " not found: " + id));

        if (entity.getStatus() != PublishStatus.PENDING_REVIEW) {
            throw new IllegalStateException(
                "Cannot withdraw: current status is " + entity.getStatus()
                    + ", expected PENDING_REVIEW");
        }

        entity.setStatus(PublishStatus.TESTING);
        entity.setUpdatedBy(withdrawer);
        ruleRepository.save(entity);

        ApprovalRecord record = ApprovalRecord.of(
            generateRecordId(), type, id, entity.getVersion(),
            ApprovalAction.WITHDRAW, withdrawer, reason);
        approvalStore.put(record.getRecordId(), record);

        return entity;
    }

    /**
     * 查询指定规则的审批历史。
     *
     * @param type 规则类型
     * @param id   规则 ID
     * @return 审批记录列表（按时间倒序）
     */
    public List<ApprovalRecord> getApprovalHistory(String type, String id) {
        return approvalStore.values().stream()
            .filter(r -> type.equals(r.getTargetType()) && id.equals(r.getTargetId()))
            .sorted((a, b) -> b.getOperatedAt().compareTo(a.getOperatedAt()))
            .collect(Collectors.toList());
    }

    /**
     * 查询指定规则指定版本的审批历史。
     */
    public List<ApprovalRecord> getApprovalHistory(String type, String id, int version) {
        return approvalStore.values().stream()
            .filter(r -> type.equals(r.getTargetType())
                && id.equals(r.getTargetId())
                && r.getTargetVersion() == version)
            .sorted((a, b) -> b.getOperatedAt().compareTo(a.getOperatedAt()))
            .collect(Collectors.toList());
    }

    /**
     * 查询待我审批的列表。
     *
     * @param approver 审批人
     * @return 待审批的规则实体列表
     */
    public List<RuleEntity> getPendingApprovals(String approver) {
        return ruleRepository.listByType("RULE").stream()
            .filter(e -> e.getStatus() == PublishStatus.PENDING_REVIEW)
            .collect(Collectors.toList());
    }

    private String generateRecordId() {
        return "apr-" + System.currentTimeMillis() + "-" + idSequence.incrementAndGet();
    }
}
