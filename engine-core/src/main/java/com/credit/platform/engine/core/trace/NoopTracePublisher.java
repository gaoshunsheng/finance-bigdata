package com.credit.platform.engine.core.trace;

/**
 * 空操作追踪发布器 — 默认实现，不做任何操作。
 * <p>
 * 用于测试环境或不需要追踪日志发布的场景。
 * 单例模式，无状态，线程安全。
 * </p>
 */
public final class NoopTracePublisher implements TracePublisher {

    private static final NoopTracePublisher INSTANCE = new NoopTracePublisher();

    private NoopTracePublisher() {
        // 单例 — 禁止外部实例化
    }

    /**
     * 获取单例实例。
     *
     * @return 空操作发布器实例
     */
    public static NoopTracePublisher getInstance() {
        return INSTANCE;
    }

    @Override
    public void publish(DecisionTrace trace) {
        // 不做任何操作
    }
}
