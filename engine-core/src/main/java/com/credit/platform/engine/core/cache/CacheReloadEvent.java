package com.credit.platform.engine.core.cache;

/**
 * 缓存重载事件 — 触发缓存更新的数据模型。
 * <p>
 * 由 HotReloadListener 接收 MQ 广播消息后生成，包含:
 * <ul>
 *   <li>artifactId — 需要重载的产物 ID</li>
 *   <li>artifactType — 产物类型 (rule/flow/scorecard)</li>
 *   <li>version — 目标版本号</li>
 *   <li>source — 触发来源 (MANUAL/MQ/GRADED_RELEASE/ROLLBACK)</li>
 * </ul>
 * </p>
 */
public final class CacheReloadEvent {

    /** 产物类型枚举 */
    public enum Type {
        RULE, FLOW, SCORECARD, ALL
    }

    /** 触发来源枚举 */
    public enum Source {
        /** MQ 广播消息触发 */
        MQ,
        /** 手动触发 */
        MANUAL,
        /** 灰度发布触发 */
        GRADED_RELEASE,
        /** 回滚触发 */
        ROLLBACK
    }

    private final String artifactId;
    private final Type type;
    private final int version;
    private final Source source;

    /**
     * 创建缓存重载事件。
     *
     * @param artifactId 产物 ID
     * @param type       产物类型
     * @param version    目标版本号
     * @param source     触发来源
     */
    public CacheReloadEvent(String artifactId, Type type, int version, Source source) {
        this.artifactId = artifactId;
        this.type = type;
        this.version = version;
        this.source = source;
    }

    public String getArtifactId() { return artifactId; }
    public Type getType() { return type; }
    public int getVersion() { return version; }
    public Source getSource() { return source; }

    /**
     * 是否为全量重载。
     *
     * @return 是否需要重载所有产物
     */
    public boolean isReloadAll() {
        return type == Type.ALL;
    }

    @Override
    public String toString() {
        return "CacheReloadEvent{id='" + artifactId + "', type=" + type
            + ", v=" + version + ", source=" + source + '}';
    }
}
