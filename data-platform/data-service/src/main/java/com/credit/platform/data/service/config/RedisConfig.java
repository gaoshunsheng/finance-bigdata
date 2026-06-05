package com.credit.platform.data.service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 配置 — 用于实时特征缓存查询。
 *
 * <p>连接 Redis Cluster，提供 StringRedisTemplate 供 FeatureQueryService 使用。
 * <p>特征 Key 命名规范: feature:{featureType}:{customerId}
 * <p>TTL: 1-24 小时（按特征类型配置）
 */
@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
