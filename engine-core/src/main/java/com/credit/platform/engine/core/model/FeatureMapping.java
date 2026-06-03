package com.credit.platform.engine.core.model;

import java.util.Objects;

/**
 * 特征映射定义 — 将决策引擎变量映射为模型特征。
 * <p>
 * 定义一条从决策引擎变量到模型特征的映射规则，包含:
 * <ul>
 *   <li>sourceVar: 决策引擎中的变量名 (如 age, income)</li>
 *   <li>targetFeature: 模型期望的特征名 (如 f_age, annual_income)</li>
 *   <li>type: 特征类型 (INTEGER, DOUBLE, STRING, BOOLEAN)</li>
 *   <li>defaultValue: 变量缺失时的默认值</li>
 * </ul>
 * </p>
 */
public final class FeatureMapping {

    /** 特征数据类型 */
    public enum Type {
        INTEGER, DOUBLE, STRING, BOOLEAN
    }

    private final String sourceVar;
    private final String targetFeature;
    private final Type type;
    private final Object defaultValue;

    public FeatureMapping(String sourceVar, String targetFeature, Type type, Object defaultValue) {
        this.sourceVar = Objects.requireNonNull(sourceVar, "sourceVar must not be null");
        this.targetFeature = Objects.requireNonNull(targetFeature, "targetFeature must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.defaultValue = defaultValue;
    }

    /**
     * 简化构造 — 源变量名和目标特征名相同，无默认值。
     */
    public FeatureMapping(String varName, Type type) {
        this(varName, varName, type, null);
    }

    public String getSourceVar() { return sourceVar; }
    public String getTargetFeature() { return targetFeature; }
    public Type getType() { return type; }
    public Object getDefaultValue() { return defaultValue; }

    @Override
    public String toString() {
        return "FeatureMapping{" + sourceVar + " → " + targetFeature
            + " (" + type + ", default=" + defaultValue + ")}";
    }
}
