package com.credit.platform.engine.core.compiler.model;

/**
 * 规则条件比较运算符。
 * <p>
 * 支持 8 种比较操作，覆盖信贷规则常见场景。
 * </p>
 */
public enum ComparisonOperator {

    /** 大于 */
    GT,
    /** 小于 */
    LT,
    /** 大于等于 */
    GTE,
    /** 小于等于 */
    LTE,
    /** 等于 */
    EQ,
    /** 不等于 */
    NEQ,
    /** 区间判断 (委托给 Aviator between 函数) */
    BETWEEN,
    /** 集合包含 (委托给 Aviator inList 函数) */
    IN;

    /**
     * 从字符串解析运算符，忽略大小写。
     *
     * @param value 运算符字符串（如 "GT", "gte"）
     * @return 对应的运算符枚举
     * @throws IllegalArgumentException 不支持的运算符
     */
    public static ComparisonOperator fromString(String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Comparison operator must not be null or empty");
        }
        return valueOf(value.toUpperCase());
    }
}
