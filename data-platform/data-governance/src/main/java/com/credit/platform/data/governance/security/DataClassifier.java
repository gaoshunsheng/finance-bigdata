package com.credit.platform.data.governance.security;

import java.util.*;

/**
 * 数据分级分类器 — 将数据资产分为四个安全等级。
 *
 * <p>安全等级:
 * <ul>
 *   <li>PUBLIC (公开) — 可对外公开的数据</li>
 *   <li>INTERNAL (内部) — 仅内部使用，不对外公开</li>
 *   <li>CONFIDENTIAL (机密) — 敏感数据，需 AES-256 加密存储</li>
 *   <li>TOP_SECRET (绝密) — 核心敏感数据，最高安全保护</li>
 * </ul>
 *
 * <p>自动分级规则:
 * <ul>
 *   <li>包含身份证号的字段 → CONFIDENTIAL</li>
 *   <li>包含手机号的字段 → CONFIDENTIAL</li>
 *   <li>包含银行卡号的字段 → TOP_SECRET</li>
 *   <li>包含金额的字段 → INTERNAL</li>
 *   <li>其他 → PUBLIC</li>
 * </ul>
 */
public class DataClassifier {

    /** 字段名关键词 → 安全等级映射 */
    private static final Map<String, SensitivityLevel> KEYWORD_MAP = new LinkedHashMap<>();

    static {
        // 绝密级
        KEYWORD_MAP.put("bank_card", SensitivityLevel.TOP_SECRET);
        KEYWORD_MAP.put("bankcard", SensitivityLevel.TOP_SECRET);
        KEYWORD_MAP.put("card_no", SensitivityLevel.TOP_SECRET);
        KEYWORD_MAP.put("cardno", SensitivityLevel.TOP_SECRET);
        KEYWORD_MAP.put("password", SensitivityLevel.TOP_SECRET);
        KEYWORD_MAP.put("secret", SensitivityLevel.TOP_SECRET);

        // 机密级
        KEYWORD_MAP.put("id_card", SensitivityLevel.CONFIDENTIAL);
        KEYWORD_MAP.put("idcard", SensitivityLevel.CONFIDENTIAL);
        KEYWORD_MAP.put("identity", SensitivityLevel.CONFIDENTIAL);
        KEYWORD_MAP.put("phone", SensitivityLevel.CONFIDENTIAL);
        KEYWORD_MAP.put("mobile", SensitivityLevel.CONFIDENTIAL);
        KEYWORD_MAP.put("tel", SensitivityLevel.CONFIDENTIAL);
        KEYWORD_MAP.put("email", SensitivityLevel.CONFIDENTIAL);
        KEYWORD_MAP.put("address", SensitivityLevel.CONFIDENTIAL);
        KEYWORD_MAP.put("name", SensitivityLevel.CONFIDENTIAL);
        KEYWORD_MAP.put("customer_name", SensitivityLevel.CONFIDENTIAL);

        // 内部级
        KEYWORD_MAP.put("amount", SensitivityLevel.INTERNAL);
        KEYWORD_MAP.put("income", SensitivityLevel.INTERNAL);
        KEYWORD_MAP.put("salary", SensitivityLevel.INTERNAL);
        KEYWORD_MAP.put("balance", SensitivityLevel.INTERNAL);
        KEYWORD_MAP.put("debt", SensitivityLevel.INTERNAL);
        KEYWORD_MAP.put("score", SensitivityLevel.INTERNAL);
    }

    public enum SensitivityLevel {
        PUBLIC(1, "公开"),
        INTERNAL(2, "内部"),
        CONFIDENTIAL(3, "机密"),
        TOP_SECRET(4, "绝密");

        private final int level;
        private final String description;

        SensitivityLevel(int level, String description) {
            this.level = level;
            this.description = description;
        }

        public int getLevel() { return level; }
        public String getDescription() { return description; }
    }

    /**
     * 根据字段名自动推断安全等级。
     *
     * @param columnName 列名
     * @return 安全等级
     */
    public SensitivityLevel classify(String columnName) {
        if (columnName == null) {
            return SensitivityLevel.PUBLIC;
        }
        String lower = columnName.toLowerCase();

        for (Map.Entry<String, SensitivityLevel> entry : KEYWORD_MAP.entrySet()) {
            if (lower.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return SensitivityLevel.PUBLIC;
    }

    /**
     * 批量分类。
     */
    public Map<String, SensitivityLevel> classifyAll(Collection<String> columnNames) {
        Map<String, SensitivityLevel> result = new LinkedHashMap<>();
        for (String col : columnNames) {
            result.put(col, classify(col));
        }
        return result;
    }

    /**
     * 判断是否需要加密存储（机密级及以上）。
     */
    public boolean requiresEncryption(SensitivityLevel level) {
        return level.getLevel() >= SensitivityLevel.CONFIDENTIAL.getLevel();
    }

    /**
     * 判断是否需要脱敏展示。
     */
    public boolean requiresMasking(SensitivityLevel level) {
        return level.getLevel() >= SensitivityLevel.CONFIDENTIAL.getLevel();
    }
}
