package com.credit.platform.server.adapter.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.credit.platform.server.adapter.AbstractExternalApiAdapter;
import com.credit.platform.server.adapter.ExternalApiProperties;

/**
 * 征信 API Mock 适配器 — 模拟央行征信报告数据。
 * <p>
 * 提供变量：credit_score, overdue_count_6m, credit_query_count_3m,
 * max_overdue_days, loan_count, credit_card_count
 * </p>
 */
public class CreditBureauMockAdapter extends AbstractExternalApiAdapter {

    public CreditBureauMockAdapter(ExternalApiProperties properties) {
        super(properties);
    }

    @Override
    public String getAdapterType() {
        return "credit_bureau";
    }

    @Override
    public Set<String> getSupportedVariables() {
        return Set.of(
            "credit_score",
            "overdue_count_6m",
            "credit_query_count_3m",
            "max_overdue_days",
            "loan_count",
            "credit_card_count"
        );
    }

    @Override
    protected Map<String, Object> doFetch(Set<String> varIds, Map<String, Object> contextVars) {
        // 生产环境：调用央行征信 API
        // 此处为 Mock 实现，模拟 HTTP 调用延迟
        simulateLatency(200, 500);
        return generateMockData(varIds, contextVars);
    }

    @Override
    protected Map<String, Object> doFetchMock(Set<String> varIds, Map<String, Object> contextVars) {
        return generateMockData(varIds, contextVars);
    }

    private Map<String, Object> generateMockData(Set<String> varIds, Map<String, Object> contextVars) {
        Map<String, Object> result = new HashMap<>();
        String customerId = (String) contextVars.getOrDefault("customerId", "DEFAULT");

        // 根据 customerId 的 hash 生成确定性的 Mock 数据（同一客户返回一致结果）
        int hash = Math.abs(customerId.hashCode());

        if (varIds.contains("credit_score")) {
            result.put("credit_score", 600 + (hash % 200)); // 600-800
        }
        if (varIds.contains("overdue_count_6m")) {
            result.put("overdue_count_6m", hash % 4); // 0-3
        }
        if (varIds.contains("credit_query_count_3m")) {
            result.put("credit_query_count_3m", 1 + (hash % 8)); // 1-8
        }
        if (varIds.contains("max_overdue_days")) {
            result.put("max_overdue_days", hash % 31); // 0-30
        }
        if (varIds.contains("loan_count")) {
            result.put("loan_count", 1 + (hash % 5)); // 1-5
        }
        if (varIds.contains("credit_card_count")) {
            result.put("credit_card_count", hash % 4); // 0-3
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
