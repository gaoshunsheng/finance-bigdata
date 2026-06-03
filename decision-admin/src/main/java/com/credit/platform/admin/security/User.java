package com.credit.platform.admin.security;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

/**
 * 用户实体 — 支持多角色。
 * <p>
 * 密码使用 BCrypt 加密存储。
 * </p>
 */
public class User {

    private Long id;
    private String username;
    private String password;
    private String displayName;
    private String email;
    private Role role;
    private boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastLoginAt;

    public User() {
        this.enabled = true;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 创建新用户。
     */
    public static User create(String username, String displayName, String email, Role role) {
        User user = new User();
        user.username = Objects.requireNonNull(username);
        user.displayName = displayName;
        user.email = email;
        user.role = Objects.requireNonNull(role);
        return user;
    }

    /**
     * 检查用户是否拥有指定权限。
     */
    public boolean hasPermission(Permission permission) {
        return enabled && role != null && role.hasPermission(permission);
    }

    /**
     * 检查用户是否拥有指定角色或更高角色。
     */
    public boolean hasRoleOrAbove(Role requiredRole) {
        if (role == null) return false;
        return role.ordinal() >= requiredRole.ordinal();
    }

    // ========== Getters & Setters ==========

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; this.updatedAt = LocalDateTime.now(); }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; this.updatedAt = LocalDateTime.now(); }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; this.updatedAt = LocalDateTime.now(); }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
}
