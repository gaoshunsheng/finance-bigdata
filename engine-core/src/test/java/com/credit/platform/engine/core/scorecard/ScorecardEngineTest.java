package com.credit.platform.engine.core.scorecard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.credit.platform.engine.common.exception.RuleCompileException;
import com.credit.platform.engine.core.executor.ExecutionContext;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 评分卡引擎综合测试 — 编译 + 执行 + 边界条件。
 */
class ScorecardEngineTest {

    private ScorecardCompiler compiler;
    private ScorecardExecutor executor;

    @BeforeEach
    void setUp() {
        compiler = new ScorecardCompiler();
        executor = new ScorecardExecutor();
    }

    private static final String CREDIT_SCORECARD_JSON = """
        {
          "scorecardId": "SC_CREDIT_A",
          "name": "信贷评分卡A",
          "initialScore": 500,
          "characteristics": [
            {
              "name": "年龄",
              "field": "age",
              "bins": [
                {"range": [null, 22],  "score": -10, "reason": "年龄偏小"},
                {"range": [22, 30],    "score": 15},
                {"range": [30, 45],    "score": 25},
                {"range": [45, 60],    "score": 20},
                {"range": [60, null],  "score": -5, "reason": "年龄偏大"}
              ]
            },
            {
              "name": "月收入",
              "field": "monthly_income",
              "bins": [
                {"range": [null, 5000],   "score": -15},
                {"range": [5000, 10000],  "score": 10},
                {"range": [10000, 20000], "score": 25},
                {"range": [20000, null],  "score": 35}
              ]
            },
            {
              "name": "近6月逾期次数",
              "field": "overdue_count_6m",
              "bins": [
                {"range": [null, 1],  "score": 30},
                {"range": [1, 3],     "score": 10},
                {"range": [3, 5],     "score": -20},
                {"range": [5, null],  "score": -50}
              ]
            }
          ],
          "cutoff": {
            "reject": 500,
            "review": 550,
            "pass": 550
          }
        }
        """;

    // ==================== 编译测试 ====================

    @Nested
    @DisplayName("评分卡编译测试")
    class CompilationTest {

        @Test
        @DisplayName("成功编译完整评分卡")
        void compileFull() {
            CompiledScorecard sc = compiler.compile(CREDIT_SCORECARD_JSON);

            assertEquals("SC_CREDIT_A", sc.getRuleId());
            assertEquals("信贷评分卡A", sc.getName());
            assertEquals("SCORECARD", sc.getRuleType());
            assertEquals(500, sc.getInitialScore());
            assertEquals(3, sc.getCharacteristics().size());
            assertNotNull(sc.getCutoff());
            assertEquals(3, sc.getReferencedFields().size());
            assertTrue(sc.getReferencedFields().contains("age"));
            assertTrue(sc.getReferencedFields().contains("monthly_income"));
            assertTrue(sc.getReferencedFields().contains("overdue_count_6m"));
        }

        @Test
        @DisplayName("分箱按 lowerBound 排序")
        void binsSorted() {
            CompiledScorecard sc = compiler.compile(CREDIT_SCORECARD_JSON);
            CompiledScorecard.Characteristic ageChar = sc.getCharacteristics().get(0);

            // 5 个分箱: (-∞,22), [22,30), [30,45), [45,60), [60,+∞)
            assertEquals(5, ageChar.getBins().size());
            // 第一个 bin 的 lowerBound 应为 null (负无穷)
            assertEquals(null, ageChar.getBins().get(0).getLowerBound());
        }

        @Test
        @DisplayName("缺少 scorecardId → 编译失败")
        void missingId_throws() {
            String json = """
                {
                  "initialScore": 500,
                  "characteristics": [],
                  "cutoff": {"reject": 400, "review": 500, "pass": 500}
                }
                """;
            assertThrows(RuleCompileException.class, () -> compiler.compile(json));
        }

        @Test
        @DisplayName("空 characteristics → 编译失败")
        void emptyCharacteristics_throws() {
            String json = """
                {
                  "scorecardId": "SC001",
                  "initialScore": 500,
                  "characteristics": [],
                  "cutoff": {"reject": 400, "review": 500, "pass": 500}
                }
                """;
            assertThrows(RuleCompileException.class, () -> compiler.compile(json));
        }

