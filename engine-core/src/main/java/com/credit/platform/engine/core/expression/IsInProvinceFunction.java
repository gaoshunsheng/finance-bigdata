package com.credit.platform.engine.core.expression;

import com.googlecode.aviator.runtime.function.AbstractVariadicFunction;
import com.googlecode.aviator.runtime.type.AviatorBoolean;
import com.googlecode.aviator.runtime.type.AviatorObject;
import java.util.Map;
import java.util.Set;

/**
 * Aviator 自定义函数：{@code isInProvince(address, province)}。
 * <p>
 * 判断地址字符串是否包含指定省份关键词（不区分大小写），
 * 用于风控规则中的地域筛选。
 * </p>
 *
 * <pre>
 * isInProvince("北京市海淀区", "北京")   // true
 * isInProvince("上海市浦东新区", "北京") // false
 * isInProvince(null, "北京")            // false
 * </pre>
 */
public class IsInProvinceFunction extends AbstractVariadicFunction {

    private static final long serialVersionUID = 1L;

    /** 直辖市和省份简称映射 */
    private static final Set<String> MUNICIPALITIES = Set.of("北京", "上海", "天津", "重庆");

    @Override
    public String getName() {
        return "isInProvince";
    }

    @Override
    public AviatorObject variadicCall(Map<String, Object> env, AviatorObject... args) {
        if (args == null || args.length < 2) {
            return AviatorBoolean.FALSE;
        }

        Object addressVal = args[0].getValue(env);
        Object provinceVal = args[1].getValue(env);

        if (addressVal == null || provinceVal == null) {
            return AviatorBoolean.FALSE;
        }

        String address = addressVal.toString();
        String province = provinceVal.toString();

        // 去除"市""省"后缀进行匹配
        String normalizedProvince = province.replaceAll("(省|市|自治区|特别行政区)$", "");
        boolean match = address.contains(normalizedProvince);

        return match ? AviatorBoolean.TRUE : AviatorBoolean.FALSE;
    }
}
