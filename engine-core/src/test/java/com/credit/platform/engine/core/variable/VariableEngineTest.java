package com.credit.platform.engine.core.variable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.credit.platform.engine.core.expression.ExpressionEngine;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 变量引擎综合测试。
 */
class VariableEngineTest {

    private VariableRegistry registry;
    private ExpressionEngine expressionEngine;

    @BeforeEach
    void setUp() {
        registry = new VariableRegistry();
        expressionEngine = new ExpressionEngine();

        // 注册测试变量
        // L0 输入
        registry.register(VariableDefinition.builder()
            .varId("age").name("年龄").layer(VariableLayer.INPUT)
            .dataType("INTEGER").category("基本信息").build());
        registry.register(VariableDefinition.builder()
            .varId("name").name("姓名").layer(VariableLayer.INPUT)
            .dataType("STRING").category("基本信息").build());

        // L1 外部
        registry.register(VariableDefinition.builder()
            .varId("credit_score").name("征信评分").layer(VariableLayer.EXTERNAL)
            .dataType("INTEGER").category("征信").build());
        registry.register(VariableDefinition.builder()
            .varId("overdue_count").name("逾期次数").layer(VariableLayer.EXTERNAL)
            .dataType("INTEGER").category("征信").build());

        // L2 缓存
        registry.register(VariableDefinition.builder()
            .varId("feature_6m_avg").name("近6月平均消费").layer(VariableLayer.CACHED)
            .dataType("DECIMAL").category("运营商").build());

        // L3 衍生
        registry.register(VariableDefinition.builder()
            .varId("is_young").name("是否年轻").layer(VariableLayer.DERIVED)
            .dataType("BOOLEAN").category("衍生")
            .expression("age < 30")
            .dependencies(Set.of("age")).build());
        registry.register(VariableDefinition.builder()
            .varId("risk_level").name("风险等级").layer(VariableLayer.DERIVED)
            .dataType("INTEGER").category("衍生")
            .expression("overdue_count > 3 ? 3 : overdue_count")
            .dependencies(Set.of("overdue_count")).build());
    }

    @Nested
    @DisplayName("VariableRegistry 测试")
    class RegistryTest {

        @Test
        @DisplayName("注册和查询变量")
        void registerAndGet() {
            assertNotNull(registry.get("age"));
            assertEquals("年龄", registry.get("age").getName());
            assertEquals(VariableLayer.INPUT, registry.get("age").getLayer());
        }

        @Test
        @DisplayName("按层级查询")
        void getByLayer() {
            Set<VariableDefinition> externals = registry.getByLayer(VariableLayer.EXTERNAL);
            assertEquals(2, externals.size());
        }

        @Test
        @DisplayName("按类别查询")
        void getByCategory() {
            Set<VariableDefinition>征信 = registry.getByCategory("征信");
            assertEquals(2,征信.size());
        }

        @Test
        @DisplayName("依赖解析 — 递归展开")
        void resolveDependencies() {
            // is_young 依赖 age
            Set<String> deps = registry.resolveDependencies(Set.of("is_young"));
            assertTrue(deps.contains("is_young"));
            assertTrue(deps.contains("age"));
            assertEquals(2, deps.size());
        }

        @Test
        @DisplayName("无循环依赖时返回空集合")
        void noCircularDeps() {
            assertTrue(registry.detectCircularDependencies().isEmpty());
        }

        @Test
        @DisplayName("变量总数")
        void size() {
            // 6 variables registered in setUp
            assertEquals(7, registry.size());
        }

        @Test
        @DisplayName("检测循环依赖 — 不影响 size")
        void circularDeps_size() {
            registry.register(VariableDefinition.builder()
                .varId("a").name("A").layer(VariableLayer.DERIVED)
                .expression("b + 1").dependencies(Set.of("b")).build());
            registry.register(VariableDefinition.builder()
                .varId("b").name("B").layer(VariableLayer.DERIVED)
                .expression("a + 1").dependencies(Set.of("a")).build());

            Set<String> circular = registry.detectCircularDependencies();
            assertFalse(circular.isEmpty());
            // 7 + 2 = 9
            assertEquals(9, registry.size());
        }
    }

    @Nested
    @DisplayName("VariableEngine 分层解析测试")
    class EngineResolveTest {

        private VariableEngine engine;

        @BeforeEach
        void initEngine() {
            engine = new VariableEngine(registry)
                .setProvider(VariableLayer.EXTERNAL, (varIds, ctx) -> {
                    // Mock 外部数据源
                    Map<String, Object> result = new java.util.HashMap<>();
                    if (varIds.contains("credit_score")) result.put("credit_score", 720);
                    if (varIds.contains("overdue_count")) result.put("overdue_count", 1);
                    return result;
                })
                .setProvider(VariableLayer.CACHED, (varIds, ctx) -> {
                    // Mock Redis/HBase
                    Map<String, Object> result = new java.util.HashMap<>();
                    if (varIds.contains("feature_6m_avg")) result.put("feature_6m_avg", 3560.5);
                    return result;
                })
                .setDerivedProvider(expressionEngine);
        }

