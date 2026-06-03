package com.credit.platform.engine.core.sandbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 沙箱回测引擎单元测试。
 * <p>
 * 覆盖: SandboxRunner (单版本回测 + 双版本对比), DiffReport, HistorySample。
 * </p>
 */
class SandboxRunnerTest {

    /** 模拟决策函数 — age < 22 或 age > 60 拒绝 */
    private static final java.util.function.Function<Map<String, Object>, Map<String, Object>> DECISION_FN =
        vars -> {
            int age = vars.get("age") instanceof Number ? ((Number) vars.get("age")).intValue() : 0;
            if (age < 22) {
                return Map.of("result", "REJECT", "rejectCode", "AGE_TOO_YOUNG", "score", 400);
            } else if (age > 60) {
                return Map.of("result", "REJECT", "rejectCode", "AGE_TOO_OLD", "score", 420);
            } else {
                return Map.of("result", "PASS", "score", 700);
            }
        };

    // ========== 单版本回测 ==========

    @Nested
    @DisplayName("SandboxRunner - 单版本回测")
    class SingleVersionTests {

        @Test
        @DisplayName("单版本回测 - 通过样本")
        void run_singleSample_pass() {
            SandboxRunner runner = SandboxRunner.create(DECISION_FN);
            HistorySample sample = HistorySample.of("APP_001", Map.of("age", 28));

            List<SandboxResult> results = runner.run(List.of(sample));

            assertEquals(1, results.size());
            assertEquals("APP_001", results.get(0).getSampleId());
            assertEquals("PASS", results.get(0).getResult());
            assertEquals(700, results.get(0).getScore());
            assertTrue(results.get(0).isSuccess());
        }

        @Test
        @DisplayName("单版本回测 - 拒绝样本")
        void run_singleSample_reject() {
            SandboxRunner runner = SandboxRunner.create(DECISION_FN);
            HistorySample sample = HistorySample.of("APP_002", Map.of("age", 18));

            List<SandboxResult> results = runner.run(List.of(sample));

            assertEquals("REJECT", results.get(0).getResult());
            assertEquals("AGE_TOO_YOUNG", results.get(0).getRejectCode());
        }

        @Test
        @DisplayName("单版本回测 - 批量样本")
        void run_batchSamples() {
            SandboxRunner runner = SandboxRunner.create(DECISION_FN);
            List<HistorySample> samples = List.of(
                HistorySample.of("APP_001", Map.of("age", 28)),
                HistorySample.of("APP_002", Map.of("age", 18)),
                HistorySample.of("APP_003", Map.of("age", 65))
            );

            List<SandboxResult> results = runner.run(samples);

            assertEquals(3, results.size());
            assertEquals("PASS", results.get(0).getResult());
            assertEquals("REJECT", results.get(1).getResult());
            assertEquals("REJECT", results.get(2).getResult());
        }
    }

    // ========== 双版本对比 ==========

    @Nested
    @DisplayName("SandboxRunner - 双版本对比")
    class CompareTests {

        @Test
        @DisplayName("对比报告 - 结果一致")
        void compare_unchangedResults() {
            SandboxRunner runner = SandboxRunner.create(DECISION_FN);

            SandboxRequest request = SandboxRequest.builder()
                .strategyId("STR_001")
                .baselineVersion("3.1")
                .candidateVersion("3.2")
                .addSample(HistorySample.of("APP_001", Map.of("age", 28), "PASS", null))
                .build();

            DiffReport report = runner.compare(request);

            assertEquals(1, report.getTotalSamples());
            assertEquals(0, report.getChangedCount());
            assertEquals(0.0, report.getDiffRate(), 0.001);
        }

        @Test
        @DisplayName("对比报告 - 结果变化")
        void compare_resultChanged() {
            SandboxRunner runner = SandboxRunner.create(DECISION_FN);

            SandboxRequest request = SandboxRequest.builder()
                .strategyId("STR_001")
                .baselineVersion("3.1")
                .candidateVersion("3.2")
                .addSample(HistorySample.of("APP_001", Map.of("age", 18), "PASS", null))
                .build();

            DiffReport report = runner.compare(request);

            assertEquals(1, report.getChangedCount());
            assertEquals(1.0, report.getDiffRate(), 0.001);
            assertEquals(DiffEntry.DiffType.RESULT_CHANGED, report.getDiffEntries().get(0).getDiffType());
        }

        @Test
        @DisplayName("对比报告 - 混合样本统计")
        void compare_mixedResults() {
            SandboxRunner runner = SandboxRunner.create(DECISION_FN);

            SandboxRequest request = SandboxRequest.builder()
                .strategyId("STR_001")
                .baselineVersion("3.1")
                .candidateVersion("3.2")
                .addSample(HistorySample.of("APP_001", Map.of("age", 28), "PASS", null))
                .addSample(HistorySample.of("APP_002", Map.of("age", 18), "PASS", null))   // 新版拒绝
                .addSample(HistorySample.of("APP_003", Map.of("age", 45), "PASS", null))   // 一致
                .build();

            DiffReport report = runner.compare(request);

            assertEquals(3, report.getTotalSamples());
            assertEquals(1, report.getChangedCount());
            assertEquals(1.0 / 3.0, report.getDiffRate(), 0.001);
            assertEquals(2, report.getUnchangedCount());
        }
    }

    // ========== 批量回测 ==========

    @Nested
    @DisplayName("SandboxRunner - 批量回测")
    class BatchTests {

        @Test
        @DisplayName("批量回测返回结果和报告")
        void batch_returnsResultsAndReport() {
            SandboxRunner runner = SandboxRunner.create(DECISION_FN);

            SandboxRequest request = SandboxRequest.builder()
                .strategyId("STR_001")
                .baselineVersion("3.1")
                .candidateVersion("3.2")
                .addSample(HistorySample.of("APP_001", Map.of("age", 28), "PASS", null))
                .addSample(HistorySample.of("APP_002", Map.of("age", 65), "PASS", null))
                .build();

            SandboxRunner.BatchResult batch = runner.batch(request);

            assertEquals(2, batch.getResults().size());
            assertNotNull(batch.getReport());
            assertTrue(batch.getTotalDurationMs() >= 0);
        }
    }

    // ========== DiffReport 统计 ==========

    @Nested
    @DisplayName("DiffReport - 统计报告")
    class ReportTests {

        @Test
        @DisplayName("空样本报告")
        void emptyReport() {
            DiffReport report = DiffReport.builder()
                .strategyId("STR_001")
                .baselineVersion("3.1")
                .build();

            assertEquals(0, report.getTotalSamples());
            assertEquals(0, report.getChangedCount());
            assertEquals(0.0, report.getDiffRate(), 0.001);
        }

        @Test
        @DisplayName("Summary 按类型统计正确")
        void summary_countsCorrectly() {
            DiffReport report = DiffReport.builder()
                .strategyId("STR_001")
                .baselineVersion("3.1")
                .addEntry(DiffEntry.builder().sampleId("A").diffType(DiffEntry.DiffType.RESULT_CHANGED).build())
                .addEntry(DiffEntry.builder().sampleId("B").diffType(DiffEntry.DiffType.UNCHANGED).build())
                .addEntry(DiffEntry.builder().sampleId("C").diffType(DiffEntry.DiffType.SCORE_CHANGED).build())
                .build();

            assertEquals(3, report.getTotalSamples());
            assertEquals(2, report.getChangedCount());
            assertEquals(1, report.getSummary().getResultChanged());
            assertEquals(1, report.getSummary().getScoreChanged());
        }
    }
}
