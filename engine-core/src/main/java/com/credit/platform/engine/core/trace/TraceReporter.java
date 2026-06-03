package com.credit.platform.engine.core.trace;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 追踪报告生成器 — 将 DecisionTrace 转换为结构化决策报告。
 * <p>
 * 支持四级可解释性输出:
 * <ul>
 *   <li>L1 RULE — 规则级: 命中规则、变量阈值比较、评分卡得分明细 → 业务用户</li>
 *   <li>L2 FLOW — 流程级: DAG 路径可视化、高亮执行节点 → 策略分析师</li>
 *   <li>L3 MODEL — 模型级: 特征重要性、SHAP 值（预留） → 模型工程师</li>
 *   <li>L4 AUDIT — 审计级: 完整输入输出快照、时间戳 → 合规人员</li>
 * </ul>
 * 高级别包含低级别的所有信息。
 * </p>
 *
 * <pre>
 * DecisionTrace trace = ...;
 * DecisionReport report = TraceReporter.generateReport(trace, TraceLevel.L2);
 * Map&lt;String, Object&gt; json = TraceReporter.toJsonMap(report, TraceLevel.L2);
 * </pre>
 */
public final class TraceReporter {

    private TraceReporter() {
        // 工具类 — 禁止实例化
    }

    /**
     * 生成决策报告。
     * <p>
     * 根据指定的可解释性级别，从 DecisionTrace 中提取相应信息生成报告。
     * </p>
     *
     * @param trace 决策追踪
     * @param level 可解释性级别
     * @return 决策报告
     */
    public static DecisionReport generateReport(DecisionTrace trace, TraceLevel level) {
        List<DecisionReport.NodeReport> nodeReports = generateNodeReports(trace, level);
        List<DecisionReport.ContributionFactor> factors = extractTopFactors(trace);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalNodes", trace.getEntryCount());
        summary.put("pathLength", trace.getDecisionPath().size());
        summary.put("successfulNodes", trace.getEntries().stream()
            .filter(TraceEntry::isSuccess).count());

        // L2+ 包含决策路径
        List<String> path = level.includes(TraceLevel.FLOW)
            ? trace.getDecisionPath()
            : List.of();

        // L4 包含时间详情
        if (level.includes(TraceLevel.AUDIT)) {
            summary.put("startTimeMs", trace.getStartTimeMs());
            summary.put("totalDurationMs", trace.getTotalDurationMs());
        }

        return DecisionReport.builder()
            .traceId(trace.getTraceId())
            .decisionId(trace.getDecisionId())
            .strategyId(trace.getStrategyId())
            .finalResult(trace.getFinalResult())
            .finalScore(trace.getFinalScore())
            .rejectReason(trace.getRejectReason())
            .rejectCode(trace.getRejectCode())
            .totalDurationMs(trace.getTotalDurationMs())
            .decisionPath(path)
            .nodeReports(nodeReports)
            .topFactors(factors)
            .summary(summary)
            .build();
    }

    /**
     * 生成默认级别的决策报告 (L2 流程级)。
     *
     * @param trace 决策追踪
     * @return 决策报告
     */
    public static DecisionReport generateReport(DecisionTrace trace) {
        return generateReport(trace, TraceLevel.FLOW);
    }

    /**
     * 将决策报告转换为 JSON 友好的 Map 结构。
     * <p>
     * 根据 TraceLevel 过滤字段:
     * <ul>
     *   <li>L1: nodeId, nodeType, outputSnapshot (命中结果/得分)</li>
     *   <li>L2: + decisionPath, durationMs</li>
     *   <li>L3: + details (特征重要性)</li>
     *   <li>L4: + inputSnapshot, startTimeMs, endTimeMs, errorMessage</li>
     * </ul>
     * </p>
     *
     * @param report 决策报告
     * @param level  可解释性级别
     * @return JSON 友好的 Map
     */
    public static Map<String, Object> toJsonMap(DecisionReport report, TraceLevel level) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("traceId", report.getTraceId());
        json.put("decisionId", report.getDecisionId());
        json.put("strategyId", report.getStrategyId());
        json.put("finalResult", report.getFinalResult());
        if (report.getFinalScore() != null) {
            json.put("finalScore", report.getFinalScore());
        }
        if (report.getRejectReason() != null) {
            json.put("rejectReason", report.getRejectReason());
        }
        if (report.getRejectCode() != null) {
            json.put("rejectCode", report.getRejectCode());
        }
        json.put("totalDurationMs", report.getTotalDurationMs());

        // L2+: 决策路径
        if (level.includes(TraceLevel.FLOW)) {
            json.put("decisionPath", report.getDecisionPath());
        }

