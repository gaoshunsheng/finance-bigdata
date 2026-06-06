package com.credit.platform.server.channel;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.context.annotation.Configuration;

/**
 * 渠道配置 — 定义不同渠道的字段映射和默认值。
 * <p>
 * 多渠道接入时，不同渠道使用不同的字段名:
 * <ul>
 *   <li>APP 渠道: age → applicantAge</li>
 *   <li>WEB 渠道: age → userAge</li>
 *   <li>API 渠道: 直接使用标准字段名</li>
 * </ul>
 * </p>
 */
@Configuration
public class ChannelConfig {

    /** 支持的渠道集合 */
    private static final Set<String> SUPPORTED_CHANNELS = Set.of("APP", "WEB", "API", "PARTNER");

    /** 渠道 → 字段映射 (外部字段名 → 标准字段名) */
    private final Map<String, Map<String, String>> fieldMappings;

    /** 渠道 → 默认值 */
    private final Map<String, Map<String, Object>> defaultValues;

    public ChannelConfig() {
        this.fieldMappings = new HashMap<>();
        this.defaultValues = new HashMap<>();
        initDefaultMappings();
    }

    /**
     * 标准化渠道字段 — 将渠道特定字段映射为引擎标准字段。
     *
     * @param channel   渠道标识
     * @param rawFields 原始字段
     * @return 标准化后的字段
     */
    public Map<String, Object> normalize(String channel, Map<String, Object> rawFields) {
        if (rawFields == null) {
            return Collections.emptyMap();
        }

        Map<String, Object> normalized = new LinkedHashMap<>(rawFields);

        // 应用字段映射
        Map<String, String> mapping = fieldMappings.getOrDefault(
            channel, Collections.emptyMap());
        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            Object value = normalized.remove(entry.getKey());
            if (value != null) {
                normalized.put(entry.getValue(), value);
            }
        }

        // 应用默认值
        Map<String, Object> defaults = defaultValues.getOrDefault(
            channel, Collections.emptyMap());
        for (Map.Entry<String, Object> entry : defaults.entrySet()) {
            normalized.putIfAbsent(entry.getKey(), entry.getValue());
        }

        // 注入渠道信息
        normalized.put("_channel", channel);

        return normalized;
    }

    /**
     * 检查渠道是否受支持。
     *
     * @param channel 渠道标识
     * @return 是否受支持
     */
    public boolean isSupported(String channel) {
        return channel != null && SUPPORTED_CHANNELS.contains(channel.toUpperCase());
    }

    private void initDefaultMappings() {
        // APP 渠道映射
        Map<String, String> appMapping = new LinkedHashMap<>();
        appMapping.put("applicantAge", "age");
        appMapping.put("applicantIncome", "income");
        appMapping.put("loanAmt", "applyAmount");
        appMapping.put("loanTermMonths", "loanTerm");
        fieldMappings.put("APP", appMapping);

        // WEB 渠道映射
        Map<String, String> webMapping = new LinkedHashMap<>();
        webMapping.put("userAge", "age");
        webMapping.put("userIncome", "income");
        webMapping.put("amount", "applyAmount");
        fieldMappings.put("WEB", webMapping);

        // 默认值
        Map<String, Object> appDefaults = new LinkedHashMap<>();
        appDefaults.put("channel", "APP");
        defaultValues.put("APP", appDefaults);

        Map<String, Object> webDefaults = new LinkedHashMap<>();
        webDefaults.put("channel", "WEB");
        defaultValues.put("WEB", webDefaults);
    }
}
