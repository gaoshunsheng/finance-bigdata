package com.credit.platform.server.provider;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import com.credit.platform.engine.core.variable.PrefetchResult;
import com.credit.platform.engine.core.variable.VariableDefinition;
import com.credit.platform.engine.core.variable.VariableLayer;
import com.credit.platform.engine.core.variable.VariableRegistry;
import com.credit.platform.engine.core.variable.VariableResolveContext;
import com.credit.platform.server.config.FeatureProperties;

/**
 * CachedVariableProvider + VariablePrefetcher unit tests.
 * Pure unit tests, no Spring context.
 */
class CachedProviderTest {

    // ========================================================================
    // CachedVariableProvider Tests
    // ========================================================================
    @Nested
    class CachedVariableProviderTest {

        private RedisFeatureProvider redisProvider;
        private HBaseFeatureProvider hbaseProvider;
        private FeatureProperties properties;
        private CachedVariableProvider cachedProvider;

        @BeforeEach
        void setUp() {
            redisProvider = Mockito.mock(RedisFeatureProvider.class);
            hbaseProvider = Mockito.mock(HBaseFeatureProvider.class);
            properties = new FeatureProperties();
            cachedProvider = new CachedVariableProvider(redisProvider, hbaseProvider, properties);
        }

        @Test
        void provide_returnsEmpty_whenVarIdsEmpty() {
            VariableResolveContext ctx = new VariableResolveContext("req-1", Map.of("customer_id", "C001"));
            Map<String, Object> result = cachedProvider.provide(Set.of(), ctx);
            assertTrue(result.isEmpty());
        }

        @Test
        void provide_returnsEmpty_whenVarIdsNull() {
            VariableResolveContext ctx = new VariableResolveContext("req-1", Map.of("customer_id", "C001"));
            Map<String, Object> result = cachedProvider.provide(null, ctx);
            assertTrue(result.isEmpty());
        }

        @Test
        void provide_returnsEmpty_whenNoCustomerId() {
            VariableResolveContext ctx = new VariableResolveContext("req-1", Map.of());
            Map<String, Object> result = cachedProvider.provide(Set.of("var_overdue_6m"), ctx);
            assertTrue(result.isEmpty());
        }

        @Test
        void provide_redisHit_returnsValuesWithoutHBase() {
            Map<String, Object> redisResult = Map.of("var_overdue_6m", 3, "var_debt_ratio", 0.45);
            when(redisProvider.query(anySet(), eq("C001"))).thenReturn(redisResult);

            VariableResolveContext ctx = new VariableResolveContext("req-1",
                Map.of("customer_id", "C001"));
            Map<String, Object> result = cachedProvider.provide(
                Set.of("var_overdue_6m", "var_debt_ratio"), ctx);

            assertEquals(2, result.size());
            assertEquals(3, result.get("var_overdue_6m"));
            assertEquals(0.45, result.get("var_debt_ratio"));

            // HBase should NOT be queried
            verify(hbaseProvider, never()).query(anySet(), anyString());
        }

        @Test
        void provide_redisMiss_hbaseHit_returnsValues() {
            // Redis returns empty
            when(redisProvider.query(anySet(), eq("C001"))).thenReturn(Map.of());

            // HBase returns values
            Map<String, Object> hbaseResult = Map.of("var_overdue_6m", 5);
            when(hbaseProvider.query(anySet(), eq("C001"))).thenReturn(hbaseResult);

            VariableResolveContext ctx = new VariableResolveContext("req-1",
                Map.of("customer_id", "C001"));
            Map<String, Object> result = cachedProvider.provide(
                Set.of("var_overdue_6m"), ctx);

            assertEquals(1, result.size());
            assertEquals(5, result.get("var_overdue_6m"));
        }

