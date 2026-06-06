package com.credit.platform.engine.core.expression;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 自定义 Aviator 函数测试 — isInProvince / overdueCount / creditQueryCount。
 */
class CustomFunctionTest {

    private ExpressionEngine engine;

    @BeforeEach
    void setUp() {
        engine = new ExpressionEngine();
    }

    @Nested
    @DisplayName("isInProvince 函数测试")
    class IsInProvinceTest {

        @Test
        @DisplayName("地址包含省份关键词 → true")
        void addressContainsProvince() {
            assertTrue(engine.executeAsBoolean(
                "isInProvince(address, province)",
                Map.of("address", "北京市海淀区", "province", "北京")
            ));
        }

        @Test
        @DisplayName("地址不含省份关键词 → false")
        void addressNotContainsProvince() {
            assertFalse(engine.executeAsBoolean(
                "isInProvince(address, province)",
                Map.of("address", "上海市浦东新区", "province", "北京")
            ));
        }

        @Test
        @DisplayName("address 为 null → false")
        void nullAddress() {
            Map<String, Object> env = new java.util.HashMap<>();
            env.put("address", null);
            env.put("province", "北京");
            assertFalse(engine.executeAsBoolean(
                "isInProvince(address, province)", env
            ));
        }

        @Test
        @DisplayName("province 带后缀 (省/市) 自动去除")
        void provinceWithSuffix() {
            assertTrue(engine.executeAsBoolean(
                "isInProvince(address, province)",
                Map.of("address", "广东省深圳市", "province", "广东省")
            ));
        }

        @Test
        @DisplayName("参数不足 → false")
        void insufficientArgs() {
            assertFalse(engine.executeAsBoolean(
                "isInProvince('北京')",
                Map.of()
            ));
        }
    }

    @Nested
    @DisplayName("overdueCount 函数测试")
    class OverdueCountTest {

        @Test
        @DisplayName("逾期次数 >= 阈值 → true")
        void countExceedsThreshold() {
            assertTrue(engine.executeAsBoolean(
                "overdueCount(count, threshold)",
                Map.of("count", 3, "threshold", 2)
            ));
        }

        @Test
        @DisplayName("逾期次数 < 阈值 → false")
        void countBelowThreshold() {
            assertFalse(engine.executeAsBoolean(
                "overdueCount(count, threshold)",
                Map.of("count", 1, "threshold", 2)
            ));
        }

        @Test
        @DisplayName("逾期次数等于阈值 → true")
        void countEqualsThreshold() {
            assertTrue(engine.executeAsBoolean(
                "overdueCount(count, threshold)",
                Map.of("count", 2, "threshold", 2)
            ));
        }

        @Test
        @DisplayName("count 为 null → false")
        void nullCount() {
            Map<String, Object> env = new java.util.HashMap<>();
            env.put("count", null);
            env.put("threshold", 2);
            assertFalse(engine.executeAsBoolean(
                "overdueCount(count, threshold)", env
            ));
        }

        @Test
        @DisplayName("参数为字符串数字 → 正确解析")
        void stringNumberArgs() {
            assertTrue(engine.executeAsBoolean(
                "overdueCount(count, threshold)",
                Map.of("count", "5", "threshold", "3")
            ));
        }
    }

    @Nested
    @DisplayName("creditQueryCount 函数测试")
    class CreditQueryCountTest {

        @Test
        @DisplayName("查询次数 >= 阈值 → true (频繁查询)")
        void countExceedsThreshold() {
            assertTrue(engine.executeAsBoolean(
                "creditQueryCount(count, threshold)",
                Map.of("count", 6, "threshold", 5)
            ));
        }

        @Test
        @DisplayName("查询次数 < 阈值 → false")
        void countBelowThreshold() {
            assertFalse(engine.executeAsBoolean(
                "creditQueryCount(count, threshold)",
                Map.of("count", 3, "threshold", 5)
            ));
        }

        @Test
        @DisplayName("查询次数等于阈值 → true")
        void countEqualsThreshold() {
            assertTrue(engine.executeAsBoolean(
                "creditQueryCount(count, threshold)",
                Map.of("count", 5, "threshold", 5)
            ));
        }

        @Test
        @DisplayName("threshold 为 null → false")
        void nullThreshold() {
            Map<String, Object> env = new java.util.HashMap<>();
            env.put("count", 10);
            env.put("threshold", null);
            assertFalse(engine.executeAsBoolean(
                "creditQueryCount(count, threshold)", env
            ));
        }

        @Test
        @DisplayName("参数为字符串数字 → 正确解析")
        void stringNumberArgs() {
            assertTrue(engine.executeAsBoolean(
                "creditQueryCount(count, threshold)",
                Map.of("count", "8", "threshold", "5")
            ));
        }

        @Test
        @DisplayName("非数字字符串 → false")
        void nonNumericString() {
            assertFalse(engine.executeAsBoolean(
                "creditQueryCount(count, threshold)",
                Map.of("count", "abc", "threshold", "5")
            ));
        }
    }
}
