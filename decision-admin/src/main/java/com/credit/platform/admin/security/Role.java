package com.credit.platform.admin.security;

import java.util.Collections;
import java.util.Set;

/**
 * 系统角色 — viewer/editor/approver/admin 四级 RBAC。
 * <p>
 * 每个角色拥有一组权限 (Permission)，权限粒度到资源+操作级别。
 * 角色继承关系: viewer ⊂ editor ⊂ approver ⊂ admin
 * </p>
 */
public enum Role {

    /** 只读查看 — 可查看规则/评分卡/决策流/变量/日志 */
    VIEWER(Set.of(
        Permission.RULE_READ,
        Permission.SCORECARD_READ,
        Permission.TABLE_READ,
        Permission.TREE_READ,
        Permission.FLOW_READ,
        Permission.VARIABLE_READ,
        Permission.EXPERIMENT_READ,
        Permission.DECISION_LOG_READ,
        Permission.ANALYTICS_READ
    )),

    /** 编辑 — 可创建/修改规则 (继承 VIEWER) */
    EDITOR(Set.of(
        Permission.RULE_READ, Permission.RULE_WRITE,
        Permission.SCORECARD_READ, Permission.SCORECARD_WRITE,
        Permission.TABLE_READ, Permission.TABLE_WRITE,
        Permission.TREE_READ, Permission.TREE_WRITE,
        Permission.FLOW_READ, Permission.FLOW_WRITE,
        Permission.VARIABLE_READ, Permission.VARIABLE_WRITE,
        Permission.EXPERIMENT_READ, Permission.EXPERIMENT_WRITE,
        Permission.DECISION_LOG_READ,
        Permission.ANALYTICS_READ,
        Permission.SANDBOX_EXECUTE
    )),

    /** 审批 — 可审批发布 (继承 EDITOR) */
    APPROVER(Set.of(
        Permission.RULE_READ, Permission.RULE_WRITE,
        Permission.SCORECARD_READ, Permission.SCORECARD_WRITE,
        Permission.TABLE_READ, Permission.TABLE_WRITE,
        Permission.TREE_READ, Permission.TREE_WRITE,
        Permission.FLOW_READ, Permission.FLOW_WRITE,
        Permission.VARIABLE_READ, Permission.VARIABLE_WRITE,
        Permission.EXPERIMENT_READ, Permission.EXPERIMENT_WRITE,
        Permission.DECISION_LOG_READ,
        Permission.ANALYTICS_READ,
        Permission.SANDBOX_EXECUTE,
        Permission.PUBLISH_APPROVE, Permission.PUBLISH_REJECT,
        Permission.GRAYSCALE_MANAGE
    )),

    /** 管理员 — 全部权限 */
    ADMIN(Set.of(Permission.values()));

    private final Set<Permission> permissions;

    Role(Set<Permission> permissions) {
        this.permissions = Collections.unmodifiableSet(permissions);
    }

    /**
     * 检查角色是否拥有指定权限。
     */
    public boolean hasPermission(Permission permission) {
        return permissions.contains(permission);
    }

    /**
     * 获取角色所有权限。
     */
    public Set<Permission> getPermissions() {
        return permissions;
    }

    /**
     * 安全解析角色名称，不区分大小写。
     *
     * @param name 角色名称 (如 "ADMIN", "admin", "Admin")
     * @return 对应的 Role，无效输入返回 null
     */
    public static Role parse(String name) {
        if (name == null) return null;
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
