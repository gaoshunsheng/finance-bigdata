package com.credit.platform.engine.core.table;

import com.credit.platform.engine.core.compiler.CompiledRule;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 编译后的决策表 — 二维表，列是条件，行是规则，交叉格是结果。
 * <p>
 * 不可变对象，线程安全。支持 FIRST_MATCH 命中策略和 {@code *} 通配符。
 * </p>
 *
 * <pre>
 * // 执行: 逐行匹配条件列，首行命中即返回结果
 * CompiledDecisionTable table = compiler.compile(json);
 * TableRow matched = table.evaluate(variables);
 * </pre>
 */
public final class CompiledDecisionTable implements CompiledRule {

    private static final String RULE_TYPE = "DECISION_TABLE";

    private final String tableId;
    private final String name;
    private final int version;
    private final List<Column> columns;
    private final List<TableRow> rows;
    private final String hitPolicy;  // FIRST_MATCH
    private final Set<String> referencedFields;

    public CompiledDecisionTable(String tableId, String name, int version,
                                  List<Column> columns, List<TableRow> rows,
                                  String hitPolicy) {
        this.tableId = Objects.requireNonNull(tableId);
        this.name = name;
        this.version = version;
        this.columns = Collections.unmodifiableList(new ArrayList<>(
            Objects.requireNonNull(columns)));
        this.rows = Collections.unmodifiableList(new ArrayList<>(
            Objects.requireNonNull(rows)));
        this.hitPolicy = hitPolicy;

        Set<String> fields = new HashSet<>();
        for (Column col : this.columns) {
            fields.add(col.getField());
        }
        this.referencedFields = Collections.unmodifiableSet(fields);
    }

    /**
     * 执行决策表匹配。
     * <p>
     * 逐行评估条件列值是否与变量匹配。{@code "*"} 通配符匹配任意值。
     * FIRST_MATCH 策略：首行命中即返回。
     * </p>
     *
     * @param variables 变量上下文
     * @return 匹配的行，无匹配时返回 null
     */
    public TableRow evaluate(Map<String, Object> variables) {
        for (TableRow row : rows) {
            if (row.matches(columns, variables)) {
                return row;
            }
        }
        return null;
    }

    @Override
    public String getRuleId() { return tableId; }
    @Override
    public String getName() { return name; }
    @Override
    public int getVersion() { return version; }
    @Override
    public String getRuleType() { return RULE_TYPE; }

    public List<Column> getColumns() { return columns; }
    public List<TableRow> getRows() { return rows; }
    public String getHitPolicy() { return hitPolicy; }
    public Set<String> getReferencedFields() { return referencedFields; }

    /**
     * 决策表列定义。
     */
    public static final class Column {
        private final String name;
        private final String field;

        public Column(String name, String field) {
            this.name = Objects.requireNonNull(name);
            this.field = Objects.requireNonNull(field);
        }

        public String getName() { return name; }
        public String getField() { return field; }
    }

    /**
     * 决策表行 — 条件值列表 + 结果。
     */
    public static final class TableRow {
        private final List<Object> conditions;
        private final Map<String, Object> result;

        public TableRow(List<Object> conditions, Map<String, Object> result) {
            this.conditions = Collections.unmodifiableList(new ArrayList<>(
                Objects.requireNonNull(conditions)));
            this.result = result != null
                ? Collections.unmodifiableMap(result)
                : Collections.emptyMap();
        }

        /**
         * 判断该行是否匹配变量上下文。
         * {@code "*"} 通配符匹配任意值（包括 null）。
         */
        public boolean matches(List<Column> columns, Map<String, Object> variables) {
            if (conditions.size() != columns.size()) {
                return false;
            }
            for (int i = 0; i < columns.size(); i++) {
                Object expected = conditions.get(i);
                Object actual = variables.get(columns.get(i).getField());

                // "*" 通配符匹配任意值
                if ("*".equals(expected)) {
                    continue;
                }

                if (!equalsSafe(actual, expected)) {
                    return false;
                }
            }
            return true;
        }

        private boolean equalsSafe(Object actual, Object expected) {
            if (actual == null && expected == null) return true;
            if (actual == null || expected == null) return false;
            // Number 跨类型比较
            if (actual instanceof Number && expected instanceof Number) {
                return Double.compare(
                    ((Number) actual).doubleValue(),
                    ((Number) expected).doubleValue()) == 0;
            }
            return actual.equals(expected);
        }

        public List<Object> getConditions() { return conditions; }
        public Map<String, Object> getResult() { return result; }
    }

    @Override
    public String toString() {
        return "CompiledDecisionTable{id='" + tableId + "', columns=" + columns.size()
            + ", rows=" + rows.size() + '}';
    }
}
