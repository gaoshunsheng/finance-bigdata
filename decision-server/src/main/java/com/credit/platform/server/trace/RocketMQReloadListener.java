package com.credit.platform.server.trace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.credit.platform.engine.core.cache.CacheReloadEvent;
import com.credit.platform.engine.core.cache.HotReloadListener;
import com.credit.platform.engine.core.cache.VersionedRuleCache;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * RocketMQ 缓存重载监听器 — 监听规则变更消息并触发缓存热加载。
 *
 * <p>当 {@code cache.reload.listener=rocketmq} 时激活。
 * 不直接依赖 RocketMQ 客户端 — 由外部消费者调用 {@link #onMessage(String)}。
 * 无需编译期 RocketMQ 依赖。</p>
 *
 * <p>消息 JSON 格式:
 * <pre>{"artifactId":"rule-001","type":"RULE","version":2,"source":"MQ"}</pre></p>
 */
@Component
@ConditionalOnProperty(name = "cache.reload.listener", havingValue = "rocketmq")
public class RocketMQReloadListener implements HotReloadListener {

    private static final Logger log = LoggerFactory.getLogger(RocketMQReloadListener.class);

    private final VersionedRuleCache versionedRuleCache;
    private final ObjectMapper objectMapper;

    public RocketMQReloadListener(VersionedRuleCache versionedRuleCache) {
        this.versionedRuleCache = versionedRuleCache;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 处理从 MQ 接收到的规则重载消息。
     *
     * @param message MQ 消息 JSON 字符串
     */
    public void onMessage(String message) {
        try {
            CacheReloadEvent event = parseMessage(message);
            if (event != null) {
                onReload(event);
            }
        } catch (Exception e) {
            log.warn("[RocketMQReloadListener] Failed to process reload message: {}",
                message, e);
        }
    }

    @Override
    public void onReload(CacheReloadEvent event) {
        try {
            log.info("[RocketMQReloadListener] Reloading cache: {}", event);
            versionedRuleCache.reload(event, (artifactId, version) -> null);
        } catch (Exception e) {
            log.warn("[RocketMQReloadListener] Failed to reload cache for: {}", event, e);
        }
    }

    private CacheReloadEvent parseMessage(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            String artifactId = node.path("artifactId").asText("");
            String typeStr = node.path("type").asText("ALL");
            int version = node.path("version").asInt(0);
            String sourceStr = node.path("source").asText("MQ");

            CacheReloadEvent.Type type;
            try { type = CacheReloadEvent.Type.valueOf(typeStr); }
            catch (IllegalArgumentException e) { type = CacheReloadEvent.Type.ALL; }

            CacheReloadEvent.Source source;
            try { source = CacheReloadEvent.Source.valueOf(sourceStr); }
            catch (IllegalArgumentException e) { source = CacheReloadEvent.Source.MQ; }

            return new CacheReloadEvent(artifactId, type, version, source);
        } catch (Exception e) {
            log.warn("[RocketMQReloadListener] Failed to parse message: {}", message, e);
            return null;
        }
    }
}
