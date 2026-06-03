package com.credit.platform.engine.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * SHAP 解释结果 — 记录模型特征贡献度。
 * <p>
 * SHAP (SHapley Additive exPlanations) 值用于量化每个特征对模型预测的贡献。
 * 此类封装单次预测的 SHAP 分析结果，集成到引擎的 L3 模型可解释性中。
 * </p>
 *
 * <pre>
 * ShapResult result = ShapResult.builder()
 *     .modelId("MOD_ANTI_FRAUD_V2")
 *     .baseValue(0.5)
 *     .addContribution("overdue_count_6m", 0, 0.15)
 *     .addContribution("age", 28, -0.05)
 *     .addContribution("income", 50000, 0.12)
 *     .build();
 * // baseValue + sum(contributions) ≈ modelScore
 * // 0.5 + 0.15 + (-0.05) + 0.12 = 0.72
 * </pre>
 */
public final class ShapResult {

    private final String modelId;
    private final double baseValue;
    private final List<FeatureContribution> contributions;
    private final double totalContribution;
    private final boolean success;
    private final String errorMessage;

    private ShapResult(Builder builder) {
        this.modelId = builder.modelId;
        this.baseValue = builder.baseValue;
        this.contributions = Collections.unmodifiableList(new ArrayList<>(builder.contributions));
        this.totalContribution = contributions.stream()
            .mapToDouble(FeatureContribution::getShapValue)
            .sum();
        this.success = builder.success;
        this.errorMessage = builder.errorMessage;
    }

    public String getModelId() { return modelId; }
    public double getBaseValue() { return baseValue; }
    public List<FeatureContribution> getContributions() { return contributions; }
    public double getTotalContribution() { return totalContribution; }
    public boolean isSuccess() { return success; }
    public String getErrorMessage() { return errorMessage; }

    /**
     * 获取预测值 = baseValue + totalContribution。
     */
    public double getPredictedValue() {
        return baseValue + totalContribution;
    }

    /**
     * 获取按贡献度绝对值排序的 Top-N 特征。
     *
     * @param n 排名数量
     * @return 贡献度最高的 N 个特征
     */
    public List<FeatureContribution> getTopContributions(int n) {
        return contributions.stream()
            .sorted((a, b) -> Double.compare(Math.abs(b.getShapValue()), Math.abs(a.getShapValue())))
            .limit(n)
            .toList();
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String modelId;
        private double baseValue;
        private final List<FeatureContribution> contributions = new ArrayList<>();
        private boolean success = true;
        private String errorMessage;

        public Builder modelId(String modelId) { this.modelId = modelId; return this; }
        public Builder baseValue(double baseValue) { this.baseValue = baseValue; return this; }
        public Builder success(boolean success) { this.success = success; return this; }
        public Builder errorMessage(String msg) { this.errorMessage = msg; return this; }

        public Builder addContribution(String feature, Object value, double shapValue) {
            this.contributions.add(new FeatureContribution(feature, value, shapValue));
            return this;
        }

        public Builder contributions(List<FeatureContribution> contributions) {
            this.contributions.clear();
            this.contributions.addAll(contributions);
            return this;
        }

        public ShapResult build() { return new ShapResult(this); }
    }

    /**
     * 单个特征的 SHAP 贡献度。
     */
    public static final class FeatureContribution {

        private final String feature;
        private final Object value;
        private final double shapValue;

        public FeatureContribution(String feature, Object value, double shapValue) {
            this.feature = Objects.requireNonNull(feature);
            this.value = value;
            this.shapValue = shapValue;
        }

        public String getFeature() { return feature; }
        public Object getValue() { return value; }
        public double getShapValue() { return shapValue; }

        /**
         * 获取贡献度方向描述。
         */
        public String getDirection() {
            if (shapValue > 0.001) return "正向";
            if (shapValue < -0.001) return "负向";
            return "中性";
        }

        @Override
        public String toString() {
            return feature + "=" + value + " (" + getDirection() + ", " + String.format("%.4f", shapValue) + ")";
        }
    }

    /**
     * 创建一个错误结果。
     */
    public static ShapResult error(String modelId, String errorMessage) {
        return builder()
            .modelId(modelId)
            .success(false)
            .errorMessage(errorMessage)
            .build();
    }

    @Override
    public String toString() {
        return "ShapResult{modelId='" + modelId + "', baseValue=" + baseValue
            + ", predictedValue=" + getPredictedValue()
            + ", contributions=" + contributions.size() + '}';
    }
}
