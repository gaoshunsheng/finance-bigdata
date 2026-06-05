package com.credit.platform.engine.common.masking;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 敏感数据脱敏工具类。
 * <p>
 * 支持身份证号、手机号、银行卡号、姓名、邮箱、地址等自动脱敏。
 * 脱敏规则:
 * <ul>
 *   <li>身份证号: 保留前3后4，中间用*代替 (110***********1234)</li>
 *   <li>手机号: 保留前3后4，中间用*代替 (138****1234)</li>
 *   <li>银行卡号: 保留前4后4，中间用*代替 (6222****1234)</li>
 *   <li>姓名: 保留姓，名用*代替 (张*)</li>
 *   <li>邮箱: 保留首字符和@后域名，中间用*代替 (z***@example.com)</li>
 *   <li>地址: 保留省市，详细地址用*代替</li>
 * </ul>
 * </p>
 *
 * <pre>
 * // 基本用法
 * String masked = SensitiveDataMasker.mask("110101199001011234", SensitiveType.ID_CARD);
 * // 输出: 110***********1234
 *
 * // 批量脱敏
 * Map&lt;String, Object&gt; data = Map.of("idCard", "110101199001011234", "phone", "13812345678");
 * Map&lt;String, Object&gt; masked = SensitiveDataMasker.maskMap(data, maskingRules);
 * </pre>
 */
public final class SensitiveDataMasker {

    private static final String MASK_CHAR = "*";

    /** 身份证号正则 (18位, 允许最后一位X) */
    private static final Pattern ID_CARD_PATTERN =
        Pattern.compile("(?<!\\d)(\\d{3})\\d{11}(\\d{4})(?!\\d)");

    /** 手机号正则 (11位, 1开头) */
    private static final Pattern MOBILE_PATTERN =
        Pattern.compile("(?<!\\d)(1\\d{2})\\d{4}(\\d{4})(?!\\d)");

    /** 银行卡号正则 (13-19位) */
    private static final Pattern BANK_CARD_PATTERN =
        Pattern.compile("(?<!\\d)(\\d{4})\\d{8,11}(\\d{4})(?!\\d)");

    /** 邮箱正则 */
    private static final Pattern EMAIL_PATTERN =
        Pattern.compile("(\\w{1})\\w*(@\\w+\\.\\w+)");

    /** 已注册的脱敏策略 */
    private static final Map<SensitiveType, Function<String, String>> MASK_STRATEGIES = new HashMap<>();

    static {
        MASK_STRATEGIES.put(SensitiveType.ID_CARD, SensitiveDataMasker::maskIdCard);
        MASK_STRATEGIES.put(SensitiveType.MOBILE, SensitiveDataMasker::maskMobile);
        MASK_STRATEGIES.put(SensitiveType.BANK_CARD, SensitiveDataMasker::maskBankCard);
        MASK_STRATEGIES.put(SensitiveType.NAME, SensitiveDataMasker::maskName);
        MASK_STRATEGIES.put(SensitiveType.EMAIL, SensitiveDataMasker::maskEmail);
        MASK_STRATEGIES.put(SensitiveType.ADDRESS, SensitiveDataMasker::maskAddress);
    }

    private SensitiveDataMasker() {}

    // ========== 核心脱敏方法 ==========

    /**
     * 对字符串进行指定类型的脱敏处理。
     *
     * @param value 原始值
     * @param type  脱敏类型
     * @return 脱敏后的字符串，如果输入为null或空则直接返回
     */
    public static String mask(String value, SensitiveType type) {
        if (value == null || value.isEmpty()) return value;
        Function<String, String> strategy = MASK_STRATEGIES.get(type);
        if (strategy == null) return value;
        return strategy.apply(value);
    }

    /**
     * 对 Map 中的指定字段进行批量脱敏。
     *
     * @param data       原始数据
     * @param fieldRules 字段脱敏规则 (字段名 → 脱敏类型)
     * @return 脱敏后的新 Map (不修改原始数据)
     */
    public static Map<String, Object> maskMap(Map<String, Object> data,
                                               Map<String, SensitiveType> fieldRules) {
        Objects.requireNonNull(data, "data must not be null");
        Objects.requireNonNull(fieldRules, "fieldRules must not be null");

        Map<String, Object> result = new HashMap<>(data);
        for (Map.Entry<String, SensitiveType> rule : fieldRules.entrySet()) {
            String field = rule.getKey();
            SensitiveType type = rule.getValue();
            Object value = result.get(field);
            if (value instanceof String strValue) {
                result.put(field, mask(strValue, type));
            }
        }
        return result;
    }