        @Test
        void provide_partialRedisHit_queriesHbaseForRemainder() {
            // Redis hits one of two
            Map<String, Object> redisResult = Map.of("var_overdue_6m", 3);
            when(redisProvider.query(anySet(), eq("C001"))).thenReturn(redisResult);

            // HBase hits the remaining one
            Map<String, Object> hbaseResult = Map.of("var_debt_ratio", 0.6);
            when(hbaseProvider.query(anySet(), eq("C001"))).thenReturn(hbaseResult);

            VariableResolveContext ctx = new VariableResolveContext("req-1",
                Map.of("customer_id", "C001"));
            Map<String, Object> result = cachedProvider.provide(
                Set.of("var_overdue_6m", "var_debt_ratio"), ctx);

            assertEquals(2, result.size());
        }

        @Test
        void provide_totalMiss_returnsEmpty() {
            when(redisProvider.query(anySet(), eq("C001"))).thenReturn(Map.of());
            when(hbaseProvider.query(anySet(), eq("C001"))).thenReturn(Map.of());

            VariableResolveContext ctx = new VariableResolveContext("req-1",
                Map.of("customer_id", "C001"));
            Map<String, Object> result = cachedProvider.provide(
                Set.of("var_unknown"), ctx);

            assertTrue(result.isEmpty());
        }

        @Test
        void provide_redisException_fallsBackToHbase() {
            when(redisProvider.query(anySet(), eq("C001")))
                .thenThrow(new RuntimeException("Redis connection refused"));

            Map<String, Object> hbaseResult = Map.of("var_overdue_6m", 2);
            when(hbaseProvider.query(anySet(), eq("C001"))).thenReturn(hbaseResult);

            VariableResolveContext ctx = new VariableResolveContext("req-1",
                Map.of("customer_id", "C001"));
            Map<String, Object> result = cachedProvider.provide(
                Set.of("var_overdue_6m"), ctx);

            assertEquals(1, result.size());
            assertEquals(2, result.get("var_overdue_6m"));
        }

        @Test
        void provide_hbaseException_doesNotThrow() {
            when(redisProvider.query(anySet(), eq("C001"))).thenReturn(Map.of());
            when(hbaseProvider.query(anySet(), eq("C001")))
                .thenThrow(new RuntimeException("HBase unavailable"));

            VariableResolveContext ctx = new VariableResolveContext("req-1",
                Map.of("customer_id", "C001"));

            assertDoesNotThrow(() -> cachedProvider.provide(Set.of("var_overdue_6m"), ctx));
        }

        @Test
        void provide_extractsCustomerId_alternativeKeys() {
            when(redisProvider.query(anySet(), anyString())).thenReturn(Map.of());

            // Test with "customerId" (camelCase)
            VariableResolveContext ctx1 = new VariableResolveContext("req-1",
                Map.of("customerId", "C002"));
            cachedProvider.provide(Set.of("var_overdue_6m"), ctx1);
            verify(redisProvider).query(anySet(), eq("C002"));
        }

        @Test
        void getStats_tracksHits() {
            when(redisProvider.query(anySet(), eq("C001")))
                .thenReturn(Map.of("var_a", 1))
                .thenReturn(Map.of("var_b", 2));
            when(hbaseProvider.query(anySet(), eq("C001")))
                .thenReturn(Map.of());

            VariableResolveContext ctx = new VariableResolveContext("req-1",
                Map.of("customer_id", "C001"));
            cachedProvider.provide(Set.of("var_a"), ctx);
            cachedProvider.provide(Set.of("var_b"), ctx);

            CachedVariableProvider.CacheStats stats = cachedProvider.getStats();
            assertEquals(2, stats.getTotalQueries());
            assertEquals(2, stats.getRedisHits());
        }
    }

    // ========================================================================
    // VariablePrefetcher Tests
    // ========================================================================
    @Nested
    class VariablePrefetcherTest {

        private VariableRegistry registry;
        private CachedVariableProvider cachedProvider;
        private VariablePrefetcher prefetcher;

        @BeforeEach
        void setUp() {
            registry = new VariableRegistry();
            cachedProvider = Mockito.mock(CachedVariableProvider.class);
            prefetcher = new VariablePrefetcher(registry, cachedProvider,
                Executors.newFixedThreadPool(2));
        }

