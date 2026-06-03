package com.credit.platform.engine.core.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 默认模型推理服务客户端实现。
 * <p>
 * 使用 JDK 内置的 {@link HttpURLConnection} 发起 REST 调用，
 * 无需引入额外的 HTTP 客户端依赖，保持 engine-core 的纯 Java 特性。
 * </p>
 *
 * <p>功能特性:
 * <ul>
 *   <li>Mock 模式: 不发起网络调用，直接返回配置的 mockScore</li>
 *   <li>超时控制: 连接超时 + 读取超时双控制</li>
 *   <li>重试机制: 可配置最大重试次数</li>
 *   <li>降级策略: 调用失败时返回降级响应</li>
 * </ul>
 * </p>
 */
public class DefaultModelServiceClient implements ModelServiceClient {

    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final int HTTP_OK = 200;

    private final ModelConfig config;

    public DefaultModelServiceClient(ModelConfig config) {
        this.config = config;
    }

    @Override
    public ModelResponse predict(ModelRequest request) {
        return predict(request, config.getTimeoutMs());
    }

    @Override
    public ModelResponse predict(ModelRequest request, long timeoutMs) {
        if (config.isMockEnabled()) {
            return buildMockResponse(request);
        }

        Exception lastException = null;
        for (int attempt = 0; attempt <= config.getMaxRetries(); attempt++) {
            try {
                return doPredict(request, timeoutMs);
            } catch (Exception e) {
                lastException = e;
                if (attempt < config.getMaxRetries()) {
                    sleep(100L * (attempt + 1)); // 线性退避
                }
            }
        }

        // 所有重试失败 → 降级
        return buildFallbackResponse(request, lastException);
    }

    @Override
    public boolean isHealthy() {
        if (config.isMockEnabled()) {
            return true;
        }
        if (config.getEndpoint() == null || config.getEndpoint().isEmpty()) {
            return false;
        }
        try {
            HttpURLConnection conn = createConnection(config.getEndpoint() + "/health", 2000);
            conn.setRequestMethod("GET");
            return conn.getResponseCode() == HTTP_OK;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 执行实际的 HTTP 调用。
     */
    private ModelResponse doPredict(ModelRequest request, long timeoutMs) throws IOException {
        String endpoint = config.getEndpoint();
        if (endpoint == null || endpoint.isEmpty()) {
            throw new IOException("Model endpoint not configured for: " + request.getModelId());
        }

        String url = endpoint + "/" + request.getModelId() + "/predict";
        HttpURLConnection conn = createConnection(url, timeoutMs);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", CONTENT_TYPE_JSON);
        conn.setRequestProperty("Accept", CONTENT_TYPE_JSON);
        conn.setDoOutput(true);

        // 发送请求体
        String requestBody = serializeRequest(request);
        long startMs = System.currentTimeMillis();
        try (OutputStream os = conn.getOutputStream()) {
            os.write(requestBody.getBytes(StandardCharsets.UTF_8));
        }

        // 读取响应
        int responseCode = conn.getResponseCode();
        long latencyMs = System.currentTimeMillis() - startMs;

        if (responseCode != HTTP_OK) {
            String errorBody = readErrorStream(conn);
            throw new IOException("Model service returned HTTP " + responseCode + ": " + errorBody);
        }

        String responseBody = readResponseStream(conn);
        return deserializeResponse(request.getModelId(), responseBody, latencyMs);
    }

    /**
     * 创建 HTTP 连接。
     */
    private HttpURLConnection createConnection(String url, long timeoutMs) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout((int) Math.min(timeoutMs, Integer.MAX_VALUE));
        conn.setReadTimeout((int) Math.min(timeoutMs, Integer.MAX_VALUE));
        return conn;
    }

    /**
     * 构建 Mock 响应。
     */
    private ModelResponse buildMockResponse(ModelRequest request) {
        return ModelResponse.builder()
            .modelId(request.getModelId())
            .score(config.getMockScore())
            .probability(config.getMockScore())
            .label(scoreToLabel(config.getMockScore()))
            .success(true)
            .latencyMs(0)
            .build();
    }

    /**
     * 构建降级响应。
     */
    private ModelResponse buildFallbackResponse(ModelRequest request, Exception cause) {
        return ModelResponse.builder()
            .modelId(request.getModelId())
            .score(config.getMockScore())
            .probability(config.getMockScore())
            .label("FALLBACK")
            .success(true)
            .latencyMs(0)
            .output("fallback", true)
            .output("fallbackReason", cause != null ? cause.getMessage() : "unknown")
            .build();
    }

    /**
     * 分数 → 标签映射。
     */
    private String scoreToLabel(double score) {
        if (score >= 0.8) return "LOW_RISK";
        if (score >= 0.5) return "MEDIUM_RISK";
        return "HIGH_RISK";
    }

    // ========== 序列化 (简单 JSON 手工拼接，避免额外依赖) ==========

    private String serializeRequest(ModelRequest request) {
        StringBuilder sb = new StringBuilder("{\"modelId\":\"")
            .append(escapeJson(request.getModelId())).append("\"");
        if (request.getRequestId() != null) {
            sb.append(",\"requestId\":\"").append(escapeJson(request.getRequestId())).append("\"");
        }
        sb.append(",\"features\":{");
        Map<String, Object> features = request.getFeatures();
        int i = 0;
        for (Map.Entry<String, Object> entry : features.entrySet()) {
            if (i > 0) sb.append(',');
            sb.append('"').append(escapeJson(entry.getKey())).append("\":");
            sb.append(valueToJson(entry.getValue()));
            i++;
        }
        sb.append("}}");
        return sb.toString();
    }

    private String valueToJson(Object value) {
        if (value == null) return "null";
        if (value instanceof Number) return value.toString();
        if (value instanceof Boolean) return value.toString();
        return "\"" + escapeJson(value.toString()) + "\"";
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    // ========== 反序列化 ==========

    private ModelResponse deserializeResponse(String modelId, String json, long latencyMs) {
        // 简单 JSON 解析 (无依赖场景)
        double score = extractDouble(json, "score");
        double probability = extractDouble(json, "probability");
        String label = extractString(json, "label");

        return ModelResponse.builder()
            .modelId(modelId)
            .score(score)
            .probability(probability)
            .label(label != null ? label : scoreToLabel(score))
            .success(true)
            .latencyMs(latencyMs)
            .build();
    }

    private double extractDouble(String json, String key) {
        String pattern = "\"" + key + "\":";
        int idx = json.indexOf(pattern);
        if (idx < 0) return 0.0;
        int start = idx + pattern.length();
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end))
            || json.charAt(end) == '.' || json.charAt(end) == '-')) {
            end++;
        }
        try {
            return Double.parseDouble(json.substring(start, end));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private String extractString(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return null;
        int start = idx + pattern.length();
        int end = json.indexOf('"', start);
        return end > start ? json.substring(start, end) : null;
    }

    // ========== 工具方法 ==========

    private String readResponseStream(HttpURLConnection conn) throws IOException {
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private String readErrorStream(HttpURLConnection conn) {
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            return "unable to read error stream";
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
