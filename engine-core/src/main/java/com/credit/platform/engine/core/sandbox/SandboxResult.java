package com.credit.platform.engine.core.sandbox;

import java.util.Objects;

/**
 * 沙箱回测单条结果。
 * <p>
 * 记录一个历史样本在沙箱中的回测结果。
 * </p>
 */
public final class SandboxResult {

    private final String sampleId;
    private final String result;         // PASS / REJECT / REVIEW / MANUAL
    private final String rejectCode;
    private final Integer score;
    private final long durationMs;
    private final boolean success;
    private final String errorMessage;

    private SandboxResult(Builder builder) {
        this.sampleId = Objects.requireNonNull(builder.sampleId);
        this.result = builder.result;
        this.rejectCode = builder.rejectCode;
        this.score = builder.score;
        this.durationMs = builder.durationMs;
        this.success = builder.success;
        this.errorMessage = builder.errorMessage;
    }

    public String getSampleId() { return sampleId; }
    public String getResult() { return result; }
    public String getRejectCode() { return rejectCode; }
    public Integer getScore() { return score; }
    public long getDurationMs() { return durationMs; }
    public boolean isSuccess() { return success; }
    public String getErrorMessage() { return errorMessage; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String sampleId;
        private String result;
        private String rejectCode;
        private Integer score;
        private long durationMs;
        private boolean success = true;
        private String errorMessage;

        public Builder sampleId(String id) { this.sampleId = id; return this; }
        public Builder result(String result) { this.result = result; return this; }
        public Builder rejectCode(String code) { this.rejectCode = code; return this; }
        public Builder score(Integer score) { this.score = score; return this; }
        public Builder durationMs(long ms) { this.durationMs = ms; return this; }
        public Builder success(boolean success) { this.success = success; return this; }
        public Builder errorMessage(String msg) { this.errorMessage = msg; return this; }
        public SandboxResult build() { return new SandboxResult(this); }
    }

    /**
     * 创建一个错误结果。
     */
    public static SandboxResult error(String sampleId, String errorMessage) {
        return builder().sampleId(sampleId).success(false).errorMessage(errorMessage).build();
    }

    @Override
    public String toString() {
        return "SandboxResult{sample='" + sampleId + "', result='" + result + "', score=" + score + '}';
    }
}