        @Test
        void prefetch_returnsEmpty_whenNoVarIds() {
            PrefetchResult result = prefetcher.prefetch(Set.of(), "C001");
            assertTrue(result.isAllSuccess());
            assertTrue(result.getVariables().isEmpty());
        }

        @Test
        void prefetch_returnsEmpty_whenNoCustomerId() {
            PrefetchResult result = prefetcher.prefetch(Set.of("var_a"), null);
            assertTrue(result.getVariables().isEmpty());
        }

        @Test
        void prefetch_returnsEmpty_whenNoL2Variables() {
            // Register only L0 and L1 variables
            registry.register(VariableDefinition.builder()
                .varId("var_input").name("input").layer(VariableLayer.INPUT)
                .dataType("STRING").category("test").build());
            registry.register(VariableDefinition.builder()
                .varId("var_external").name("external").layer(VariableLayer.EXTERNAL)
                .dataType("STRING").category("test").build());

            PrefetchResult result = prefetcher.prefetch(
                Set.of("var_input", "var_external"), "C001");

            assertTrue(result.isAllSuccess());
            assertTrue(result.getVariables().isEmpty());
            verify(cachedProvider, never()).queryWithFallback(anySet(), anyString());
        }

        @Test
        void prefetch_queriesOnlyL2Variables() {
            // Register variables across all layers
            registry.register(VariableDefinition.builder()
                .varId("var_input").name("input").layer(VariableLayer.INPUT)
                .dataType("STRING").category("test").build());
            registry.register(VariableDefinition.builder()
                .varId("var_cached_1").name("cached1").layer(VariableLayer.CACHED)
                .dataType("INTEGER").category("test").build());
            registry.register(VariableDefinition.builder()
                .varId("var_external").name("external").layer(VariableLayer.EXTERNAL)
                .dataType("STRING").category("test").build());
            registry.register(VariableDefinition.builder()
                .varId("var_cached_2").name("cached2").layer(VariableLayer.CACHED)
                .dataType("DECIMAL").category("test").build());

            when(cachedProvider.queryWithFallback(anySet(), eq("C001")))
                .thenReturn(Map.of("var_cached_1", 5, "var_cached_2", 0.3));

            PrefetchResult result = prefetcher.prefetch(
                Set.of("var_input", "var_cached_1", "var_external", "var_cached_2"),
                "C001");

            // Only L2 variables should be queried
            verify(cachedProvider).queryWithFallback(
                eq(Set.of("var_cached_1", "var_cached_2")), eq("C001"));

            assertTrue(result.isAllSuccess());
            assertEquals(2, result.getSuccessCount());
            assertEquals(5, result.getVariables().get("var_cached_1"));
            assertEquals(0.3, result.getVariables().get("var_cached_2"));
        }

        @Test
        void prefetch_handlesPartialFailure() {
            registry.register(VariableDefinition.builder()
                .varId("var_a").name("a").layer(VariableLayer.CACHED)
                .dataType("INTEGER").category("test").build());
            registry.register(VariableDefinition.builder()
                .varId("var_b").name("b").layer(VariableLayer.CACHED)
                .dataType("INTEGER").category("test").build());

            // Only var_a is returned
            when(cachedProvider.queryWithFallback(anySet(), eq("C001")))
                .thenReturn(Map.of("var_a", 10));

            PrefetchResult result = prefetcher.prefetch(
                Set.of("var_a", "var_b"), "C001");

            assertTrue(result.hasPartialFailure());
            assertEquals(1, result.getSuccessCount());
            assertEquals(1, result.getFailureCount());
            assertTrue(result.getFailed().contains("var_b"));
        }

        @Test
        void prefetch_handlesTotalFailure() {
            registry.register(VariableDefinition.builder()
                .varId("var_x").name("x").layer(VariableLayer.CACHED)
                .dataType("INTEGER").category("test").build());

            when(cachedProvider.queryWithFallback(anySet(), eq("C001")))
                .thenReturn(Map.of());

            PrefetchResult result = prefetcher.prefetch(Set.of("var_x"), "C001");

            assertFalse(result.isAllSuccess());
            assertEquals(0, result.getSuccessCount());
            assertEquals(1, result.getFailureCount());
        }

