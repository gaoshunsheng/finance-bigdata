package com.credit.platform.engine.core.cache;

/**
 * 热加载监听器接口 — 监听规则变更消息并触发缓存重载。
 * <p>
 * 生产环境由上层 Spring Boot 模块实现:
 * <ul>
 *   <li>监听 RocketMQ 广播消息</li>
 *   <li>解析消息生成 {@link CacheReloadEvent}</li>
 *   <li>调用 {@link VersionedRuleCache#reload(CacheReloadEvent, ArtifactCompiler)} 执行重载</li>
 * </ul>
 * engine-core 不引入 MQ 依赖，仅定义接口。
 * </p>
 *
 * <pre>
 * // Spring Boot 实现示例:
 * {@literal @}Component
 * public class RocketMQReloadListener implements HotReloadListener {
 *     {@literal @}RocketMQMessageListener(topic = "rule-reload")
 *     public void onMessage(String message) {
 *         CacheReloadEvent event = parseMessage(message);
 *         versionedRuleCache.reload(event, this::recompile);
 *     }
 * }
 * </pre>
 */
public interface HotReloadListener {

    /**
     * 收到规则变更消息。
     * <p>
     * 实现要求:
     * <ul>
     *   <li>解析消息生成 {@link CacheReloadEvent}</li>
     *   <li>调用缓存重载逻辑</li>
     *   <li>异常不应阻塞后续消息处理</li>
     * </ul>
     * </p>
     *
     * @param event 缓存重载事件
     */
    void onReload(CacheReloadEvent event);
}