        @Test
        @DisplayName("cutoff 顺序错误 → 编译失败")
        void invalidCutoff_throws() {
            String json = """
                {
                  "scorecardId": "SC001",
                  "initialScore": 500,
                  "characteristics": [
                    {
                      "name": "年龄", "field": "age",
                      "bins": [{"range": [null, null], "score": 0}]
                    }
                  ],
                  "cutoff": {"reject": 600, "review": 500, "pass": 500}
                }
                """;
            assertThrows(RuleCompileException.class, () -> compiler.compile(json));
        }
    }

    // ==================== 分箱查找测试 ====================

    @Nested
    @DisplayName("分箱查找测试")
    class BinLookupTest {

        private CompiledScorecard scorecard;

        @BeforeEach
        void compile() {
            scorecard = compiler.compile(CREDIT_SCORECARD_JSON);
        }

        @Test
        @DisplayName("年龄 18 → (-∞, 22) → -10分")
        void age_young() {
            CompiledScorecard.Characteristic age = scorecard.getCharacteristics().get(0);
            CompiledScorecard.Bin bin = age.findBin(18);
            assertNotNull(bin);
            assertEquals(-10, bin.getScore());
        }

        @Test
        @DisplayName("年龄 28 → [22, 30) → +15分")
        void age_youngAdult() {
            CompiledScorecard.Characteristic age = scorecard.getCharacteristics().get(0);
            CompiledScorecard.Bin bin = age.findBin(28);
            assertNotNull(bin);
            assertEquals(15, bin.getScore());
        }

        @Test
        @DisplayName("年龄 35 → [30, 45) → +25分")
        void age_prime() {
            CompiledScorecard.Characteristic age = scorecard.getCharacteristics().get(0);
            CompiledScorecard.Bin bin = age.findBin(35);
            assertNotNull(bin);
            assertEquals(25, bin.getScore());
        }

        @Test
        @DisplayName("年龄 65 → [60, +∞) → -5分")
        void age_senior() {
            CompiledScorecard.Characteristic age = scorecard.getCharacteristics().get(0);
            CompiledScorecard.Bin bin = age.findBin(65);
            assertNotNull(bin);
            assertEquals(-5, bin.getScore());
        }

        @Test
        @DisplayName("边界值 22 → [22, 30) 而不是 (-∞, 22)")
        void boundary_lower() {
            CompiledScorecard.Characteristic age = scorecard.getCharacteristics().get(0);
            CompiledScorecard.Bin bin = age.findBin(22);
            assertNotNull(bin);
            assertEquals(15, bin.getScore()); // [22, 30) 的分数
        }

        @Test
        @DisplayName("null 值 → 返回 null bin")
        void nullValue_returnsNull() {
            CompiledScorecard.Characteristic age = scorecard.getCharacteristics().get(0);
            assertEquals(null, age.findBin(null));
        }
    }

    // ==================== 执行测试 ====================

    @Nested
    @DisplayName("评分卡执行测试")
    class ExecutionTest {

        private CompiledScorecard scorecard;

        @BeforeEach
        void compile() {
            scorecard = compiler.compile(CREDIT_SCORECARD_JSON);
        }

        @Test
        @DisplayName("优质客户 → PASS")
        void premiumCustomer_pass() {
            // 28岁 +15, 月收入15000 +25, 逾期0次 +30 → 500+15+25+30 = 570
            ExecutionContext ctx = ExecutionContext.create(Map.of(
                "age", 28,
                "monthly_income", 15000,
                "overdue_count_6m", 0
            ));

            ScorecardResult result = executor.execute(scorecard, ctx);

            assertEquals(570, result.getFinalScore());
            assertEquals("PASS", result.getResult());
            assertEquals(3, result.getBreakdown().size());
        }

        @Test
        @DisplayName("年轻低收入客户 → REVIEW (边界分数)")
        void youngLowIncome_review() {
            // 20岁 -10, 月收入3000 -15, 逾期0次 +30 → 500-10-15+30 = 505
            // cutoff: reject<500, review<550, pass>=550 → 505 → REVIEW
            ExecutionContext ctx = ExecutionContext.create(Map.of(
                "age", 20,
                "monthly_income", 3000,
                "overdue_count_6m", 0
            ));

            ScorecardResult result = executor.execute(scorecard, ctx);

            assertEquals(505, result.getFinalScore());
            assertEquals("REVIEW", result.getResult());
        }

