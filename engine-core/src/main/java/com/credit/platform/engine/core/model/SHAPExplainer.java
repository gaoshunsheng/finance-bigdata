package com.credit.platform.engine.core.model;

/**
 * SHAP 模型可解释性解释器。
 * <p>
 * 调用模型服务的 SHAP 解释接口，获取每个特征的贡献度。
 * SHAP 值集成到引擎的 L3 模型可解释性中，用于:
 * <ul>
 *   <li>业务人员: 理解哪些因素影响了决策结果</li>
 *   <li>模型工程师: 验证模型行为是否符合预期</li>
 *   <li>审计合规: 提供可解释的决策依据</li>
 * </ul>
 * </p>
 *
 * <p>设计要点:
 * <ul>
 *   <li>SHAP 调用为可选操作，调用失败不影响决策主流程</li>
 *   <li>支持同步获取和降级处理</li>
 *   <li>线程安全</li>
 * </ul>
 * </p>
 */
public class SHAPExplainer {

    private final ModelServiceClient modelServiceClient;
    private final boolean enabled;
    private final double baseValue;
    private final double featureMax;         // 归一化除数 — 特征最大值 (默认 100.0)
    private final double maxAmplification;   // 放大倍数上限 (默认 2.0)
    private final double minFactor;          // 缩放因子下限 (默认 0.1)

    /**
     * 创建 SHAP 解释器。
     *
     * @param modelServiceClient 模型服务客户端
     * @param enabled            是否启用 SHAP 解释
     */
    public SHAPExplainer(ModelServiceClient modelServiceClient, boolean enabled) {
        this(modelServiceClient, enabled, 0.5);
    }

    /**
     * 创建 SHAP 解释器，指定模型基线值。
     *
     * @param modelServiceClient 模型服务客户端
     * @param enabled            是否启用 SHAP 解释
     * @param baseValue          模型基线值 (通常为训练集平均预测值)
     */
    public SHAPExplainer(ModelServiceClient modelServiceClient, boolean enabled, double baseValue) {
        this(modelServiceClient, enabled, baseValue, 100.0, 2.0, 0.1);
    }

    /**
     * 创建 SHAP 解释器，指定全部参数。
     *
     * @param modelServiceClient 模型服务客户端
     * @param enabled            是否启用 SHAP 解释
     * @param baseValue          模型基线值
     * @param featureMax         特征归一化除数 (特征值域最大值)
     * @param maxAmplification   放大倍数上限
     * @param minFactor          缩放因子下限
     */
    public SHAPExplainer(ModelServiceClient modelServiceClient, boolean enabled,
                         double baseValue, double featureMax,
                         double maxAmplification, double minFactor) {
        this.modelServiceClient = modelServiceClient;
        this.enabled = enabled;
        this.baseValue = baseValue;
        this.featureMax = featureMax;
        this.maxAmplification = maxAmplification;
        this.minFactor = minFactor;
    }

    /**
     * 解释模型预测 — 获取 SHAP 特征贡献度。
     * <p>
     * 如果 SHAP 未启用或调用失败，返回降级结果 (各特征等分贡献度)。
     * </p>
     *
     * @param response 模型推理响应
     * @param request  原始模型请求 (包含特征值)
     * @return SHAP 解释结果
     */
    public ShapResult explain(ModelResponse response, ModelRequest request) {
        if (!enabled) {
            return buildUniformExplanation(response, request);
        }

        if (response == null || !response.isSuccess()) {
            return ShapResult.error(
                request != null ? request.getModelId() : "unknown",
                "Cannot explain: model response is null or failed");
        }

        try {
            return doExplain(response, request);
        } catch (Exception e) {
            // SHAP 调用失败 → 降级为均匀分配
            return buildUniformExplanation(response, request);
        }
    }

    /**
     * 执行实际的 SHAP 解释。
     * <p>
     * 当前实现使用均匀贡献度作为默认策略。
     * 生产环境中可通过子类覆盖此方法，实际调用模型服务的 SHAP 接口。
     * </p>
     */
    protected ShapResult doExplain(ModelResponse response, ModelRequest request) {
        // 默认策略: 按特征值的重要性均匀分配预测值与基线值的差
        double delta = response.getScore() - baseValue;
        var features = request.getFeatures();

        if (features.isEmpty()) {
            return ShapResult.builder()
                .modelId(response.getModelId())
                .baseValue(baseValue)
                .build();
        }

        // 均匀分配贡献度
        double contributionPerFeature = delta / features.size();

        ShapResult.Builder builder = ShapResult.builder()
            .modelId(response.getModelId())
            .baseValue(baseValue);

        for (var entry : features.entrySet()) {
            // 简单策略: 值越大贡献越正向
            double adjustedContribution = scaleContribution(entry.getValue(), contributionPerFeature);
            builder.addContribution(entry.getKey(), entry.getValue(), adjustedContribution);
        }

        return builder.build();
    }

    /**
     * 根据特征值缩放贡献度。
     * <p>
     * 简单线性缩放: 归一化后的贡献度。参数通过构造函数配置。
     * 生产环境应替换为真实的 SHAP 值。
     * </p>
     */
    private double scaleContribution(Object value, double baseContribution) {
        if (value instanceof Number) {
            double numValue = Math.abs(((Number) value).doubleValue());
            // 归一化: 值越大贡献越大，使用可配置参数
            double factor = Math.min(numValue / featureMax, maxAmplification);
            return baseContribution * Math.max(factor, minFactor);
        }
        return baseContribution;
    }

    /**
     * 构建降级解释 — 均匀分配贡献度。
     */
    private ShapResult buildUniformExplanation(ModelResponse response, ModelRequest request) {
        if (request == null) {
            return ShapResult.builder()
                .modelId("unknown")
                .baseValue(baseValue)
                .build();
        }

        double score = response != null ? response.getScore() : baseValue;
        double delta = score - baseValue;
        var features = request.getFeatures();

        double contributionPerFeature = features.isEmpty() ? 0.0 : delta / features.size();

        ShapResult.Builder builder = ShapResult.builder()
            .modelId(request.getModelId())
            .baseValue(baseValue);

        for (var entry : features.entrySet()) {
            builder.addContribution(entry.getKey(), entry.getValue(), contributionPerFeature);
        }

        return builder.build();
    }

    public boolean isEnabled() { return enabled; }
    public double getBaseValue() { return baseValue; }
    public double getFeatureMax() { return featureMax; }
    public double getMaxAmplification() { return maxAmplification; }
    public double getMinFactor() { return minFactor; }
}
