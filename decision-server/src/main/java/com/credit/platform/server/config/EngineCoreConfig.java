package com.credit.platform.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.credit.platform.engine.core.cache.VersionedRuleCache;
import com.credit.platform.engine.core.scorecard.ScorecardExecutor;
import com.credit.platform.engine.core.trace.LoggingTracePublisher;
import com.credit.platform.engine.core.trace.TracePublisher;

/**
 * 引擎核心 Bean 配置 — 将 engine-core 组件注册到 Spring 容器。
 * <p>
 * engine-core 是纯 Java 模块（无 Spring 依赖），
 * 通过此配置类将其组件以 Bean 形式注入到 decision-server。
 * </p>
 */
@Configuration
public class EngineCoreConfig {

    @Bean
    public VersionedRuleCache versionedRuleCache(
            @Value("${decision.engine.cache.max-size:10000}") int maxSize,
            @Value("${decision.engine.cache.max-versions:5}") int maxVersions) {
        return new VersionedRuleCache(maxSize, maxVersions);
    }

    @Bean
    public ScorecardExecutor scorecardExecutor() {
        return new ScorecardExecutor();
    }

    @Bean
    public TracePublisher tracePublisher() {
        return LoggingTracePublisher.getInstance();
    }
}
