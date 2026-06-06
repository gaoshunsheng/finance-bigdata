package com.credit.platform.engine.core.table;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.credit.platform.engine.common.exception.RuleCompileException;
import com.credit.platform.engine.core.executor.ExecutionContext;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 决策表引擎测试 — 编译 + 执行。
 */
class DecisionTableEngineTest {

    private DecisionTableCompiler compiler;

    @BeforeEach
    void setUp() {
        compiler = new DecisionTableCompiler();
    }

    private static final String LOAN_TABLE_JSON = """
        {
          "tableId": "DT_LOAN_POLICY",
          "name": "贷款策略决策表",
          "columns": [
            {"name": "客户类型", "field": "customer_type"},
            {"name": "信用等级", "field": "credit_level"},
            {"name": "贷款用途", "field": "loan_purpose"}
          ],
          "rows": [
            {
              "conditions": ["NEW", "A", "CONSUMPTION"],
              "result": {"action": "APPROVE", "maxAmount": 500000}
            },
            {
              "conditions": ["NEW", "A", "BUSINESS"],
              "result": {"action": "APPROVE", "maxAmount": 300000}
            },
            {
              "conditions": ["NEW", "C", "*"],
              "result": {"action": "REJECT", "reason": "新客C级不可准入"}
            },
            {
              "conditions": ["EXISTING", "A", "*"],
              "result": {"action": "APPROVE", "maxAmount": 1000000}
            },
            {
              "conditions": ["*", "D", "*"],
              "result": {"action": "REJECT", "reason": "D级客户不可准入"}
            }
          ],
          "hitPolicy": "FIRST_MATCH"
        }
        """;

    @Nested
    @DisplayName("决策表编译测试")
    class CompilationTest {

        @Test
        @DisplayName("成功编译决策表")
        void compileSuccess() {
            CompiledDecisionTable table = compiler.compile(LOAN_TABLE_JSON);

            assertEquals("DT_LOAN_POLICY", table.getRuleId());
            assertEquals("DECISION_TABLE", table.getRuleType());
            assertEquals(3, table.getColumns().size());
            assertEquals(5, table.getRows().size());
            assertEquals(3, table.getReferencedFields().size());
        }

        @Test
        @DisplayName("缺少 tableId → 失败")
        void missingId() {
            assertThrows(RuleCompileException.class, () -> compiler.compile("""
                {"columns": [], "rows": []}
                """));
        }

        @Test
        @DisplayName("空 rows → 失败")
        void emptyRows() {
            assertThrows(RuleCompileException.class, () -> compiler.compile("""
                {
                  "tableId": "DT001",
                  "columns": [{"name": "x", "field": "x"}],
                  "rows": []
                }
                """));
        }
    }

    @Nested
    @DisplayName("决策表执行测试")
    class ExecutionTest {

        private CompiledDecisionTable table;

        @BeforeEach
        void compile() {
            table = compiler.compile(LOAN_TABLE_JSON);
        }

        @Test
        @DisplayName("新客A类消费贷 → 50万额度")
        void newACoreConsumption() {
            CompiledDecisionTable.TableRow row = table.evaluate(Map.of(
                "customer_type", "NEW",
                "credit_level", "A",
                "loan_purpose", "CONSUMPTION"
            ));

            assertNotNull(row);
            assertEquals("APPROVE", row.getResult().get("action"));
            assertEquals(500000, row.getResult().get("maxAmount"));
        }

        @Test
        @DisplayName("新客A类经营贷 → 30万额度")
        void newABusiness() {
            CompiledDecisionTable.TableRow row = table.evaluate(Map.of(
                "customer_type", "NEW",
                "credit_level", "A",
                "loan_purpose", "BUSINESS"
            ));

            assertNotNull(row);
            assertEquals("APPROVE", row.getResult().get("action"));
            assertEquals(300000, row.getResult().get("maxAmount"));
        }

        @Test
        @DisplayName("新客C类 → 拒绝 (* 通配符匹配任意贷款用途)")
        void newCReject() {
            CompiledDecisionTable.TableRow row = table.evaluate(Map.of(
                "customer_type", "NEW",
                "credit_level", "C",
                "loan_purpose", "CONSUMPTION"
            ));

            assertNotNull(row);
            assertEquals("REJECT", row.getResult().get("action"));
            assertEquals("新客C级不可准入", row.getResult().get("reason"));
        }

        @Test
        @DisplayName("老客A类 → 100万额度 (* 通配符匹配用途)")
        void existingA() {
            CompiledDecisionTable.TableRow row = table.evaluate(Map.of(
                "customer_type", "EXISTING",
                "credit_level", "A",
                "loan_purpose", "ANY_PURPOSE"
            ));

            assertNotNull(row);
            assertEquals("APPROVE", row.getResult().get("action"));
            assertEquals(1000000, row.getResult().get("maxAmount"));
        }

        @Test
        @DisplayName("D级客户 → 拒绝 (全 * 通配符兜底)")
        void dLevelReject() {
            CompiledDecisionTable.TableRow row = table.evaluate(Map.of(
                "customer_type", "EXISTING",
                "credit_level", "D",
                "loan_purpose", "CONSUMPTION"
            ));

            assertNotNull(row);
            assertEquals("REJECT", row.getResult().get("action"));
        }

        @Test
        @DisplayName("无匹配行 → 返回 null")
        void noMatch() {
            // 老客B类经营贷 → 不在任何行中
            CompiledDecisionTable.TableRow row = table.evaluate(Map.of(
                "customer_type", "EXISTING",
                "credit_level", "B",
                "loan_purpose", "BUSINESS"
            ));

            assertNull(row);
        }

        @Test
        @DisplayName("多列通配符匹配 — 全 * 行匹配后返回空结果集")
        void wildcardMatching_emptyResult() {
            // 使用仅含通配符的决策表，验证 * 匹配任意值且空行表编译失败
            String wildcardTableJson = """
                {
                  "tableId": "DT_WILDCARD",
                  "name": "通配符测试表",
                  "columns": [
                    {"name": "地区", "field": "region"},
                    {"name": "等级", "field": "level"}
                  ],
                  "rows": [
                    {
                      "conditions": ["*", "*"],
                      "result": {}
                    }
                  ],
                  "hitPolicy": "FIRST_MATCH"
                }
                """;

            CompiledDecisionTable wTable = compiler.compile(wildcardTableJson);
            // 任意输入都应该命中全 * 行
            CompiledDecisionTable.TableRow row = wTable.evaluate(Map.of(
                "region", "华东",
                "level", "A"
            ));

            assertNotNull(row);
            // result 为空 map → 无 action / reason 等字段
            assertTrue(row.getResult().isEmpty());
        }
    }
}
