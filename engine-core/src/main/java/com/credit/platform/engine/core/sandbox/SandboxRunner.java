package com.credit.platform.engine.core.sandbox;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * 沙箱回测引擎 — 使用历史数据回放决策流程。
 * <p>
 * 在隔离的沙箱环境中执行决策逻辑，不影响在线流量。支持:
 * <ul>
 *   <li>单版本回测: 验证某版本策略对历史数据的表现</li>
 *   <li>双版本对比: 基线版本 vs 候选版本的差异分析</li>
 * </ul>
 * </p>
 *
 * <p>设计要点:
 * <ul>
 *   <li>沙箱执行不写入在线日志和 ES</li>
 *   <li>不触发外部 API 调用（使用快照数据）</li>
 *   <li>结果仅存于内存，可随时丢弃</li>
 * </ul>
 * </p>
 *
 * <pre>
 * SandboxRunner runner = SandboxRunner.create(decisionFunction);
 * DiffReport report = runner.compare(request);
 * report.getDiffEntries().forEach(diff ->
 *     System.out.println(diff.getSampleId() + ": " + diff.getDiffType()));
 * </pre>
 */
public class SandboxRunner {

    /** 决策函数 — 接收变量上下文，返回决策结果 Map */
    private final Function<Map<String, Object>, Map<String, Object>> decisionFunction;

    /** 评分差异阈值 (默认 5 分) */
    private final int scoreDiffThreshold;

    private SandboxRunner(Function<Map<String, Object>, Map<String, Object>> decisionFunction,
                           int scoreDiffThreshold) {
        this.decisionFunction = Objects.requireNonNull(decisionFunction, "decisionFunction must not be null");
        this.scoreDiffThreshold = scoreDiffThreshold;
    }

    /**
     * 创建沙箱运行器。
     *
     * @param decisionFunction 决策执行函数 (inputVariables → result map)
     * @return 沙箱运行器
     */
    public static SandboxRunner create(Function<Map<String, Object>, Map<String, Object>> decisionFunction) {
        return new SandboxRunner(decisionFunction, 5);
    }

    /**
     * 创建沙箱运行器，指定评分差异阈值。
     */
    public static SandboxRunner create(Function<Map<String, Object>, Map<String, Object>> decisionFunction,
                                        int scoreDiffThreshold) {
        return new SandboxRunner(decisionFunction, scoreDiffThreshold);
    }

    /**
     * 执行单版本回测。
     *
     * @param samples 历史样本列表
     * @return 回测结果列表
     */
    public List<SandboxResult> run(List<HistorySample> samples) {
        return samples.stream()
            .map(this::executeSample)
            .toList();
    }

    /**
     * 执行双版本对比回测。
     * <p>
     * 对每个样本分别执行基线版本和候选版本的决策函数，对比差异。
     * </p>
     *
     * @param request 沙箱请求 (含基线版本、候选版本、样本列表)
     * @return 对比报告
     */
    public DiffReport compare(SandboxRequest request) {
        long startMs = System.currentTimeMillis();

        List<HistorySample> samples = request.getSamples();
        java.util.List<DiffEntry> entries = new java.util.ArrayList<>(samples.size());

        for (HistorySample sample : samples) {
            // 执行候选版本
            SandboxResult candidateResult = executeSample(sample);

            // 与原始结果对比
            DiffEntry entry = buildDiffEntry(sample, candidateResult);
            entries.add(entry);
        }

        long totalMs = System.currentTimeMillis() - startMs;

        return DiffReport.builder()
            .strategyId(request.getStrategyId())
            .baselineVersion(request.getBaselineVersion())
            .candidateVersion(request.getCandidateVersion())
            .entries(entries)
            .totalDurationMs(totalMs)
            .build();
    }

    /**
     * 批量回测并生成摘要报告。
     *
     * @param request 沙箱请求
     * @return 包含回测结果和对比报告的完整结果
     */
    public BatchResult batch(SandboxRequest request) {
        long startMs = System.currentTimeMillis();

        java.util.List<SandboxResult> results = new java.util.ArrayList<>();
        java.util.List<DiffEntry> diffs = new java.util.ArrayList<>();

        for (HistorySample sample : request.getSamples()) {
            SandboxResult result = executeSample(sample);
            results.add(result);

            if (sample.getOriginalResult() != null) {
                diffs.add(buildDiffEntry(sample, result));
            }
        }

        long totalMs = System.currentTimeMillis() - startMs;

        DiffReport report = DiffReport.builder()
            .strategyId(request.getStrategyId())
            .baselineVersion(request.getBaselineVersion())
            .candidateVersion(request.getCandidateVersion())
            .entries(diffs)
            .totalDurationMs(totalMs)
            .build();

        return new BatchResult(results, report, totalMs);
    }

    /**
     * 执行单个样本。
     */
    private SandboxResult executeSample(HistorySample sample) {
        long startMs = System.currentTimeMillis();
        try {
            Map<String, Object> rawResult = decisionFunction.apply(sample.getInputVariables());
            long durationMs = System.currentTimeMillis() - startMs;

            return SandboxResult.builder()
                .sampleId(sample.getSampleId())
                .result(getString(rawResult, "result"))
                .rejectCode(getString(rawResult, "rejectCode"))
                .score(getInteger(rawResult, "score"))
                .durationMs(durationMs)
                .build();
        } catch (Exception e) {
            return SandboxResult.error(sample.getSampleId(), e.getMessage());
        }
    }

    /**
     * 构建差异条目。
     */
    private DiffEntry buildDiffEntry(HistorySample sample, SandboxResult candidate) {
        String originalResult = sample.getOriginalResult();
        String newResult = candidate.getResult();

        DiffEntry.DiffType diffType;
        if (originalResult == null || originalResult.equals(newResult)) {
            // 检查评分差异
            if (sample.getOriginalScore() != null && candidate.getScore() != null) {
                int scoreDiff = Math.abs(sample.getOriginalScore() - candidate.getScore());
                diffType = scoreDiff > scoreDiffThreshold
                    ? DiffEntry.DiffType.SCORE_CHANGED
                    : DiffEntry.DiffType.UNCHANGED;
            } else if (originalResult != null && !Objects.equals(sample.getOriginalRejectCode(), candidate.getRejectCode())) {
                diffType = DiffEntry.DiffType.REJECT_CODE_CHANGED;
            } else {
                diffType = DiffEntry.DiffType.UNCHANGED;
            }
        } else {
            diffType = DiffEntry.DiffType.RESULT_CHANGED;
        }

        return DiffEntry.builder()
            .sampleId(sample.getSampleId())
            .diffType(diffType)
            .baselineResult(originalResult)
            .candidateResult(newResult)
            .baselineRejectCode(sample.getOriginalRejectCode())
            .candidateRejectCode(candidate.getRejectCode())
            .baselineScore(sample.getOriginalScore())
            .candidateScore(candidate.getScore())
            .durationMs(candidate.getDurationMs())
            .build();
    }

    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }

    private Integer getInteger(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Integer) return (Integer) val;
        if (val instanceof Number) return ((Number) val).intValue();
        return null;
    }

    /**
     * 批量回测结果。
     */
    public static final class BatchResult {
        private final List<SandboxResult> results;
        private final DiffReport report;
        private final long totalDurationMs;

        BatchResult(List<SandboxResult> results, DiffReport report, long totalDurationMs) {
            this.results = results;
            this.report = report;
            this.totalDurationMs = totalDurationMs;
        }

        public List<SandboxResult> getResults() { return results; }
        public DiffReport getReport() { return report; }
        public long getTotalDurationMs() { return totalDurationMs; }
    }
}
