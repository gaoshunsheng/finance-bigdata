package com.credit.platform.engine.common.model;

/**
 * 决策结果枚举。
 * <p>
 * 定义决策引擎可能输出的四种结论。
 * </p>
 */
public enum DecisionResult {

    /** 通过 */
    PASS("通过"),

    /** 拒绝 */
    REJECT("拒绝"),

    /** 人工审核 */
    REVIEW("人工审核"),

    /** 人工介入 */
    MANUAL("人工介入");

    private final String description;

    DecisionResult(String description) {
        this.description = description;
    }

    /**
     * 获取决策结果的中文描述。
     *
     * @return 中文描述
     */
    public String getDescription() {
        return description;
    }
}
