package com.credit.platform.engine.core.trace;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 日志追踪发布器 — 将追踪数据输出到日志。
 * <p>
 * 用于开发环境和集成测试，通过 JUL 输出追踪数据。
 * 不阻塞调用线程，日志写入失败不影响决策。
 * </p>
 */
public final class LoggingTracePublisher implements TracePublisher {

    private static final Logger LOGGER = Logger.getLogger(LoggingTracePublisher.class.getName());
    private static final LoggingTracePublisher INSTANCE = new LoggingTracePublisher();

    private LoggingTracePublisher() {
        // 单例
    }

    /**
     * 获取单例实例。
     *
     * @return 日志发布器实例
     */
    public static LoggingTracePublisher getInstance() {
        return INSTANCE;
    }

    @Override
    public void publish(DecisionTrace trace) {
        try {
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.info(String.format(
                    "[DecisionTrace] traceId=%s decisionId=%s result=%s score=%s "
                        + "nodes=%d path=%s duration=%dms",
                    trace.getTraceId(),
                    trace.getDecisionId(),
                    trace.getFinalResult(),
                    trace.getFinalScore(),
                    trace.getEntryCount(),
                    trace.getDecisionPath(),
                    trace.getTotalDurationMs()
                ));
            }

            // L4 审计级 — 输出每个节点的详细信息
            if (LOGGER.isLoggable(Level.FINE)) {
                for (TraceEntry entry : trace.getEntries()) {
                    LOGGER.fine(String.format(
                        "  [Node] %s type=%s duration=%dms success=%s output=%s",
                        entry.getNodeId(),
                        entry.getNodeType(),
                        entry.getDurationMs(),
                        entry.isSuccess(),
                        entry.getOutputSnapshot()
                    ));
                }
            }
        } catch (Exception e) {
            // 日志写入失败不影响决策
            LOGGER.log(Level.WARNING, "Failed to publish trace log", e);
        }
    }
}
