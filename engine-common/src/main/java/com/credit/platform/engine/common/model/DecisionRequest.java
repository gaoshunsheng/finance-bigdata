package com.credit.platform.engine.common.model;

import java.util.Map;

/**
 * 决策请求。
 * <p>
 * 封装一次决策调用的全部入参，包括策略标识、渠道、申请人信息及元数据。
 * </p>
 */
public class DecisionRequest {

    /** 策略ID */
    private String strategyId;

    /** 渠道 (APP / PC / H5 / PARTNER) */
    private String channel;

    /** 申请人信息 */
    private Map<String, Object> applicant;

    /** 元数据 (设备指纹 / IP 等) */
    private Map<String, Object> metadata;

    /** 请求唯一ID */
    private String requestId;

    /** 无参构造函数 */
    public DecisionRequest() {
    }

    /**
     * 全参构造函数。
     *
     * @param strategyId 策略ID
     * @param channel    渠道
     * @param applicant  申请人信息
     * @param metadata   元数据
     * @param requestId  请求唯一ID
     */
    public DecisionRequest(String strategyId, String channel,
                           Map<String, Object> applicant,
                           Map<String, Object> metadata,
                           String requestId) {
        this.strategyId = strategyId;
        this.channel = channel;
        this.applicant = applicant;
        this.metadata = metadata;
        this.requestId = requestId;
    }

    public String getStrategyId() {
        return strategyId;
    }

    public void setStrategyId(String strategyId) {
        this.strategyId = strategyId;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public Map<String, Object> getApplicant() {
        return applicant;
    }

    public void setApplicant(Map<String, Object> applicant) {
        this.applicant = applicant;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    @Override
    public String toString() {
        return "DecisionRequest{" +
                "strategyId='" + strategyId + '\'' +
                ", channel='" + channel + '\'' +
                ", requestId='" + requestId + '\'' +
                '}';
    }
}
