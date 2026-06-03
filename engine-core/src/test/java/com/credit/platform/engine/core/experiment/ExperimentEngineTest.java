package com.credit.platform.engine.core.experiment;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.*;

/**
 * AB 实验引擎测试 (≥10 用例)。
 */
@DisplayName("AB 实验引擎")
class ExperimentEngineTest {

    private ExperimentConfig config;

    @BeforeEach
    void setUp() {
        config = ExperimentConfig.builder()
            .experimentId("exp-001")
            .name("利率策略AB测试")
            .trafficKey("userId")
            .addGroup(new ExperimentConfig.GroupConfig("control", "对照组", 0.5, "STRATEGY_V1"))
            .addGroup(new ExperimentConfig.GroupConfig("experiment", "实验组", 0.5, "STRATEGY_V2"))
            .build();
    }

    // ========== 分流一致性 ==========

    @Test
    @DisplayName("1. 一致性哈希 — 相同 key 始终分到同一组")
    void split_consistency() {
        String group = ExperimentSplitter.split(config, "user-123");
        for (int i = 0; i < 100; i++) {
            assertEquals(group, ExperimentSplitter.split(config, "user-123"));
        }
    }

    @Test
    @DisplayName("2. 分流比例近似 50/50")
    void split_approximateRatio() {
        Map<String, Integer> counts = new HashMap<>();
        counts.put("control", 0);
        counts.put("experiment", 0);

        for (int i = 0; i < 10000; i++) {
            String group = ExperimentSplitter.split(config, "user-" + i);
            counts.merge(group, 1, Integer::sum);
        }

        double controlRatio = counts.get("control") / 10000.0;
        assertTrue(controlRatio > 0.45 && controlRatio < 0.55,
            "Control ratio should be ~50%, was " + controlRatio);
    }

    @Test
    @DisplayName("3. 不同实验 ID 产生不同分流")
    void split_differentExperiment() {
        ExperimentConfig config2 = ExperimentConfig.builder()
            .experimentId("exp-002").trafficKey("userId")
            .addGroup(new ExperimentConfig.GroupConfig("A", "A", 0.5, "S1"))
            .addGroup(new ExperimentConfig.GroupConfig("B", "B", 0.5, "S2"))
            .build();

        int diff = 0;
        for (int i = 0; i < 1000; i++) {
            String g1 = ExperimentSplitter.split(config, "u" + i);
            String g2 = ExperimentSplitter.split(config2, "u" + i);
            if (!g1.equals(g2)) diff++;
        }
        assertTrue(diff > 100, "Different experiments should produce different splits");
    }

    @Test
    @DisplayName("4. 禁用实验 — 返回默认组")
    void split_disabled_returnsDefault() {
        ExperimentConfig disabled = ExperimentConfig.builder()
            .experimentId("exp-off").trafficKey("userId").enabled(false)
            .addGroup(new ExperimentConfig.GroupConfig("control", "C", 1.0, "S1"))
            .addGroup(new ExperimentConfig.GroupConfig("experiment", "E", 0.0, "S2"))
            .build();

        assertEquals("control", ExperimentSplitter.split(disabled, "any-user"));
    }

    @Test
    @DisplayName("5. 非均分比例 (80/20)")
    void split_80_20() {
        ExperimentConfig cfg = ExperimentConfig.builder()
            .experimentId("exp-8020").trafficKey("userId")
            .addGroup(new ExperimentConfig.GroupConfig("A", "80%", 0.8, "S1"))
            .addGroup(new ExperimentConfig.GroupConfig("B", "20%", 0.2, "S2"))
            .build();

        int b = 0;
        for (int i = 0; i < 10000; i++) {
            if ("B".equals(ExperimentSplitter.split(cfg, "u" + i))) b++;
        }
        double ratio = b / 10000.0;
        assertTrue(ratio > 0.15 && ratio < 0.25, "B ratio ~20%, was " + ratio);
    }

    // ========== 指标收集 ==========

    @Test
    @DisplayName("6. 指标收集 — 通过率和平均耗时")
    void metrics_collection() {
        ExperimentMetrics metrics = new ExperimentMetrics();

        metrics.record("exp-001", "control", true, 100, 3);
        metrics.record("exp-001", "control", true, 200, 2);
        metrics.record("exp-001", "control", false, 150, 5);

        ExperimentMetrics.Snapshot snap = metrics.getSnapshot("exp-001", "control");
        assertEquals(3, snap.total());
        assertEquals(2, snap.passed());
        assertEquals(2.0 / 3.0, snap.passRate(), 0.01);
        assertEquals(150.0, snap.avgDurationMs(), 0.01);
    }

    @Test
    @DisplayName("7. 指标收集 — 不同分组独立统计")
    void metrics_independentGroups() {
        ExperimentMetrics metrics = new ExperimentMetrics();
        metrics.record("exp-001", "control", true, 100, 1);
        metrics.record("exp-001", "experiment", false, 200, 2);

        assertEquals(1.0, metrics.getSnapshot("exp-001", "control").passRate());
        assertEquals(0.0, metrics.getSnapshot("exp-001", "experiment").passRate());
    }

    // ========== 统计显著性 ==========

    @Test
    @DisplayName("8. p-value — 差异显著时 p < 0.05")
    void pValue_significant() {
        ExperimentMetrics metrics = new ExperimentMetrics();

        // 对照组: 90% 通过率, 1000 样本
        for (int i = 0; i < 900; i++) metrics.record("exp", "C", true, 100, 0);
        for (int i = 0; i < 100; i++) metrics.record("exp", "C", false, 100, 0);

        // 实验组: 85% 通过率, 1000 样本
        for (int i = 0; i < 850; i++) metrics.record("exp", "E", true, 100, 0);
        for (int i = 0; i < 150; i++) metrics.record("exp", "E", false, 100, 0);

        double pValue = metrics.calculatePValue("exp", "C", "E");
        assertTrue(pValue < 0.05, "Should be significant, p=" + pValue);
    }

    @Test
    @DisplayName("9. p-value — 无差异时 p > 0.05")
    void pValue_notSignificant() {
        ExperimentMetrics metrics = new ExperimentMetrics();

        for (int i = 0; i < 500; i++) metrics.record("exp", "C", true, 100, 0);
        for (int i = 0; i < 500; i++) metrics.record("exp", "E", true, 100, 0);

        double pValue = metrics.calculatePValue("exp", "C", "E");
        assertTrue(pValue > 0.05, "Should not be significant, p=" + pValue);
    }

    @Test
    @DisplayName("10. p-value — 样本不足时返回 1.0")
    void pValue_insufficientData() {
        ExperimentMetrics metrics = new ExperimentMetrics();
        assertEquals(1.0, metrics.calculatePValue("exp", "C", "E"));
    }

    @Test
    @DisplayName("11. 指标重置")
    void metrics_reset() {
        ExperimentMetrics metrics = new ExperimentMetrics();
        metrics.record("exp", "C", true, 100, 0);
        metrics.reset();
        assertEquals(0, metrics.getSnapshot("exp", "C").total());
    }
}