        @Test
        @DisplayName("高风险客户 → REJECT")
        void highRisk_reject() {
            // 20岁 -10, 月收入3000 -15, 逾期6次 -50 → 500-10-15-50 = 425
            ExecutionContext ctx = ExecutionContext.create(Map.of(
                "age", 20,
                "monthly_income", 3000,
                "overdue_count_6m", 6
            ));

            ScorecardResult result = executor.execute(scorecard, ctx);

            assertEquals(425, result.getFinalScore());
            assertEquals("REJECT", result.getResult());
        }

        @Test
        @DisplayName("中等客户 → REVIEW")
        void mediumCustomer_review() {
            // 25岁 +15, 月收入8000 +10, 逾期2次 +10 → 500+15+10+10 = 535
            ExecutionContext ctx = ExecutionContext.create(Map.of(
                "age", 25,
                "monthly_income", 8000,
                "overdue_count_6m", 2
            ));

            ScorecardResult result = executor.execute(scorecard, ctx);

            assertEquals(535, result.getFinalScore());
            assertEquals("REVIEW", result.getResult());
        }

        @Test
        @DisplayName("得分明细包含每个特征的评分")
        void breakdownDetails() {
            ExecutionContext ctx = ExecutionContext.create(Map.of(
                "age", 35,
                "monthly_income", 25000,
                "overdue_count_6m", 0
            ));

            ScorecardResult result = executor.execute(scorecard, ctx);

            // 35 → +25, 25000 → +35, 0 → +30 → 500+25+35+30 = 590
            assertEquals(590, result.getFinalScore());
            assertEquals("PASS", result.getResult());

            // 验证明细
            assertEquals("年龄", result.getBreakdown().get(0).getName());
            assertEquals(25, result.getBreakdown().get(0).getScore());
            assertEquals("月收入", result.getBreakdown().get(1).getName());
            assertEquals(35, result.getBreakdown().get(1).getScore());
            assertEquals("近6月逾期次数", result.getBreakdown().get(2).getName());
            assertEquals(30, result.getBreakdown().get(2).getScore());
        }

        @Test
        @DisplayName("变量缺失时该特征得分 0")
        void missingVariable_scoresZero() {
            ExecutionContext ctx = ExecutionContext.create(new HashMap<>());
            // age=null → 0, monthly_income=null → 0, overdue_count_6m=null → 0
            // → 500 + 0 + 0 + 0 = 500 → REVIEW (500 < 550)

            ScorecardResult result = executor.execute(scorecard, ctx);

            assertEquals(500, result.getFinalScore());
            assertEquals("REVIEW", result.getResult());
        }

        @Test
        @DisplayName("执行结果包含耗时信息")
        void resultContainsTiming() {
            ExecutionContext ctx = ExecutionContext.create(Map.of(
                "age", 30, "monthly_income", 10000, "overdue_count_6m", 0
            ));

            ScorecardResult result = executor.execute(scorecard, ctx);
            assertTrue(result.getDurationMs() >= 0);
        }
    }

    // ==================== Cutoff 判定测试 ====================

    @Nested
    @DisplayName("Cutoff 阈值判定测试")
    class CutoffTest {

        @Test
        @DisplayName("得分低于 reject → REJECT")
        void belowReject() {
            CompiledScorecard.Cutoff cutoff = new CompiledScorecard.Cutoff(500, 550, 550);
            assertEquals("REJECT", cutoff.decide(499));
        }

        @Test
        @DisplayName("得分等于 reject → REVIEW (not REJECT)")
        void atReject() {
            CompiledScorecard.Cutoff cutoff = new CompiledScorecard.Cutoff(500, 550, 550);
            assertEquals("REVIEW", cutoff.decide(500));
        }

        @Test
        @DisplayName("得分等于 review → PASS (not REVIEW)")
        void atReview() {
            CompiledScorecard.Cutoff cutoff = new CompiledScorecard.Cutoff(500, 550, 550);
            assertEquals("PASS", cutoff.decide(550));
        }

        @Test
        @DisplayName("高分 → PASS")
        void highScore_pass() {
            CompiledScorecard.Cutoff cutoff = new CompiledScorecard.Cutoff(500, 550, 550);
            assertEquals("PASS", cutoff.decide(700));
        }
    }
}
