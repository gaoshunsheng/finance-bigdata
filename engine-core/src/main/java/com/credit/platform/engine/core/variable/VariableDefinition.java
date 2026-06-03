package com.credit.platform.engine.core.variable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 变量定义 — 描述一个决策变量的元信息。
 * <p>
 * 不可变对象。注册在 {@link VariableRegistry} 中，用于变量预取和依赖分析。
 * </p>
 */
public final class VariableDefinition {

    private final String varId;
    private final String name;
    private final VariableLayer layer;
    private final String dataType;         // STRING / INTEGER / DECIMAL / DATE / BOOLEAN
    private final String category;         // 业务域: 征信/工商/运营商/内部/衍生
    private final String expression;       // L3 衍生变量的 Aviator 计算表达式
    private final Set<String> dependencies; // 依赖的变量 ID 列表
    private final int version;
    private final String description;

    private VariableDefinition(Builder builder) {
        this.varId = Objects.requireNonNull(builder.varId);
        this.name = Objects.requireNonNull(builder.name);
        this.layer = Objects.requireNonNull(builder.layer);
        this.dataType = builder.dataType;
        this.category = builder.category;
        this.expression = builder.expression;
        this.dependencies = builder.dependencies != null
            ? Collections.unmodifiableSet(new HashSet<>(builder.dependencies))
            : Collections.emptySet();
        this.version = builder.version;
        this.description = builder.description;
    }

    public String getVarId() { return varId; }
    public String getName() { return name; }
    public VariableLayer getLayer() { return layer; }
    public String getDataType() { return dataType; }
    public String getCategory() { return category; }
    public String getExpression() { return expression; }
    public Set<String> getDependencies() { return dependencies; }
    public int getVersion() { return version; }
    public String getDescription() { return description; }

    public boolean isDerived() { return layer == VariableLayer.DERIVED; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String varId;
        private String name;
        private VariableLayer layer;
        private String dataType = "STRING";
        private String category;
        private String expression;
        private Set<String> dependencies;
        private int version = 1;
        private String description;

        public Builder varId(String v) { this.varId = v; return this; }
        public Builder name(String n) { this.name = n; return this; }
        public Builder layer(VariableLayer l) { this.layer = l; return this; }
        public Builder dataType(String t) { this.dataType = t; return this; }
        public Builder category(String c) { this.category = c; return this; }
        public Builder expression(String e) { this.expression = e; return this; }
        public Builder dependencies(Set<String> d) { this.dependencies = d; return this; }
        public Builder version(int v) { this.version = v; return this; }
        public Builder description(String d) { this.description = d; return this; }
        public VariableDefinition build() { return new VariableDefinition(this); }
    }

    @Override
    public String toString() {
        return "Var{id='" + varId + "', layer=" + layer + ", type=" + dataType + '}';
    }
}
