package com.credit.platform.engine.common.masking;

import org.junit.jupiter.api.*;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 敏感数据脱敏工具测试。
 */
class SensitiveDataMaskerTest {

    // ========== 身份证号脱敏 ==========

    @Test
    @DisplayName("脱敏-身份证: 18位标准身份证号")
    void maskIdCard_standard() {
        assertEquals("110***********1234", SensitiveDataMasker.maskIdCard("110101199001011234"));
    }

    @Test
    @DisplayName("脱敏-身份证: 15位旧身份证号")
    void maskIdCard_15digits() {
        // 15位: 前3 + *(15-7=8) + 后4
        assertEquals("110********1234", SensitiveDataMasker.maskIdCard("110101900101234"));
    }

    @Test
    @DisplayName("脱敏-身份证: 末位X")
    void maskIdCard_endWithX() {
        assertEquals("110***********123X", SensitiveDataMasker.maskIdCard("11010119900101123X"));
    }

    @Test
    @DisplayName("脱敏-身份证: null/空/短字符串原样返回")
    void maskIdCard_edgeCases() {
        assertNull(SensitiveDataMasker.maskIdCard(null));
        assertEquals("", SensitiveDataMasker.maskIdCard(""));
        assertEquals("1234567", SensitiveDataMasker.maskIdCard("1234567")); // < 8
    }

    // ========== 手机号脱敏 ==========

    @Test
    @DisplayName("脱敏-手机号: 11位标准手机号")
    void maskMobile_standard() {
        assertEquals("138****5678", SensitiveDataMasker.maskMobile("13812345678"));
    }

    @Test
    @DisplayName("脱敏-手机号: 不同运营商号段")
    void maskMobile_differentPrefixes() {
        assertEquals("159****1234", SensitiveDataMasker.maskMobile("15912341234"));
        assertEquals("186****9999", SensitiveDataMasker.maskMobile("18612349999"));
        assertEquals("170****0000", SensitiveDataMasker.maskMobile("17012340000"));
    }

    @Test
    @DisplayName("脱敏-手机号: null/短字符串原样返回")
    void maskMobile_edgeCases() {
        assertNull(SensitiveDataMasker.maskMobile(null));
        assertEquals("123456", SensitiveDataMasker.maskMobile("123456")); // < 7
    }

    // ========== 银行卡号脱敏 ==========

    @Test
    @DisplayName("脱敏-银行卡: 16位卡号")
    void maskBankCard_16digits() {
        assertEquals("6222********1234", SensitiveDataMasker.maskBankCard("6222021234561234"));
    }

    @Test
    @DisplayName("脱敏-银行卡: 19位卡号")
    void maskBankCard_19digits() {
        // 19位: 前4 + *(19-8=11) + 后4
        assertEquals("6222***********1234", SensitiveDataMasker.maskBankCard("6222020123456781234"));
    }

    @Test
    @DisplayName("脱敏-银行卡: null/短字符串原样返回")
    void maskBankCard_edgeCases() {
        assertNull(SensitiveDataMasker.maskBankCard(null));
        assertEquals("1234567", SensitiveDataMasker.maskBankCard("1234567")); // < 8
    }

    // ========== 姓名脱敏 ==========

    @Test
    @DisplayName("脱敏-姓名: 两个字")
    void maskName_twoChars() {
        assertEquals("张*", SensitiveDataMasker.maskName("张三"));
    }

    @Test
    @DisplayName("脱敏-姓名: 三个字")
    void maskName_threeChars() {
        assertEquals("张**", SensitiveDataMasker.maskName("张三丰"));
    }

    @Test
    @DisplayName("脱敏-姓名: 单字原样返回")
    void maskName_singleChar() {
        assertEquals("张", SensitiveDataMasker.maskName("张"));
    }

    @Test
    @DisplayName("脱敏-姓名: 英文名")
    void maskName_english() {
        assertEquals("J***", SensitiveDataMasker.maskName("John"));
    }

    @Test
    @DisplayName("脱敏-姓名: null/空原样返回")
    void maskName_edgeCases() {
        assertNull(SensitiveDataMasker.maskName(null));
        assertEquals("", SensitiveDataMasker.maskName(""));
    }

    // ========== 邮箱脱敏 ==========

    @Test
    @DisplayName("脱敏-邮箱: 标准邮箱")
    void maskEmail_standard() {
        assertEquals("z***@example.com", SensitiveDataMasker.maskEmail("zhangsan@example.com"));
    }

    @Test
    @DisplayName("脱敏-邮箱: 短邮箱名")
    void maskEmail_short() {
        assertEquals("t***@test.cn", SensitiveDataMasker.maskEmail("tom@test.cn"));
    }

