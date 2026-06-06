package com.credit.platform.server.adapter.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.credit.platform.server.adapter.AbstractExternalApiAdapter;
import com.credit.platform.server.adapter.ExternalApiProperties;

/**
 * 运营商数据 Mock 适配器 — 模拟手机实名和在网信息。
 * <p>
 * 提供变量：phone_real_name_match, phone_active_months,
 * phone_monthly_fee, phone_area_match
 * </p>
 */
public class TelecomMockAdapter extends AbstractExternalApiAdapter {

    public TelecomMockAdapter(ExternalApiProperties properties) {
        super(properties);
    }

    @Override
    public String getAdapterType() {
        return "telecom";
    }

    @Override
    public Set<String> getSupportedVariables() {
        return Set.of(
            "phone_real_name_match",
            "phone_active_months",
            "phone_monthly_fee",
            "phone_area_match"
        );
    }

    @Override
    protected Map<String, Object> doFetch(Set<String> varIds, Map<String, Object> contextVars) {
        simulateLatency(200, 600);
        return generateMockData(varIds, contextVars);
    }

    @Override
    protected Map<String, Object> doFetchMock(Set<String> varIds, Map<String, Object> contextVars) {
        return generateMockData(varIds, contextVars);
    }

    private Map<String, Object> generateMockData(Set<String> varIds, Map<String, Object> contextVars) {
        Map<String, Object> result = new HashMap<>();
        String customerId = (String) contextVars.getOrDefault("customerId", "DEFAULT");
        int hash = Math.abs(customerId.hashCode());

        if (varIds.contains("phone_real_name_match")) {
            result.put("phone_real_name_match", hash % 10 > 1); // 80% 实名匹配
        }
        if (varIds.contains("phone_active_months")) {
            result.put("phone_active_months", 6 + (hash % 60)); // 6-65个月
        }
        if (varIds.contains("phone_monthly_fee")) {
            double[] fees = {38.0, 58.0, 88.0, 128.0, 198.0, 298.0};
            result.put("phone_monthly_fee", fees[hash % fees.length]);
        }
        if (varIds.contains("phone_area_match")) {
            result.put("phone_area_match", hash % 10 > 2); // 70% 归属地匹配
        }

        return result;
    }

    private void simulateLatency(int minMs, int maxMs) {
        try {
            long delay = minMs + (long) (Math.random() * (maxMs - minMs));
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
