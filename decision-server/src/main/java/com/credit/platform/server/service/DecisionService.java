package com.credit.platform.server.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.credit.platform.engine.common.model.ActionType;
import com.credit.platform.engine.common.model.DecisionResponse;
import com.credit.platform.engine.common.model.DecisionResult;
import com.credit.platform.engine.core.cache.VersionedArtifact;
import com.credit.platform.engine.core.cache.VersionedRuleCache;
import com.credit.platform.engine.core.executor.ExecutionContext;
import com.credit.platform.engine.core.executor.RuleExecutionResult;
import com.credit.platform.engine.core.flow.CompiledDAG;
import com.credit.platform.engine.core.flow.FlowExecutionResult;
import com.credit.platform.engine.core.scorecard.CompiledScorecard;
import com.credit.platform.engine.core.scorecard.ScorecardExecutor;
import com.credit.platform.engine.core.scorecard.ScorecardResult;
import com.credit.platform.engine.core.trace.DecisionTrace;
import com.credit.platform.engine.core.trace.DecisionTracer;
import com.credit.platform.engine.core.trace.TracePublisher;
import com.credit.platform.engine.core.trace.TraceReporter;
import com.credit.platform.server.model.DecisionLogDocument;
import com.credit.platform.server.repository.DecisionLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 决策服务 — 编排引擎核心组件执行完整决策流程。
 * <p>
 * 执行流程:
 * <ol>
 *   <li>创建 ExecutionContext（含渠道映射后的变量）</li>
 *   <li>加载编译后的策略（从 VersionedRuleCache）</li>
 *   <li>执行 DAG 决策流（或直接执行规则集/评分卡）</li>
 *   <li>追踪可解释性信息</li>
 *   <li>异步发布追踪日志</li>
 * </ol>
 * </p>
 */
@Service
public class DecisionService {

    private static final Logger log = LoggerFactory.getLogger(DecisionService.class);
    private static final DateTimeFormatter ID_FORMATTER =
        DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final VersionedRuleCache ruleCache;
    private final ScorecardExecutor scorecardExecutor;
    private final TracePublisher tracePublisher;
    private final DecisionLogRepository decisionLogRepository;
    private final ObjectMapper objectMapper;
    private final AtomicLong requestCounter = new AtomicLong(0);

