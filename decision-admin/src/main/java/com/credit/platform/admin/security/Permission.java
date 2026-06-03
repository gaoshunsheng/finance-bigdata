package com.credit.platform.admin.security;

/**
 * 权限定义 — 资源 + 操作粒度。
 * <p>
 * 命名规则: RESOURCE_ACTION
 * </p>
 */
public enum Permission {

    // ==================== 规则 ====================
    /** 查看规则 */
    RULE_READ("rule:read", "查看规则"),
    /** 创建/修改规则 */
    RULE_WRITE("rule:write", "创建/修改规则"),

    // ==================== 评分卡 ====================
    SCORECARD_READ("scorecard:read", "查看评分卡"),
    SCORECARD_WRITE("scorecard:write", "创建/修改评分卡"),

    // ==================== 决策表 ====================
    TABLE_READ("table:read", "查看决策表"),
    TABLE_WRITE("table:write", "创建/修改决策表"),

    // ==================== 决策树 ====================
    TREE_READ("tree:read", "查看决策树"),
    TREE_WRITE("tree:write", "创建/修改决策树"),

    // ==================== 决策流 ====================
    FLOW_READ("flow:read", "查看决策流"),
    FLOW_WRITE("flow:write", "创建/修改决策流"),

    // ==================== 变量 ====================
    VARIABLE_READ("variable:read", "查看变量"),
    VARIABLE_WRITE("variable:write", "创建/修改变量"),

    // ==================== 实验 ====================
    EXPERIMENT_READ("experiment:read", "查看实验"),
    EXPERIMENT_WRITE("experiment:write", "创建/修改实验"),

    // ==================== 决策日志 ====================
    DECISION_LOG_READ("decision_log:read", "查看决策日志"),

    // ==================== 分析 ====================
    ANALYTICS_READ("analytics:read", "查看分析看板"),

    // ==================== 发布 ====================
    /** 审批通过 */
    PUBLISH_APPROVE("publish:approve", "审批通过"),
    /** 审批驳回 */
    PUBLISH_REJECT("publish:reject", "审批驳回"),

    // ==================== 灰度 ====================
    /** 灰度管理 */
    GRAYSCALE_MANAGE("grayscale:manage", "灰度管理"),

    // ==================== 沙箱 ====================
    /** 沙箱测试 */
    SANDBOX_EXECUTE("sandbox:execute", "沙箱测试"),

    // ==================== 用户管理 ====================
    /** 用户管理 */
    USER_MANAGE("user:manage", "用户管理"),

    // ==================== 审计 ====================
    /** 查看审计日志 */
    AUDIT_READ("audit:read", "查看审计日志");

    private final String code;
    private final String description;

    Permission(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() { return code; }
    public String getDescription() { return description; }
}
