package com.credit.platform.engine.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 模型特征映射器 — 将决策引擎变量映射为模型特征向量。
 * <p>
 * 根据预定义的 {@link FeatureMapping} 列表，从决策上下文的变量中提取值，
 * 执行类型转换和默认值填充，生成模型推理服务所需的特征向量。
 * </p>
 *
 * <pre>
 * ModelFeatureMapper mapper = ModelFeatureMapper.builder()
 *     .modelId("MOD_ANTI_FRAUD_V2")
 *     .addMapping(new FeatureMapping("age", "f_age", FeatureMapping.Type.INTEGER, 0))
 *     .addMapping(new FeatureMapping("income", "f_annual_income", FeatureMapping.Type.DOUBLE, 0.0))
 *     .addMapping(new FeatureMapping("overdue_count_6m", FeatureMapping.Type.INTEGER))
 *     .build();
 *
 * Map&lt;String, Object&gt; context = Map.of("age", 28, "income", 50000, "overdue_count_6m", 0);
 * Map&lt;String, Object&gt; features = mapper.map(context);
 * // → {f_age=28, f_annual_income=50000.0, overdue_count_6m=0}
 * </pre>
 */
public final class ModelFeatureMapper {

    private final String modelId;
    private final List<FeatureMapping> mappings;

    private ModelFeatureMapper(Builder builder) {
        this.modelId = Objects.requireNonNull(builder.modelId, "modelId must not be null");
        this.mappings = Collections.unmodifiableList(new ArrayList<>(builder.mappings));
    }

    public String getModelId() { return modelId; }
    public List<FeatureMapping> getMappings() { return mappings; }

    /**
     * 将决策上下文变量映射为模型特征向量。
     *
     * @param context 决策上下文变量 (变量名 → 变量值)
     * @return 模型特征向量 (特征名 → 特征值)
     */
    public Map<String, Object> map(Map<String, Object> context) {
        Objects.requireNonNull(context, "context must not be null");
        Map<String, Object> features = new LinkedHashMap<>(mappings.size());

        for (FeatureMapping mapping : mappings) {
            Object rawValue = context.get(mapping.getSourceVar());
            Object featureValue = convertValue(rawValue, mapping);
            features.put(mapping.getTargetFeature(), featureValue);
        }

        return features;
    }

    /**
     * 从决策上下文构建完整的 {@link ModelRequest}。
     *
     * @param context   决策上下文变量
     * @param requestId 请求 ID
     * @return 模型推理请求
     */
    public ModelRequest buildRequest(Map<String, Object> context, String requestId) {
        Map<String, Object> features = map(context);
        return ModelRequest.builder()
            .modelId(modelId)
            .requestId(requestId)
            .features(features)
            .build();
    }

    /**
     * 类型转换 + 默认值处理。
     */
    private Object convertValue(Object rawValue, FeatureMapping mapping) {
        // 空值 → 使用默认值
        if (rawValue == null) {
            return mapping.getDefaultValue() != null ? mapping.getDefaultValue() : defaultForType(mapping.getType());
        }

        try {
            switch (mapping.getType()) {
                case INTEGER:
                    if (rawValue instanceof Number) {
                        return ((Number) rawValue).intValue();
                    }
                    return Integer.parseInt(rawValue.toString());
                case DOUBLE:
                    if (rawValue instanceof Number) {
                        return ((Number) rawValue).doubleValue();
                    }
                    return Double.parseDouble(rawValue.toString());
                case BOOLEAN:
                    if (rawValue instanceof Boolean) {
                        return rawValue;
                    }
                    return Boolean.parseBoolean(rawValue.toString());
                case STRING:
                default:
                    return rawValue.toString();
            }
        } catch (NumberFormatException e) {
            // 类型转换失败 → 使用默认值
            return mapping.getDefaultValue() != null ? mapping.getDefaultValue() : defaultForType(mapping.getType());
        }
    }

    /**
     * 各类型的零值默认。
     */
    private Object defaultForType(FeatureMapping.Type type) {
        switch (type) {
            case INTEGER: return 0;
            case DOUBLE: return 0.0;
            case BOOLEAN: return false;
            case STRING: default: return "";
        }
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String modelId;
        private final List<FeatureMapping> mappings = new ArrayList<>();

        public Builder modelId(String modelId) { this.modelId = modelId; return this; }

        public Builder addMapping(FeatureMapping mapping) {
            this.mappings.add(Objects.requireNonNull(mapping));
            return this;
        }

        public Builder mappings(List<FeatureMapping> mappings) {
            this.mappings.clear();
            this.mappings.addAll(mappings);
            return this;
        }

        public ModelFeatureMapper build() { return new ModelFeatureMapper(this); }
    }

    @Override
    public String toString() {
        return "ModelFeatureMapper{modelId='" + modelId + "', mappings=" + mappings.size() + " features}";
    }
}
