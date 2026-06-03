package com.credit.platform.engine.core.trace;

/**
 * 可解释性级别枚举。
 * <p>
 * 四级可解释性体系，面向不同用户角色:
 * <ul>
 *   <li>L1 规则级 — 业务用户: 命中规则、变量阈值比较、评分卡得分明细</li>
 *   <li>L2 流程级 — 策略分析师: DAG 路径可视化、高亮执行节点</li>
 *   <li>L3 模型级 — 模型工程师: 特征重要性、SHAP 值（预留）</li>
 *   <li>L4 审计级 — 合规人员: 完整输入输出快照、时间戳、审计日志</li>
 * </ul>
 * </p>
 */
public enum TraceLevel {

    /** L1 规则级 — 业务用户 */
    RULE(1, "规则级"),

    /** L2 流程级 — 策略分析师 */
    FLOW(2, "流程级"),

    /** L3 模型级 — 模型工程师 */
    MODEL(3, "模型级"),

    /** L4 审计级 — 合规人员 */
    AUDIT(4, "审计级");

    private final int level;
    private final String description;

    TraceLevel(int level, String description) {
        this.level = level;
        this.description = description;
    }

    /**
     * 获取级别编号。
     *
     * @return 级别编号 (1-4)
     */
    public int getLevel() {
        return level;
    }

    /**
     * 获取级别中文描述。
     *
     * @return 中文描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 判断当前级别是否包含目标级别的数据。
     * <p>
     * 高级别包含低级别的所有信息: AUDIT 包含全部，RULE 只包含自己。
     * </p>
     *
     * @param target 目标级别
     * @return 是否包含
     */
    public boolean includes(TraceLevel target) {
        return this.level >= target.level;
    }
}
