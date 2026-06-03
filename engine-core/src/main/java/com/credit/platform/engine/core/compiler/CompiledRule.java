package com.credit.platform.engine.core.compiler;

/**
 * 编译后的规则 — 不可变、线程安全的编译产物。
 * <p>
 * 所有规则类型（条件规则、评分卡、决策表、决策树、规则集）编译后都实现此接口。
 * 编译产物缓存在 Caffeine 中，运行时直接使用。
 * </p>
 */
public interface CompiledRule {

    /**
     * 规则唯一标识。
     *
     * @return 规则 ID
     */
    String getRuleId();

    /**
     * 规则名称。
     *
     * @return 规则名称
     */
    String getName();

    /**
     * 规则版本号。
     *
     * @return 版本号
     */
    int getVersion();

    /**
     * 规则类型标识（用于路由到对应的执行器）。
     *
     * @return 类型字符串
     */
    String getRuleType();
}
