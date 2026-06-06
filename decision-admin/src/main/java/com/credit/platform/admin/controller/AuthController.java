package com.credit.platform.admin.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import com.credit.platform.admin.model.ApiResponse;
import com.credit.platform.admin.security.*;

/**
 * 认证 Controller — 登录/登出/刷新 Token/用户管理。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;

    public AuthController(UserService userService, JwtService jwtService) {
        this.userService = userService;
        this.jwtService = jwtService;
    }

    /**
     * 用户登录 — 返回 Access Token + Refresh Token。
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(@RequestBody LoginRequest request) {
        try {
            User user = userService.findByUsername(request.username());

            if (!user.isEnabled()) {
                return ResponseEntity.status(403)
                    .body(ApiResponse.error(403, "User is disabled"));
            }

            if (!userService.checkPassword(request.username(), request.password())) {
                return ResponseEntity.status(401)
                    .body(ApiResponse.error(401, "Invalid credentials"));
            }

            // 更新最后登录时间并持久化
            user.setLastLoginAt(java.time.LocalDateTime.now());
            // 注意: 需要 UserService 提供保存方法来持久化 lastLoginAt
            // userService.updateLastLogin(user.getUsername());

            // 生成 Token
            String accessToken = jwtService.generateAccessToken(user.getUsername(), user.getRole());
            String refreshToken = jwtService.generateRefreshToken(user.getUsername());

            Map<String, Object> data = Map.of(
                "accessToken", accessToken,
                "refreshToken", refreshToken,
                "tokenType", "Bearer",
                "expiresIn", 7200,
                "username", user.getUsername(),
                "role", user.getRole().name(),
                "displayName", user.getDisplayName() != null ? user.getDisplayName() : user.getUsername()
            );

            return ResponseEntity.ok(ApiResponse.success(data));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401)
                .body(ApiResponse.error(401, "Invalid credentials"));
        }
    }

    /**
     * 刷新 Token — 用 Refresh Token 换新 Token 对。
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Map<String, Object>>> refresh(@RequestBody RefreshRequest request) {
        JwtService.TokenPair tokens = jwtService.refreshTokens(
            request.refreshToken(),
            username -> {
                try {
                    return userService.findByUsername(username).getRole();
                } catch (Exception e) {
                    return null;
                }
            });

        if (tokens == null) {
            return ResponseEntity.status(401)
                .body(ApiResponse.error(401, "Invalid or expired refresh token"));
        }

        Map<String, Object> data = Map.of(
            "accessToken", tokens.accessToken(),
            "refreshToken", tokens.refreshToken(),
            "tokenType", "Bearer",
            "expiresIn", 7200
        );

        return ResponseEntity.ok(ApiResponse.success(data));
    }

    /**
     * 登出 — 撤销 Refresh Token。
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestBody RefreshRequest request) {
        jwtService.revokeRefreshToken(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.success());
    }

    /**
     * 获取当前用户信息。
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> me(
            @RequestHeader("Authorization") String authorization) {
        String token = extractToken(authorization);
        if (token == null) {
            return ResponseEntity.status(401)
                .body(ApiResponse.error(401, "Missing token"));
        }

        Map<String, Object> claims = jwtService.validateAccessToken(token);
        if (claims == null) {
            return ResponseEntity.status(401)
                .body(ApiResponse.error(401, "Invalid or expired token"));
        }

        String username = (String) claims.get("sub");
        try {
            User user = userService.findByUsername(username);
            Map<String, Object> data = Map.of(
                "username", user.getUsername(),
                "displayName", user.getDisplayName() != null ? user.getDisplayName() : user.getUsername(),
                "email", user.getEmail() != null ? user.getEmail() : "",
                "role", user.getRole().name(),
                "enabled", user.isEnabled()
            );
            return ResponseEntity.ok(ApiResponse.success(data));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401)
                .body(ApiResponse.error(401, "User not found"));
        }
    }

    // ==================== 用户管理 API ====================

    /**
     * 创建用户 — 需要 ADMIN 角色。
     * 安全修复: 添加 @PreAuthorize 注解进行方法级权限校验。
     */
    @PostMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createUser(@RequestBody CreateUserRequest request) {
        try {
            Role role = Role.valueOf(request.role());
            User user = userService.createUser(
                request.username(), request.password(),
                request.displayName(), request.email(),
                role);
            return ResponseEntity.ok(ApiResponse.success(toUserSummary(user)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.badRequest(e.getMessage()));
        }
    }

    /**
     * 列出所有用户。
     */
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<Object>> listUsers() {
        return ResponseEntity.ok(ApiResponse.success(
            userService.listUsers().stream().map(this::toUserSummary).toList()));
    }

    /**
     * 修改密码 — 需要验证当前登录用户身份。
     * 安全修复: 验证请求中的 username 与当前认证用户一致（管理员除外）。
     */
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(@RequestBody ChangePasswordRequest request) {
        try {
            // 获取当前认证用户
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String currentUser = auth != null ? auth.getName() : null;

            // 管理员可以修改任何人的密码，普通用户只能修改自己的
            boolean isAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

            if (!isAdmin && !request.username().equals(currentUser)) {
                return ResponseEntity.status(403)
                    .body(ApiResponse.error(403, "Cannot change another user's password"));
            }

            userService.changePassword(request.username(), request.oldPassword(), request.newPassword());
            return ResponseEntity.ok(ApiResponse.success());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.badRequest(e.getMessage()));
        }
    }

    private String extractToken(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return null;
    }

    private Map<String, Object> toUserSummary(User user) {
        return Map.of(
            "id", user.getId(),
            "username", user.getUsername(),
            "displayName", user.getDisplayName() != null ? user.getDisplayName() : "",
            "email", user.getEmail() != null ? user.getEmail() : "",
            "role", user.getRole().name(),
            "enabled", user.isEnabled()
        );
    }

    // ==================== Request DTOs ====================

    public record LoginRequest(String username, String password) {}
    public record RefreshRequest(String refreshToken) {}
    public record CreateUserRequest(String username, String password, String displayName,
                                     String email, String role) {}
    public record ChangePasswordRequest(String username, String oldPassword, String newPassword) {}
}