    // ========== 地址脱敏 ==========

    @Test
    @DisplayName("脱敏-地址: 标准地址")
    void maskAddress_standard() {
        // 前6字符 + *剩余
        assertEquals("北京市朝阳区**********", SensitiveDataMasker.maskAddress("北京市朝阳区望京街道某小区1号楼"));
    }

    @Test
    @DisplayName("脱敏-地址: 短地址原样返回")
    void maskAddress_short() {
        assertEquals("北京市", SensitiveDataMasker.maskAddress("北京市"));
    }

    // ========== 通用 mask 方法 ==========

    @Test
    @DisplayName("脱敏-通用: 按类型脱敏")
    void mask_byType() {
        assertEquals("110***********1234",
            SensitiveDataMasker.mask("110101199001011234", SensitiveType.ID_CARD));
        assertEquals("138****5678",
            SensitiveDataMasker.mask("13812345678", SensitiveType.MOBILE));
        assertEquals("6222********1234",
            SensitiveDataMasker.mask("6222021234561234", SensitiveType.BANK_CARD));
        assertEquals("张*",
            SensitiveDataMasker.mask("张三", SensitiveType.NAME));
    }

    @Test
    @DisplayName("脱敏-通用: null/空原样返回")
    void mask_nullAndEmpty() {
        assertNull(SensitiveDataMasker.mask(null, SensitiveType.ID_CARD));
        assertEquals("", SensitiveDataMasker.mask("", SensitiveType.MOBILE));
    }

    // ========== 自定义脱敏 ==========

    @Test
    @DisplayName("脱敏-自定义: 指定前后保留位数")
    void maskCustom_basic() {
        assertEquals("AB****GH", SensitiveDataMasker.maskCustom("ABCDEFGH", 2, 2));
    }

    @Test
    @DisplayName("脱敏-自定义: 字符串长度等于前后保留之和时原样返回")
    void maskCustom_noMiddle() {
        assertEquals("ABGH", SensitiveDataMasker.maskCustom("ABGH", 2, 2));
    }

    @Test
    @DisplayName("脱敏-自定义: null原样返回")
    void maskCustom_null() {
        assertNull(SensitiveDataMasker.maskCustom(null, 2, 2));
    }

    // ========== 批量脱敏 ==========

    @Test
    @DisplayName("脱敏-批量: Map多字段脱敏")
    void maskMap_multipleFields() {
        Map<String, Object> data = Map.of(
            "idCard", "110101199001011234",
            "phone", "13812345678",
            "name", "张三",
            "amount", 50000
        );
        Map<String, SensitiveType> rules = Map.of(
            "idCard", SensitiveType.ID_CARD,
            "phone", SensitiveType.MOBILE,
            "name", SensitiveType.NAME
        );
        Map<String, Object> result = SensitiveDataMasker.maskMap(data, rules);

        assertEquals("110***********1234", (String) result.get("idCard"));
        assertEquals("138****5678", (String) result.get("phone"));
        assertEquals("张*", (String) result.get("name"));
        assertEquals(50000, result.get("amount")); // 非字符串不处理
    }

    // ========== 自动检测脱敏 ==========

    @Test
    @DisplayName("脱敏-自动: 检测文本中的身份证号")
    void autoMask_idCard() {
        String text = "客户身份证号110101199001011234已验证";
        assertEquals("客户身份证号110***********1234已验证", SensitiveDataMasker.autoMask(text));
    }

    @Test
    @DisplayName("脱敏-自动: 检测文本中的手机号")
    void autoMask_mobile() {
        String text = "联系电话13812345678";
        assertEquals("联系电话138****5678", SensitiveDataMasker.autoMask(text));
    }

    @Test
    @DisplayName("脱敏-自动: 检测文本中的银行卡号")
    void autoMask_bankCard() {
        String text = "银行卡6222021234561234";
        assertEquals("银行卡6222********1234", SensitiveDataMasker.autoMask(text));
    }

    @Test
    @DisplayName("脱敏-自动: 混合多种敏感信息")
    void autoMask_mixed() {
        String text = "身份证110101199001011234手机13812345678";
        String masked = SensitiveDataMasker.autoMask(text);
        assertTrue(masked.contains("110***********1234"));
        assertTrue(masked.contains("138****5678"));
        assertFalse(masked.contains("110101199001011234"));
        assertFalse(masked.contains("13812345678"));
    }

    @Test
    @DisplayName("脱敏-自动: null/空原样返回")
    void autoMask_edgeCases() {
        assertNull(SensitiveDataMasker.autoMask(null));
        assertEquals("", SensitiveDataMasker.autoMask(""));
        assertEquals("无敏感信息", SensitiveDataMasker.autoMask("无敏感信息"));
    }
}
