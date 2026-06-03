package com.credit.platform.engine.core.scorecard;

import com.credit.platform.engine.common.model.ActionType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 评分卡执行结果。
 * <p>
 * 包含最终得分、分段结果、以及每个特征的得分明细（用于可解释性）。
 * </p>
 */
public final class ScorecardResult {

    private final String scorecardId;
    private final int initialScore;
    private final int finalScore;
    private final String result;          // PASS / REVIEW / REJECT
    private final List<CharacteristicScore> breakdown;
    private final long durationMs;

    private ScorecardResult(Builder builder) {
        this.scorecardId = builder.scorecardId;
        this.initialScore = builder.initialScore;
        this.finalScore = builder.finalScore;
        this.result = builder.result;
        this.breakdown = Collections.unmodifiableList(new ArrayList<>(builder.breakdown));
        this.durationMs = builder.durationMs;
    }

    public String getScorecardId() { return scorecardId; }
    public int getInitialScore() { return initialScore; }
    public int getFinalScore() { return finalScore; }
    public String getResult() { return result; }
    public List<CharacteristicScore> getBreakdown() { return breakdown; }
    public long getDurationMs() { return durationMs; }

    public static Builder builder() { return new Builder(); }

    /** 构建器 */
    public static class Builder {
        private String scorecardId;
        private int initialScore;
        private int finalScore;
        private String result;
        private List<CharacteristicScore> breakdown = new ArrayList<>();
        private long durationMs;

        public Builder scorecardId(String id) { this.scorecardId = id; return this; }
        public Builder initialScore(int score) { this.initialScore = score; return this; }
        public Builder finalScore(int score) { this.finalScore = score; return this; }
        public Builder result(String result) { this.result = result; return this; }
        public Builder breakdown(List<CharacteristicScore> breakdown) {
            this.breakdown = breakdown != null ? breakdown : Collections.emptyList();
            return this;
        }
        public Builder durationMs(long ms) { this.durationMs = ms; return this; }
        public ScorecardResult build() { return new ScorecardResult(this); }
    }

    /**
     * 单个特征的得分明细。
     */
    public static final class CharacteristicScore {
        private final String name;
        private final String field;
        private final Object value;
        private final String binLabel;
        private final int score;

        public CharacteristicScore(String name, String field, Object value,
                                    String binLabel, int score) {
            this.name = name;
            this.field = field;
            this.value = value;
            this.binLabel = binLabel;
            this.score = score;
        }

        public String getName() { return name; }
        public String getField() { return field; }
        public Object getValue() { return value; }
        public String getBinLabel() { return binLabel; }
        public int getScore() { return score; }

        @Override
        public String toString() {
            return name + "=" + value + " → " + binLabel + " (" + (score >= 0 ? "+" : "") + score + ")";
        }
    }

    @Override
    public String toString() {
        return "ScorecardResult{id='" + scorecardId + "', score=" + finalScore
            + ", result=" + result + ", breakdown=" + breakdown.size() + '}';
    }
}
