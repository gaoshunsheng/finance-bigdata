package com.credit.platform.engine.core.compiler.model;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 字段比较条件节点 — {@code field op value}。
 * <p>
 * 不可变对象。支持 8 种比较运算符，null 安全（null 参与比较返回 false）。
 * Number 类型统一转 double 比较，避免 Integer/Long 类型不兼容。
 * </p>
 *
 * <pre>
 * // age >= 22
 * ConditionNode node = new ComparisonConditionNode("age", ComparisonOperator.GTE, 22);
 * </pre>
 */
public final class ComparisonConditionNode implements ConditionNode {

    private final String field;
    private final ComparisonOperator operator;
    private final Object expectedValue;

    public ComparisonConditionNode(String field, ComparisonOperator operator, Object expectedValue) {
        this.field = Objects.requireNonNull(field, "field must not be null");
        this.operator = Objects.requireNonNull(operator, "operator must not be null");
        this.expectedValue = expectedValue;
    }

    public String getField() { return field; }
    public ComparisonOperator getOperator() { return operator; }
    public Object getExpectedValue() { return expectedValue; }

    @Override
    public boolean evaluate(Map<String, Object> variables) {
        Object actual = variables.get(field);

        // null 安全：字段值或期望值为 null 时返回 false
        if (actual == null) {
            return false;
        }

        switch (operator) {
            case GT:  return compareNumbers(actual, expectedValue) > 0;
            case LT:  return compareNumbers(actual, expectedValue) < 0;
            case GTE: return compareNumbers(actual, expectedValue) >= 0;
            case LTE: return compareNumbers(actual, expectedValue) <= 0;
            case EQ:  return equalsSafe(actual, expectedValue);
            case NEQ: return !equalsSafe(actual, expectedValue);
            case BETWEEN:
                return evaluateBetween(actual, expectedValue);
            case IN:
                return evaluateIn(actual, expectedValue);
            default:
                return false;
        }
    }

    /**
     * Number 类型统一转 double 比较，避免 Integer/Long 类型不兼容。
     * 非 Number 类型按 Comparable 自然比较。
     */
    @SuppressWarnings("unchecked")
    private int compareNumbers(Object actual, Object expected) {
        if (expected == null) {
            return 1; // null expected 视为不匹配
        }
        try {
            if (actual instanceof Number && expected instanceof Number) {
                return Double.compare(
                    ((Number) actual).doubleValue(),
                    ((Number) expected).doubleValue()
                );
            }
            // 类型不匹配时直接返回不匹配，避免 ClassCastException
            if (!actual.getClass().isInstance(expected) && !expected.getClass().isInstance(actual)) {
                return 1;
            }
            return ((Comparable<Object>) actual).compareTo(expected);
        } catch (ClassCastException e) {
            return 1; // 类型不兼容视为不匹配
        }
    }

    /**
     * 相等比较，支持 Number 跨类型比较。
     */
    private boolean equalsSafe(Object actual, Object expected) {
        if (expected == null) {
            return false;
        }
        if (actual instanceof Number && expected instanceof Number) {
            return Double.compare(
                ((Number) actual).doubleValue(),
                ((Number) expected).doubleValue()
            ) == 0;
        }
        return actual.equals(expected);
    }

    /**
     * BETWEEN: expectedValue 为 Object[]{min, max}。
     */
    private boolean evaluateBetween(Object actual, Object range) {
        if (!(range instanceof Object[])) {
            return false;
        }
        Object[] bounds = (Object[]) range;
        if (bounds.length != 2) {
            return false;
        }
        // null 边界表示无边界
        boolean aboveMin = (bounds[0] == null) || compareNumbers(actual, bounds[0]) >= 0;
        boolean belowMax = (bounds[1] == null) || compareNumbers(actual, bounds[1]) <= 0;
        return aboveMin && belowMax;
    }

    /**
     * IN: expectedValue 为 Collection 或 Object[]。
     */
    private boolean evaluateIn(Object actual, Object collection) {
        if (collection instanceof Collection) {
            for (Object item : (Collection<?>) collection) {
                if (equalsSafe(actual, item)) {
                    return true;
                }
            }
            return false;
        }
        if (collection instanceof Object[]) {
            for (Object item : (Object[]) collection) {
                if (equalsSafe(actual, item)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public Set<String> getReferencedFields() {
        return Collections.singleton(field);
    }

    @Override
    public String toString() {
        return "ComparisonNode{" + field + " " + operator + " " + expectedValue + '}';
    }
}
