package com.credit.platform.engine.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * 模型服务请求 DTO — 用于 Jackson 序列化。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModelServiceRequestDTO {

    @JsonProperty("modelId")
    private final String modelId;

    @JsonProperty("requestId")
    private final String requestId;

    @JsonProperty("features")
    private final Map<String, Object> features;

    public ModelServiceRequestDTO(ModelRequest request) {
        this.modelId = request.getModelId();
        this.requestId = request.getRequestId();
        this.features = request.getFeatures();
    }

    public String getModelId() { return modelId; }
    public String getRequestId() { return requestId; }
    public Map<String, Object> getFeatures() { return features; }
}
