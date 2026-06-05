package com.credit.platform.engine.common.masking;

/**
 * 敏感数据类型枚举。
 */
public enum SensitiveType {
    /** 身份证号 */
    ID_CARD,
    /** 手机号 */
    MOBILE,
    /** 银行卡号 */
    BANK_CARD,
    /** 姓名 */
    NAME,
    /** 邮箱 */
    EMAIL,
    /** 地址 */
    ADDRESS,
    /** 自定义 */
    CUSTOM
}
