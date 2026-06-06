package com.credit.platform.data.flink.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;

/**
 * Redis 特征 Sink — 将窗口聚合结果写入 Redis 缓存。
 *
 * <p>Key 格式: {@code feature:{featureType}:{customerId}}（由 FeatureKey 生成）
 * <p>Value: JSON 字符串（特征 Map 序列化）
 * <p>TTL: 24 小时（热特征缓存）
 *
 * <p>连接失败时日志告警并跳过，保证作业容错运行（graceful degradation）。
 */
public class RedisFeatureSink implements SinkFunction<Map<String, Object>> {

    private static final Logger log = LoggerFactory.getLogger(RedisFeatureSink.class);
    private static final long serialVersionUID = 1L;

    private static final long TTL_SECONDS = 24 * 3600; // 24 hours

    private final String redisUri;
    private final String featureType;

    private transient RedisClient redisClient;
    private transient StatefulRedisConnection<String, String> connection;
    private transient RedisCommands<String, String> commands;
    private transient ObjectMapper objectMapper;

    /**
     * @param redisUri    Redis 连接 URI，如 redis://localhost:6379
     * @param featureType 特征类型，用于构建 Redis Key
     */
    public RedisFeatureSink(String redisUri, String featureType) {
        this.redisUri = redisUri;
        this.featureType = featureType;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        this.objectMapper = new ObjectMapper();
        try {
            this.redisClient = RedisClient.create(RedisURI.create(redisUri));
            this.connection = redisClient.connect();
            this.commands = connection.sync();
            log.info("[RedisFeatureSink] 连接成功: {}", redisUri);
        } catch (Exception e) {
            log.warn("[RedisFeatureSink] Redis 连接失败，将跳过写入: {}", e.getMessage());
        }
    }

    @Override
    public void invoke(Map<String, Object> value, Context context) throws Exception {
        if (commands == null) {
            return; // 连接未建立，跳过
        }
        try {
            String customerId = (String) value.get("customerId");
            if (customerId == null || customerId.isEmpty()) {
                return;
            }
            String key = FeatureKey.redisKey(featureType, customerId);
            String json = objectMapper.writeValueAsString(value);
            commands.setex(key, TTL_SECONDS, json);
        } catch (Exception e) {
            log.warn("[RedisFeatureSink] 写入失败，跳过: {}", e.getMessage());
        }
    }

    @Override
    public void close() throws Exception {
        try {
            if (connection != null) {
                connection.close();
            }
            if (redisClient != null) {
                redisClient.shutdown(Duration.ofSeconds(2));
            }
        } catch (Exception e) {
            log.warn("[RedisFeatureSink] 关闭连接异常: {}", e.getMessage());
        }
    }
}
