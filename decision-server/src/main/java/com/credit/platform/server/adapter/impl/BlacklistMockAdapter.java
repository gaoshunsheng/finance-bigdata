package com.credit.platform.server.adapter.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.credit.platform.server.adapter.AbstractExternalApiAdapter;
import com.credit.platform.server.adapter.ExternalApiProperties;

/**
 * 黑名单/舆情数据 Mock 适配器 — 模拟多源头黑名单和舆情预警。
 * <p>
 * 提供变量：blacklist_hit, blacklist_sources, negative_news_count, risk_tag
 * </p>
 */
public class BlacklistMockAdapter extends AbstractExternalApiAdapter {

    public BlacklistMockAdapter(ExternalApiProperties properties) {
        super(properties);
    }

    @Override
    public String getAdapterType() {
        return "blacklist";
    }

    @Override
    public Set<String> getSupportedVariables() {
        return Set.of(
            "blacklist_hit",
            "blacklist_sources",
            "negative_news_count",
            "risk_tag"
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
        String customerId = (String) contextVars.getOrDefault("customerId", "DEFAULT");
        int hash = Math.abs(customerId.hashCode());

        if (varIds.contains("blacklist_hit")) {
            result.put("blacklist_hit", hash % 50 == 0); // 2% 命中黑名单
        }
        if (varIds.contains("blacklist_sources")) {
            // 命中的黑名单来源列表
            if (hash % 50 == 0) {
                result.put("blacklist_sources", java.util.List.of("反欺诈联盟", "同业黑名单"));
            } else {
                result.put("blacklist_sources", java.util.List.of());
            }
        }
        if (varIds.contains("negative_news_count")) {
            result.put("negative_news_count", hash % 20 > 18 ? 1 + (hash % 3) : 0); // 90% 无负面
        }
        if (varIds.contains("risk_tag")) {
            String[] tags = {"NORMAL", "ATTENTION", "WARNING", "HIGH_RISK"};
            int idx = hash % 50 == 0 ? 3 : (hash % 20 > 18 ? 2 : (hash % 10 > 7 ? 1 : 0));
            result.put("risk_tag", tags[idx]);
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
