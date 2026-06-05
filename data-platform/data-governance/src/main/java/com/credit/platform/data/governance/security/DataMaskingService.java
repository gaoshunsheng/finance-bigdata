package com.credit.platform.data.governance.security;

import com.credit.platform.engine.common.masking.SensitiveDataMasker;
import com.credit.platform.engine.common.masking.SensitiveType;

import java.util.*;

/**
 * 数据脱敏服务 — 基于 engine-common 的 SensitiveDataMasker 实现。
 *
 * <p>脱敏策略:
 * <ul>
 *   <li>身份证: 保留前 3 后 4，中间掩码 → 110***********1234</li>
 *   <li>手机号: 保留前 3 后 4，中间掩码 → 138****5678</li>
 *   <li>银行卡: 保留前 6 后 4，中间掩码 → 622848******1234</li>
 *   <li>姓名: 保留姓，后面掩码 → 张*</li>
 *   <li>邮箱: 保留首字符和域名 → z***@example.com</li>
 *   <li>地址: 保留前 6 字符 → 北京市海淀***</li>
 * </ul>
 */
public class DataMaskingService {

    private final DataClassifier classifier = new DataClassifier();

    /** 字段名 → SensitiveType 映射规则 */
    private static final Map<String, SensitiveType> TYPE_MAP = new LinkedHashMap<>();

    static {
        TYPE_MAP.put("id_card", SensitiveType.ID_CARD);
        TYPE_MAP.put("idcard", SensitiveType.ID_CARD);
        TYPE_MAP.put("identity", SensitiveType.ID_CARD);
        TYPE_MAP.put("phone", SensitiveType.MOBILE);
        TYPE_MAP.put("mobile", SensitiveType.MOBILE);
        TYPE_MAP.put("tel", SensitiveType.MOBILE);
        TYPE_MAP.put("bank_card", SensitiveType.BANK_CARD);
        TYPE_MAP.put("bankcard", SensitiveType.BANK_CARD);
        TYPE_MAP.put("card_no", SensitiveType.BANK_CARD);
        TYPE_MAP.put("name", SensitiveType.NAME);
        TYPE_MAP.put("customer_name", SensitiveType.NAME);
        TYPE_MAP.put("email", SensitiveType.EMAIL);
        TYPE_MAP.put("address", SensitiveType.ADDRESS);
    }

    /**
     * 根据字段名推断敏感类型。
     */
    public SensitiveType inferSensitiveType(String columnName) {
        if (columnName == null) return null;
        String lower = columnName.toLowerCase();
        for (Map.Entry<String, SensitiveType> entry : TYPE_MAP.entrySet()) {
            if (lower.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 脱敏单个字段值。
     *
     * @param columnName 字段名（用于推断脱敏策略）
     * @param value      原始值
     * @return 脱敏后的值（如果不需要脱敏则返回原值）
     */
    public String mask(String columnName, String value) {
        if (value == null) return null;

        // 先检查安全等级
        DataClassifier.SensitivityLevel level = classifier.classify(columnName);
        if (!classifier.requiresMasking(level)) {
            return value;
        }

        // 推断脱敏类型
        SensitiveType type = inferSensitiveType(columnName);
        if (type == null) {
            // 无法推断具体类型，使用通用脱敏（保留前2后2）
            return SensitiveDataMasker.maskCustom(value, 2, 2);
        }

        return SensitiveDataMasker.mask(value, type);
    }

    /**
     * 批量脱敏数据记录。
     *
     * @param record 原始数据记录
     * @return 脱敏后的记录
     */
    public Map<String, Object> maskRecord(Map<String, Object> record) {
        Map<String, Object> masked = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : record.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String strValue) {
                masked.put(entry.getKey(), mask(entry.getKey(), strValue));
            } else {
                masked.put(entry.getKey(), value);
            }
        }
        return masked;
    }
}
