package com.credit.platform.engine.core.trace;

/**
 * 追踪日志发布器接口 — 异步写入追踪数据。
 * <p>
 * 定义 fire-and-forget 的异步写入契约:
 * <ul>
 *   <li>发布操作不阻塞决策主流程</li>
 *   <li>发布失败不影响决策结果</li>
 *   <li>实现类负责重试和容错</li>
 * </ul>
 * </p>
 * <p>
 * 生产环境实现示例:
 * <ul>
 *   <li>MqTracePublisher — 通过 RocketMQ 广播到 Elasticsearch</li>
 *   <li>KafkaTracePublisher — 通过 Kafka 写入日志系统</li>
 * </ul>
 * engine-core 不引入 MQ/ES 依赖，由上层 Spring Boot 模块提供具体实现。
 * </p>
 */
@FunctionalInterface
public interface TracePublisher {

    /**
     * 异步发布追踪数据。
     * <p>
     * 实现要求:
     * <ul>
     *   <li>此方法必须是非阻塞的（异步执行或写入内存队列）</li>
     *   <li>发布失败不应抛出异常，应内部处理重试或记录日志</li>
     *   <li>不应影响调用方的决策结果和响应时间</li>
     * </ul>
     * </p>
     *
     * @param trace 决策追踪数据
     */
    void publish(DecisionTrace trace);
}
