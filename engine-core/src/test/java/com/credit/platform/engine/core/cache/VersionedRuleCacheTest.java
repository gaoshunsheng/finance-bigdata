package com.credit.platform.engine.core.cache;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 版本化规则缓存测试。
 * <p>
 * 覆盖: CopyOnWrite 语义、版本快照、热加载、回滚、并发安全。
 * </p>
 */
@DisplayName("版本化规则缓存")
class VersionedRuleCacheTest {

    private VersionedRuleCache cache;

    @BeforeEach
    void setUp() {
        cache = new VersionedRuleCache(1000, 5);
    }

    // ==================== 基础存取 ====================

    @Nested
    @DisplayName("基础存取")
    class BasicAccessTest {

        @Test
        @DisplayName("1. put/get 基础存取")
        void putAndGet_basic() {
            cache.put("rule-001", "RuleV1", 1);

            VersionedArtifact<?> artifact = cache.get("rule-001");
            assertNotNull(artifact);
            assertEquals("rule-001", artifact.getArtifactId());
            assertEquals(1, artifact.getVersion());
            assertEquals("RuleV1", artifact.getArtifact());
        }

        @Test
        @DisplayName("2. getArtifact 直接获取产物")
        void getArtifact_direct() {
            cache.put("rule-001", "RuleV1", 1);
            assertEquals("RuleV1", cache.getArtifact("rule-001"));
        }

        @Test
        @DisplayName("3. getVersion 获取版本号")
        void getVersion_returnsVersion() {
            cache.put("rule-001", "RuleV1", 1);
            assertEquals(1, cache.getVersion("rule-001"));

            cache.put("rule-001", "RuleV2", 2);
            assertEquals(2, cache.getVersion("rule-001"));
        }

        @Test
        @DisplayName("4. 缓存未命中返回 null")
        void get_cacheMiss_returnsNull() {
            assertNull(cache.get("non-existent"));
            assertNull(cache.getArtifact("non-existent"));
            assertEquals(-1, cache.getVersion("non-existent"));
        }
    }

    // ==================== CopyOnWrite 语义 ====================

    @Nested
    @DisplayName("CopyOnWrite 语义")
    class CopyOnWriteTest {

        @Test
        @DisplayName("5. 在途请求持有旧引用 — 缓存更新不影响在途请求")
        void copyOnWrite_inFlightRequestKeepsOldReference() {
            cache.put("rule-001", "RuleV1", 1);

            // 模拟在途请求获取旧版本
            VersionedArtifact<?> inFlight = cache.get("rule-001");
            assertNotNull(inFlight);
            assertEquals("RuleV1", inFlight.getArtifact());
            assertEquals(1, inFlight.getVersion());

            // 缓存更新
            cache.put("rule-001", "RuleV2", 2);

            // 新请求获取新版本
            VersionedArtifact<?> newRequest = cache.get("rule-001");
            assertEquals("RuleV2", newRequest.getArtifact());
            assertEquals(2, newRequest.getVersion());

            // 在途请求仍持有旧版本
            assertEquals("RuleV1", inFlight.getArtifact());
            assertEquals(1, inFlight.getVersion());
        }

        @Test
        @DisplayName("6. 多次更新 — CopyOnWrite 保证每个引用独立")
        void copyOnWrite_multipleUpdates() {
            cache.put("rule-001", "V1", 1);
            VersionedArtifact<?> ref1 = cache.get("rule-001");

            cache.put("rule-001", "V2", 2);
            VersionedArtifact<?> ref2 = cache.get("rule-001");

            cache.put("rule-001", "V3", 3);
            VersionedArtifact<?> ref3 = cache.get("rule-001");

            // 每个引用保持各自的版本
            assertEquals("V1", ref1.getArtifact());
            assertEquals("V2", ref2.getArtifact());
            assertEquals("V3", ref3.getArtifact());
        }
    }

    // ==================== 版本快照 ====================

    @Nested
    @DisplayName("版本快照")
    class VersionSnapshotTest {

        @Test
        @DisplayName("7. 版本历史自动记录")
        void versionHistory_autoRecorded() {
            cache.put("rule-001", "V1", 1);
            cache.put("rule-001", "V2", 2);
            cache.put("rule-001", "V3", 3);

            List<VersionedArtifact<?>> history = cache.getVersionHistory("rule-001");
            assertEquals(2, history.size());
            // 降序: v2 在 v1 之前
            assertEquals(2, history.get(0).getVersion());
            assertEquals(1, history.get(1).getVersion());
        }

