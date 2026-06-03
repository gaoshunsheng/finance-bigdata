package com.credit.platform.engine.core.experiment;

import java.util.*;

/**
 * 实验配置 — 定义 AB 实验的分流规则。
 */
public final class ExperimentConfig {

    private final String experimentId;
    private final String name;
    private final String trafficKey;        // 分流键 (userId/phone等)
    private final List<GroupConfig> groups; // 实验分组
    private final long startTimeMs;
    private final long endTimeMs;
    private final boolean enabled;
    private final String terminationCondition; // e.g. "p_value<0.05"

    private ExperimentConfig(Builder builder) {
        this.experimentId = Objects.requireNonNull(builder.experimentId);
        this.name = builder.name;
        this.trafficKey = Objects.requireNonNull(builder.trafficKey);
        this.groups = Collections.unmodifiableList(new ArrayList<>(builder.groups));
        this.startTimeMs = builder.startTimeMs;
        this.endTimeMs = builder.endTimeMs;
        this.enabled = builder.enabled;
        this.terminationCondition = builder.terminationCondition;
    }

    public String getExperimentId() { return experimentId; }
    public String getName() { return name; }
    public String getTrafficKey() { return trafficKey; }
    public List<GroupConfig> getGroups() { return groups; }
    public long getStartTimeMs() { return startTimeMs; }
    public long getEndTimeMs() { return endTimeMs; }
    public boolean isEnabled() { return enabled; }
    public String getTerminationCondition() { return terminationCondition; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String experimentId;
        private String name;
        private String trafficKey;
        private List<GroupConfig> groups = new ArrayList<>();
        private long startTimeMs;
        private long endTimeMs;
        private boolean enabled = true;
        private String terminationCondition;

        public Builder experimentId(String id) { this.experimentId = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder trafficKey(String key) { this.trafficKey = key; return this; }
        public Builder groups(List<GroupConfig> groups) { this.groups = groups; return this; }
        public Builder addGroup(GroupConfig group) { this.groups.add(group); return this; }
        public Builder startTimeMs(long ms) { this.startTimeMs = ms; return this; }
        public Builder endTimeMs(long ms) { this.endTimeMs = ms; return this; }
        public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
        public Builder terminationCondition(String cond) { this.terminationCondition = cond; return this; }
        public ExperimentConfig build() { return new ExperimentConfig(this); }
    }

    /**
     * 实验分组配置。
     */
    public static final class GroupConfig {
        private final String groupId;
        private final String name;
        private final double trafficRatio;  // 0.0 ~ 1.0
        private final String strategyId;    // 该分组使用的策略

        public GroupConfig(String groupId, String name, double trafficRatio, String strategyId) {
            this.groupId = groupId;
            this.name = name;
            this.trafficRatio = trafficRatio;
            this.strategyId = strategyId;
        }

        public String getGroupId() { return groupId; }
        public String getName() { return name; }
        public double getTrafficRatio() { return trafficRatio; }
        public String getStrategyId() { return strategyId; }
    }
}
