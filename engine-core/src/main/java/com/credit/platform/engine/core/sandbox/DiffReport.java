package com.credit.platform.engine.core.sandbox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 对比报告 — 新旧版本决策结果对比。
 * <p>
 * 汇总所有样本在基线版本和候选版本下的差异，统计差异率。
 * </p>
 *
 * <pre>
 * DiffReport report = sandboxRunner.run(request);
 * System.out.println("差异率: " + report.getDiffRate());
 * report.getDiffEntries().stream()
 *     .filter(DiffEntry::hasDiff)
 *     .forEach(e -> System.out.println(e.getSampleId() + ": " + e));
 * </pre>
 */
public final class DiffReport {

    private final String strategyId;
    private final String baselineVersion;
    private final String candidateVersion;
    private final int totalSamples;
    private final int unchangedCount;
    private final int changedCount;
    private final double diffRate;
    private final List<DiffEntry> entries;
    private final long totalDurationMs;
    private final Summary summary;

    private DiffReport(Builder builder) {
        this.strategyId = builder.strategyId;
        this.baselineVersion = builder.baselineVersion;
        this.candidateVersion = builder.candidateVersion;
        this.entries = Collections.unmodifiableList(new ArrayList<>(builder.entries));
        this.totalSamples = entries.size();
        this.changedCount = (int) entries.stream().filter(DiffEntry::hasDiff).count();
        this.unchangedCount = totalSamples - changedCount;
        this.diffRate = totalSamples > 0 ? (double) changedCount / totalSamples : 0.0;
        this.totalDurationMs = builder.totalDurationMs;

        // 按差异类型统计
        int resultChanged = (int) entries.stream()
            .filter(e -> e.getDiffType() == DiffEntry.DiffType.RESULT_CHANGED).count();
        int scoreChanged = (int) entries.stream()
            .filter(e -> e.getDiffType() == DiffEntry.DiffType.SCORE_CHANGED).count();
        int rejectCodeChanged = (int) entries.stream()
            .filter(e -> e.getDiffType() == DiffEntry.DiffType.REJECT_CODE_CHANGED).count();

        this.summary = new Summary(resultChanged, scoreChanged, rejectCodeChanged);
    }

    public String getStrategyId() { return strategyId; }
    public String getBaselineVersion() { return baselineVersion; }
    public String getCandidateVersion() { return candidateVersion; }
    public int getTotalSamples() { return totalSamples; }
    public int getUnchangedCount() { return unchangedCount; }
    public int getChangedCount() { return changedCount; }
    public double getDiffRate() { return diffRate; }
    public List<DiffEntry> getEntries() { return entries; }
    public long getTotalDurationMs() { return totalDurationMs; }
    public Summary getSummary() { return summary; }

    /**
     * 获取仅有差异的条目。
     */
    public List<DiffEntry> getDiffEntries() {
        return entries.stream().filter(DiffEntry::hasDiff).toList();
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String strategyId;
        private String baselineVersion;
        private String candidateVersion;
        private final List<DiffEntry> entries = new ArrayList<>();
        private long totalDurationMs;

        public Builder strategyId(String id) { this.strategyId = id; return this; }
        public Builder baselineVersion(String v) { this.baselineVersion = v; return this; }
        public Builder candidateVersion(String v) { this.candidateVersion = v; return this; }
        public Builder addEntry(DiffEntry entry) { this.entries.add(entry); return this; }
        public Builder entries(List<DiffEntry> entries) { this.entries.addAll(entries); return this; }
        public Builder totalDurationMs(long ms) { this.totalDurationMs = ms; return this; }
        public DiffReport build() { return new DiffReport(this); }
    }

    /**
     * 差异统计摘要。
     */
    public static final class Summary {
        private final int resultChanged;
        private final int scoreChanged;
        private final int rejectCodeChanged;

        Summary(int resultChanged, int scoreChanged, int rejectCodeChanged) {
            this.resultChanged = resultChanged;
            this.scoreChanged = scoreChanged;
            this.rejectCodeChanged = rejectCodeChanged;
        }

        public int getResultChanged() { return resultChanged; }
        public int getScoreChanged() { return scoreChanged; }
        public int getRejectCodeChanged() { return rejectCodeChanged; }
    }

    @Override
    public String toString() {
        return "DiffReport{strategy='" + strategyId
            + "', total=" + totalSamples
            + ", changed=" + changedCount
            + String.format(", diffRate=%.2f%%", diffRate * 100) + '}';
    }
}
