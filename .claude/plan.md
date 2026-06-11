# Plan: VariablePrefetcher + Redis/HBase L2 Feature Providers

## Context

engine-core defines `VariableProvider` functional interface and 4-layer variable architecture (L0 INPUT → L1 EXTERNAL → L2 CACHED → L3 DERIVED). Currently only L1 (ExternalApiProvider) is implemented. L2 CACHED has no provider — this plan fills that gap.

**Key architecture facts:**
- `VariableProvider` is a `@FunctionalInterface`: `Map<String, Object> provide(Set<String> varIds, VariableResolveContext ctx)`
- Spring bean wiring is in `decision-server/.../adapter/ExternalApiConfig.java`
- Redis key format: `feature:{featureType}:{customerId}` (from Flink `FeatureKey`)
- HBase table: `customer_feature`, CF: `cf`, rowKey: `{reversedCustomerId}_{featureType}_{timestamp}` (from Flink `FeatureKey`)
- Existing `FeatureQueryService` in data-service already implements Redis→HBase cache miss pattern
- Thread pools configured in `decision-server/.../config/ThreadPoolConfig.java`
- engine-core has no Redis/HBase dependencies — concrete providers live in decision-server

## Files to Create

### 1. `decision-server/src/main/java/com/credit/platform/server/config/RedisConfig.java`
Redis client configuration for decision-server (currently only exists in data-service).

### 2. `decision-server/src/main/java/com/credit/platform/server/config/HBaseConfig.java`
HBase client configuration for decision-server.

### 3. `decision-server/src/main/java/com/credit/platform/server/provider/CachedVariableProvider.java`
L2 CACHED layer VariableProvider implementation. Composite pattern:
- Tries Redis first (RedisFeatureProvider)
- On cache miss, falls back to HBase (HBaseFeatureProvider)
- On HBase hit, writes back to Redis with TTL
- Returns map of varId → value for all resolved variables

### 4. `decision-server/src/main/java/com/credit/platform/server/provider/RedisFeatureProvider.java`
Redis-backed feature reader. Reads from `feature:{featureType}:{customerId}` keys.
Uses pipeline/mget for batch efficiency.

### 5. `decision-server/src/main/java/com/credit/platform/server/provider/HBaseFeatureProvider.java`
HBase-backed feature reader. Reads from `customer_feature` table.
Supports batch Get operations.

### 6. `decision-server/src/main/java/com/credit/platform/server/provider/VariablePrefetcher.java`
Standalone prefetcher that pre-resolves L2 variables before decision execution.
- Called by DATA_PREP DAG node
- Uses CachedVariableProvider to fetch all required L2 vars
- Stores results in a request-scoped cache
- Reduces decision-time latency by front-loading Redis/HBase calls

### 7. `decision-server/src/main/java/com/credit/platform/server/config/CachedProviderConfig.java`
Spring configuration to wire RedisConfig, HBaseConfig, CachedVariableProvider, and VariablePrefetcher beans.

### 8. `decision-server/src/main/java/com/credit/platform/server/config/FeatureProperties.java`
Configuration properties for feature store connection (Redis host/port, HBase quorum, key patterns, TTLs).

### 9. `engine-core/src/main/java/com/credit/platform/engine/core/variable/PrefetchResult.java`
Simple value object holding prefetch results — keeps engine-core free of Redis/HBase imports.

### 10. `decision-server/src/test/java/com/credit/platform/server/provider/CachedVariableProviderTest.java`
Unit tests for the L2 composite provider.

### 11. `decision-server/src/test/java/com/credit/platform/server/provider/VariablePrefetcherTest.java`
Unit tests for the prefetcher.

## Tasks

### Task 1: Create FeatureProperties configuration class
- Define properties for Redis (host, port, password, database)
- Define properties for HBase (quorum, port, table name, column family)
- Define key pattern config and TTL settings
- Use `@ConfigurationProperties(prefix = "feature.store")`

### Task 2: Create RedisConfig for decision-server
- Configure `StringRedisTemplate` bean (matching data-service pattern)
- Connection factory with password support
- Lettuce client for pipeline operations

### Task 3: Create HBaseConfig for decision-server
- Configure HBase `Configuration` and `Connection` beans (matching data-service pattern)
- Connection pooling and timeout settings

### Task 4: Create RedisFeatureProvider
- Implement `VariableProvider` (or internal helper used by CachedVariableProvider)
- Key format: `feature:{featureType}:{customerId}`
- Use Redis pipeline for batch reads
- Deserialize JSON values
- Return Map<String, Object> for resolved variables

### Task 5: Create HBaseFeatureProvider
- Implement HBase read logic (internal helper used by CachedVariableProvider)
- Table: `customer_feature`, CF: `cf`
- Batch Get with filter by featureType
- Deserialize stored values
- Return Map<String, Object> for resolved variables

### Task 6: Create CachedVariableProvider (L2 composite)
- Implements `VariableProvider`
- Redis → HBase fallback chain
- HBase hit → write-back to Redis (1-hour TTL, matching data-service pattern)
- Timeout and error handling with graceful degradation
- Log cache hit/miss metrics

### Task 7: Create PrefetchResult in engine-core
- Lightweight value object: `PrefetchResult(Map<String, Object> variables, Set<String> failed, long latencyMs)`
- Static factory methods for success/partial/failure

### Task 8: Create VariablePrefetcher
- Accept Set<String> variable IDs + customerId
- Filter to L2 CACHED variables only (from VariableRegistry)
- Call CachedVariableProvider.provide()
- Wrap result in PrefetchResult
- Store in request-scoped context for reuse during decision execution
- Support async mode with CompletableFuture

### Task 9: Create CachedProviderConfig Spring wiring
- Wire RedisConfig, HBaseConfig beans
- Create RedisFeatureProvider, HBaseFeatureProvider beans
- Create CachedVariableProvider bean
- Create VariablePrefetcher bean
- Register CachedVariableProvider as L2 provider in VariableEngine
- Add `feature.store.*` properties to application.yml

### Task 10: Add application.yml configuration
- Redis connection properties
- HBase connection properties
- Feature store key patterns
- TTL and timeout settings
- Prefetcher thread pool settings

### Task 11: Unit tests for CachedVariableProvider
- Test Redis hit path
- Test Redis miss → HBase hit path with write-back
- Test complete miss path
- Test timeout/degradation handling

### Task 12: Unit tests for VariablePrefetcher
- Test prefetch filters L2 variables only
- Test async mode
- Test partial failure handling

### Task 13: Verify compilation and tests
- `mvn compile -pl decision-server`
- `mvn test -pl decision-server`
- Ensure no regressions in engine-core tests

## Dependencies
```
Task 1 (FeatureProperties) ──→ Task 4, 5, 6
Task 2 (RedisConfig) ────────→ Task 4
Task 3 (HBaseConfig) ────────→ Task 5
Task 4 (RedisProvider) ──────→ Task 6
Task 5 (HBaseProvider) ──────→ Task 6
Task 6 (CachedProvider) ─────→ Task 8, 9
Task 7 (PrefetchResult) ────→ Task 8
Task 8 (Prefetcher) ────────→ Task 9
Task 9 (Config wiring) ────→ Task 10
Task 10 (YAML) ─────────────→ verification
Task 11-12 (Tests) ────────→ after Task 6, 8
Task 13 (Verify) ───────────→ after all
```