        @Test
        void prefetch_handlesProviderException() {
            registry.register(VariableDefinition.builder()
                .varId("var_y").name("y").layer(VariableLayer.CACHED)
                .dataType("INTEGER").category("test").build());

            when(cachedProvider.queryWithFallback(anySet(), eq("C001")))
                .thenThrow(new RuntimeException("Connection refused"));

            PrefetchResult result = prefetcher.prefetch(Set.of("var_y"), "C001");

            assertFalse(result.isAllSuccess());
            assertTrue(result.getFailed().contains("var_y"));
        }

        @Test
        void prefetchAsync_completesSuccessfully() throws Exception {
            registry.register(VariableDefinition.builder()
                .varId("var_async").name("async").layer(VariableLayer.CACHED)
                .dataType("INTEGER").category("test").build());

            when(cachedProvider.queryWithFallback(anySet(), eq("C001")))
                .thenReturn(Map.of("var_async", 42));

            CompletableFuture<PrefetchResult> future = prefetcher.prefetchAsync(
                Set.of("var_async"), "C001");

            PrefetchResult result = future.get();
            assertTrue(result.isAllSuccess());
            assertEquals(42, result.getVariables().get("var_async"));
        }

        @Test
        void injectPrefetchResult_mergesVariables() {
            Map<String, Object> params = new HashMap<>();
            params.put("customer_id", "C001");
            params.put("loan_amount", 50000);

            PrefetchResult prefetch = PrefetchResult.success(
                Map.of("var_overdue_6m", 3, "var_debt_ratio", 0.45), 10);

            Map<String, Object> merged = prefetcher.injectPrefetchResult(params, prefetch);

            assertEquals(4, merged.size());
            assertEquals("C001", merged.get("customer_id"));
            assertEquals(50000, merged.get("loan_amount"));
            assertEquals(3, merged.get("var_overdue_6m"));
            assertEquals(0.45, merged.get("var_debt_ratio"));
        }

        @Test
        void injectPrefetchResult_handlesNullResult() {
            Map<String, Object> params = Map.of("customer_id", "C001");

            Map<String, Object> merged = prefetcher.injectPrefetchResult(params, null);

            assertEquals(1, merged.size());
        }
    }

    // ========================================================================
    // PrefetchResult Tests
    // ========================================================================
    @Nested
    class PrefetchResultTest {

        @Test
        void success_allFieldsSet() {
            PrefetchResult result = PrefetchResult.success(Map.of("a", 1, "b", 2), 50);

            assertTrue(result.isAllSuccess());
            assertFalse(result.hasPartialFailure());
            assertEquals(2, result.getSuccessCount());
            assertEquals(0, result.getFailureCount());
            assertEquals(50, result.getLatencyMs());
        }

        @Test
        void partial_bothSuccessAndFailed() {
            PrefetchResult result = PrefetchResult.partial(
                Map.of("a", 1), Set.of("b", "c"), 30);

            assertFalse(result.isAllSuccess());
            assertTrue(result.hasPartialFailure());
            assertEquals(1, result.getSuccessCount());
            assertEquals(2, result.getFailureCount());
        }

        @Test
        void failure_onlyFailed() {
            PrefetchResult result = PrefetchResult.failure(Set.of("x", "y"), 10);

            assertFalse(result.isAllSuccess());
            assertFalse(result.hasPartialFailure());
            assertEquals(0, result.getSuccessCount());
            assertEquals(2, result.getFailureCount());
        }

        @Test
        void empty_noData() {
            PrefetchResult result = PrefetchResult.empty();

            assertTrue(result.isAllSuccess());
            assertEquals(0, result.getSuccessCount());
            assertEquals(0, result.getLatencyMs());
        }

        @Test
        void toString_containsKeyInfo() {
            PrefetchResult result = PrefetchResult.partial(
                Map.of("a", 1), Set.of("b"), 25);

            String str = result.toString();
            assertTrue(str.contains("success=1"));
            assertTrue(str.contains("failed=1"));
            assertTrue(str.contains("latencyMs=25"));
        }
    }
}
