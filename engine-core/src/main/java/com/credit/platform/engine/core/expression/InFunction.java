package com.credit.platform.engine.core.expression;

import com.googlecode.aviator.runtime.function.AbstractVariadicFunction;
import com.googlecode.aviator.runtime.type.AviatorBoolean;
import com.googlecode.aviator.runtime.type.AviatorObject;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Aviator 自定义函数：{@code inList(field, collection)}。
 * <p>
 * 判断 {@code field} 是否在 {@code collection} 中。
 * {@code collection} 参数支持 {@link java.util.Collection} 或以英文逗号分隔的字符串。
 * 任一参数为 null 时返回 {@code false}。
 * </p>
 * <p>
 * 注意：函数名使用 {@code inList} 而非 {@code in}，因为 {@code in} 是 Aviator 的保留关键字。
 * </p>
 *
 * <pre>
 * inList("A", seq.list("A", "B", "C"))   // true
 * inList("D", seq.list("A", "B", "C"))   // false
 * inList("A", "A,B,C")                   // true
 * </pre>
 */
public class InFunction extends AbstractVariadicFunction {

    private static final long serialVersionUID = 1L;

    /**
     * 函数名称。
     *
     * @return "in"
     */
    @Override
    public String getName() {
        return "inList";
    }

    /**
     * 执行 in 判断。必须传入 2 个参数：field、collection。
     *
     * @param args 函数参数
     * @param env  变量环境
     * @return {@link AviatorBoolean#TRUE} 或 {@link AviatorBoolean#FALSE}
     */
    @Override
    public AviatorObject variadicCall(Map<String, Object> env, AviatorObject... args) {
        if (args == null || args.length != 2) {
            return AviatorBoolean.FALSE;
        }

        Object fieldVal = args[0].getValue(env);
        Object collectionVal = args[1].getValue(env);

        if (fieldVal == null || collectionVal == null) {
            return AviatorBoolean.FALSE;
        }

        Collection<?> collection = toCollection(collectionVal);
        if (collection == null) {
            return AviatorBoolean.FALSE;
        }

        for (Object item : collection) {
            if (item != null && item.equals(fieldVal)) {
                return AviatorBoolean.TRUE;
            }
        }
        return AviatorBoolean.FALSE;
    }

    /**
     * 将参数转换为 Collection。
     * 支持 Collection 类型或逗号分隔字符串。
     *
     * @param value 原始参数值
     * @return 转换后的集合，无法转换时返回 null
     */
    private Collection<?> toCollection(Object value) {
        if (value instanceof Collection) {
            return (Collection<?>) value;
        }
        if (value instanceof String) {
            String str = (String) value;
            if (str.isEmpty()) {
                return null;
            }
            return Arrays.stream(str.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        }
        return null;
    }
}
