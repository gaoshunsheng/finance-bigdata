package com.credit.platform.engine.core.expression;

import com.googlecode.aviator.runtime.function.AbstractVariadicFunction;
import com.googlecode.aviator.runtime.type.AviatorBoolean;
import com.googlecode.aviator.runtime.type.AviatorObject;
import java.util.Map;

/**
 * Aviator 自定义函数：{@code creditQueryCount(count, threshold)}。
 * <p>
 * 判断征信查询次数是否超过阈值，用于风控规则中的征信查询频率判断。
 * 常见用法：{@code creditQueryCount(query_3m, 5)} 判断近3月征信查询是否 ≥ 5次。
 * </p>
 *
 * <pre>
 * creditQueryCount(6, 5)   // true (6 >= 5, 频繁查询)
 * creditQueryCount(3, 5)   // false
 * creditQueryCount(null, 5) // false
 * </pre>
 */
public class CreditQueryCountFunction extends AbstractVariadicFunction {

    private static final long serialVersionUID = 1L;

    @Override
    public String getName() {
        return "creditQueryCount";
    }

    @Override
    public AviatorObject variadicCall(Map<String, Object> env, AviatorObject... args) {
        if (args == null || args.length < 2) {
            return AviatorBoolean.FALSE;
        }

        Object countVal = args[0].getValue(env);
        Object thresholdVal = args[1].getValue(env);

        if (countVal == null || thresholdVal == null) {
            return AviatorBoolean.FALSE;
        }

        try {
            double count = (countVal instanceof Number)
                ? ((Number) countVal).doubleValue()
                : Double.parseDouble(countVal.toString());
            double threshold = (thresholdVal instanceof Number)
                ? ((Number) thresholdVal).doubleValue()
                : Double.parseDouble(thresholdVal.toString());

            return (count >= threshold) ? AviatorBoolean.TRUE : AviatorBoolean.FALSE;
        } catch (NumberFormatException e) {
            return AviatorBoolean.FALSE;
        }
    }
}
