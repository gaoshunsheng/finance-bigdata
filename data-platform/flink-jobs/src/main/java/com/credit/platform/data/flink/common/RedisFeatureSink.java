package com.credit.platform.data.flink.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Redis 特征 Sink — 将窗口聚合结果写入 Redis 缓存。
 *
 * <p>Key 格式: {@code feature:{featureType}:{customerId}}（由 FeatureKey 生成）
 * <p>Value: JSON 字符串（特征 Map 序列化）
 * <p>TTL: 24 小时（热特征缓存）
 *
 * <p>连接失败时日志告警并跳过，保证作业容错运行（graceful degradation）。
 */
public class RedisFeatureSink extends RichSinkFunction<Map<String, Object>> {

    private static final Logger log = LoggerFactory.getLogger(RedisFeatureSink.class);
    private static final long serialVersionUID = 1L;

    private static final long TTL_SECONDS = 24 * 3600; // 24 hours

    private final String redisUri;
    private final String featureType;

    private static final int MAX_RECONNECT_ATTEMPTS = 3;

    private transient RedisClient redisClient;
    private transient StatefulRedisConnection<String, String> connection;
    private transient RedisCommands<String, String> commands;
    private transient ObjectMapper objectMapper;
    private transient int consecutiveFailures;

    public RedisFeatureSink(String redisUri, String featureType) {
        this.redisUri = redisUri;
        this.featureType = featureType;
    }

    @Override
    public void open(org.apache.flink.configuration.Configuration parameters) throws Exception {
        this.objectMapper = new ObjectMapper();
        this.consecutiveFailures = 0;
        tryConnect();
    }

    private void tryConnect() {
        try {
            // Close old connection if any
            closeConnection();
            this.redisClient = RedisClient.create(RedisURI.create(redisUri));
            this.connection = redisClient.connect();
            this.commands = connection.sync();
            this.consecutiveFailures = 0;
            log.info("[RedisFeatureSink] 连接成功: {}", redisUri);
        } catch (Exception e) {
            this.commands = null;
            log.warn("[RedisFeatureSink] Redis 连接失败，将在下次写入时重试: {}", e.getMessage());
        }
    }

    @Override
    public void invoke(Map<String, Object> value, Context context) throws Exception {
        if (commands == null) {
            // Attempt reconnection if previously failed
            if (consecutiveFailures < MAX_RECONNECT_ATTEMPTS) {
                tryConnect();
            }
            if (commands == null) {
                return;
            }
        }
        try {
            String customerId = (String) value.get("customerId");
            if (customerId == null || customerId.isEmpty()) {
                return;
            }
            String key = FeatureKey.redisKey(featureType, customerId);
            String json = objectMapper.writeValueAsString(value);
            commands.setex(key, TTL_SECONDS, json);
            consecutiveFailures = 0;
        } catch (Exception e) {
            consecutiveFailures++;
            log.warn("[RedisFeatureSink] 写入失败 ({}/{}): {}",
                    consecutiveFailures, MAX_RECONNECT_ATTEMPTS, e.getMessage());
            if (consecutiveFailures >= MAX_RECONNECT_ATTEMPTS) {
                log.error("[RedisFeatureSink] 连续失败次数达到上限，尝试重连");
                tryConnect();
            }
        }
    }

    private void closeConnection() {
        try {
            if (connection != null) {
                connection.close();
                connection = null;
            }
            if (redisClient != null) {
                redisClient.shutdown(2, 2, TimeUnit.SECONDS);
                redisClient = null;
            }
            commands = null;
        } catch (Exception e) {
            log.warn("[RedisFeatureSink] 关闭连接异常: {}", e.getMessage());
        }
    }

    @Override
    public void close() throws Exception {
        closeConnection();
    }
}