        // 节点报告
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (DecisionReport.NodeReport node : report.getNodeReports()) {
            nodes.add(nodeToJson(node, level));
        }
        json.put("nodes", nodes);

        // 贡献因子
        List<Map<String, Object>> factors = new ArrayList<>();
        for (DecisionReport.ContributionFactor factor : report.getTopFactors()) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("factor", factor.getFactor());
            f.put("description", factor.getDescription());
            f.put("influence", factor.getInfluence());
            f.put("direction", factor.getDirection());
            factors.add(f);
        }
        json.put("topFactors", factors);

        // L4: 审计摘要
        if (level.includes(TraceLevel.AUDIT)) {
            json.put("summary", report.getSummary());
        }

        return json;
    }

    // ========== 内部方法 ==========

    /**
     * 生成节点报告列表。
     */
    private static List<DecisionReport.NodeReport> generateNodeReports(
            DecisionTrace trace, TraceLevel level) {
        List<DecisionReport.NodeReport> reports = new ArrayList<>();

        for (TraceEntry entry : trace.getEntries()) {
            // L1+: 输出快照始终包含
            Map<String, Object> output = entry.getOutputSnapshot();

            // L4+: 输入快照
            Map<String, Object> input = level.includes(TraceLevel.AUDIT)
                ? entry.getInputSnapshot()
                : Map.of();

            // L3+: details
            Map<String, Object> details = level.includes(TraceLevel.MODEL)
                ? entry.getDetails()
                : Map.of();

            reports.add(new DecisionReport.NodeReport(
                entry.getNodeId(),
                entry.getNodeType(),
                entry.getDurationMs(),
                entry.isSuccess(),
                level.includes(TraceLevel.AUDIT) ? entry.getErrorMessage() : null,
                input,
                output,
                details
            ));
        }

        return reports;
    }

    /**
     * 从追踪条目中提取贡献因子。
     * <p>
     * 按得分变化量排序，识别对最终决策影响最大的因素。
     * </p>
     */
    private static List<DecisionReport.ContributionFactor> extractTopFactors(DecisionTrace trace) {
        List<DecisionReport.ContributionFactor> factors = new ArrayList<>();

        for (TraceEntry entry : trace.getEntries()) {
            // 评分卡节点 — 提取得分变化作为贡献因子
            if (entry.getNodeType() == NodeType.SCORECARD) {
                Object delta = entry.getDetails().get("scoreDelta");
                if (delta instanceof Number) {
                    double influence = ((Number) delta).doubleValue();
                    String direction = influence >= 0 ? "POSITIVE" : "NEGATIVE";
                    factors.add(new DecisionReport.ContributionFactor(
                        entry.getNodeId(),
                        entry.getNodeType().getDescription() + " 得分变化",
                        Math.abs(influence),
                        direction
                    ));
                }
            }

            // 规则集节点 — 命中规则作为贡献因子
            if (entry.getNodeType() == NodeType.RULE_SET) {
                Object hit = entry.getOutputSnapshot().get("hit");
                if (Boolean.TRUE.equals(hit)) {
                    Object matched = entry.getOutputSnapshot().get("matchedCount");
                    int count = matched instanceof Number ? ((Number) matched).intValue() : 1;
                    factors.add(new DecisionReport.ContributionFactor(
                        entry.getNodeId(),
                        "命中 " + count + " 条规则",
                        count * 10.0,
                        "NEGATIVE"
                    ));
                }
            }
        }

        // 按影响度降序排序
        factors.sort(Comparator.comparingDouble(
            DecisionReport.ContributionFactor::getInfluence).reversed());
        return factors;
    }

    /**
     * 将节点报告转换为 JSON Map。
     */
    private static Map<String, Object> nodeToJson(
            DecisionReport.NodeReport node, TraceLevel level) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("nodeId", node.getNodeId());
        json.put("nodeType", node.getNodeType().name());
        json.put("success", node.isSuccess());

        // L1+: 输出
        json.put("output", node.getOutputSnapshot());

        // L2+: 耗时
        if (level.includes(TraceLevel.FLOW)) {
            json.put("durationMs", node.getDurationMs());
        }

        // L3+: details
        if (level.includes(TraceLevel.MODEL)) {
            if (!node.getDetails().isEmpty()) {
                json.put("details", node.getDetails());
            }
        }

        // L4: 完整快照
        if (level.includes(TraceLevel.AUDIT)) {
            if (!node.getInputSnapshot().isEmpty()) {
                json.put("inputSnapshot", node.getInputSnapshot());
            }
            if (node.getErrorMessage() != null) {
                json.put("errorMessage", node.getErrorMessage());
            }
        }

        return json;
    }
}
