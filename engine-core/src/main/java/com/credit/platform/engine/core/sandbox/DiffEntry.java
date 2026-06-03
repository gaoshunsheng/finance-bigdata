package com.credit.platform.engine.core.sandbox;

import java.util.Objects;

/**
 * 单条差异记录 — 新旧版本决策结果差异。
 * <p>
 * 对比同一样本在基线版本和候选版本下的决策结果差异。
 * </p>
 */
public final class DiffEntry {

    /** 差异类型 */
    public enum DiffType {
        /** 决策结果不同 (如 PASS → REJECT) */
        RESULT_CHANGED,
        /** 评分差异超过阈值 */
        SCORE_CHANGED,
        /** 拒绝码不同 */
        REJECT_CODE_CHANGED,
        /** 结果一致 */
        UNCHANGED
    }

    private final String sampleId;
    private final DiffType diffType;
    private final String baselineResult;
    private final String candidateResult;
    private final String baselineRejectCode;
    private final String candidateRejectCode;
    private final Integer baselineScore;
    private final Integer candidateScore;
    private final long durationMs;

    private DiffEntry(Builder builder) {
        this.sampleId = Objects.requireNonNull(builder.sampleId);
        this.diffType = Objects.requireNonNull(builder.diffType);
        this.baselineResult = builder.baselineResult;
        this.candidateResult = builder.candidateResult;
        this.baselineRejectCode = builder.baselineRejectCode;
        this.candidateRejectCode = builder.candidateRejectCode;
        this.baselineScore = builder.baselineScore;
        this.candidateScore = builder.candidateScore;
        this.durationMs = builder.durationMs;
    }

    public String getSampleId() { return sampleId; }
    public DiffType getDiffType() { return diffType; }
    public String getBaselineResult() { return baselineResult; }
    public String getCandidateResult() { return candidateResult; }
    public String getBaselineRejectCode() { return baselineRejectCode; }
    public String getCandidateRejectCode() { return candidateRejectCode; }
    public Integer getBaselineScore() { return baselineScore; }
    public Integer getCandidateScore() { return candidateScore; }
    public long getDurationMs() { return durationMs; }

    public boolean hasDiff() { return diffType != DiffType.UNCHANGED; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String sampleId;
        private DiffType diffType;
        private String baselineResult;
        private String candidateResult;
        private String baselineRejectCode;
        private String candidateRejectCode;
        private Integer baselineScore;
        private Integer candidateScore;
        private long durationMs;

        public Builder sampleId(String id) { this.sampleId = id; return this; }
        public Builder diffType(DiffType type) { this.diffType = type; return this; }
        public Builder baselineResult(String r) { this.baselineResult = r; return this; }
        public Builder candidateResult(String r) { this.candidateResult = r; return this; }
        public Builder baselineRejectCode(String c) { this.baselineRejectCode = c; return this; }
        public Builder candidateRejectCode(String c) { this.candidateRejectCode = c; return this; }
        public Builder baselineScore(Integer s) { this.baselineScore = s; return this; }
        public Builder candidateScore(Integer s) { this.candidateScore = s; return this; }
        public Builder durationMs(long ms) { this.durationMs = ms; return this; }
        public DiffEntry build() { return new DiffEntry(this); }
    }

    @Override
    public String toString() {
        return "DiffEntry{sample='" + sampleId + "', type=" + diffType
            + ", baseline=" + baselineResult + " → candidate=" + candidateResult + '}';
    }
}