    public DecisionService(VersionedRuleCache ruleCache,
                           ScorecardExecutor scorecardExecutor,
                           TracePublisher tracePublisher,
                           DecisionLogRepository decisionLogRepository,
                           ObjectMapper objectMapper) {
        this.ruleCache = Objects.requireNonNull(ruleCache);
        this.scorecardExecutor = Objects.requireNonNull(scorecardExecutor);
        this.tracePublisher = Objects.requireNonNull(tracePublisher);
        this.decisionLogRepository = decisionLogRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行决策。
     *
     * @param strategyId 策略 ID
     * @param channel    渠道 (APP/WEB/API)
     * @param applicant  申请人信息
     * @param metadata   元数据
     * @return 决策响应
     */
    public DecisionResponse execute(String strategyId, String channel,
                                     Map<String, Object> applicant,
                                     Map<String, Object> metadata) {
        long startMs = System.currentTimeMillis();
        String decisionId = generateDecisionId();

        // 创建追踪器
        DecisionTracer tracer = DecisionTracer.create(decisionId, strategyId);
        tracer.addMetadata("channel", channel);
        if (metadata != null) {
            metadata.forEach(tracer::addMetadata);
        }

        try {
            // 创建执行上下文
            ExecutionContext ctx = ExecutionContext.create(
                decisionId, strategyId, applicant != null ? applicant : Map.of(), metadata);

            // 尝试加载 DAG 决策流
            VersionedArtifact<?> dagArtifact = ruleCache.get(strategyId);
            if (dagArtifact != null && dagArtifact.getArtifact() instanceof CompiledDAG dag) {
                return executeDAG(dag, ctx, tracer, decisionId, startMs);
            }

            // 降级: 无策略 → 返回人工审核
            log.warn("No strategy found for {}, routing to MANUAL review", strategyId);
            tracer.finish("MANUAL", null, "策略不存在", "STRATEGY_NOT_FOUND");
            publishTrace(tracer.getTrace());
            DecisionResponse manualResp = DecisionResponse.manual(decisionId, tracer.getTrace().getTraceId(),
                System.currentTimeMillis() - startMs);
            saveDecisionLog(decisionId, strategyId, tracer.getTrace().getTraceId(), "MANUAL",
                0, null, null, applicant, metadata, System.currentTimeMillis() - startMs);
            return manualResp;

        } catch (Exception e) {
            // 安全修复: 系统异常不应静默返回 MANUAL，应明确返回错误
            log.error("Decision execution failed for {}", decisionId, e);
            tracer.finish("ERROR", null, "系统异常: " + e.getMessage(), "SYSTEM_ERROR");
            publishTrace(tracer.getTrace());
            saveDecisionLog(decisionId, strategyId, tracer.getTrace().getTraceId(), "ERROR",
                0, e.getMessage(), null, applicant, metadata, System.currentTimeMillis() - startMs);
            // 返回 ERROR 响应而非 MANUAL，让调用方知道发生了系统错误
            DecisionResponse errorResp = DecisionResponse.error(decisionId, tracer.getTrace().getTraceId(),
                "Decision execution failed: " + e.getMessage(), System.currentTimeMillis() - startMs);
            return errorResp;
        }
    }

    /**
     * 执行 DAG 决策流。
     */
    private DecisionResponse executeDAG(CompiledDAG dag, ExecutionContext ctx,
                                          DecisionTracer tracer, String decisionId,
                                          long startMs) {
        tracer.recordDecisionPath("dag:" + dag.getRuleId());

        // 执行 DAG
        FlowExecutionResult flowResult = dag.execute(ctx,
            new com.credit.platform.engine.core.expression.ExpressionEngine());

        // 记录执行路径
        for (String nodeId : flowResult.getDecisionPath()) {
            tracer.recordDecisionPath(nodeId);
        }

        // 提取最终结果
        DecisionResult finalResult;
        ActionType action = flowResult.getFinalAction();

        if (action == null) {
            finalResult = DecisionResult.MANUAL;
        } else {
            finalResult = switch (action) {
                case PASS -> DecisionResult.PASS;
                case REJECT -> DecisionResult.REJECT;
                case REVIEW -> DecisionResult.REVIEW;
                case MANUAL -> DecisionResult.MANUAL;
                case ASSIGN, SCORE -> DecisionResult.PASS;
            };
        }

        Integer score = null;
        String rejectReason = flowResult.getFinalReason();
        String rejectCode = null;

        // 从 DAG 输出中提取评分
        Map<String, Object> outputs = flowResult.getNodeOutputs();
        for (Map.Entry<String, Object> entry : outputs.entrySet()) {
            if (entry.getValue() instanceof Map<?, ?> map) {
                if (map.containsKey("finalScore")) {
                    Object scoreObj = map.get("finalScore");
                    if (scoreObj instanceof Number num) {
                        score = num.intValue();
                    }
                }
                if (map.containsKey("rejectCode")) {
                    Object codeObj = map.get("rejectCode");
                    if (codeObj instanceof String s) {
                        rejectCode = s;
                    }
                }
            }
        }

        long durationMs = System.currentTimeMillis() - startMs;
        String traceId = tracer.getTrace().getTraceId();

        // 完成追踪
        tracer.finish(finalResult.name(), score, rejectReason, rejectCode);
        publishTrace(tracer.getTrace());

        // 持久化到 ES
        saveDecisionLog(decisionId, dag.getRuleId(), traceId, finalResult.name(),
            score != null ? score : 0, rejectReason, flowResult.getDecisionPath(),
            ctx.getAllVariables(), Map.of(), durationMs);

        // 构建响应
        return switch (finalResult) {
            case PASS -> DecisionResponse.pass(decisionId, score,
                buildExtra(flowResult), traceId, durationMs);
            case REJECT -> DecisionResponse.reject(decisionId,
                rejectReason, rejectCode, traceId, durationMs);
            case REVIEW -> DecisionResponse.review(decisionId, score, traceId, durationMs);
            case MANUAL -> DecisionResponse.manual(decisionId, traceId, durationMs);
        };
    }

    /**
     * 查询决策报告。
     *
     * @param decisionId 决策 ID
     * @return 决策报告 (JSON Map)，不存在时返回 null
     */
    public Map<String, Object> getReport(String decisionId) {
        // 安全修复: 按 decisionId (文档 ID) 查询，而非 traceId
        DecisionLogDocument doc = decisionLogRepository.findById(decisionId).orElse(null);
        if (doc == null) {
            return null;
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("decisionId", doc.getId());
        report.put("traceId", doc.getTraceId());
        report.put("result", doc.getDecisionResult());
        report.put("score", doc.getScore());
        report.put("riskLevel", doc.getRiskLevel());
        report.put("rejectReason", doc.getRejectReason());
        report.put("rulesExecuted", doc.getRulesExecuted());
        report.put("executionTimeMs", doc.getExecutionTimeMs());
        report.put("timestamp", doc.getTimestamp());
        return report;
    }

    // ========== 内部方法 ==========

    private void saveDecisionLog(String decisionId, String strategyId, String traceId,
                                  String result, double score, String reason,
                                  List<String> rulesExecuted, Map<String, Object> input,
                                  Map<String, Object> metadata, long durationMs) {
        try {
            DecisionLogDocument doc = new DecisionLogDocument();
            doc.setId(decisionId);
            doc.setTraceId(traceId);
            doc.setDecisionResult(result);
            doc.setScore(score);
            doc.setRulesExecuted(rulesExecuted);
            doc.setExecutionTimeMs(durationMs);
            // 安全修复: rejectReason 存入专用字段，而非覆盖 riskLevel
            if (reason != null) {
                doc.setRejectReason(reason);
            }
            try {
                doc.setInputSnapshot(objectMapper.writeValueAsString(input));
                doc.setOutputSnapshot(objectMapper.writeValueAsString(metadata));
            } catch (Exception e) {
                log.debug("Failed to serialize snapshots for {}: {}", decisionId, e.getMessage());
            }
            decisionLogRepository.save(doc);
        } catch (Exception e) {
            log.warn("Failed to persist decision log to ES for {}: {}", decisionId, e.getMessage());
        }
    }

    private void publishTrace(DecisionTrace trace) {
        try {
            tracePublisher.publish(trace);
        } catch (Exception e) {
            log.warn("Failed to publish trace for {}: {}", trace.getDecisionId(), e.getMessage());
        }
    }

    private String generateDecisionId() {
        String timestamp = LocalDateTime.now().format(ID_FORMATTER);
        long seq = requestCounter.incrementAndGet() % 100000;
        return String.format("DEC_%s_%05d", timestamp, seq);
    }

    private Map<String, Object> buildExtra(FlowExecutionResult result) {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("flowId", result.getFlowId());
        extra.put("decisionPath", result.getDecisionPath());
        if (result.getFinalReason() != null) {
            extra.put("reason", result.getFinalReason());
        }
        return extra;
    }
}