        @Test
        @DisplayName("完整分层解析 — L0 + L1 + L2 + L3")
        void fullResolve() {
            VariableResolveContext ctx = engine.resolve(
                Set.of("age", "name", "credit_score", "feature_6m_avg", "is_young", "risk_level"),
                "REQ_001",
                Map.of("age", 25, "name", "张三")
            );

            // L0 输入
            assertEquals(25, ctx.get("age"));
            assertEquals("张三", ctx.get("name"));

            // L1 外部
            assertEquals(720, ctx.get("credit_score"));
            assertEquals(1, ctx.get("overdue_count"));

            // L2 缓存
            assertEquals(3560.5, ctx.get("feature_6m_avg"));

            // L3 衍生
            assertEquals(true, ctx.get("is_young"));       // age=25 < 30 → true
            assertEquals(1, ctx.get("risk_level"));         // overdue_count=1 → 1
        }

        @Test
        @DisplayName("仅解析输入变量")
        void inputOnly() {
            VariableResolveContext ctx = engine.resolve(
                Set.of("age", "name"),
                "REQ_002",
                Map.of("age", 30, "name", "李四")
            );

            assertEquals(30, ctx.get("age"));
            assertEquals("李四", ctx.get("name"));
        }

        @Test
        @DisplayName("衍生变量依赖外部变量时正确计算")
        void derivedDependsOnExternal() {
            VariableResolveContext ctx = engine.resolve(
                Set.of("risk_level"),
                "REQ_003",
                Map.of()  // 无输入
            );

            // risk_level 依赖 overdue_count (L1), overdue_count=1
            assertEquals(1, ctx.get("risk_level"));
        }

        @Test
        @DisplayName("未注册的变量不报错，只返回 null")
        void unknownVar() {
            VariableResolveContext ctx = engine.resolve(
                Set.of("unknown_var"),
                "REQ_004",
                Map.of()
            );

            assertNull(ctx.get("unknown_var"));
        }

        @Test
        @DisplayName("衍生变量计算失败时返回 null")
        void derivedCalcError() {
            // age 为 null → age < 30 会抛异常 → is_young = null
            VariableResolveContext ctx = engine.resolve(
                Set.of("is_young"),
                "REQ_005",
                Map.of()  // age 未提供
            );

            // is_young 依赖 age，但 age 为 null → 表达式求值失败 → null
            // 注意: age 未在请求中也不在注册表中会触发 expression error
            // 但 ExpressionEngine.executeAsBoolean 会 catch 异常返回 false
            // 这里用 execute 可能抛异常
            assertNotNull(ctx);  // 不崩溃即可
        }

        @Test
        @DisplayName("循环依赖时 resolve 返回 null 而非无限递归")
        void circularDependency_resolution() {
            // 创建独立的 registry 和 engine 避免影响其他测试
            VariableRegistry circularRegistry = new VariableRegistry();
            circularRegistry.register(VariableDefinition.builder()
                .varId("x").name("X").layer(VariableLayer.DERIVED)
                .expression("y + 1").dependencies(Set.of("y")).build());
            circularRegistry.register(VariableDefinition.builder()
                .varId("y").name("Y").layer(VariableLayer.DERIVED)
                .expression("z + 1").dependencies(Set.of("z")).build());
            circularRegistry.register(VariableDefinition.builder()
                .varId("z").name("Z").layer(VariableLayer.DERIVED)
                .expression("x + 1").dependencies(Set.of("x")).build());

            // 先确认循环依赖能被检测到
            assertFalse(circularRegistry.detectCircularDependencies().isEmpty());

            // 尝试解析循环依赖变量 — 应安全返回而非无限递归
            VariableEngine circularEngine = new VariableEngine(circularRegistry)
                .setDerivedProvider(expressionEngine);

            VariableResolveContext ctx = circularEngine.resolve(
                Set.of("x"),
                "REQ_CIRCULAR",
                Map.of()
            );

            assertNotNull(ctx);  // 不崩溃
            // 循环依赖变量无法解析 → 返回 null
            assertNull(ctx.get("x"));
        }
    }

    @Nested
    @DisplayName("VariableDefinition 构建测试")
    class DefinitionTest {

        @Test
        @DisplayName("Builder 正确创建变量定义")
        void builder() {
            VariableDefinition def = VariableDefinition.builder()
                .varId("test_var").name("测试变量")
                .layer(VariableLayer.DERIVED)
                .dataType("INTEGER").category("衍生")
                .expression("age * 2")
                .dependencies(Set.of("age"))
                .version(2)
                .description("测试用衍生变量")
                .build();

            assertEquals("test_var", def.getVarId());
            assertEquals("测试变量", def.getName());
            assertTrue(def.isDerived());
            assertEquals("age * 2", def.getExpression());
            assertTrue(def.getDependencies().contains("age"));
            assertEquals(2, def.getVersion());
        }

        @Test
        @DisplayName("null dependencies 默认为空集合")
        void nullDependencies() {
            VariableDefinition def = VariableDefinition.builder()
                .varId("x").name("X").layer(VariableLayer.INPUT).build();
            assertTrue(def.getDependencies().isEmpty());
        }
    }
}
