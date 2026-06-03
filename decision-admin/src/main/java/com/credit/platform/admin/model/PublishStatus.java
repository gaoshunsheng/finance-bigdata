package com.credit.platform.admin.model;

/**
 * 规则版本发布状态枚举。
 */
public enum PublishStatus {

    /** 草稿 */
    DRAFT("草稿"),
    /** 测试中 */
    TESTING("测试中"),
    /** 待审批 */
    PENDING_REVIEW("待审批"),
    /** 审批通过 */
    APPROVED("已审批"),
    /** 灰度发布中 */
    GRAYSCALE("灰度中"),
    /** 已发布 */
    RELEASED("已发布"),
    /** 已回滚 */
    ROLLED_BACK("已回滚");

    private final String description;

    PublishStatus(String description) {
        this.description = description;
    }

    public String getDescription() { return description; }
}
