package com.credit.platform.engine.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 模型调用引擎单元测试。
 * <p>
 * 覆盖: ModelServiceClient (Mock模式), ModelFeatureMapper, SHAPExplainer。
 * </p>
 */
class ModelServiceClientTest {

    // ========== ModelServiceClient (Mock 模式) ==========

    @Nested
    @DisplayName("ModelServiceClient - Mock 模式")
    class MockModeTests {

        @Test
        @DisplayName("Mock 模式返回配置的 mockScore")
        void mockMode_returnsConfiguredScore() {
            ModelConfig config = ModelConfig.builder()
                .modelId("MOD_001")
                .mockEnabled(true)
                .mockScore(0.85)
                .build();

            ModelServiceClient client = new DefaultModelServiceClient(config);
            ModelRequest request = ModelRequest.builder()
                .modelId("MOD_001")
                .addFeature("age", 28)
                .build();

            ModelResponse response = client.predict(request);

            assertTrue(response.isSuccess());
            assertEquals(0.85, response.getScore(), 0.001);
            assertEquals(0.85, response.getProbability(), 0.001);
            assertEquals("LOW_RISK", response.getLabel());
        }

        @Test
        @DisplayName("Mock 模式不需要 endpoint 配置")
        void mockMode_noEndpointRequired() {
            ModelConfig config = ModelConfig.builder()
                .modelId("MOD_002")
                .mockEnabled(true)
                .build();

            ModelServiceClient client = new DefaultModelServiceClient(config);
            assertTrue(client.isHealthy());

            ModelResponse response = client.predict(
                ModelRequest.builder().modelId("MOD_002").build());
            assertTrue(response.isSuccess());
        }

        @Test
        @DisplayName("降级策略 - 无 endpoint 时返回降级响应")
        void fallback_noEndpoint_returnsFallbackResponse() {
            ModelConfig config = ModelConfig.builder()
                .modelId("MOD_003")
                .mockScore(0.5)
                .maxRetries(0)
                .build();

            ModelServiceClient client = new DefaultModelServiceClient(config);
            ModelRequest request = ModelRequest.builder()
                .modelId("MOD_003")
                .addFeature("age", 30)
                .build();

            ModelResponse response = client.predict(request);

            assertTrue(response.isSuccess());
            assertEquals(0.5, response.getScore(), 0.001);
            assertEquals("FALLBACK", response.getLabel());
            assertEquals(true, response.getOutputs().get("fallback"));
        }
    }

    // ========== ModelFeatureMapper ==========

    @Nested
    @DisplayName("ModelFeatureMapper - 特征映射")
    class FeatureMapperTests {

        @Test
        @DisplayName("基本特征映射 - 变量名到特征名转换")
        void basicMapping_varToFeature() {
            ModelFeatureMapper mapper = ModelFeatureMapper.builder()
                .modelId("MOD_001")
                .addMapping(new FeatureMapping("age", "f_age", FeatureMapping.Type.INTEGER, 0))
                .addMapping(new FeatureMapping("income", "f_annual_income", FeatureMapping.Type.DOUBLE, 0.0))
                .build();

            Map<String, Object> context = Map.of("age", 28, "income", 50000);
            Map<String, Object> features = mapper.map(context);

            assertEquals(28, features.get("f_age"));
            assertEquals(50000.0, features.get("f_annual_income"));
        }

        @Test
        @DisplayName("缺失变量使用默认值")
        void missingVariable_usesDefault() {
            ModelFeatureMapper mapper = ModelFeatureMapper.builder()
                .modelId("MOD_001")
                .addMapping(new FeatureMapping("age", FeatureMapping.Type.INTEGER))
                .addMapping(new FeatureMapping("score", "f_score", FeatureMapping.Type.DOUBLE, -1.0))
                .build();

            Map<String, Object> context = Map.of("age", 25);
            Map<String, Object> features = mapper.map(context);

            assertEquals(25, features.get("age"));
            assertEquals(-1.0, features.get("f_score"));
        }

        @Test
        @DisplayName("类型转换 - String 到 Number")
        void typeConversion_stringToNumber() {
            ModelFeatureMapper mapper = ModelFeatureMapper.builder()
                .modelId("MOD_001")
                .addMapping(new FeatureMapping("age", FeatureMapping.Type.INTEGER))
                .addMapping(new FeatureMapping("ratio", FeatureMapping.Type.DOUBLE))
                .build();

            Map<String, Object> context = Map.of("age", "28", "ratio", "0.75");
            Map<String, Object> features = mapper.map(context);

            assertEquals(28, features.get("age"));
            assertEquals(0.75, features.get("ratio"));
        }

        @Test
        @DisplayName("buildRequest 构建 ModelRequest")
        void buildRequest_createsModelRequest() {
            ModelFeatureMapper mapper = ModelFeatureMapper.builder()
                .modelId("MOD_001")
                .addMapping(new FeatureMapping("age", FeatureMapping.Type.INTEGER))
                .build();

            Map<String, Object> context = Map.of("age", 30);
            ModelRequest request = mapper.buildRequest(context, "REQ_001");

            assertEquals("MOD_001", request.getModelId());
            assertEquals("REQ_001", request.getRequestId());
            assertEquals(30, request.getFeatures().get("age"));
        }
    }

    // ========== SHAPExplainer ==========

    @Nested
    @DisplayName("SHAPExplainer - 模型可解释性")
    class SHAPExplainerTests {

