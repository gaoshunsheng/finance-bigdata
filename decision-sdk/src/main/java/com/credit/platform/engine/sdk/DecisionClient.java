package com.credit.platform.engine.sdk;

import com.credit.platform.engine.common.model.DecisionRequest;
import com.credit.platform.engine.common.model.DecisionResponse;

/**
 * 决策引擎客户端 — 封装与 decision-server 的 HTTP 通信。
 * <p>
 * 提供同步调用决策引擎的能力，支持:
 * <ul>
 *   <li>同步执行决策</li>
 *   <li>查询决策报告</li>
 *   <li>超时控制 + 重试机制</li>
 *   <li>API Key 鉴权</li>
 * </ul>
 * </p>
 *
 * <pre>
 * DecisionClient client = DecisionClient.create(
 *     DecisionClientConfig.builder()
 *         .endpoint("http://localhost:8080")
 *         .apiKey("your-api-key")
 *         .build()
 * );
 *
 * DecisionRequest request = new DecisionRequest();
 * request.setStrategyId("STR_CREDIT_V3");
 * request.setApplicant(Map.of("name", "张三", "age", 28));
 *
 * DecisionResponse response = client.execute(request);
 * System.out.println(response.getResult());
 * </pre>
 */
public class DecisionClient {

    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String HEADER_API_KEY = "X-API-Key";
    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    private static final int HTTP_OK = 200;

    private final DecisionClientConfig config;

    private DecisionClient(DecisionClientConfig config) {
        this.config = config;
    }

    /**
     * 工厂方法 — 创建决策引擎客户端。
     *
     * @param config 客户端配置
     * @return 决策引擎客户端实例
     */
    public static DecisionClient create(DecisionClientConfig config) {
        return new DecisionClient(config);
    }

    /**
     * 同步执行决策。
     *
     * @param request 决策请求
     * @return 决策响应
     * @throws DecisionClientException 调用失败时抛出
     */
    public DecisionResponse execute(DecisionRequest request) {
        if (!config.isEnabled()) {
            throw new DecisionClientException("Decision client is disabled");
        }

        Exception lastException = null;
        for (int attempt = 0; attempt <= config.getMaxRetries(); attempt++) {
            try {
                return doExecute(request);
            } catch (Exception e) {
                lastException = e;
                if (attempt < config.getMaxRetries()) {
                    sleep(200L * (attempt + 1));
                }
            }
        }

        throw new DecisionClientException(
            "Decision execution failed after " + (config.getMaxRetries() + 1) + " attempts",
            lastException);
    }

    /**
     * 查询决策报告。
     *
     * @param decisionId 决策 ID
     * @return 决策报告 JSON 字符串
     * @throws DecisionClientException 调用失败时抛出
     */
    public String getReport(String decisionId) {
        if (!config.isEnabled()) {
            throw new DecisionClientException("Decision client is disabled");
        }

        try {
            String url = config.getReportUrl(decisionId);
            java.net.HttpURLConnection conn = createGetConnection(url);
            return readResponse(conn);
        } catch (Exception e) {
            throw new DecisionClientException("Failed to get report for: " + decisionId, e);
        }
    }

    /**
     * 健康检查。
     *
     * @return true 表示决策引擎服务可用
     */
    public boolean isHealthy() {
        try {
            String url = config.getEndpoint() + "/actuator/health";
            java.net.HttpURLConnection conn = createGetConnection(url);
            return conn.getResponseCode() == HTTP_OK;
        } catch (Exception e) {
            return false;
        }
    }

    // ========== 内部方法 ==========

