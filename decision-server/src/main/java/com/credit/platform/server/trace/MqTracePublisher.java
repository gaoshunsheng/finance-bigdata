package com.credit.platform.server.trace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.credit.platform.engine.core.trace.DecisionTrace;
import com.credit.platform.engine.core.trace.TracePublisher;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * RocketMQ 追踪发布器 — 通过 RocketMQ 将追踪数据异步广播。
 *
 * <p>当 {@code trace.publisher=rocketmq} 时激活。
 * 使用反射调用 RocketMQTemplate，无需编译期依赖。
 * 需要运行时 classpath 包含 rocketmq-spring-boot-starter。
 * 如果 RocketMQTemplate 不可用，降级为日志输出。</p>
 */
@Component
@ConditionalOnProperty(name = "trace.publisher", havingValue = "rocketmq")
public class MqTracePublisher implements TracePublisher {

    private static final Logger log = LoggerFactory.getLogger(MqTracePublisher.class);
    private static final String TOPIC = "decision-trace";

    private final Object rocketMQTemplate; // may be null
    private final ObjectMapper objectMapper;
    private final java.lang.reflect.Method convertAndSendMethod;

    public MqTracePublisher(ApplicationContext ctx) {
        Object template = null;
        java.lang.reflect.Method method = null;
        try {
            Class<?> clazz = Class.forName("org.apache.rocketmq.spring.core.RocketMQTemplate");
            template = ctx.getBeanProvider(clazz).getIfAvailable();
            if (template != null) {
                method = clazz.getMethod("convertAndSend", String.class, Object.class);
                log.info("[MqTracePublisher] RocketMQTemplate detected, trace publishing enabled");
            }
        } catch (ClassNotFoundException e) {
            log.warn("[MqTracePublisher] RocketMQTemplate not on classpath, falling back to logging");
        } catch (Exception e) {
            log.warn("[MqTracePublisher] Failed to initialize RocketMQTemplate: {}", e.getMessage());
        }
        this.rocketMQTemplate = template;
        this.convertAndSendMethod = method;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void publish(DecisionTrace trace) {
        if (rocketMQTemplate == null || convertAndSendMethod == null) {
            publishAsLog(trace);
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(trace);
            convertAndSendMethod.invoke(rocketMQTemplate, TOPIC, json);
        } catch (Exception e) {
            log.warn("[MqTracePublisher] Failed to publish trace, traceId={}: {}",
                trace.getTraceId(), e.getMessage());
            publishAsLog(trace);
        }
    }

    private void publishAsLog(DecisionTrace trace) {
        try {
            log.info("[DecisionTrace] traceId={} decisionId={} result={} score={} duration={}ms",
                trace.getTraceId(), trace.getDecisionId(),
                trace.getFinalResult(), trace.getFinalScore(), trace.getTotalDurationMs());
        } catch (Exception e) {
            log.warn("[MqTracePublisher] Failed to log trace: {}", e.getMessage());
        }
    }
}
