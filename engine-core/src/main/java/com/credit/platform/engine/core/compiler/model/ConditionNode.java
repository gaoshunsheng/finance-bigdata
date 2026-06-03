package com.credit.platform.engine.core.compiler.model;

import java.util.Map;

/**
 * 条件表达式 AST 节点基类。
 * <p>
 * 不可变接口，编译时创建，运行时求值。
 * 支持两种子类型：{@link LogicalConditionNode}（逻辑组合）和
 * {@link ComparisonConditionNode}（字段比较）。
 * </p>
 *
 * <pre>
 * // 构建一棵条件树:
 * //   age >= 22 AND age <= 60
 * ConditionNode tree = LogicalConditionNode.and(
 *     new ComparisonConditionNode("age", ComparisonOperator.GTE, 22),
 *     new ComparisonConditionNode("age", ComparisonOperator.LTE, 60)
 * );
 * </pre>
 */
public interface ConditionNode {

    /**
     * 对当前条件节点求值。
     *
     * @param variables 变量上下文（字段名 → 值）
     * @return 求值结果；变量为 null 时返回 {@code false}（null 安全）
     */
    boolean evaluate(Map<String, Object> variables);

    /**
     * 返回该节点引用的所有变量字段名（用于变量预取分析）。
     *
     * @return 字段名集合
     */
    java.util.Set<String> getReferencedFields();
}
