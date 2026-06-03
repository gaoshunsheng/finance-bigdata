package com.credit.platform.engine.core.expression;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.Expression;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 Aviator 的表达式引擎。
 * <p>
 * 编译一次，执行多次。表达式字符串编译为 {@link Expression} 对象后缓存在本地，
 * 后续相同表达式的执行直接从缓存获取已编译对象，避免重复解析。
 * </p>
 *
 * <pre>
 * // 使用示例
 * ExpressionEngine engine = new ExpressionEngine();
 * boolean result = engine.executeAsBoolean("between(age, 20, 60)", env);
 * </pre>
 */
public class ExpressionEngine {

    private final Map<String, Expression> cache = new ConcurrentHashMap<>();

    /**
     * 构造函数，注册内置自定义函数。
     */
    public ExpressionEngine() {
        registerFunctions();
    }

    /**
     * 编译表达式并缓存。若表达式已编译过，直接返回缓存实例。
     *
     * @param expression 表达式字符串
     * @return 编译后的 {@link Expression} 对象
     * @throws com.googlecode.aviator.exception.ExpressionSyntaxErrorException 表达式语法错误时抛出
     */
    public Expression compile(String expression) {
        return cache.computeIfAbsent(expression,
            expr -> AviatorEvaluator.compile(expr, true));
    }

    /**
     * 执行表达式并返回结果。
     *
     * @param expression 表达式字符串
     * @param env        变量环境
     * @return 表达式执行结果
     */
    public Object execute(String expression, Map<String, Object> env) {
        Expression compiled = compile(expression);
        return compiled.execute(env);
    }

    /**
     * 执行表达式并返回布尔结果。
     * <p>
     * 若结果为 null、非布尔类型或执行异常，统一返回 {@code false}。
     * </p>
     *
     * @param expression 表达式字符串
     * @param env        变量环境
     * @return 布尔结果，异常或非布尔值时返回 {@code false}
     */
    public boolean executeAsBoolean(String expression, Map<String, Object> env) {
        try {
            Object result = execute(expression, env);
            if (result instanceof Boolean) {
                return (Boolean) result;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 注册自定义 Aviator 函数。
     */
    private void registerFunctions() {
        AviatorEvaluator.addFunction(new BetweenFunction());
        AviatorEvaluator.addFunction(new InFunction());
        AviatorEvaluator.addFunction(new DaysBetweenFunction());
    }

    /**
     * 清除已编译表达式的本地缓存。
     */
    public void clearCache() {
        cache.clear();
    }
}