        @Test
        @DisplayName("8. 版本历史最多保留 N 个")
        void versionHistory_maxVersions() {
            for (int i = 1; i <= 8; i++) {
                cache.put("rule-001", "V" + i, i);
            }

            List<VersionedArtifact<?>> history = cache.getVersionHistory("rule-001");
            assertEquals(5, history.size()); // maxVersions = 5
            // 保留最近的 5 个: v7, v6, v5, v4, v3
            assertEquals(7, history.get(0).getVersion());
            assertEquals(3, history.get(4).getVersion());
        }

        @Test
        @DisplayName("9. 无历史返回空列表")
        void versionHistory_emptyWhenNoHistory() {
            cache.put("rule-001", "V1", 1);
            // 第一次 put 没有旧版本，所以历史为空
            assertTrue(cache.getVersionHistory("rule-001").isEmpty());

            // 不存在的 key
            assertTrue(cache.getVersionHistory("non-existent").isEmpty());
        }
    }

    // ==================== 回滚 ====================

    @Nested
    @DisplayName("版本回滚")
    class RollbackTest {

        @Test
        @DisplayName("10. 回滚到指定版本")
        void rollback_toSpecificVersion() {
            cache.put("rule-001", "V1", 1);
            cache.put("rule-001", "V2", 2);
            cache.put("rule-001", "V3", 3);

            // 回滚到 v1
            VersionedArtifact<?> rolled = cache.rollback("rule-001", 1);
            assertNotNull(rolled);
            assertEquals(1, rolled.getVersion());
            assertEquals("V1", rolled.getArtifact());

            // 新请求看到的是回滚后的版本
            assertEquals(1, cache.getVersion("rule-001"));
        }

        @Test
        @DisplayName("11. 回滚到上一个版本")
        void rollbackToPrevious_success() {
            cache.put("rule-001", "V1", 1);
            cache.put("rule-001", "V2", 2);

            VersionedArtifact<?> rolled = cache.rollbackToPrevious("rule-001");
            assertNotNull(rolled);
            assertEquals(1, rolled.getVersion());
            assertEquals("V1", rolled.getArtifact());
        }

        @Test
        @DisplayName("12. 回滚不存在的版本返回 null")
        void rollback_nonExistentVersion_returnsNull() {
            cache.put("rule-001", "V1", 1);
            cache.put("rule-001", "V2", 2);

            assertNull(cache.rollback("rule-001", 99));
            assertNull(cache.rollback("non-existent", 1));
        }

        @Test
        @DisplayName("13. 无历史时回滚返回 null")
        void rollbackToPrevious_noHistory_returnsNull() {
            cache.put("rule-001", "V1", 1);
            assertNull(cache.rollbackToPrevious("rule-001"));
        }
    }

    // ==================== 热加载 ====================

    @Nested
    @DisplayName("热加载")
    class HotReloadTest {

        @Test
        @DisplayName("14. reload — 编译成功替换缓存")
        void reload_success() {
            cache.put("rule-001", "V1", 1);

            CacheReloadEvent event = new CacheReloadEvent(
                "rule-001", CacheReloadEvent.Type.RULE, 2, CacheReloadEvent.Source.MQ);

            VersionedArtifact<?> result = cache.reload(event, (id, ver) -> "V2-compiled");

            assertNotNull(result);
            assertEquals(2, result.getVersion());
            assertEquals("V2-compiled", result.getArtifact());
            assertEquals("V2-compiled", cache.getArtifact("rule-001"));
        }

        @Test
        @DisplayName("15. reload — 编译失败不影响现有缓存")
        void reload_compileFailure_doesNotAffectCache() {
            cache.put("rule-001", "V1", 1);

            CacheReloadEvent event = new CacheReloadEvent(
                "rule-001", CacheReloadEvent.Type.RULE, 2, CacheReloadEvent.Source.MQ);

            VersionedArtifact<?> result = cache.reload(event, (id, ver) -> {
                throw new RuntimeException("Compile error");
            });

            assertNull(result);
            // 现有缓存不受影响
            assertEquals("V1", cache.getArtifact("rule-001"));
            assertEquals(1, cache.getVersion("rule-001"));
        }

        @Test
        @DisplayName("16. reload — 编译返回 null 不替换缓存")
        void reload_nullCompile_doesNotReplace() {
            cache.put("rule-001", "V1", 1);

            CacheReloadEvent event = new CacheReloadEvent(
                "rule-001", CacheReloadEvent.Type.RULE, 2, CacheReloadEvent.Source.MANUAL);

            VersionedArtifact<?> result = cache.reload(event, (id, ver) -> null);

            assertNull(result);
            assertEquals("V1", cache.getArtifact("rule-001"));
        }

