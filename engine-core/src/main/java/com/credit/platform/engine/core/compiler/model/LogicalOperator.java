package com.credit.platform.engine.core.compiler.model;

/**
 * 条件逻辑运算符。
 * <p>
 * 用于组合多个条件节点的逻辑关系。
 * </p>
 */
public enum LogicalOperator {

    /** 逻辑与 — 所有子条件都为 true 时结果为 true */
    AND,
    /** 逻辑或 — 任一子条件为 true 时结果为 true */
    OR,
    /** 逻辑非 — 对单个子条件取反 */
    NOT;

    /**
     * 从字符串解析逻辑运算符，忽略大小写。
     *
     * @param value 运算符字符串（如 "AND", "or"）
     * @return 对应的逻辑运算符枚举
     * @throws IllegalArgumentException 不支持的运算符
     */
    public static LogicalOperator fromString(String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Logical operator must not be null or empty");
        }
        return valueOf(value.toUpperCase());
    }
}
