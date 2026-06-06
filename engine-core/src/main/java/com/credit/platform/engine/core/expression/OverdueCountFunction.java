package com.credit.platform.engine.core.expression;

import com.googlecode.aviator.runtime.function.AbstractVariadicFunction;
import com.googlecode.aviator.runtime.type.AviatorBoolean;
import com.googlecode.aviator.runtime.type.AviatorObject;
import java.util.Map;

/**
 * Aviator 自定义函数：{@code overdueCount(count, threshold)}。
 * <p>
 * 判断逾期次数是否超过阈值，用于风控规则中的逾期判断。
 * 常见用法：{@code overdueCount(overdue_6m, 2)} 判断近6月逾期是否 ≥ 2次。
 * </p>
 *
 * <pre>
 * overdueCount(3, 2)   // true (3 >= 2)
 * overdueCount(1, 2)   // false
 * overdueCount(null, 2) // false
 * </pre>
 */
public class OverdueCountFunction extends AbstractVariadicFunction {

    private static final long serialVersionUID = 1L;

    @Override
    public String getName() {
        return "overdueCount";
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
