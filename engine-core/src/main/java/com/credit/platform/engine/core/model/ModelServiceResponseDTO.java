package com.credit.platform.engine.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 模型服务响应 DTO — 用于 Jackson 反序列化。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ModelServiceResponseDTO {

    @JsonProperty("score")
    private double score;

    @JsonProperty("probability")
    private double probability;

    @JsonProperty("label")
    private String label;

    public double getScore() { return score; }
    public double getProbability() { return probability; }
    public String getLabel() { return label; }
}
