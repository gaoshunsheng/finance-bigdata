package com.credit.platform.engine.core.compiler.model;

/**
 * 规则集命中策略。
 * <p>
 * 决定规则集内多条规则命中后的处理方式。
 * </p>
 */
public enum HitPolicy {

    /** 首条命中即返回 — 适用于黑名单/准入检查 */
    FIRST_HIT,
    /** 全量执行，收集所有命中结果 — 适用于全面检查 */
    ALL,
    /** 按优先级排序执行，命中即停 — 适用于多级分类 */
    PRIORITY;

    /**
     * 从字符串解析命中策略，忽略大小写。
     *
     * @param value 策略字符串
     * @return 对应的命中策略枚举
     * @throws IllegalArgumentException 不支持的策略
     */
    public static HitPolicy fromString(String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Hit policy must not be null or empty");
        }
        return valueOf(value.toUpperCase());
    }
}
