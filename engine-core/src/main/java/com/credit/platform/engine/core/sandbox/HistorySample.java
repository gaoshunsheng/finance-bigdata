package com.credit.platform.engine.core.sandbox;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 历史样本 — 沙箱回测的单条输入数据。
 * <p>
 * 代表一次历史决策请求的输入，包含样本 ID、输入变量和（可选的）原始决策结果。
 * </p>
 */
public final class HistorySample {

    private final String sampleId;
    private final Map<String, Object> inputVariables;
    private final String originalResult;
    private final String originalRejectCode;
    private final Integer originalScore;

    private HistorySample(Builder builder) {
        this.sampleId = Objects.requireNonNull(builder.sampleId, "sampleId must not be null");
        this.inputVariables = Collections.unmodifiableMap(
            new LinkedHashMap<>(builder.inputVariables));
        this.originalResult = builder.originalResult;
        this.originalRejectCode = builder.originalRejectCode;
        this.originalScore = builder.originalScore;
    }

    /**
     * 简化工厂方法。
     */
    public static HistorySample of(String sampleId, Map<String, Object> inputVariables) {
        return builder().sampleId(sampleId).inputVariables(inputVariables).build();
    }

    /**
     * 带原始结果的工厂方法。
     */
    public static HistorySample of(String sampleId, Map<String, Object> inputVariables,
                                    String originalResult, String originalRejectCode) {
        return builder()
            .sampleId(sampleId)
            .inputVariables(inputVariables)
            .originalResult(originalResult)
            .originalRejectCode(originalRejectCode)
            .build();
    }

    public String getSampleId() { return sampleId; }
    public Map<String, Object> getInputVariables() { return inputVariables; }
    public String getOriginalResult() { return originalResult; }
    public String getOriginalRejectCode() { return originalRejectCode; }
    public Integer getOriginalScore() { return originalScore; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String sampleId;
        private Map<String, Object> inputVariables = new LinkedHashMap<>();
        private String originalResult;
        private String originalRejectCode;
        private Integer originalScore;

        public Builder sampleId(String id) { this.sampleId = id; return this; }
        public Builder inputVariables(Map<String, Object> vars) {
            this.inputVariables = vars != null ? new LinkedHashMap<>(vars) : new LinkedHashMap<>();
            return this;
        }
        public Builder originalResult(String result) { this.originalResult = result; return this; }
        public Builder originalRejectCode(String code) { this.originalRejectCode = code; return this; }
        public Builder originalScore(Integer score) { this.originalScore = score; return this; }
        public HistorySample build() { return new HistorySample(this); }
    }

    @Override
    public String toString() {
        return "HistorySample{id='" + sampleId + "', result='" + originalResult + "'}";
    }
}
