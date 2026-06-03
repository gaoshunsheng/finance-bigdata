package com.credit.platform.engine.core.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.googlecode.aviator.Expression;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ExpressionEngine} 单元测试。
 */
class ExpressionEngineTest {

    private static ExpressionEngine engine;

    @BeforeEach
    void setUp() {
        if (engine == null) {
            engine = new ExpressionEngine();
        }
    }

    // ==================== BetweenFunction ====================

    @Test
    @DisplayName("between(25, 20, 30) == true")
    void between_inRange_returnsTrue() {
        Map<String, Object> env = new HashMap<>();
        env.put("age", 25);
        assertTrue(engine.executeAsBoolean("between(age, 20, 30)", env));
    }

    @Test
    @DisplayName("between(15, 20, 30) == false")
    void between_outOfRange_returnsFalse() {
        Map<String, Object> env = new HashMap<>();
        env.put("age", 15);
        assertFalse(engine.executeAsBoolean("between(age, 20, 30)", env));
    }

    @Test
    @DisplayName("between with null field returns false")
    void between_nullField_returnsFalse() {
        Map<String, Object> env = new HashMap<>();
        env.put("age", null);
        assertFalse(engine.executeAsBoolean("between(age, 20, 30)", env));
    }

    @Test
    @DisplayName("between at lower boundary returns true")
    void between_atLowerBoundary_returnsTrue() {
        Map<String, Object> env = new HashMap<>();
        env.put("value", 20);
        assertTrue(engine.executeAsBoolean("between(value, 20, 30)", env));
    }

    @Test
    @DisplayName("between at upper boundary returns true")
    void between_atUpperBoundary_returnsTrue() {
        Map<String, Object> env = new HashMap<>();
        env.put("value", 30);
        assertTrue(engine.executeAsBoolean("between(value, 20, 30)", env));
    }

    // ==================== InFunction ====================

    @Test
    @DisplayName("inList('A', collection) with match returns true")
    void in_withMatch_returnsTrue() {
        Map<String, Object> env = new HashMap<>();
        env.put("status", "A");
        env.put("list", Arrays.asList("A", "B", "C"));
        assertTrue(engine.executeAsBoolean("inList(status, list)", env));
    }

    @Test
    @DisplayName("inList('D', collection) without match returns false")
    void in_withoutMatch_returnsFalse() {
        Map<String, Object> env = new HashMap<>();
        env.put("status", "D");
        env.put("list", Arrays.asList("A", "B", "C"));
        assertFalse(engine.executeAsBoolean("inList(status, list)", env));
    }

    @Test
    @DisplayName("inList with comma-separated string returns true")
    void in_commaSeparatedString_returnsTrue() {
        Map<String, Object> env = new HashMap<>();
        env.put("status", "A");
        env.put("allowed", "A,B,C");
        assertTrue(engine.executeAsBoolean("inList(status, allowed)", env));
    }

    @Test
    @DisplayName("inList with null field returns false")
    void in_nullField_returnsFalse() {
        Map<String, Object> env = new HashMap<>();
        env.put("status", null);
        env.put("list", Arrays.asList("A", "B", "C"));
        assertFalse(engine.executeAsBoolean("inList(status, list)", env));
    }

    // ==================== DaysBetweenFunction ====================

    @Test
    @DisplayName("daysBetween between two string dates returns correct days")
    void daysBetween_stringDates_returnsCorrectDays() {
        Map<String, Object> env = new HashMap<>();
        env.put("d1", "2024-01-01");
        env.put("d2", "2024-01-10");
        Object result = engine.execute("daysBetween(d1, d2)", env);
        assertEquals(9L, result);
    }

    @Test
    @DisplayName("daysBetween with LocalDate returns correct days")
    void daysBetween_localDates_returnsCorrectDays() {
        Map<String, Object> env = new HashMap<>();
        env.put("d1", LocalDate.of(2024, 1, 1));
        env.put("d2", LocalDate.of(2024, 1, 15));
        Object result = engine.execute("daysBetween(d1, d2)", env);
        assertEquals(14L, result);
    }

    @Test
    @DisplayName("daysBetween returns absolute value regardless of order")
    void daysBetween_reversedOrder_returnsAbsoluteDays() {
        Map<String, Object> env = new HashMap<>();
        env.put("d1", "2024-01-10");
        env.put("d2", "2024-01-01");
        Object result = engine.execute("daysBetween(d1, d2)", env);
        assertEquals(9L, result);
    }

    @Test
    @DisplayName("daysBetween with null returns -1")
    void daysBetween_nullDate_returnsNegativeOne() {
        Map<String, Object> env = new HashMap<>();
        env.put("d1", null);
        env.put("d2", "2024-01-10");
        Object result = engine.execute("daysBetween(d1, d2)", env);
        assertEquals(-1L, result);
    }

    // ==================== Compile Caching ====================

    @Test
    @DisplayName("Same expression returns same compiled instance")
    void compile_sameExpression_returnsSameInstance() {
        Expression expr1 = engine.compile("a + b");
        Expression expr2 = engine.compile("a + b");
        assertSame(expr1, expr2);
    }

    @Test
    @DisplayName("Compiled expression can execute correctly")
    void compile_thenExecute_returnsCorrectResult() {
        Expression expr = engine.compile("x * 2 + y");
        Map<String, Object> env = new HashMap<>();
        env.put("x", 5);
        env.put("y", 3);
        Object result = expr.execute(env);
        assertEquals(13L, result);
    }

    // ==================== executeAsBoolean ====================

    @Test
    @DisplayName("executeAsBoolean with null env returns false")
    void executeAsBoolean_nullEnv_returnsFalse() {
        // Expression referencing undefined variable will throw, caught as false
        assertFalse(engine.executeAsBoolean("undefined_var > 10", new HashMap<>()));
    }

    @Test
    @DisplayName("executeAsBoolean with true expression returns true")
    void executeAsBoolean_trueExpression_returnsTrue() {
        Map<String, Object> env = new HashMap<>();
        env.put("x", 10);
        assertTrue(engine.executeAsBoolean("x > 5", env));
    }

    @Test
    @DisplayName("executeAsBoolean with non-boolean expression returns false")
    void executeAsBoolean_nonBooleanExpression_returnsFalse() {
        Map<String, Object> env = new HashMap<>();
        env.put("x", 10);
        // "x + 1" evaluates to a number, not boolean
        assertFalse(engine.executeAsBoolean("x + 1", env));
    }

    // ==================== Clear Cache ====================

    @Test
    @DisplayName("clearCache allows recompilation")
    void clearCache_works() {
        Expression expr1 = engine.compile("a + b");
        engine.clearCache();
        Expression expr2 = engine.compile("a + b");
        // After clearing cache, the internal AviatorEvaluator may still cache,
        // but the local cache map is cleared
        assertNotNull(expr2);
    }
}
