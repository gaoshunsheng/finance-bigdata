package com.credit.platform.engine.core.cache;

import java.util.Objects;

/**
 * 版本化编译产物 — 包装编译后的规则/流程/评分卡，附加版本信息。
 * <p>
 * 支持版本快照和 CopyOnWrite 语义:
 * <ul>
 *   <li>每个编译产物关联一个版本号</li>
 *   <li>缓存替换时创建新的 VersionedArtifact，在途请求持有旧引用</li>
 *   <li>GC 自动回收无引用的旧版本</li>
 * </ul>
 * </p>
 *
 * @param <T> 编译产物类型 (CompiledRule / CompiledDAG / CompiledScorecard 等)
 */
public final class VersionedArtifact<T> {

    private final String artifactId;
    private final int version;
    private final long createdAtMs;
    private final T artifact;

    /**
     * 创建版本化编译产物。
     *
     * @param artifactId  产物 ID (ruleId / flowId / scorecardId)
     * @param version     版本号
     * @param artifact    编译产物
     */
    public VersionedArtifact(String artifactId, int version, T artifact) {
        this.artifactId = Objects.requireNonNull(artifactId, "artifactId must not be null");
        this.version = version;
        this.createdAtMs = System.currentTimeMillis();
        this.artifact = Objects.requireNonNull(artifact, "artifact must not be null");
    }

    public String getArtifactId() { return artifactId; }
    public int getVersion() { return version; }
    public long getCreatedAtMs() { return createdAtMs; }
    public T getArtifact() { return artifact; }

    /**
     * 判断此产物是否比目标版本更新。
     *
     * @param other 另一个版本化产物
     * @return 是否更新
     */
    public boolean isNewerThan(VersionedArtifact<T> other) {
        return other == null || this.version > other.version;
    }

    @Override
    public String toString() {
        return "VersionedArtifact{id='" + artifactId + "', v=" + version
            + ", created=" + createdAtMs + '}';
    }
}
