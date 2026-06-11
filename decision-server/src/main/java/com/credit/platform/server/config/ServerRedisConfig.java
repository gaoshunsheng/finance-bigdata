package com.credit.platform.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 配置 — 用于 L2 缓存变量查询。
 *
 * <p>仅在 {@code feature.store.enabled=true} 时激活。
 * <p>特征 Key 命名规范: {@code feature:{featureType}:{customerId}}
 */
@Configuration
@ConditionalOnProperty(name = "feature.store.enabled", havingValue = "true")
public class ServerRedisConfig {

    private static final Logger log = LoggerFactory.getLogger(ServerRedisConfig.class);

    @Bean
    public StringRedisTemplate stringRedisTemplate(FeatureProperties properties) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(properties.getRedis().getHost());
        config.setPort(properties.getRedis().getPort());
        config.setDatabase(properties.getRedis().getDatabase());
        if (properties.getRedis().getPassword() != null
                && !properties.getRedis().getPassword().isEmpty()) {
            config.setPassword(properties.getRedis().getPassword());
        }

        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        factory.setTimeout(properties.getRedis().getTimeoutMs());
        factory.afterPropertiesSet();

        log.info("Redis 连接初始化: host={}:{}", properties.getRedis().getHost(), properties.getRedis().getPort());
        return new StringRedisTemplate(factory);
    }
}