    private DecisionResponse doExecute(DecisionRequest request) throws Exception {
        String url = config.getDecisionUrl();
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
            new java.net.URL(url).openConnection();

        conn.setRequestMethod("POST");
        conn.setRequestProperty(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON);
        conn.setRequestProperty("Accept", CONTENT_TYPE_JSON);
        conn.setConnectTimeout((int) config.getConnectTimeoutMs());
        conn.setReadTimeout((int) config.getReadTimeoutMs());
        conn.setDoOutput(true);

        // API Key 鉴权
        if (config.getApiKey() != null && !config.getApiKey().isEmpty()) {
            conn.setRequestProperty(HEADER_API_KEY, config.getApiKey());
        }

        // 发送请求
        String jsonBody = toJson(request);
        try (java.io.OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        int responseCode = conn.getResponseCode();
        if (responseCode != HTTP_OK) {
            String errorBody = readError(conn);
            throw new DecisionClientException(
                "Server returned HTTP " + responseCode + ": " + errorBody);
        }

        String responseBody = readResponse(conn);
        return parseResponse(responseBody);
    }

    private java.net.HttpURLConnection createGetConnection(String url) throws Exception {
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout((int) config.getConnectTimeoutMs());
        conn.setReadTimeout((int) config.getReadTimeoutMs());
        if (config.getApiKey() != null && !config.getApiKey().isEmpty()) {
            conn.setRequestProperty(HEADER_API_KEY, config.getApiKey());
        }
        return conn;
    }

    // ========== 简单 JSON 处理 ==========

    private String toJson(DecisionRequest request) {
        StringBuilder sb = new StringBuilder("{");
        appendField(sb, "strategyId", request.getStrategyId());
        appendField(sb, "channel", request.getChannel());
        appendField(sb, "requestId", request.getRequestId());

        if (request.getApplicant() != null && !request.getApplicant().isEmpty()) {
            sb.append(",\"applicant\":{");
            int i = 0;
            for (var entry : request.getApplicant().entrySet()) {
                if (i > 0) sb.append(',');
                sb.append('"').append(escapeJson(entry.getKey())).append("\":");
                sb.append(valueToJson(entry.getValue()));
                i++;
            }
            sb.append('}');
        }

        sb.append('}');
        return sb.toString();
    }

    private void appendField(StringBuilder sb, String field, String value) {
        if (value != null) {
            if (sb.length() > 1) sb.append(',');
            sb.append('"').append(field).append("\":\"").append(escapeJson(value)).append('"');
        }
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

    private DecisionResponse parseResponse(String json) {
        DecisionResponse response = new DecisionResponse();
        response.setDecisionId(extractString(json, "decisionId"));
        response.setTraceId(extractString(json, "traceId"));
        response.setRejectReason(extractString(json, "rejectReason"));
        response.setRejectCode(extractString(json, "rejectCode"));

        String resultStr = extractString(json, "result");
        if (resultStr != null) {
            try {
                response.setResult(com.credit.platform.engine.common.model.DecisionResult.valueOf(resultStr));
            } catch (IllegalArgumentException ignored) {
            }
        }

        Integer score = extractInteger(json, "score");
        if (score != null) response.setScore(score);

        response.setDurationMs(extractLong(json, "durationMs"));
        return response;
    }

    private String extractString(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return null;
        int start = idx + pattern.length();
        int end = json.indexOf('"', start);
        return end > start ? json.substring(start, end) : null;
    }

    private Integer extractInteger(String json, String key) {
        String pattern = "\"" + key + "\":";
        int idx = json.indexOf(pattern);
        if (idx < 0) return null;
        int start = idx + pattern.length();
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        try {
            return Integer.parseInt(json.substring(start, end));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private long extractLong(String json, String key) {
        String pattern = "\"" + key + "\":";
        int idx = json.indexOf(pattern);
        if (idx < 0) return 0L;
        int start = idx + pattern.length();
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        try {
            return Long.parseLong(json.substring(start, end));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private String readResponse(java.net.HttpURLConnection conn) throws Exception {
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
            new java.io.InputStreamReader(conn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    private String readError(java.net.HttpURLConnection conn) {
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
            new java.io.InputStreamReader(conn.getErrorStream(), java.nio.charset.StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        } catch (Exception e) {
            return "unable to read error stream";
        }
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
