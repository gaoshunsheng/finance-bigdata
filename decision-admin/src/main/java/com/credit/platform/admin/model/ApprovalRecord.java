package com.credit.platform.admin.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 审批记录 — 记录每次审批操作。
 * <p>
 * 支持多级审批: 提交审批 → 审批通过 / 审批驳回。
 * 每次操作生成一条 ApprovalRecord，完整追溯审批历史。
 * </p>
 */
@TableName("approval_record")
public class ApprovalRecord {

    @TableId(type = IdType.INPUT)
    private String recordId;
    /** 关联的规则类型: RULE / SCORECARD / DECISION_TABLE / DECISION_TREE / FLOW / VARIABLE */
    @TableField("target_type")
    private String targetType;
    /** 关联的规则 ID */
    @TableField("target_id")
    private String targetId;
    /** 关联的规则版本 */
    @TableField("target_version")
    private int targetVersion;
    /** 操作类型 */
    @TableField("action")
    private ApprovalAction action;
    /** 操作人 */
    @TableField("operator")
    private String operator;
    /** 审批意见/原因 */
    @TableField("comment")
    private String comment;
    /** 操作时间 */
    @TableField("operated_at")
    private LocalDateTime operatedAt;

    public ApprovalRecord() {
        this.operatedAt = LocalDateTime.now();
    }

    /**
     * 审批操作类型。
     */
    public enum ApprovalAction {
        /** 提交审批 */
        SUBMIT("提交审批"),
        /** 审批通过 */
        APPROVE("审批通过"),
        /** 审批驳回 */
        REJECT("审批驳回"),
        /** 撤回审批 */
        WITHDRAW("撤回审批");

        private final String description;

        ApprovalAction(String description) {
            this.description = description;
        }

        public String getDescription() { return description; }
    }

    /**
     * 创建审批记录。
     */
    public static ApprovalRecord of(String recordId, String targetType, String targetId,
                                     int targetVersion, ApprovalAction action,
                                     String operator, String comment) {
        ApprovalRecord record = new ApprovalRecord();
        record.recordId = Objects.requireNonNull(recordId);
        record.targetType = Objects.requireNonNull(targetType);
        record.targetId = Objects.requireNonNull(targetId);
        record.targetVersion = targetVersion;
        record.action = Objects.requireNonNull(action);
        record.operator = operator;
        record.comment = comment;
        record.operatedAt = LocalDateTime.now();
        return record;
    }

    // ========== Getters & Setters ==========

    public String getRecordId() { return recordId; }
    public void setRecordId(String recordId) { this.recordId = recordId; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }
    public int getTargetVersion() { return targetVersion; }
    public void setTargetVersion(int targetVersion) { this.targetVersion = targetVersion; }
    public ApprovalAction getAction() { return action; }
    public void setAction(ApprovalAction action) { this.action = action; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public LocalDateTime getOperatedAt() { return operatedAt; }
    public void setOperatedAt(LocalDateTime operatedAt) { this.operatedAt = operatedAt; }
}
