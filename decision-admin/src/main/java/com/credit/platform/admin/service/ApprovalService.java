package com.credit.platform.admin.service;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.credit.platform.admin.mapper.ApprovalRecordMapper;
import com.credit.platform.admin.model.ApprovalRecord;
import com.credit.platform.admin.model.ApprovalRecord.ApprovalAction;
import com.credit.platform.admin.model.PublishStatus;
import com.credit.platform.admin.model.RuleEntity;

/**
 * 审批服务 — 管理规则发布审批流程。
 * <p>
 * 完整审批流程: 提交审批 → 审批通过 / 审批驳回 / 撤回审批。
 * 每次操作生成 ApprovalRecord，支持完整审批历史追溯。
 * 持久化到 approval_record 表。
 * </p>
 */
@Service
public class ApprovalService {

    private final RuleRepository ruleRepository;
    private final ApprovalRecordMapper approvalRecordMapper;
    private final AtomicLong idSequence = new AtomicLong(0);

    public ApprovalService(RuleRepository ruleRepository, ApprovalRecordMapper approvalRecordMapper) {
        this.ruleRepository = Objects.requireNonNull(ruleRepository);
        this.approvalRecordMapper = approvalRecordMapper;
    }

    /**
     * 提交审批 — 将规则状态从 TESTING 推进到 PENDING_REVIEW，记录审批提交记录。
     */
    public RuleEntity submitForApproval(String type, String id, String submitter, String comment) {
        RuleEntity entity = ruleRepository.findLatest(type, id)
            .orElseThrow(() -> new IllegalArgumentException(type + " not found: " + id));

        if (entity.getStatus() != PublishStatus.TESTING) {
            throw new IllegalStateException(
                "Cannot submit for approval: current status is " + entity.getStatus()
                    + ", expected TESTING");
        }

        entity.setStatus(PublishStatus.PENDING_REVIEW);
        entity.setUpdatedBy(submitter);
        ruleRepository.save(entity);

        ApprovalRecord record = ApprovalRecord.of(
            generateRecordId(), type, id, entity.getVersion(),
            ApprovalAction.SUBMIT, submitter, comment);
        approvalRecordMapper.insert(record);

        return entity;
    }

    /**
     * 审批通过 — 将规则状态从 PENDING_REVIEW 推进到 APPROVED。
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
        approvalRecordMapper.insert(record);

        return entity;
    }

    /**
     * 审批驳回 — 将规则状态回退到 DRAFT。
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
        approvalRecordMapper.insert(record);

        return entity;
    }

    /**
     * 撤回审批 — 将规则状态从 PENDING_REVIEW 回退到 TESTING。
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
        approvalRecordMapper.insert(record);

        return entity;
    }

    /**
     * 查询指定规则的审批历史。
     */
    public List<ApprovalRecord> getApprovalHistory(String type, String id) {
        return approvalRecordMapper.findByTarget(type, id);
    }

    /**
     * 查询指定规则指定版本的审批历史。
     */
    public List<ApprovalRecord> getApprovalHistory(String type, String id, int version) {
        return approvalRecordMapper.findByTargetAndVersion(type, id, version);
    }

    /**
     * 查询待我审批的列表。
     */
    public List<RuleEntity> getPendingApprovals(String approver) {
        // 跨所有类型查询 PENDING_REVIEW 状态
        List<RuleEntity> pending = new java.util.ArrayList<>();
        for (String type : List.of("RULE", "SCORECARD", "DECISION_TABLE", "DECISION_TREE", "FLOW", "VARIABLE")) {
            pending.addAll(ruleRepository.findByTypeAndStatus(type, PublishStatus.PENDING_REVIEW));
        }
        return pending;
    }

    private String generateRecordId() {
        return "apr-" + System.currentTimeMillis() + "-" + idSequence.incrementAndGet();
    }
}
