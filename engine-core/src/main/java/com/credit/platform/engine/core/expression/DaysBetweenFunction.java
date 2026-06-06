package com.credit.platform.engine.core.expression;

import com.googlecode.aviator.runtime.function.AbstractVariadicFunction;
import com.googlecode.aviator.runtime.type.AviatorLong;
import com.googlecode.aviator.runtime.type.AviatorObject;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;

/**
 * Aviator 自定义函数：{@code daysBetween(date1, date2)}。
 * <p>
 * 返回两个日期之间的绝对天数差。支持以下日期类型：
 * <ul>
 *   <li>{@link java.time.LocalDate}</li>
 *   <li>{@link java.util.Date}（转换为 LocalDate 后计算）</li>
 *   <li>{@link String}（yyyy-MM-dd 格式）</li>
 * </ul>
 * 任一参数为 null 或格式无法解析时返回 {@code -1}。
 * </p>
 *
 * <pre>
 * daysBetween('2024-01-01', '2024-01-10')  // 9
 * daysBetween(localDate1, localDate2)       // 绝对天数差
 * </pre>
 */
public class DaysBetweenFunction extends AbstractVariadicFunction {

    private static final long serialVersionUID = 1L;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 统一时区，确保分布式部署结果一致 */
    private static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");

    /**
     * 函数名称。
     *
     * @return "daysBetween"
     */
    @Override
    public String getName() {
        return "daysBetween";
    }

    /**
     * 执行 daysBetween 计算。必须传入 2 个日期参数。
     *
     * @param args 函数参数
     * @param env  变量环境
     * @return 绝对天数差的 {@link AviatorLong}，异常时返回 -1
     */
    @Override
    public AviatorObject variadicCall(Map<String, Object> env, AviatorObject... args) {
        if (args == null || args.length != 2) {
            return AviatorLong.valueOf(-1L);
        }

        LocalDate date1 = toLocalDate(args[0].getValue(env));
        LocalDate date2 = toLocalDate(args[1].getValue(env));

        if (date1 == null || date2 == null) {
            return AviatorLong.valueOf(-1L);
        }

        long days = Math.abs(ChronoUnit.DAYS.between(date1, date2));
        return AviatorLong.valueOf(days);
    }

    /**
     * 将参数转换为 {@link LocalDate}。
     *
     * @param value 原始参数值
     * @return 转换后的 LocalDate，无法转换时返回 null
     */
    private LocalDate toLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate) {
            return (LocalDate) value;
        }
        if (value instanceof Date) {
            return ((Date) value).toInstant()
                .atZone(ZONE_SHANGHAI)
                .toLocalDate();
        }
        if (value instanceof String) {
            try {
                return LocalDate.parse((String) value, DATE_FORMATTER);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
