package com.credit.platform.engine.core.compiler.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 逻辑组合条件节点 — AND / OR / NOT。
 * <p>
 * 不可变对象。NOT 仅接受一个操作数；AND / OR 支持多个操作数。
 * 短路求值：AND 遇 false 即停，OR 遇 true 即停。
 * </p>
 */
public final class LogicalConditionNode implements ConditionNode {

    private final LogicalOperator operator;
    private final List<ConditionNode> operands;

    LogicalConditionNode(LogicalOperator operator, List<ConditionNode> operands) {
        this.operator = operator;
        this.operands = Collections.unmodifiableList(new ArrayList<>(operands));
    }

    /**
     * 创建 AND 节点。
     */
    public static LogicalConditionNode and(ConditionNode... nodes) {
        return new LogicalConditionNode(LogicalOperator.AND, Arrays.asList(nodes));
    }

    /**
     * 创建 OR 节点。
     */
    public static LogicalConditionNode or(ConditionNode... nodes) {
        return new LogicalConditionNode(LogicalOperator.OR, Arrays.asList(nodes));
    }

    /**
     * 创建 NOT 节点。
     */
    public static LogicalConditionNode not(ConditionNode node) {
        return new LogicalConditionNode(LogicalOperator.NOT, Collections.singletonList(node));
    }

    /**
     * 使用逻辑运算符和操作数列表创建节点。
     *
     * @param operator 逻辑运算符
     * @param operands 子条件节点列表
     * @return 逻辑组合节点
     */
    public static LogicalConditionNode create(LogicalOperator operator, List<ConditionNode> operands) {
        return new LogicalConditionNode(operator, operands);
    }

    public LogicalOperator getOperator() { return operator; }
    public List<ConditionNode> getOperands() { return operands; }

    @Override
    public boolean evaluate(Map<String, Object> variables) {
        switch (operator) {
            case AND:
                // 短路：任一为 false 即返回 false
                for (ConditionNode operand : operands) {
                    if (!operand.evaluate(variables)) {
                        return false;
                    }
                }
                return true;

            case OR:
                // 短路：任一为 true 即返回 true
                for (ConditionNode operand : operands) {
                    if (operand.evaluate(variables)) {
                        return true;
                    }
                }
                return false;

            case NOT:
                return !operands.get(0).evaluate(variables);

            default:
                return false;
        }
    }

    @Override
    public Set<String> getReferencedFields() {
        Set<String> fields = new HashSet<>();
        for (ConditionNode operand : operands) {
            fields.addAll(operand.getReferencedFields());
        }
        return fields;
    }

    @Override
    public String toString() {
        return "LogicalNode{" + operator + ", operands=" + operands.size() + '}';
    }
}
