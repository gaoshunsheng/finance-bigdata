package com.credit.platform.server.adapter.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.credit.platform.server.adapter.AbstractExternalApiAdapter;
import com.credit.platform.server.adapter.ExternalApiProperties;

/**
 * 司法数据 Mock 适配器 — 模拟法院诉讼和执行信息。
 * <p>
 * 提供变量：lawsuit_count, execution_count, dishonest_count,
 * case_status, judicial_risk_level
 * </p>
 */
public class JudicialMockAdapter extends AbstractExternalApiAdapter {

    public JudicialMockAdapter(ExternalApiProperties properties) {
        super(properties);
    }

    @Override
    public String getAdapterType() {
        return "judicial";
    }

    @Override
    public Set<String> getSupportedVariables() {
        return Set.of(
            "lawsuit_count",
            "execution_count",
            "dishonest_count",
            "case_status",
            "judicial_risk_level"
        );
    }

    @Override
    protected Map<String, Object> doFetch(Set<String> varIds, Map<String, Object> contextVars) {
        simulateLatency(150, 400);
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

        if (varIds.contains("lawsuit_count")) {
            result.put("lawsuit_count", hash % 10 > 7 ? 1 + (hash % 3) : 0); // 80% 无诉讼
        }
        if (varIds.contains("execution_count")) {
            result.put("execution_count", hash % 20 > 18 ? 1 : 0); // 90% 无执行
        }
        if (varIds.contains("dishonest_count")) {
            result.put("dishonest_count", hash % 50 > 48 ? 1 : 0); // 96% 无失信
        }
        if (varIds.contains("case_status")) {
            String[] statuses = {"无案件", "审理中", "已结案", "执行中"};
            result.put("case_status", statuses[hash % 10 > 7 ? 1 + (hash % 3) : 0]);
        }
        if (varIds.contains("judicial_risk_level")) {
            String[] levels = {"LOW", "MEDIUM", "HIGH"};
            int idx = hash % 10 > 7 ? (hash % 2) + 1 : 0;
            result.put("judicial_risk_level", levels[idx]);
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
