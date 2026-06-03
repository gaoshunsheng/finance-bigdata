package com.credit.platform.engine.core.variable;

import java.util.Map;
import java.util.Set;

/**
 * 变量提供者接口 — 按层级获取变量值。
 * <p>
 * 引擎核心只依赖此接口，具体实现在 decision-server 层注入。
 * 这样 engine-core 保持纯 Java，不依赖 Spring/Redis/HBase。
 * </p>
 *
 * <pre>
 * // L0: 输入变量 — 直接从请求取
 * VariableProvider inputProvider = (varIds, ctx) -> ctx.getAllVariables();
 *
 * // L1: 外部变量 — 调用三方 API (在 decision-server 实现)
 * VariableProvider externalProvider = (varIds, ctx) -> callExternalAPIs(varIds, ctx);
 *
 * // L2: 缓存变量 — 查 Redis/HBase (在 decision-server 实现)
 * VariableProvider cachedProvider = (varIds, ctx) -> queryRedisAndHBase(varIds, ctx);
 *
 * // L3: 衍生变量 — 用 Aviator 表达式实时计算
 * VariableProvider derivedProvider = new DerivedVariableProvider(expressionEngine, registry);
 * </pre>
 */
@FunctionalInterface
public interface VariableProvider {

    /**
     * 批量获取变量值。
     *
     * @param varIds  需要获取的变量 ID 集合
     * @param context 执行上下文（包含请求参数和已获取的变量）
     * @return 变量名 → 变量值映射
     */
    Map<String, Object> provide(Set<String> varIds, VariableResolveContext context);
}
