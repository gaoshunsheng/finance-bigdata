package com.credit.platform.server.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 线程池配置 — DAG 并行执行 + 外部 API 调用。
 * <p>
 * 两个独立线程池:
 * <ul>
 *   <li><b>dagExecutionExecutor</b> — DAG 节点并行执行，CPU 密集型</li>
 *   <li><b>externalApiExecutor</b> — 外部 API 调用，IO 密集型</li>
 * </ul>
 * </p>
 */
@Configuration
public class ThreadPoolConfig {

    @Bean("dagExecutionExecutor")
    public Executor dagExecutionExecutor(
            @Value("${decision.engine.thread-pool.dag-execution.core-size:8}") int coreSize,
            @Value("${decision.engine.thread-pool.dag-execution.max-size:32}") int maxSize,
            @Value("${decision.engine.thread-pool.dag-execution.queue-capacity:200}") int queueCapacity) {
        return createExecutor(coreSize, maxSize, queueCapacity, "dag-exec-");
    }

    @Bean("externalApiExecutor")
    public Executor externalApiExecutor(
            @Value("${decision.engine.thread-pool.external-api.core-size:4}") int coreSize,
            @Value("${decision.engine.thread-pool.external-api.max-size:16}") int maxSize,
            @Value("${decision.engine.thread-pool.external-api.queue-capacity:100}") int queueCapacity) {
        return createExecutor(coreSize, maxSize, queueCapacity, "ext-api-");
    }

    private ThreadPoolTaskExecutor createExecutor(int coreSize, int maxSize,
                                                   int queueCapacity, String prefix) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(prefix);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