        @Test
        @DisplayName("启用 SHAP 时返回特征贡献度")
        void enabled_returnsContributions() {
            ModelConfig config = ModelConfig.builder()
                .modelId("MOD_001")
                .mockEnabled(true)
                .mockScore(0.8)
                .build();

            ModelServiceClient client = new DefaultModelServiceClient(config);
            SHAPExplainer explainer = new SHAPExplainer(client, true, 0.5);

            ModelRequest request = ModelRequest.builder()
                .modelId("MOD_001")
                .addFeature("age", 28)
                .addFeature("income", 50000)
                .build();

            ModelResponse response = client.predict(request);
            ShapResult shapResult = explainer.explain(response, request);

            assertTrue(shapResult.isSuccess());
            assertEquals(0.5, shapResult.getBaseValue(), 0.001);
            assertEquals(2, shapResult.getContributions().size());
        }

        @Test
        @DisplayName("Top-N 特征按贡献度排序")
        void topContributions_sortedByAbsoluteValue() {
            ModelConfig config = ModelConfig.builder()
                .modelId("MOD_001")
                .mockEnabled(true)
                .mockScore(0.9)
                .build();

            ModelServiceClient client = new DefaultModelServiceClient(config);
            SHAPExplainer explainer = new SHAPExplainer(client, true, 0.5);

            ModelRequest request = ModelRequest.builder()
                .modelId("MOD_001")
                .addFeature("overdue_count", 5)
                .addFeature("age", 25)
                .addFeature("income", 30000)
                .build();

            ModelResponse response = client.predict(request);
            ShapResult shapResult = explainer.explain(response, request);

            var top2 = shapResult.getTopContributions(2);
            assertEquals(2, top2.size());
            // Top 特征的贡献度绝对值应 >= 其他特征
            assertTrue(Math.abs(top2.get(0).getShapValue()) >= Math.abs(top2.get(1).getShapValue()));
        }

        @Test
        @DisplayName("SHAP 未启用时返回均匀分配的降级结果")
        void disabled_returnsUniformExplanation() {
            ModelConfig config = ModelConfig.builder()
                .modelId("MOD_001")
                .mockEnabled(true)
                .mockScore(0.7)
                .build();

            ModelServiceClient client = new DefaultModelServiceClient(config);
            SHAPExplainer explainer = new SHAPExplainer(client, false, 0.5);

            ModelRequest request = ModelRequest.builder()
                .modelId("MOD_001")
                .addFeature("age", 28)
                .build();

            ModelResponse response = client.predict(request);
            ShapResult shapResult = explainer.explain(response, request);

            assertTrue(shapResult.isSuccess());
            assertEquals(1, shapResult.getContributions().size());
            assertEquals(0.2, shapResult.getContributions().get(0).getShapValue(), 0.001);
        }

        @Test
        @DisplayName("null 响应返回错误结果")
        void nullResponse_returnsError() {
            ModelConfig config = ModelConfig.builder()
                .modelId("MOD_001")
                .mockEnabled(true)
                .build();

            ModelServiceClient client = new DefaultModelServiceClient(config);
            SHAPExplainer explainer = new SHAPExplainer(client, true);

            ModelRequest request = ModelRequest.builder()
                .modelId("MOD_001")
                .build();

            ShapResult result = explainer.explain(null, request);
            assertFalse(result.isSuccess());
        }
    }

    // ========== 集成场景 ==========

    @Nested
    @DisplayName("集成场景 - 端到端模型调用流程")
    class IntegrationTests {

        @Test
        @DisplayName("完整流程: 特征映射 → 模型调用 → SHAP 解释")
        void fullPipeline_mappingPredictExplain() {
            // 1. 配置
            ModelConfig config = ModelConfig.builder()
                .modelId("MOD_ANTI_FRAUD_V2")
                .mockEnabled(true)
                .mockScore(0.82)
                .build();

            // 2. 特征映射
            ModelFeatureMapper mapper = ModelFeatureMapper.builder()
                .modelId("MOD_ANTI_FRAUD_V2")
                .addMapping(new FeatureMapping("age", "f_age", FeatureMapping.Type.INTEGER, 0))
                .addMapping(new FeatureMapping("income", "f_income", FeatureMapping.Type.DOUBLE, 0.0))
                .addMapping(new FeatureMapping("overdue_count_6m", "f_overdue", FeatureMapping.Type.INTEGER, 0))
                .build();

            // 3. 映射特征
            Map<String, Object> context = Map.of(
                "age", 28,
                "income", 50000,
                "overdue_count_6m", 0
            );
            ModelRequest request = mapper.buildRequest(context, "REQ_001");

            assertEquals(3, request.getFeatures().size());
            assertEquals(28, request.getFeatures().get("f_age"));

            // 4. 模型调用
            ModelServiceClient client = new DefaultModelServiceClient(config);
            ModelResponse response = client.predict(request);

            assertTrue(response.isSuccess());
            assertEquals(0.82, response.getScore(), 0.001);
            assertEquals("LOW_RISK", response.getLabel());

            // 5. SHAP 解释
            SHAPExplainer explainer = new SHAPExplainer(client, true, 0.5);
            ShapResult shapResult = explainer.explain(response, request);

            assertTrue(shapResult.isSuccess());
            assertEquals(3, shapResult.getContributions().size());
            // baseValue + sum(contributions) ≈ predictedValue
            double predictedValue = shapResult.getPredictedValue();
            assertTrue(predictedValue > 0, "Predicted value should be positive");
        }
    }
}
