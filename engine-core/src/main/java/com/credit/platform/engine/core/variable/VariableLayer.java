package com.credit.platform.engine.core.variable;

/**
 * 变量层级 — 定义变量的来源和获取延迟。
 * <p>
 * L0: 输入变量 — 来自业务请求，0ms<br>
 * L1: 外部变量 — 三方 API 调用，200-800ms<br>
 * L2: 缓存变量 — Redis/HBase，5-20ms<br>
 * L3: 衍生变量 — 基于其他变量实时计算，&lt;1ms
 * </p>
 */
public enum VariableLayer {

    /** 输入变量 — 来自业务请求 */
    INPUT(0),
    /** 外部变量 — 三方 API 调用 */
    EXTERNAL(1),
    /** 缓存变量 — Redis/HBase (Flink 预计算) */
    CACHED(2),
    /** 衍生变量 — 基于其他变量实时计算 (Aviator 表达式) */
    DERIVED(3);

    private final int level;

    VariableLayer(int level) {
        this.level = level;
    }

    public int getLevel() { return level; }
}
