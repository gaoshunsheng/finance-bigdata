package com.credit.platform.engine.common.model;

import java.util.Map;

/**
 * 决策响应。
 * <p>
 * 封装一次决策调用的完整结果，包括决策结论、评分、额外信息及追踪信息。
 * </p>
 */
public class DecisionResponse {

    /** 决策唯一ID */
    private String decisionId;

    /** 决策结果: PASS / REJECT / REVIEW / MANUAL */
    private DecisionResult result;

    /** 评分卡得分 (可为null) */
    private Integer score;

    /** 额外信息 (额度 / 利率等) */
    private Map<String, Object> extra;

    /** 拒绝原因 */
    private String rejectReason;

    /** 拒绝码 */
    private String rejectCode;

    /** 追踪ID (用于查询决策报告) */
    private String traceId;

    /** 决策耗时 (毫秒) */
    private long durationMs;

    /** 无参构造函数 */
    public DecisionResponse() {
    }

    /**
     * 全参构造函数。
     *
     * @param decisionId   决策唯一ID
     * @param result       决策结果
     * @param score        评分卡得分
     * @param extra        额外信息
     * @param rejectReason 拒绝原因
     * @param rejectCode   拒绝码
     * @param traceId      追踪ID
     * @param durationMs   决策耗时 (毫秒)
     */
    public DecisionResponse(String decisionId, DecisionResult result, Integer score,
                            Map<String, Object> extra, String rejectReason,
                            String rejectCode, String traceId, long durationMs) {
        this.decisionId = decisionId;
        this.result = result;
        this.score = score;
        this.extra = extra;
        this.rejectReason = rejectReason;
        this.rejectCode = rejectCode;
        this.traceId = traceId;
        this.durationMs = durationMs;
    }

    // ========== 静态工厂方法 ==========

    /**
     * 构建通过响应。
     *
     * @param decisionId 决策唯一ID
     * @param score      评分卡得分
     * @param extra      额外信息
     * @param traceId    追踪ID
     * @param durationMs 决策耗时 (毫秒)
     * @return 通过的决策响应
     */
    public static DecisionResponse pass(String decisionId, Integer score,
                                        Map<String, Object> extra,
                                        String traceId, long durationMs) {
        return new DecisionResponse(decisionId, DecisionResult.PASS, score,
                extra, null, null, traceId, durationMs);
    }

    /**
     * 构建拒绝响应。
     *
     * @param decisionId 决策唯一ID
     * @param reason     拒绝原因
     * @param code       拒绝码
     * @param traceId    追踪ID
     * @param durationMs 决策耗时 (毫秒)
     * @return 拒绝的决策响应
     */
    public static DecisionResponse reject(String decisionId, String reason,
                                          String code, String traceId,
                                          long durationMs) {
        return new DecisionResponse(decisionId, DecisionResult.REJECT, null,
                null, reason, code, traceId, durationMs);
    }

    /**
     * 构建人工审核响应。
     *
     * @param decisionId 决策唯一ID
     * @param score      评分卡得分
     * @param traceId    追踪ID
     * @param durationMs 决策耗时 (毫秒)
     * @return 人工审核的决策响应
     */
    public static DecisionResponse review(String decisionId, Integer score,
                                           String traceId, long durationMs) {
        return new DecisionResponse(decisionId, DecisionResult.REVIEW, score,
                null, null, null, traceId, durationMs);
    }

    /**
     * 构建人工介入响应。
     *
     * @param decisionId 决策唯一ID
     * @param traceId    追踪ID
     * @param durationMs 决策耗时 (毫秒)
     * @return 人工介入的决策响应
     */
    public static DecisionResponse manual(String decisionId, String traceId,
                                          long durationMs) {
        return new DecisionResponse(decisionId, DecisionResult.MANUAL, null,
                null, null, null, traceId, durationMs);
    }

    // ========== Getters & Setters ==========

    public String getDecisionId() {
        return decisionId;
    }

    public void setDecisionId(String decisionId) {
        this.decisionId = decisionId;
    }

    public DecisionResult getResult() {
        return result;
    }

    public void setResult(DecisionResult result) {
        this.result = result;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public Map<String, Object> getExtra() {
        return extra;
    }

    public void setExtra(Map<String, Object> extra) {
        this.extra = extra;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    public String getRejectCode() {
        return rejectCode;
    }

    public void setRejectCode(String rejectCode) {
        this.rejectCode = rejectCode;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    @Override
    public String toString() {
        return "DecisionResponse{" +
                "decisionId='" + decisionId + '\'' +
                ", result=" + result +
                ", score=" + score +
                ", rejectCode='" + rejectCode + '\'' +
                ", traceId='" + traceId + '\'' +
                ", durationMs=" + durationMs +
                '}';
    }
}
