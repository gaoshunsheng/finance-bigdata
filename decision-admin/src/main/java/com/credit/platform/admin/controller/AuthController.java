package com.credit.platform.admin.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
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

            // 更新最后登录时间
            user.setLastLoginAt(java.time.LocalDateTime.now());

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
     * 创建用户 (ADMIN only)。
     */
    @PostMapping("/users")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createUser(@RequestBody CreateUserRequest request) {
        User user = userService.createUser(
            request.username(), request.password(),
            request.displayName(), request.email(),
            Role.valueOf(request.role()));
        return ResponseEntity.ok(ApiResponse.success(toUserSummary(user)));
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
     * 修改密码。
     */
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(@RequestBody ChangePasswordRequest request) {
        try {
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
