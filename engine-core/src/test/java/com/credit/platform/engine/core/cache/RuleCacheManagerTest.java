package com.credit.platform.engine.core.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link RuleCacheManager} 单元测试。
 */
class RuleCacheManagerTest {

    private RuleCacheManager cacheManager;

    @BeforeEach
    void setUp() {
        cacheManager = new RuleCacheManager();
    }

    @Test
    @DisplayName("put and get rule returns stored value")
    void putAndGetRule_returnsStoredValue() {
        cacheManager.putRule("rule-001", "RuleObject1");
        Object result = cacheManager.getRule("rule-001");
        assertEquals("RuleObject1", result);
    }

    @Test
    @DisplayName("get rule on cache miss returns null")
    void getRule_cacheMiss_returnsNull() {
        Object result = cacheManager.getRule("non-existent-key");
        assertNull(result);
    }

    @Test
    @DisplayName("put and get flow returns stored value")
    void putAndGetFlow_returnsStoredValue() {
        cacheManager.putFlow("flow-001", "FlowObject1");
        Object result = cacheManager.getFlow("flow-001");
        assertEquals("FlowObject1", result);
    }

    @Test
    @DisplayName("put and get scorecard returns stored value")
    void putAndGetScorecard_returnsStoredValue() {
        cacheManager.putScorecard("sc-001", "ScorecardObject1");
        Object result = cacheManager.getScorecard("sc-001");
        assertEquals("ScorecardObject1", result);
    }

    @Test
    @DisplayName("invalidate specific key removes entry from rule cache")
    void invalidate_specificKey_removesEntry() {
        cacheManager.putRule("rule-001", "RuleObject1");
        cacheManager.putRule("rule-002", "RuleObject2");

        cacheManager.invalidate("rule", "rule-001");

        assertNull(cacheManager.getRule("rule-001"));
        assertEquals("RuleObject2", cacheManager.getRule("rule-002"));
    }

    @Test
    @DisplayName("invalidate specific key removes entry from flow cache")
    void invalidate_flowCache_removesEntry() {
        cacheManager.putFlow("flow-001", "FlowObject1");

        cacheManager.invalidate("flow", "flow-001");

        assertNull(cacheManager.getFlow("flow-001"));
    }

    @Test
    @DisplayName("invalidate specific key removes entry from scorecard cache")
    void invalidate_scorecardCache_removesEntry() {
        cacheManager.putScorecard("sc-001", "ScorecardObject1");

        cacheManager.invalidate("scorecard", "sc-001");

        assertNull(cacheManager.getScorecard("sc-001"));
    }

    @Test
    @DisplayName("invalidateAll clears all caches")
    void invalidateAll_clearsAllCaches() {
        cacheManager.putRule("rule-001", "RuleObject1");
        cacheManager.putFlow("flow-001", "FlowObject1");
        cacheManager.putScorecard("sc-001", "ScorecardObject1");

        cacheManager.invalidateAll();

        assertNull(cacheManager.getRule("rule-001"));
        assertNull(cacheManager.getFlow("flow-001"));
        assertNull(cacheManager.getScorecard("sc-001"));
    }

    @Test
    @DisplayName("getStats returns non-null map with all cache types")
    void getStats_returnsAllCacheTypes() {
        cacheManager.putRule("rule-001", "RuleObject1");

        Map<String, Object> stats = cacheManager.getStats();

        assertNotNull(stats);
        assertTrue(stats.containsKey("rule"));
        assertTrue(stats.containsKey("flow"));
        assertTrue(stats.containsKey("scorecard"));

        @SuppressWarnings("unchecked")
        Map<String, Object> ruleStats = (Map<String, Object>) stats.get("rule");
        assertTrue(ruleStats.containsKey("hitRate"));
        assertTrue(ruleStats.containsKey("hitCount"));
        assertTrue(ruleStats.containsKey("missCount"));
        assertTrue(ruleStats.containsKey("evictionCount"));
        assertTrue(ruleStats.containsKey("size"));
    }

    @Test
    @DisplayName("overwrite existing key updates value")
    void put_overwriteKey_updatesValue() {
        cacheManager.putRule("rule-001", "OldValue");
        cacheManager.putRule("rule-001", "NewValue");

        assertEquals("NewValue", cacheManager.getRule("rule-001"));
    }
}