    /**
     * 自动检测并脱敏字符串中的所有敏感信息。
     * 依次尝试匹配: 身份证 → 银行卡 → 手机号 → 邮箱
     *
     * @param text 原始文本
     * @return 脱敏后的文本
     */
    public static String autoMask(String text) {
        if (text == null || text.isEmpty()) return text;
        String result = text;
        result = ID_CARD_PATTERN.matcher(result).replaceAll("$1***********$2");
        result = BANK_CARD_PATTERN.matcher(result).replaceAll("$1********$2");
        result = MOBILE_PATTERN.matcher(result).replaceAll("$1****$2");
        result = EMAIL_PATTERN.matcher(result).replaceAll("$1***$2");
        return result;
    }

    // ========== 具体脱敏策略 ==========

    /**
     * 身份证号脱敏: 保留前3后4。
     * 例: 110101199001011234 → 110***********1234
     */
    public static String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 8) return idCard;
        int len = idCard.length();
        return idCard.substring(0, 3) + repeat(MASK_CHAR, len - 7) + idCard.substring(len - 4);
    }

    /**
     * 手机号脱敏: 保留前3后4。
     * 例: 13812345678 → 138****5678
     */
    public static String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 7) return mobile;
        int len = mobile.length();
        return mobile.substring(0, 3) + repeat(MASK_CHAR, len - 7) + mobile.substring(len - 4);
    }

    /**
     * 银行卡号脱敏: 保留前4后4。
     * 例: 6222021234561234 → 6222********1234
     */
    public static String maskBankCard(String bankCard) {
        if (bankCard == null || bankCard.length() < 8) return bankCard;
        int len = bankCard.length();
        return bankCard.substring(0, 4) + repeat(MASK_CHAR, len - 8) + bankCard.substring(len - 4);
    }

    /**
     * 姓名脱敏: 保留姓，名用*代替。
     * 例: 张三丰 → 张**, John → J***
     */
    public static String maskName(String name) {
        if (name == null || name.isEmpty()) return name;
        if (name.length() == 1) return name;
        if (name.length() == 2) return name.charAt(0) + MASK_CHAR;
        return name.charAt(0) + repeat(MASK_CHAR, name.length() - 1);
    }

    /**
     * 邮箱脱敏: 保留首字符和@后域名。
     * 例: zhangsan@example.com → z***@example.com
     */
    public static String maskEmail(String email) {
        if (email == null || email.isEmpty()) return email;
        Matcher m = EMAIL_PATTERN.matcher(email);
        if (m.matches()) {
            return m.group(1) + "***" + m.group(2);
        }
        // 简单格式处理
        int atIndex = email.indexOf('@');
        if (atIndex > 1) {
            return email.charAt(0) + "***" + email.substring(atIndex);
        }
        return email;
    }

    /**
     * 地址脱敏: 保留前6个字符（省市），其余用*代替。
     * 例: 北京市朝阳区望京街道 → 北京市朝阳区***
     */
    public static String maskAddress(String address) {
        if (address == null || address.length() <= 6) return address;
        return address.substring(0, 6) + repeat(MASK_CHAR, address.length() - 6);
    }

    // ========== 自定义脱敏策略 ==========

    /**
     * 通用脱敏: 保留前 prefixLen 和后 suffixLen 位，中间用*代替。
     *
     * @param value     原始值
     * @param prefixLen 保留前缀长度
     * @param suffixLen 保留后缀长度
     * @return 脱敏后的字符串
     */
    public static String maskCustom(String value, int prefixLen, int suffixLen) {
        if (value == null || value.length() <= prefixLen + suffixLen) return value;
        return value.substring(0, prefixLen)
             + repeat(MASK_CHAR, value.length() - prefixLen - suffixLen)
             + value.substring(value.length() - suffixLen);
    }

    /**
     * 注册自定义脱敏策略。
     *
     * @param type     脱敏类型
     * @param strategy 脱敏函数
     */
    public static void registerStrategy(SensitiveType type, Function<String, String> strategy) {
        MASK_STRATEGIES.put(type, strategy);
    }

    // ========== 工具方法 ==========

    private static String repeat(String s, int count) {
        if (count <= 0) return "";
        StringBuilder sb = new StringBuilder(s.length() * count);
        for (int i = 0; i < count; i++) sb.append(s);
        return sb.toString();
    }
}
