package com.credit.platform.engine.sdk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Decision SDK 单元测试。
 * <p>
 * 覆盖: DecisionClientConfig, DecisionClient, DecisionAutoConfiguration。
 * </p>
 */
class DecisionClientTest {

    // ========== DecisionClientConfig ==========

    @Nested
    @DisplayName("DecisionClientConfig - 配置测试")
    class ConfigTests {

        @Test
        @DisplayName("默认配置值")
        void defaultConfig() {
            DecisionClientConfig config = DecisionClientConfig.builder().build();

            assertEquals("http://localhost:8080", config.getEndpoint());
            assertEquals(3000, config.getConnectTimeoutMs());
            assertEquals(5000, config.getReadTimeoutMs());
            assertEquals(1, config.getMaxRetries());
            assertTrue(config.isEnabled());
        }

        @Test
        @DisplayName("自定义配置覆盖默认值")
        void customConfig() {
            DecisionClientConfig config = DecisionClientConfig.builder()
                .endpoint("http://decision.example.com:9090")
                .connectTimeoutMs(5000)
                .readTimeoutMs(10000)
                .maxRetries(3)
                .apiKey("test-key-123")
                .channel("PARTNER")
                .build();

            assertEquals("http://decision.example.com:9090", config.getEndpoint());
            assertEquals(5000, config.getConnectTimeoutMs());
            assertEquals(10000, config.getReadTimeoutMs());
            assertEquals(3, config.getMaxRetries());
            assertEquals("test-key-123", config.getApiKey());
            assertEquals("PARTNER", config.getChannel());
        }

        @Test
        @DisplayName("URL 生成 - 决策执行 URL")
        void decisionUrl() {
            DecisionClientConfig config = DecisionClientConfig.builder()
                .endpoint("http://localhost:8080/")
                .build();

            assertEquals("http://localhost:8080/api/v1/decision/execute", config.getDecisionUrl());
        }

        @Test
        @DisplayName("URL 生成 - 决策报告 URL")
        void reportUrl() {
            DecisionClientConfig config = DecisionClientConfig.builder()
                .endpoint("http://localhost:8080")
                .build();

            assertEquals("http://localhost:8080/api/v1/decision/report/DEC_001", config.getReportUrl("DEC_001"));
        }
    }

    // ========== DecisionClient ==========

    @Nested
    @DisplayName("DecisionClient - 客户端测试")
    class ClientTests {

        @Test
        @DisplayName("工厂方法创建客户端")
        void createClient() {
            DecisionClientConfig config = DecisionClientConfig.builder()
                .endpoint("http://localhost:8080")
                .build();

            DecisionClient client = DecisionClient.create(config);
            assertNotNull(client);
        }

        @Test
        @DisplayName("禁用状态下抛出异常")
        void disabled_throwsException() {
            DecisionClientConfig config = DecisionClientConfig.builder()
                .endpoint("http://localhost:8080")
                .enabled(false)
                .build();

            DecisionClient client = DecisionClient.create(config);

            DecisionClientException ex = assertThrows(DecisionClientException.class, () -> {
                client.execute(new com.credit.platform.engine.common.model.DecisionRequest());
            });
            assertTrue(ex.getMessage().contains("disabled"));
        }

        @Test
        @DisplayName("未配置服务端时连接失败抛出异常")
        void noServer_throwsException() {
            DecisionClientConfig config = DecisionClientConfig.builder()
                .endpoint("http://localhost:19999")
                .connectTimeoutMs(500)
                .maxRetries(0)
                .build();

            DecisionClient client = DecisionClient.create(config);

            assertThrows(DecisionClientException.class, () -> {
                client.execute(new com.credit.platform.engine.common.model.DecisionRequest());
            });
        }

        @Test
        @DisplayName("健康检查 - 无服务返回 false")
        void healthCheck_noServer_returnsFalse() {
            DecisionClientConfig config = DecisionClientConfig.builder()
                .endpoint("http://localhost:19999")
                .connectTimeoutMs(500)
                .build();

            DecisionClient client = DecisionClient.create(config);
            assertFalse(client.isHealthy());
        }
    }

    // ========== DecisionAutoConfiguration ==========

    @Nested
    @DisplayName("DecisionAutoConfiguration - 自动装配测试")
    class AutoConfigurationTests {

        @Test
        @DisplayName("Properties 转 Config 完整映射")
        void propertiesToConfig() {
            com.credit.platform.engine.sdk.autoconfigure.DecisionClientProperties props =
                new com.credit.platform.engine.sdk.autoconfigure.DecisionClientProperties();
            props.setEndpoint("http://test-server:8080");
            props.setConnectTimeoutMs(2000);
            props.setReadTimeoutMs(4000);
            props.setMaxRetries(2);
            props.setApiKey("test-key");
            props.setChannel("TEST");
            props.setEnabled(true);

            DecisionClientConfig config = props.toConfig();

            assertEquals("http://test-server:8080", config.getEndpoint());
            assertEquals(2000, config.getConnectTimeoutMs());
            assertEquals(4000, config.getReadTimeoutMs());
            assertEquals(2, config.getMaxRetries());
            assertEquals("test-key", config.getApiKey());
            assertEquals("TEST", config.getChannel());
            assertTrue(config.isEnabled());
        }

        @Test
        @DisplayName("Properties 默认值正确")
        void propertiesDefaults() {
            com.credit.platform.engine.sdk.autoconfigure.DecisionClientProperties props =
                new com.credit.platform.engine.sdk.autoconfigure.DecisionClientProperties();

            assertEquals("http://localhost:8080", props.getEndpoint());
            assertEquals(3000, props.getConnectTimeoutMs());
            assertEquals(5000, props.getReadTimeoutMs());
            assertEquals(1, props.getMaxRetries());
            assertNull(props.getApiKey());
            assertEquals("SDK", props.getChannel());
            assertTrue(props.isEnabled());
        }
    }

    // ========== DecisionClientException ==========

    @Nested
    @DisplayName("DecisionClientException - 异常测试")
    class ExceptionTests {

        @Test
        @DisplayName("异常消息和原因正确传递")
        void exceptionWithCause() {
            RuntimeException cause = new RuntimeException("connection refused");
            DecisionClientException ex = new DecisionClientException("Failed to call decision engine", cause);

            assertEquals("Failed to call decision engine", ex.getMessage());
            assertEquals(cause, ex.getCause());
        }

        @Test
        @DisplayName("无原因异常正确创建")
        void exceptionWithoutCause() {
            DecisionClientException ex = new DecisionClientException("Config error");
            assertEquals("Config error", ex.getMessage());
            assertNull(ex.getCause());
        }
    }
}
