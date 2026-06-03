package com.credit.platform.engine.core.scorecard;

import com.credit.platform.engine.core.compiler.CompiledRule;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 编译后的评分卡 — 多特征分箱加权评分 + 阈值判定。
 * <p>
 * 不可变对象，线程安全。编译时将 JSON 分箱定义转为有序 Bin 数组，
 * 运行时通过二分查找快速定位命中的分箱。
 * </p>
 *
 * <pre>
 * // 执行流程:
 * 1. 初始分 = initialScore
 * 2. 遍历 characteristics:
 *    a. 获取 field 对应变量值
 *    b. 二分查找命中的 bin
 *    c. 累加 bin.score
 *    d. 记录得分明细 (可解释性)
 * 3. 根据 cutoff 判定: reject / review / pass
 * </pre>
 */
public final class CompiledScorecard implements CompiledRule {

    private static final String RULE_TYPE = "SCORECARD";

    private final String scorecardId;
    private final String name;
    private final int version;
    private final int initialScore;
    private final List<Characteristic> characteristics;
    private final Cutoff cutoff;
    private final Set<String> referencedFields;

    public CompiledScorecard(String scorecardId, String name, int version,
                              int initialScore, List<Characteristic> characteristics,
                              Cutoff cutoff) {
        this.scorecardId = Objects.requireNonNull(scorecardId);
        this.name = name;
        this.version = version;
        this.initialScore = initialScore;
        this.characteristics = Collections.unmodifiableList(new ArrayList<>(
            Objects.requireNonNull(characteristics)));
        this.cutoff = Objects.requireNonNull(cutoff);

        Set<String> fields = new HashSet<>();
        for (Characteristic c : this.characteristics) {
            fields.add(c.getField());
        }
        this.referencedFields = Collections.unmodifiableSet(fields);
    }

    @Override
    public String getRuleId() { return scorecardId; }
    @Override
    public String getName() { return name; }
    @Override
    public int getVersion() { return version; }
    @Override
    public String getRuleType() { return RULE_TYPE; }

    public int getInitialScore() { return initialScore; }
    public List<Characteristic> getCharacteristics() { return characteristics; }
    public Cutoff getCutoff() { return cutoff; }
    public Set<String> getReferencedFields() { return referencedFields; }

    /**
     * 特征定义 — 一个评分维度（如"年龄"、"逾期次数"）。
     */
    public static final class Characteristic {
        private final String name;
        private final String field;
        private final List<Bin> bins;

        public Characteristic(String name, String field, List<Bin> bins) {
            this.name = Objects.requireNonNull(name);
            this.field = Objects.requireNonNull(field);
            // Bin 按 lowerBound 排序（用于二分查找），null（负无穷）排最前
            List<Bin> sorted = new ArrayList<>(Objects.requireNonNull(bins));
            sorted.sort((a, b) -> {
                if (a.lowerBound == null) return -1;
                if (b.lowerBound == null) return 1;
                return Double.compare(((Number) a.lowerBound).doubleValue(),
                                      ((Number) b.lowerBound).doubleValue());
            });
            this.bins = Collections.unmodifiableList(sorted);
        }

        public String getName() { return name; }
        public String getField() { return field; }
        public List<Bin> getBins() { return bins; }

        /**
         * 查找变量值命中的分箱。使用二分查找。
         *
         * @param value 变量值
         * @return 命中的 Bin，未匹配时返回 null
         */
        public Bin findBin(Object value) {
            if (value == null) {
                return null;
            }
            double numValue = (value instanceof Number)
                ? ((Number) value).doubleValue()
                : Double.NaN;

            for (Bin bin : bins) {
                if (bin.matches(numValue)) {
                    return bin;
                }
            }
            return null;
        }
    }

    /**
     * 分箱定义 — 一个评分区间。
     * <p>
     * range = [lowerBound, upperBound)，lowerBound/upperBound 为 null 表示无边界。
     * </p>
     */
    public static final class Bin {
        private final Object lowerBound;   // null = 负无穷
        private final Object upperBound;   // null = 正无穷
        private final int score;
        private final String reason;
        private final String label;        // 用于可解释性展示

        public Bin(Object lowerBound, Object upperBound, int score,
                    String reason, String label) {
            this.lowerBound = lowerBound;
            this.upperBound = upperBound;
            this.score = score;
            this.reason = reason;
            this.label = label;
        }

        /**
         * 判断数值是否落入当前分箱。
         * 区间为左闭右开: [lowerBound, upperBound)
         */
        boolean matches(double value) {
            if (Double.isNaN(value)) {
                return false;
            }
            boolean aboveLower = (lowerBound == null)
                || value >= ((Number) lowerBound).doubleValue();
            boolean belowUpper = (upperBound == null)
                || value < ((Number) upperBound).doubleValue();
            return aboveLower && belowUpper;
        }

        public Object getLowerBound() { return lowerBound; }
        public Object getUpperBound() { return upperBound; }
        public int getScore() { return score; }
        public String getReason() { return reason; }
        public String getLabel() { return label; }

        @Override
        public String toString() {
            return "Bin{[" + lowerBound + ", " + upperBound + "), score=" + score + '}';
        }
    }

    /**
     * 截断阈值 — 根据总分判定结果。
     */
    public static final class Cutoff {
        private final int reject;    // < reject → REJECT
        private final int review;    // < review → REVIEW
        private final int pass;      // >= pass → PASS

        public Cutoff(int reject, int review, int pass) {
            this.reject = reject;
            this.review = review;
            this.pass = pass;
        }

        /**
         * 根据得分判定结果。
         *
         * @param score 最终得分
         * @return "REJECT" / "REVIEW" / "PASS"
         */
        public String decide(int score) {
            if (score < reject) {
                return "REJECT";
            } else if (score < review) {
                return "REVIEW";
            } else {
                return "PASS";
            }
        }

        public int getReject() { return reject; }
        public int getReview() { return review; }
        public int getPass() { return pass; }
    }

    @Override
    public String toString() {
        return "CompiledScorecard{id='" + scorecardId + "', initial=" + initialScore
            + ", characteristics=" + characteristics.size() + '}';
    }
}
