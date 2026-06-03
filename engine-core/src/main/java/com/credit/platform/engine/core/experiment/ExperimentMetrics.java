package com.credit.platform.engine.core.experiment;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * 实验指标收集器 — 异步收集各实验分组的通过率/耗时/命中规则数。
 * <p>
 * 线程安全，使用 LongAdder 高并发计数。
 * </p>
 */
public class ExperimentMetrics {

    private final ConcurrentHashMap<String, GroupMetrics> metrics = new ConcurrentHashMap<>();

    /**
     * 记录一次实验指标。
     *
     * @param experimentId 实验 ID
     * @param groupId      分组 ID
     * @param passed       是否通过
     * @param durationMs   耗时 ms
     * @param hitRules     命中规则数
     */
    public void record(String experimentId, String groupId,
                        boolean passed, long durationMs, int hitRules) {
        String key = experimentId + ":" + groupId;
        GroupMetrics m = metrics.computeIfAbsent(key, k -> new GroupMetrics());
        m.total.increment();
        m.totalDurationMs.add(durationMs);
        m.totalHitRules.add(hitRules);
        if (passed) m.passed.increment();
    }

    /**
     * 获取指定实验分组的指标快照。
     */
    public Snapshot getSnapshot(String experimentId, String groupId) {
        String key = experimentId + ":" + groupId;
        GroupMetrics m = metrics.get(key);
        if (m == null) return new Snapshot(0, 0, 0, 0);
        long total = m.total.sum();
        return new Snapshot(total, m.passed.sum(),
            total > 0 ? (double) m.totalDurationMs.sum() / total : 0,
            total > 0 ? (double) m.totalHitRules.sum() / total : 0);
    }

    /**
     * 计算两组间的统计显著性 (Z-test for proportions)。
     *
     * @return p-value, < 0.05 表示显著
     */
    public double calculatePValue(String experimentId, String controlGroup, String experimentGroup) {
        Snapshot control = getSnapshot(experimentId, controlGroup);
        Snapshot experiment = getSnapshot(experimentId, experimentGroup);

        if (control.total == 0 || experiment.total == 0) return 1.0;

        double p1 = control.passRate();
        double p2 = experiment.passRate();
        double p = (double) (control.passed + experiment.passed) / (control.total + experiment.total);

        double se = Math.sqrt(p * (1 - p) * (1.0 / control.total + 1.0 / experiment.total));
        if (se == 0) return 1.0;

        double z = Math.abs(p1 - p2) / se;
        return 2.0 * (1.0 - normalCdf(z));
    }

    /** 标准正态 CDF 近似 (Abramowitz and Stegun) */
    private static double normalCdf(double z) {
        double t = 1.0 / (1.0 + 0.2316419 * Math.abs(z));
        double d = 0.3989422804014327;
        double p = d * Math.exp(-z * z / 2.0) *
            (t * (0.319381530 + t * (-0.356563782 + t * (1.781477937 + t * (-1.821255978 + t * 1.330274429)))));
        return z > 0 ? 1.0 - p : p;
    }

    /** 清除所有指标 */
    public void reset() { metrics.clear(); }

    // ========== 内部类 ==========

    private static class GroupMetrics {
        final LongAdder total = new LongAdder();
        final LongAdder passed = new LongAdder();
        final LongAdder totalDurationMs = new LongAdder();
        final LongAdder totalHitRules = new LongAdder();
    }

    public record Snapshot(long total, long passed, double avgDurationMs, double avgHitRules) {
        public double passRate() { return total > 0 ? (double) passed / total : 0.0; }
    }
}