        @Test
        @DisplayName("17. reload — 旧版本自动存入历史")
        void reload_oldVersionSavedToHistory() {
            cache.put("rule-001", "V1", 1);
            cache.put("rule-001", "V2", 2);

            CacheReloadEvent event = new CacheReloadEvent(
                "rule-001", CacheReloadEvent.Type.RULE, 3, CacheReloadEvent.Source.MQ);

            cache.reload(event, (id, ver) -> "V3");

            List<VersionedArtifact<?>> history = cache.getVersionHistory("rule-001");
            // V2 被存入历史 (reload 前已有 V1, V2, V2 被替换进历史)
            assertFalse(history.isEmpty());
        }
    }

    // ==================== 失效操作 ====================

    @Test
    @DisplayName("18. invalidate 清除缓存和历史")
    void invalidate_clearsCacheAndHistory() {
        cache.put("rule-001", "V1", 1);
        cache.put("rule-001", "V2", 2);

        cache.invalidate("rule-001");

        assertNull(cache.get("rule-001"));
        assertTrue(cache.getVersionHistory("rule-001").isEmpty());
    }

    @Test
    @DisplayName("19. invalidateAll 清除全部")
    void invalidateAll_clearsAll() {
        cache.put("rule-001", "V1", 1);
        cache.put("flow-001", "F1", 1);

        cache.invalidateAll();

        assertNull(cache.get("rule-001"));
        assertNull(cache.get("flow-001"));
    }

    // ==================== 统计信息 ====================

    @Test
    @DisplayName("20. getStats 返回缓存统计")
    void getStats_returnsStatistics() {
        cache.put("rule-001", "V1", 1);
        cache.get("rule-001"); // hit
        cache.get("non-existent"); // miss

        Map<String, Object> stats = cache.getStats();
        assertTrue(stats.containsKey("hitRate"));
        assertTrue(stats.containsKey("hitCount"));
        assertTrue(stats.containsKey("missCount"));
        assertTrue(stats.containsKey("size"));
        assertTrue(stats.containsKey("versionedKeys"));
    }

    @Test
    @DisplayName("21. getAllEntries 返回所有缓存条目")
    void getAllEntries_returnsAll() {
        cache.put("rule-001", "V1", 1);
        cache.put("rule-002", "V1", 1);

        Map<String, Object> entries = cache.getAllEntries();
        assertEquals(2, entries.size());
        assertTrue(entries.containsKey("rule-001"));
        assertTrue(entries.containsKey("rule-002"));
    }

    // ==================== 并发安全 ====================

    @Test
    @DisplayName("22. 并发 put/get — 线程安全")
    void concurrentPutGet_threadSafe() throws Exception {
        int threadCount = 10;
        int iterations = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < iterations; i++) {
                        String key = "rule-" + threadId;
                        cache.put(key, "V" + i, i);
                        VersionedArtifact<?> artifact = cache.get(key);
                        if (artifact == null) {
                            errorCount.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertEquals(0, errorCount.get());
        executor.shutdown();
    }

    // ==================== CacheReloadEvent 测试 ====================

    @Nested
    @DisplayName("CacheReloadEvent")
    class ReloadEventTest {

        @Test
        @DisplayName("23. CacheReloadEvent 属性")
        void reloadEvent_properties() {
            CacheReloadEvent event = new CacheReloadEvent(
                "rule-001", CacheReloadEvent.Type.RULE, 5, CacheReloadEvent.Source.MQ);

            assertEquals("rule-001", event.getArtifactId());
            assertEquals(CacheReloadEvent.Type.RULE, event.getType());
            assertEquals(5, event.getVersion());
            assertEquals(CacheReloadEvent.Source.MQ, event.getSource());
            assertFalse(event.isReloadAll());
        }

        @Test
        @DisplayName("24. CacheReloadEvent ALL 类型")
        void reloadEvent_allType() {
            CacheReloadEvent event = new CacheReloadEvent(
                "all", CacheReloadEvent.Type.ALL, 0, CacheReloadEvent.Source.MANUAL);
            assertTrue(event.isReloadAll());
        }
    }

    // ==================== VersionedArtifact 测试 ====================

    @Test
    @DisplayName("25. VersionedArtifact isNewerThan")
    void versionedArtifact_isNewerThan() {
        VersionedArtifact<String> v1 = new VersionedArtifact<>("id", 1, "A");
        VersionedArtifact<String> v2 = new VersionedArtifact<>("id", 2, "B");

        assertTrue(v2.isNewerThan(v1));
        assertFalse(v1.isNewerThan(v2));
        assertTrue(v1.isNewerThan(null));
    }
}
