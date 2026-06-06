package com.credit.platform.engine.core.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 版本化规则缓存 — 带版本快照和 CopyOnWrite 语义。
 * <p>
 * 核心特性:
 * <ul>
 *   <li><b>CopyOnWrite</b>: 缓存替换使用 AtomicReference 交换引用，
 *       在途请求持有旧版本引用继续执行，新请求获取新版本</li>
 *   <li><b>版本快照</b>: 保留最近 N 个版本的编译产物，支持一键回滚</li>
 *   <li><b>热加载</b>: 通过 {@link #reload} 方法接收重载事件，触发重新编译和缓存替换</li>
 * </ul>
 * </p>
 *
 * <h3>缓存分区</h3>
 * <ul>
 *   <li><b>rules</b> — 编译后的规则 (CompiledRule)，最大 10,000 条</li>
 *   <li><b>flows</b> — 编译后的决策流 (CompiledDAG)，最大 1,000 条</li>
 *   <li><b>scorecards</b> — 编译后的评分卡 (CompiledScorecard)，最大 1,000 条</li>
 * </ul>
 *
 * <pre>
 * VersionedRuleCache cache = new VersionedRuleCache();
 * cache.put("rule-001", compiledRule, 1);
 *
 * // CopyOnWrite: 在途请求持有旧引用
 * VersionedArtifact&lt;?&gt; artifact = cache.get("rule-001");
 *
 * // 热加载: MQ 触发重载
 * cache.reload(event, (id, ver) -&gt; recompileRule(id));
 *
 * // 回滚
 * cache.rollback("rule-001", 1);
 * </pre>
 */
public class VersionedRuleCache {

    private static final Logger LOGGER = Logger.getLogger(VersionedRuleCache.class.getName());

    /** 默认保留版本数 */
    private static final int DEFAULT_MAX_VERSIONS = 5;

    private final int maxVersions;

    /** 主缓存: artifactId → 当前版本的 AtomicReference (CopyOnWrite) */
    private final Cache<String, AtomicReference<VersionedArtifact<?>>> mainCache;

    /** 版本历史: artifactId → 版本快照栈 */
    private final ConcurrentHashMap<String, Deque<VersionedArtifact<?>>> versionHistory;

    /** 版本历史写入锁 (per-key) */
    private final ConcurrentHashMap<String, ReentrantLock> versionLocks;

    /**
     * 构造函数 — 使用默认配置。
     */
    public VersionedRuleCache() {
        this(10_000, DEFAULT_MAX_VERSIONS);
    }

    /**
     * 构造函数 — 自定义缓存容量和版本保留数。
     *
     * @param maxSize     最大缓存条目数
     * @param maxVersions 每个产物保留的历史版本数
     */
    public VersionedRuleCache(int maxSize, int maxVersions) {
        this.maxVersions = maxVersions;
        this.mainCache = Caffeine.newBuilder()
            .maximumSize(maxSize)
            .expireAfterWrite(24, TimeUnit.HOURS)
            .recordStats()
            .build();
        this.versionHistory = new ConcurrentHashMap<>();
        this.versionLocks = new ConcurrentHashMap<>();
    }

    // ==================== 存取操作 (CopyOnWrite) ====================

    /**
     * 存入编译产物。
     * <p>
     * CopyOnWrite 语义: 创建新的 AtomicReference 并替换，在途请求持有旧引用不受影响。
     * </p>
     *
     * @param artifactId 产物 ID
     * @param artifact   编译产物
     * @param version    版本号
     * @param <T>        产物类型
     */
    public <T> void put(String artifactId, T artifact, int version) {
        Objects.requireNonNull(artifactId, "artifactId must not be null");
        Objects.requireNonNull(artifact, "artifact must not be null");

        VersionedArtifact<T> versioned = new VersionedArtifact<>(artifactId, version, artifact);

        // 安全修复: saveToHistory + ref.set 必须在同一锁内，防止竞态
        ReentrantLock lock = versionLocks.computeIfAbsent(artifactId, k -> new ReentrantLock());
        lock.lock();
        try {
            AtomicReference<VersionedArtifact<?>> ref = mainCache.get(artifactId,
                k -> new AtomicReference<>());

            // 将当前版本存入历史，然后原子替换
            VersionedArtifact<?> old = ref.get();
            if (old != null) {
                saveToHistory(artifactId, old);
            }
            ref.set(versioned);

            // 确保主缓存中有引用
            mainCache.put(artifactId, ref);
        } finally {
            lock.unlock();
        }

        LOGGER.log(Level.FINE, "Cache PUT: {0} v{1}", new Object[]{artifactId, version});
    }

    /**
     * 获取当前版本的编译产物。
     * <p>
     * CopyOnWrite 语义: 返回当前 AtomicReference 指向的对象引用。
     * 即使缓存被并发更新，此引用仍然有效（旧版本）。
     * </p>
     *
     * @param artifactId 产物 ID
     * @return 版本化产物，不存在时返回 null
     */
    public VersionedArtifact<?> get(String artifactId) {
        AtomicReference<VersionedArtifact<?>> ref = mainCache.getIfPresent(artifactId);
        return ref != null ? ref.get() : null;
    }

    /**
     * 获取编译产物（不带版本信息）。
     *
     * @param artifactId 产物 ID
     * @return 编译产物，不存在时返回 null
     */
    public Object getArtifact(String artifactId) {
        VersionedArtifact<?> versioned = get(artifactId);
        return versioned != null ? versioned.getArtifact() : null;
    }

    /**
     * 获取当前版本号。
     *
     * @param artifactId 产物 ID
     * @return 版本号，不存在时返回 -1
     */
    public int getVersion(String artifactId) {
        VersionedArtifact<?> versioned = get(artifactId);
        return versioned != null ? versioned.getVersion() : -1;
    }

    // ==================== 版本快照与回滚 ====================

    /**
     * 获取指定产物的版本历史。
     *
     * @param artifactId 产物 ID
     * @return 版本历史列表（按时间降序），不可修改
     */
    public List<VersionedArtifact<?>> getVersionHistory(String artifactId) {
        Deque<VersionedArtifact<?>> history = versionHistory.get(artifactId);
        if (history == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(history));
    }

    /**
     * 回滚到指定版本。
     * <p>
     * 从版本历史中找到目标版本，原子替换当前版本。
     * 在途请求继续使用旧版本，新请求使用回滚后的版本。
     * </p>
     *
     * @param artifactId 产物 ID
     * @param targetVersion 目标版本号
     * @return 回滚后的版本化产物，失败返回 null
     */
    public VersionedArtifact<?> rollback(String artifactId, int targetVersion) {
        Deque<VersionedArtifact<?>> history = versionHistory.get(artifactId);
        if (history == null) {
            LOGGER.warning("Rollback failed: no version history for " + artifactId);
            return null;
        }

        ReentrantLock lock = versionLocks.computeIfAbsent(artifactId, k -> new ReentrantLock());
        lock.lock();
        try {
            for (VersionedArtifact<?> artifact : history) {
                if (artifact.getVersion() == targetVersion) {
                    // 当前版本存入历史
                    AtomicReference<VersionedArtifact<?>> ref = mainCache.getIfPresent(artifactId);
                    if (ref != null) {
                        VersionedArtifact<?> current = ref.get();
                        if (current != null) {
                            saveToHistory(artifactId, current);
                        }
                        // 原子替换为回滚版本
                        ref.set(artifact);
                        LOGGER.info("Rollback: " + artifactId + " → v" + targetVersion);
                        return artifact;
                    }
                }
            }
            LOGGER.warning("Rollback failed: version " + targetVersion
                + " not found for " + artifactId);
            return null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 回滚到上一个版本。
     *
     * @param artifactId 产物 ID
     * @return 回滚后的版本化产物，无历史时返回 null
     */
    public VersionedArtifact<?> rollbackToPrevious(String artifactId) {
        Deque<VersionedArtifact<?>> history = versionHistory.get(artifactId);
        if (history == null || history.isEmpty()) {
            return null;
        }

        ReentrantLock lock = versionLocks.computeIfAbsent(artifactId, k -> new ReentrantLock());
        lock.lock();
        try {
            VersionedArtifact<?> previous = history.peekFirst();
            if (previous != null) {
                AtomicReference<VersionedArtifact<?>> ref = mainCache.getIfPresent(artifactId);
                if (ref != null) {
                    VersionedArtifact<?> current = ref.get();
                    if (current != null) {
                        // 将当前版本移回历史栈顶之后
                        history.remove(previous);
                        saveToHistory(artifactId, current);
                    }
                    ref.set(previous);
                    LOGGER.info("Rollback to previous: " + artifactId + " → v" + previous.getVersion());
                    return previous;
                }
            }
            return null;
        } finally {
            lock.unlock();
        }
    }

    // ==================== 热加载 ====================

    /**
     * 执行热加载 — 重新编译并替换缓存。
     * <p>
     * 流程:
     * <ol>
     *   <li>调用 compiler 重新编译</li>
     *   <li>创建新的 VersionedArtifact</li>
     *   <li>原子替换缓存 (CopyOnWrite)</li>
     * </ol>
     * 编译失败时不影响现有缓存。
     * </p>
     *
     * @param event    重载事件
     * @param compiler 编译产物提供器
     * @param <T>      产物类型
     * @return 新的版本化产物，编译失败返回 null
     */
    public <T> VersionedArtifact<T> reload(CacheReloadEvent event, ArtifactCompiler<T> compiler) {
        Objects.requireNonNull(event, "event must not be null");
        Objects.requireNonNull(compiler, "compiler must not be null");

        try {
            T artifact = compiler.compile(event.getArtifactId(), event.getVersion());
            if (artifact == null) {
                LOGGER.warning("Reload failed: compiler returned null for "
                    + event.getArtifactId());
                return null;
            }

            @SuppressWarnings("unchecked")
            VersionedArtifact<T> versioned = new VersionedArtifact<>(
                event.getArtifactId(), event.getVersion(), artifact);

            // 安全修复: saveToHistory + ref.set 在同一锁内
            ReentrantLock lock = versionLocks.computeIfAbsent(event.getArtifactId(), k -> new ReentrantLock());
            lock.lock();
            try {
                // CopyOnWrite: 原子替换
                AtomicReference<VersionedArtifact<?>> ref = mainCache.get(event.getArtifactId(),
                    k -> new AtomicReference<>());
                VersionedArtifact<?> old = ref.get();
                if (old != null) {
                    saveToHistory(event.getArtifactId(), old);
                }
                ref.set(versioned);
                mainCache.put(event.getArtifactId(), ref);
            } finally {
                lock.unlock();
            }

            LOGGER.info("Reload: " + event.getArtifactId() + " v" + event.getVersion()
                + " from " + event.getSource());
            return versioned;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Reload failed for " + event.getArtifactId(), e);
            return null;
        }
    }

    // ==================== 失效操作 ====================

    /**
     * 失效指定产物。
     *
     * @param artifactId 产物 ID
     */
    public void invalidate(String artifactId) {
        // 安全修复: 先获取锁再移除，防止并发 rollback/invalidate 竞态
        ReentrantLock lock = versionLocks.remove(artifactId);
        if (lock != null) {
            lock.lock();
            try {
                mainCache.invalidate(artifactId);
                versionHistory.remove(artifactId);
            } finally {
                lock.unlock();
            }
        } else {
            mainCache.invalidate(artifactId);
            versionHistory.remove(artifactId);
        }
    }

    /**
     * 清除所有缓存。
     */
    public void invalidateAll() {
        mainCache.invalidateAll();
        versionHistory.clear();
        versionLocks.clear();
    }

    // ==================== 统计信息 ====================

    /**
     * 获取缓存统计信息。
     *
     * @return 统计信息 Map
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        var cacheStats = mainCache.stats();
        stats.put("hitRate", cacheStats.hitRate());
        stats.put("hitCount", cacheStats.hitCount());
        stats.put("missCount", cacheStats.missCount());
        stats.put("evictionCount", cacheStats.evictionCount());
        stats.put("size", mainCache.estimatedSize());
        stats.put("versionedKeys", versionHistory.size());
        return stats;
    }

    /**
     * 获取所有缓存键。
     *
     * @return 缓存键集合
     */
    public Map<String, Object> getAllEntries() {
        Map<String, Object> entries = new HashMap<>();
        mainCache.asMap().forEach((key, ref) -> {
            VersionedArtifact<?> artifact = ref.get();
            if (artifact != null) {
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("version", artifact.getVersion());
                info.put("createdAt", artifact.getCreatedAtMs());
                info.put("type", artifact.getArtifact().getClass().getSimpleName());
                Deque<VersionedArtifact<?>> history = versionHistory.get(key);
                info.put("historyVersions", history != null ? history.size() : 0);
                entries.put(key, info);
            }
        });
        return entries;
    }

    // ==================== 内部方法 ====================

    private void saveToHistory(String artifactId, VersionedArtifact<?> artifact) {
        ReentrantLock lock = versionLocks.computeIfAbsent(artifactId, k -> new ReentrantLock());
        lock.lock();
        try {
            Deque<VersionedArtifact<?>> history =
                versionHistory.computeIfAbsent(artifactId, k -> new LinkedList<>());
            history.addFirst(artifact);

            // 保留最近 N 个版本
            while (history.size() > maxVersions) {
                history.removeLast();
            }
        } finally {
            lock.unlock();
        }
    }
}
