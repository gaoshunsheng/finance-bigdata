package com.credit.platform.server.adapter.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.credit.platform.server.adapter.AbstractExternalApiAdapter;
import com.credit.platform.server.adapter.ExternalApiProperties;

/**
 * 工商数据 Mock 适配器 — 模拟企业工商注册信息。
 * <p>
 * 提供变量：enterprise_status, registered_capital, established_years,
 * business_scope_match, legal_person_match
 * </p>
 */
public class BusinessRegistrationMockAdapter extends AbstractExternalApiAdapter {

    public BusinessRegistrationMockAdapter(ExternalApiProperties properties) {
        super(properties);
    }

    @Override
    public String getAdapterType() {
        return "business_registration";
    }

    @Override
    public Set<String> getSupportedVariables() {
        return Set.of(
            "enterprise_status",
            "registered_capital",
            "established_years",
            "business_scope_match",
            "legal_person_match"
        );
    }

    @Override
    protected Map<String, Object> doFetch(Set<String> varIds, Map<String, Object> contextVars) {
        simulateLatency(100, 300);
        return generateMockData(varIds, contextVars);
    }

    @Override
    protected Map<String, Object> doFetchMock(Set<String> varIds, Map<String, Object> contextVars) {
        return generateMockData(varIds, contextVars);
    }

    private Map<String, Object> generateMockData(Set<String> varIds, Map<String, Object> contextVars) {
        Map<String, Object> result = new HashMap<>();
        String enterpriseId = (String) contextVars.getOrDefault("enterpriseId", "DEFAULT");
        int hash = Math.abs(enterpriseId.hashCode());

        if (varIds.contains("enterprise_status")) {
            String[] statuses = {"存续（在营）", "存续", "注销", "吊销"};
            result.put("enterprise_status", statuses[hash % 2]); // 大概率存续
        }
        if (varIds.contains("registered_capital")) {
            long[] capitals = {1_000_000L, 5_000_000L, 10_000_000L, 50_000_000L, 100_000_000L};
            result.put("registered_capital", capitals[hash % capitals.length]);
        }
        if (varIds.contains("established_years")) {
            result.put("established_years", 3 + (hash % 18)); // 3-20年
        }
        if (varIds.contains("business_scope_match")) {
            result.put("business_scope_match", hash % 10 > 1); // 80% 匹配
        }
        if (varIds.contains("legal_person_match")) {
            result.put("legal_person_match", hash % 10 > 0); // 90% 匹配
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
