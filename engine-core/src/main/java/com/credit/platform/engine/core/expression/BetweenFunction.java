package com.credit.platform.engine.core.expression;

import com.googlecode.aviator.runtime.function.AbstractVariadicFunction;
import com.googlecode.aviator.runtime.type.AviatorBoolean;
import com.googlecode.aviator.runtime.type.AviatorNil;
import com.googlecode.aviator.runtime.type.AviatorObject;
import java.util.Map;

/**
 * Aviator 自定义函数：{@code between(field, min, max)}。
 * <p>
 * 判断 {@code field >= min && field <= max}，任一参数为 null 时返回 {@code false}。
 * </p>
 *
 * <pre>
 * between(25, 20, 30)  // true
 * between(15, 20, 30)  // false
 * between(null, 20, 30) // false
 * </pre>
 */
public class BetweenFunction extends AbstractVariadicFunction {

    private static final long serialVersionUID = 1L;

    /**
     * 函数名称。
     *
     * @return "between"
     */
    @Override
    public String getName() {
        return "between";
    }

    /**
     * 执行 between 判断。必须传入 3 个参数：field、min、max。
     * <p>
     * 参数均需实现 {@link Comparable}，否则视为类型不匹配，返回 {@code false}。
     * </p>
     *
     * @param args 函数参数
     * @param env  变量环境
     * @return {@link AviatorBoolean#TRUE} 或 {@link AviatorBoolean#FALSE}
     */
    @Override
    @SuppressWarnings("unchecked")
    public AviatorObject variadicCall(Map<String, Object> env, AviatorObject... args) {
        if (args == null || args.length != 3) {
            return AviatorBoolean.FALSE;
        }

        Object fieldVal = args[0].getValue(env);
        Object minVal = args[1].getValue(env);
        Object maxVal = args[2].getValue(env);

        if (fieldVal == null || minVal == null || maxVal == null) {
            return AviatorBoolean.FALSE;
        }

        try {
            // Number 类型统一转为 double 比较，避免 Integer/Long 类型不兼容
            if (fieldVal instanceof Number && minVal instanceof Number && maxVal instanceof Number) {
                double field = ((Number) fieldVal).doubleValue();
                double min = ((Number) minVal).doubleValue();
                double max = ((Number) maxVal).doubleValue();
                return (field >= min && field <= max)
                    ? AviatorBoolean.TRUE
                    : AviatorBoolean.FALSE;
            }
            Comparable<Object> field = (Comparable<Object>) fieldVal;
            return (field.compareTo(minVal) >= 0 && field.compareTo(maxVal) <= 0)
                ? AviatorBoolean.TRUE
                : AviatorBoolean.FALSE;
        } catch (ClassCastException e) {
            return AviatorBoolean.FALSE;
        }
    }
}
