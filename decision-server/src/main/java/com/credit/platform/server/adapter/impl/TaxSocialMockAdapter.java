package com.credit.platform.server.adapter.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.credit.platform.server.adapter.AbstractExternalApiAdapter;
import com.credit.platform.server.adapter.ExternalApiProperties;

/**
 * 税务/社保数据 Mock 适配器 — 模拟纳税记录和社保缴纳信息。
 * <p>
 * 提供变量：tax_payment_amount, tax_payment_months,
 * social_security_months, social_security_base, income_stability_score
 * </p>
 */
public class TaxSocialMockAdapter extends AbstractExternalApiAdapter {

    public TaxSocialMockAdapter(ExternalApiProperties properties) {
        super(properties);
    }

    @Override
    public String getAdapterType() {
        return "tax_social";
    }

    @Override
    public Set<String> getSupportedVariables() {
        return Set.of(
            "tax_payment_amount",
            "tax_payment_months",
            "social_security_months",
            "social_security_base",
            "income_stability_score"
        );
    }

    @Override
    protected Map<String, Object> doFetch(Set<String> varIds, Map<String, Object> contextVars) {
        simulateLatency(150, 500);
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

        if (varIds.contains("tax_payment_amount")) {
            // 年纳税额：5000-500000
            result.put("tax_payment_amount", 5000.0 + (hash % 100) * 5000.0);
        }
        if (varIds.contains("tax_payment_months")) {
            result.put("tax_payment_months", 6 + (hash % 18)); // 6-23个月
        }
        if (varIds.contains("social_security_months")) {
            result.put("social_security_months", 12 + (hash % 48)); // 12-59个月
        }
        if (varIds.contains("social_security_base")) {
            // 社保基数：3000-25000
            double[] bases = {3000.0, 5000.0, 8000.0, 12000.0, 15000.0, 20000.0, 25000.0};
            result.put("social_security_base", bases[hash % bases.length]);
        }
        if (varIds.contains("income_stability_score")) {
            // 收入稳定性评分：0.0-1.0
            result.put("income_stability_score", 0.5 + (hash % 50) / 100.0);
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
