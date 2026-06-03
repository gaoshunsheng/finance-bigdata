package com.credit.platform.engine.core.trace;

/**
 * 追踪节点类型枚举。
 * <p>
 * 对应决策引擎中所有可执行的节点类型，用于可解释性追踪中区分不同引擎的执行细节。
 * </p>
 */
public enum NodeType {

    /** 数据准备节点 — 变量计算 / 外部数据加载 */
    DATA_PREP("数据准备"),

    /** 规则集节点 — 条件规则批量求值 */
    RULE_SET("规则集"),

    /** 评分卡节点 — 特征分箱加权评分 */
    SCORECARD("评分卡"),

    /** 决策表节点 — 二维表条件匹配 */
    DECISION_TABLE("决策表"),

    /** 决策树节点 — 嵌套条件树遍历 */
    DECISION_TREE("决策树"),

    /** DAG 决策流节点 — 拓扑排序流程编排 */
    DECISION_FLOW("决策流"),

    /** 模型节点 — 外部模型调用（预留） */
    MODEL("模型"),

    /** 脚本节点 — 自定义表达式执行 */
    SCRIPT("脚本"),

    /** 条件分支节点 — IF/ELSE 路由 */
    CONDITION("条件分支"),

    /** 动作节点 — PASS/REJECT/REVIEW/MANUAL */
    ACTION("动作"),

    /** 变量解析节点 — 4层变量解析 */
    VARIABLE_RESOLVE("变量解析");

    private final String description;

    NodeType(String description) {
        this.description = description;
    }

    /**
     * 获取节点类型的中文描述。
     *
     * @return 中文描述
     */
    public String getDescription() {
        return description;
    }
}
