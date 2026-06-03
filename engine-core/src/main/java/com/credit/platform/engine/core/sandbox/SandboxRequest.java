package com.credit.platform.engine.core.sandbox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 沙箱回测请求。
 * <p>
 * 定义一次沙箱回测的输入，包含要回测的策略版本和历史样本列表。
 * </p>
 *
 * <pre>
 * SandboxRequest request = SandboxRequest.builder()
 *     .strategyId("STR_CREDIT_V3")
 *     .baselineVersion("3.1")
 *     .candidateVersion("3.2")
 *     .addSample(HistorySample.of("APP_001", Map.of("age", 28, "income", 50000)))
 *     .build();
 * </pre>
 */
public final class SandboxRequest {

    private final String strategyId;
    private final String baselineVersion;
    private final String candidateVersion;
    private final List<HistorySample> samples;

    private SandboxRequest(Builder builder) {
        this.strategyId = Objects.requireNonNull(builder.strategyId, "strategyId must not be null");
        this.baselineVersion = Objects.requireNonNull(builder.baselineVersion, "baselineVersion must not be null");
        this.candidateVersion = builder.candidateVersion;
        this.samples = Collections.unmodifiableList(new ArrayList<>(builder.samples));
    }

    public String getStrategyId() { return strategyId; }
    public String getBaselineVersion() { return baselineVersion; }
    public String getCandidateVersion() { return candidateVersion; }
    public List<HistorySample> getSamples() { return samples; }

    public boolean hasCandidateVersion() {
        return candidateVersion != null && !candidateVersion.isEmpty();
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String strategyId;
        private String baselineVersion;
        private String candidateVersion;
        private final List<HistorySample> samples = new ArrayList<>();

        public Builder strategyId(String id) { this.strategyId = id; return this; }
        public Builder baselineVersion(String v) { this.baselineVersion = v; return this; }
        public Builder candidateVersion(String v) { this.candidateVersion = v; return this; }
        public Builder samples(List<HistorySample> samples) { this.samples.addAll(samples); return this; }
        public Builder addSample(HistorySample sample) { this.samples.add(sample); return this; }
        public SandboxRequest build() { return new SandboxRequest(this); }
    }

    @Override
    public String toString() {
        return "SandboxRequest{strategyId='" + strategyId
            + "', baseline='" + baselineVersion + "', candidate='" + candidateVersion
            + "', samples=" + samples.size() + '}';
    }
}
