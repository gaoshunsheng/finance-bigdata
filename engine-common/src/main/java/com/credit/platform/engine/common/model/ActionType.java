package com.credit.platform.engine.common.model;

/**
 * 规则动作类型枚举。
 * <p>
 * 定义规则命中后可执行的动作类型。
 * </p>
 */
public enum ActionType {

    /** 拒绝 */
    REJECT,

    /** 通过 */
    PASS,

    /** 人工审核 */
    REVIEW,

    /** 人工介入 */
    MANUAL,

    /** 赋值 */
    ASSIGN,

    /** 评分 */
    SCORE
}
