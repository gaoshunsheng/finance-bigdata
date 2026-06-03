package com.credit.platform.engine.core.trace;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 决策追踪器 — 在决策执行过程中记录每个节点的 TraceEntry。
 * <p>
 * 核心追踪入口，提供 begin/end 模式记录节点执行:
 * <ul>
 *   <li>{@link #beginNode(String, NodeType)} — 开始追踪一个节点</li>
 *   <li>{@link #endNode(Map)} — 结束当前节点追踪，记录输出</li>
 *   <li>{@link #endNodeWithError(String)} — 结束当前节点，记录错误</li>
 * </ul>
 * 同时提供快捷方法用于直接记录各引擎类型的执行结果:
 * <ul>
 *   <li>{@link #recordRuleSet(String, boolean, int, int)} — 规则集执行</li>
 *   <li>{@link #recordScorecard(String, int, int, String)} — 评分卡执行</li>
 *   <li>{@link #recordDecisionPath(String)} — 记录 DAG 路径节点</li>
 * </ul>
 * </p>
 *
 * <pre>
 * DecisionTracer tracer = DecisionTracer.create("decision-001", "strategy-v1");
 *
 * // begin/end 模式
 * tracer.beginNode("scorecard_01", NodeType.SCORECARD);
 * tracer.captureInput("age", 25, "income", 50000);
 * // ... 执行评分卡 ...
 * tracer.endNode(Map.of("score", 720, "result", "PASS"));
 *
 * // 快捷方法
 * tracer.recordRuleSet("rule_blacklist", true, 5, 2);
 *
 * DecisionTrace trace = tracer.finish("PASS", 720, null, null);
 * </pre>
 */
public class DecisionTracer {

    private final DecisionTrace trace;
    private TraceEntry.Builder currentBuilder;

    private DecisionTracer(DecisionTrace trace) {
        this.trace = trace;
    }

    /**
     * 创建追踪器。
     *
     * @param decisionId 决策 ID
     * @param strategyId 策略 ID
     * @return 新的追踪器
     */
    public static DecisionTracer create(String decisionId, String strategyId) {
        String traceId = "trace-" + UUID.randomUUID().toString().substring(0, 8);
        DecisionTrace trace = DecisionTrace.create(traceId, decisionId, strategyId);
        return new DecisionTracer(trace);
    }

    /**
     * 使用指定 traceId 创建追踪器。
     *
     * @param traceId    追踪 ID
     * @param decisionId 决策 ID
     * @param strategyId 策略 ID
     * @return 新的追踪器
     */
    public static DecisionTracer create(String traceId, String decisionId, String strategyId) {
        DecisionTrace trace = DecisionTrace.create(traceId, decisionId, strategyId);
        return new DecisionTracer(trace);
    }

    // ========== begin/end 模式 ==========

    /**
     * 开始追踪一个节点。
     *
     * @param nodeId   节点 ID
     * @param nodeType 节点类型
     * @return 追踪器自身（链式调用）
     */
    public DecisionTracer beginNode(String nodeId, NodeType nodeType) {
        this.currentBuilder = TraceEntry.fromStart(trace.getTraceId(), nodeId, nodeType);
        return this;
    }

    /**
     * 捕获输入快照 — 批量。
     *
     * @param snapshot 输入变量快照
     * @return 追踪器自身
     */
    public DecisionTracer captureInput(Map<String, Object> snapshot) {
        ensureActiveBuilder().inputSnapshot(snapshot);
        return this;
    }

    /**
     * 捕获输入快照 — 键值对。
     *
     * @param keyValuePairs 键值对 (key1, value1, key2, value2, ...)
     * @return 追踪器自身
     */
    public DecisionTracer captureInput(Object... keyValuePairs) {
        ensureActiveBuilder().inputSnapshot(toMap(keyValuePairs));
        return this;
    }

    /**
     * 结束当前节点追踪 — 成功。
     *
     * @param output 输出快照
     * @return 追踪器自身
     */
    public DecisionTracer endNode(Map<String, Object> output) {
        TraceEntry.Builder builder = ensureActiveBuilder();
        builder.outputSnapshot(output).end().success(true);
        TraceEntry entry = builder.build();
        trace.addEntry(entry);
        trace.addPathNode(entry.getNodeId());
        this.currentBuilder = null;
        return this;
    }

    /**
     * 结束当前节点追踪 — 失败。
     *
     * @param errorMessage 错误信息
     * @return 追踪器自身
     */
    public DecisionTracer endNodeWithError(String errorMessage) {
        TraceEntry.Builder builder = ensureActiveBuilder();
        builder.end().success(false).errorMessage(errorMessage);
        TraceEntry entry = builder.build();
        trace.addEntry(entry);
        trace.addPathNode(entry.getNodeId());
        this.currentBuilder = null;
        return this;
    }

    // ========== 快捷记录方法 ==========

    /**
     * 快捷记录规则集执行。
     *
     * @param ruleSetId       规则集 ID
     * @param hit             是否命中
     * @param totalEvaluated  总评估规则数
     * @param matchedCount    命中规则数
     */
    public void recordRuleSet(String ruleSetId, boolean hit,
                               int totalEvaluated, int matchedCount) {
        TraceEntry entry = TraceEntry.builder()
            .traceId(trace.getTraceId())
            .nodeId(ruleSetId)
            .nodeType(NodeType.RULE_SET)
            .startTimeMs(System.currentTimeMillis())
            .end()
            .success(true)
            .outputSnapshot(Map.of("hit", hit, "totalEvaluated", totalEvaluated,
                "matchedCount", matchedCount))
            .addDetail("hitRules", matchedCount)
            .addDetail("evaluatedRules", totalEvaluated)
            .build();
        trace.addEntry(entry);
        trace.addPathNode(ruleSetId);
    }

    /**
     * 快捷记录评分卡执行。
     *
     * @param scorecardId  评分卡 ID
     * @param initialScore 初始分
     * @param finalScore   最终得分
     * @param result       结果 (PASS/REVIEW/REJECT)
     */
    public void recordScorecard(String scorecardId, int initialScore,
                                 int finalScore, String result) {
        TraceEntry entry = TraceEntry.builder()
            .traceId(trace.getTraceId())
            .nodeId(scorecardId)
            .nodeType(NodeType.SCORECARD)
            .startTimeMs(System.currentTimeMillis())
            .end()
            .success(true)
            .outputSnapshot(Map.of("initialScore", initialScore,
                "finalScore", finalScore, "result", result))
            .addDetail("scoreDelta", finalScore - initialScore)
            .build();
        trace.addEntry(entry);
        trace.addPathNode(scorecardId);
    }

    /**
     * 快捷记录评分卡执行（含得分明细）。
     *
     * @param scorecardId  评分卡 ID
     * @param initialScore 初始分
     * @param finalScore   最终得分
     * @param result       结果
     * @param breakdown    得分明细 (特征名 → 得分)
     */
    public void recordScorecard(String scorecardId, int initialScore,
                                 int finalScore, String result,
                                 Map<String, Object> breakdown) {
        TraceEntry entry = TraceEntry.builder()
            .traceId(trace.getTraceId())
            .nodeId(scorecardId)
            .nodeType(NodeType.SCORECARD)
            .startTimeMs(System.currentTimeMillis())
            .end()
            .success(true)
            .outputSnapshot(Map.of("initialScore", initialScore,
                "finalScore", finalScore, "result", result))
            .details(breakdown != null ? breakdown : Map.of())
            .addDetail("scoreDelta", finalScore - initialScore)
            .build();
        trace.addEntry(entry);
        trace.addPathNode(scorecardId);
    }

    /**
     * 记录 DAG 决策路径节点。
     *
     * @param nodeId 节点 ID
     */
    public void recordDecisionPath(String nodeId) {
        trace.addPathNode(nodeId);
    }

    /**
     * 快捷记录变量解析。
     *
     * @param variableName 变量名
     * @param layer        变量层 (INPUT/EXTERNAL/CACHED/DERIVED)
     * @param value        解析后的值
     * @param durationMs   解析耗时
     */
    public void recordVariableResolve(String variableName, String layer,
                                       Object value, long durationMs) {
        TraceEntry entry = TraceEntry.builder()
            .traceId(trace.getTraceId())
            .nodeId("var:" + variableName)
            .nodeType(NodeType.VARIABLE_RESOLVE)
            .startTimeMs(System.currentTimeMillis() - durationMs)
            .endTimeMs(System.currentTimeMillis())
            .durationMs(durationMs)
            .success(true)
            .outputSnapshot(Map.of("value", value != null ? value : "null"))
            .addDetail("layer", layer)
            .build();
        trace.addEntry(entry);
    }

    // ========== 生命周期方法 ==========

    /**
     * 完成追踪 — 设置最终决策结果。
     *
     * @param result       决策结果 (PASS/REJECT/REVIEW/MANUAL)
     * @param score        评分 (可为null)
     * @param rejectReason 拒绝原因 (可为null)
     * @param rejectCode   拒绝码 (可为null)
     * @return 完整的决策追踪
     */
    public DecisionTrace finish(String result, Integer score,
                                 String rejectReason, String rejectCode) {
        trace.setFinalResult(result, score, rejectReason, rejectCode);
        return trace;
    }

    /**
     * 获取当前追踪容器（未完成）。
     *
     * @return 追踪容器
     */
    public DecisionTrace getTrace() {
        return trace;
    }

    /**
     * 添加追踪元数据。
     *
     * @param key   键
     * @param value 值
     * @return 追踪器自身
     */
    public DecisionTracer addMetadata(String key, Object value) {
        trace.addMetadata(key, value);
        return this;
    }

    // ========== 内部方法 ==========

    private TraceEntry.Builder ensureActiveBuilder() {
        if (currentBuilder == null) {
            throw new IllegalStateException("No active node tracing. Call beginNode() first.");
        }
        return currentBuilder;
    }

    private static Map<String, Object> toMap(Object... keyValuePairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValuePairs.length - 1; i += 2) {
            map.put(String.valueOf(keyValuePairs[i]), keyValuePairs[i + 1]);
        }
        return map;
    }
}
