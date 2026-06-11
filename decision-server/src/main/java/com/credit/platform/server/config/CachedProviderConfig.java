package com.credit.platform.server.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.credit.platform.engine.core.variable.VariableEngine;
import com.credit.platform.engine.core.variable.VariableLayer;
import com.credit.platform.engine.core.variable.VariableRegistry;
import com.credit.platform.server.provider.CachedVariableProvider;
import com.credit.platform.server.provider.HBaseFeatureProvider;
import com.credit.platform.server.provider.RedisFeatureProvider;
import com.credit.platform.server.provider.VariablePrefetcher;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * L2 缓存变量提供者 Spring 配置。
 *
 * <p>仅在 {@code feature.store.enabled=true} 时激活。
 * <p>装配:
 * <ol>
 *   <li>RedisFeatureProvider — Redis 特征读取</li>
 *   <li>HBaseFeatureProvider — HBase 特征读取 + Redis 回写</li>
 *   <li>CachedVariableProvider — L2 复合提供者 (Redis→HBase)</li>
 *   <li>VariablePrefetcher — 预取引擎</li>
 *   <li>prefetchExecutor — 预取专用线程池</li>
 * </ol>
 */
@Configuration
@ConditionalOnProperty(name = "feature.store.enabled", havingValue = "true")
@EnableConfigurationProperties(FeatureProperties.class)
public class CachedProviderConfig {

    private static final Logger log = LoggerFactory.getLogger(CachedProviderConfig.class);

    @Bean
    public RedisFeatureProvider redisFeatureProvider(StringRedisTemplate stringRedisTemplate,
                                                      FeatureProperties properties,
                                                      ObjectMapper objectMapper) {
        log.info("RedisFeatureProvider 初始化");
        return new RedisFeatureProvider(stringRedisTemplate, properties, objectMapper);
    }

    @Bean
    public HBaseFeatureProvider hbaseFeatureProvider(
            org.apache.hadoop.hbase.client.Connection hbaseConnection,
            FeatureProperties properties,
            ObjectMapper objectMapper,
            RedisFeatureProvider redisFeatureProvider) {
        log.info("HBaseFeatureProvider 初始化: table={}", properties.getHbase().getTableName());
        return new HBaseFeatureProvider(hbaseConnection, properties, objectMapper, redisFeatureProvider);
    }

    @Bean
    public CachedVariableProvider cachedVariableProvider(RedisFeatureProvider redisFeatureProvider,
                                                          HBaseFeatureProvider hbaseFeatureProvider,
                                                          FeatureProperties properties) {
        log.info("CachedVariableProvider (L2) 初始化");
        return new CachedVariableProvider(redisFeatureProvider, hbaseFeatureProvider, properties);
    }

    @Bean("prefetchExecutor")
    public Executor prefetchExecutor(FeatureProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("prefetch-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        log.info("预取线程池初始化: core=4, max=8");
        return executor;
    }

    @Bean
    public VariablePrefetcher variablePrefetcher(VariableRegistry variableRegistry,
                                                   CachedVariableProvider cachedVariableProvider,
                                                   @Qualifier("prefetchExecutor") Executor prefetchExecutor) {
        log.info("VariablePrefetcher 初始化");
        return new VariablePrefetcher(variableRegistry, cachedVariableProvider, prefetchExecutor);
    }

    /**
     * 注册 L2 CACHED 层 VariableProvider 到 VariableEngine。
     *
     * <p>使用 BeanPostProcessor 方式，在 VariableEngine Bean 创建后注入 L2 provider，
     * 避免 ExternalApiConfig 中的 VariableEngine Bean 定义循环依赖。
     */
    @Bean
    public VariableEngineL2Registrar variableEngineL2Registrar(VariableEngine variableEngine,
                                                                 CachedVariableProvider cachedVariableProvider) {
        variableEngine.setProvider(VariableLayer.CACHED, cachedVariableProvider);
        log.info("VariableEngine 已注册 L2 CachedVariableProvider");
        return new VariableEngineL2Registrar();
    }

    /**
     * 标记类 — 仅用于触发 L2 provider 注册。
     */
    public static class VariableEngineL2Registrar {
    }
}
