package com.credit.platform.admin.security;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.credit.platform.admin.security.Permission;
import com.credit.platform.admin.security.Role;

/**
 * 安全模块综合测试 - RBAC / JWT / 审计日志。
 * <p>
 * 覆盖 20+ 用例，验证权限模型、JWT 生命周期、审计日志。
 * </p>
 */
@DisplayName("安全模块")
class SecurityTest {

    // ==================== RBAC 模型 ====================

    @Nested
    @DisplayName("RBAC - 角色权限模型")
    class RbacTests {

        @Test
        @DisplayName("RBAC-01: VIEWER 只有读权限")
        void viewer_readOnly() {
            assertTrue(Role.VIEWER.hasPermission(Permission.RULE_READ));
            assertTrue(Role.VIEWER.hasPermission(Permission.ANALYTICS_READ));
            assertFalse(Role.VIEWER.hasPermission(Permission.RULE_WRITE));
            assertFalse(Role.VIEWER.hasPermission(Permission.PUBLISH_APPROVE));
            assertFalse(Role.VIEWER.hasPermission(Permission.USER_MANAGE));
        }

        @Test
        @DisplayName("RBAC-02: EDITOR 有读写权限，无审批权限")
        void editor_readWriteNoApprove() {
            assertTrue(Role.EDITOR.hasPermission(Permission.RULE_READ));
            assertTrue(Role.EDITOR.hasPermission(Permission.RULE_WRITE));
            assertTrue(Role.EDITOR.hasPermission(Permission.SCORECARD_WRITE));
            assertTrue(Role.EDITOR.hasPermission(Permission.FLOW_WRITE));
            assertTrue(Role.EDITOR.hasPermission(Permission.SANDBOX_EXECUTE));
            assertFalse(Role.EDITOR.hasPermission(Permission.PUBLISH_APPROVE));
            assertFalse(Role.EDITOR.hasPermission(Permission.PUBLISH_REJECT));
            assertFalse(Role.EDITOR.hasPermission(Permission.USER_MANAGE));
        }

        @Test
        @DisplayName("RBAC-03: APPROVER 有审批权限，无用户管理")
        void approver_hasApproveNoUserManage() {
            assertTrue(Role.APPROVER.hasPermission(Permission.PUBLISH_APPROVE));
            assertTrue(Role.APPROVER.hasPermission(Permission.PUBLISH_REJECT));
            assertTrue(Role.APPROVER.hasPermission(Permission.GRAYSCALE_MANAGE));
            assertFalse(Role.APPROVER.hasPermission(Permission.USER_MANAGE));
        }

        @Test
        @DisplayName("RBAC-04: ADMIN 有全部权限")
        void admin_allPermissions() {
            for (Permission p : Permission.values()) {
                assertTrue(Role.ADMIN.hasPermission(p),
                    "ADMIN should have " + p.name());
            }
        }

        @Test
        @DisplayName("RBAC-05: 角色层级 ordinal 正确")
        void roleHierarchy() {
            assertTrue(Role.VIEWER.ordinal() < Role.EDITOR.ordinal());
            assertTrue(Role.EDITOR.ordinal() < Role.APPROVER.ordinal());
            assertTrue(Role.APPROVER.ordinal() < Role.ADMIN.ordinal());
        }
    }

    // ==================== User 模型 ====================

    @Nested
    @DisplayName("User - 用户模型")
    class UserTests {

        @Test
        @DisplayName("USR-01: 创建用户")
        void createUser() {
            User user = User.create("zhangsan", "张三", "zhangsan@test.com", Role.EDITOR);
            assertEquals("zhangsan", user.getUsername());
            assertEquals(Role.EDITOR, user.getRole());
            assertTrue(user.isEnabled());
        }

        @Test
        @DisplayName("USR-02: 用户权限检查")
        void userPermissionCheck() {
            User viewer = User.create("viewer", "V", null, Role.VIEWER);
            assertTrue(viewer.hasPermission(Permission.RULE_READ));
            assertFalse(viewer.hasPermission(Permission.RULE_WRITE));
        }

        @Test
        @DisplayName("USR-03: 禁用用户无权限")
        void disabledUserNoPermission() {
            User user = User.create("disabled", "D", null, Role.ADMIN);
            user.setEnabled(false);
            assertFalse(user.hasPermission(Permission.RULE_READ));
        }

        @Test
        @DisplayName("USR-04: 角色级别检查")
        void roleLevelCheck() {
            User editor = User.create("editor", "E", null, Role.EDITOR);
            assertTrue(editor.hasRoleOrAbove(Role.VIEWER));
            assertTrue(editor.hasRoleOrAbove(Role.EDITOR));
            assertFalse(editor.hasRoleOrAbove(Role.APPROVER));
        }
    }

    // ==================== UserService ====================

    @Nested
    @DisplayName("UserService - 用户管理")
    class UserServiceTests {

        private UserService userService;

        @BeforeEach
        void setUp() {
            userService = new UserService(new UserRepository());
        }

        @Test
        @DisplayName("USVC-01: 创建用户并验证密码")
        void createUserAndCheckPassword() {
            User user = userService.createUser("testuser", "password123", "Test User",
                "test@test.com", Role.EDITOR);
            assertNotNull(user.getId());
            assertEquals("testuser", user.getUsername());
            assertTrue(userService.checkPassword("testuser", "password123"));
            assertFalse(userService.checkPassword("testuser", "wrongpassword"));
        }

        @Test
        @DisplayName("USVC-02: 重复用户名报错")
        void duplicateUsername() {
            userService.createUser("dup", "pass", "Dup", null, Role.VIEWER);
            assertThrows(IllegalArgumentException.class,
                () -> userService.createUser("dup", "pass2", "Dup2", null, Role.EDITOR));
        }

        @Test
        @DisplayName("USVC-03: 修改密码")
        void changePassword() {
            userService.createUser("user1", "oldpass", "User1", null, Role.VIEWER);
            userService.changePassword("user1", "oldpass", "newpass");
            assertTrue(userService.checkPassword("user1", "newpass"));
            assertFalse(userService.checkPassword("user1", "oldpass"));
        }

        @Test
        @DisplayName("USVC-04: 旧密码错误时修改失败")
        void changePassword_wrongOld() {
            userService.createUser("user1", "pass", "User1", null, Role.VIEWER);
            assertThrows(IllegalArgumentException.class,
                () -> userService.changePassword("user1", "wrong", "newpass"));
        }

        @Test
        @DisplayName("USVC-05: 管理员重置密码")
        void resetPassword() {
            userService.createUser("user1", "old", "User1", null, Role.VIEWER);
            userService.resetPassword("user1", "reset123");
            assertTrue(userService.checkPassword("user1", "reset123"));
        }

        @Test
        @DisplayName("USVC-06: 启用/禁用用户")
        void toggleEnabled() {
            userService.createUser("user1", "pass", "User1", null, Role.VIEWER);
            User disabled = userService.toggleEnabled("user1", false);
            assertFalse(disabled.isEnabled());
            User enabled = userService.toggleEnabled("user1", true);
            assertTrue(enabled.isEnabled());
        }

        @Test
        @DisplayName("USVC-07: 更新用户信息")
        void updateUser() {
            userService.createUser("user1", "pass", "Old Name", "old@test.com", Role.VIEWER);
            User updated = userService.updateUser("user1", "New Name", "new@test.com", Role.EDITOR);
            assertEquals("New Name", updated.getDisplayName());
            assertEquals(Role.EDITOR, updated.getRole());
        }

        @Test
        @DisplayName("USVC-08: 初始化默认管理员")
        void initDefaultAdmin() {
            userService.initDefaultAdmin();
            User admin = userService.findByUsername("admin");
            assertEquals(Role.ADMIN, admin.getRole());
            assertTrue(userService.checkPassword("admin", "admin123"));
        }

        @Test
        @DisplayName("USVC-09: 列出和过滤用户")
        void listAndFilterUsers() {
            userService.createUser("v1", "p", "Viewer1", null, Role.VIEWER);
            userService.createUser("e1", "p", "Editor1", null, Role.EDITOR);
            userService.createUser("a1", "p", "Admin1", null, Role.ADMIN);

            assertEquals(3, userService.listUsers().size());
            assertEquals(1, userService.listByRole(Role.ADMIN).size());
        }

        @Test
        @DisplayName("USVC-10: 删除用户")
        void deleteUser() {
            userService.createUser("todel", "p", "Del", null, Role.VIEWER);
            assertTrue(userService.deleteUser("todel"));
            assertThrows(IllegalArgumentException.class,
                () -> userService.findByUsername("todel"));
        }
    }

    // ==================== JWT ====================

    @Nested
    @DisplayName("JwtService - JWT Token")
    class JwtTests {

        private JwtService jwtService;

        @BeforeEach
        void setUp() {
            jwtService = new JwtService("test-secret-key-for-unit-tests-minimum-32-chars");
        }

        @Test
        @DisplayName("JWT-01: 生成并验证 Access Token")
        void generateAndValidateAccessToken() {
            String token = jwtService.generateAccessToken("zhangsan", Role.EDITOR);
            assertNotNull(token);

            Map<String, Object> claims = jwtService.validateAccessToken(token);
            assertNotNull(claims);
            assertEquals("zhangsan", claims.get("sub"));
            assertEquals("EDITOR", claims.get("role"));
            assertEquals("access", claims.get("type"));
        }

        @Test
        @DisplayName("JWT-02: 无效 Token 验证失败")
        void invalidToken() {
            assertNull(jwtService.validateAccessToken("invalid.token.here"));
            assertNull(jwtService.validateAccessToken(""));
            assertNull(jwtService.validateAccessToken("not-even-jwt"));
        }

        @Test
        @DisplayName("JWT-03: 篡改 Token 验证失败")
        void tamperedToken() {
            String token = jwtService.generateAccessToken("zhangsan", Role.EDITOR);
            // 篡改 payload
            String[] parts = token.split("\\.");
            String tampered = parts[0] + "." + parts[1] + ".tamperedSignature";
            assertNull(jwtService.validateAccessToken(tampered));
        }

        @Test
        @DisplayName("JWT-04: 从 Token 获取用户名和角色")
        void getUsernameAndRoleFromToken() {
            String token = jwtService.generateAccessToken("lisi", Role.APPROVER);
            assertEquals("lisi", jwtService.getUsernameFromToken(token));
            assertEquals(Role.APPROVER, jwtService.getRoleFromToken(token));
        }

        @Test
        @DisplayName("JWT-05: Refresh Token 生成和验证")
        void refreshTokenLifecycle() {
            String refresh = jwtService.generateRefreshToken("zhangsan");
            assertNotNull(refresh);
            assertEquals("zhangsan", jwtService.validateRefreshToken(refresh));
        }

        @Test
        @DisplayName("JWT-06: 撤销 Refresh Token")
        void revokeRefreshToken() {
            String refresh = jwtService.generateRefreshToken("zhangsan");
            jwtService.revokeRefreshToken(refresh);
            assertNull(jwtService.validateRefreshToken(refresh));
        }

        @Test
        @DisplayName("JWT-07: 无效 Refresh Token 换新 Token 失败")
        void refreshTokens_invalid() {
            JwtService.TokenPair result = jwtService.refreshTokens(
                "nonexistent-token",
                username -> Role.EDITOR);
            assertNull(result);
        }

        @Test
        @DisplayName("JWT-08: Refresh Token 正确换新 Token 对")
        void refreshTokensCorrect() {
            String refreshToken = jwtService.generateRefreshToken("zhangsan");

            JwtService.TokenPair newTokens = jwtService.refreshTokens(
                refreshToken,
                username -> "zhangsan".equals(username) ? Role.EDITOR : null);

            assertNotNull(newTokens);
            assertNotNull(newTokens.accessToken());
            assertNotNull(newTokens.refreshToken());

            // 旧 refresh token 已失效
            assertNull(jwtService.validateRefreshToken(refreshToken));

            // 新 access token 有效
            Map<String, Object> claims = jwtService.validateAccessToken(newTokens.accessToken());
            assertEquals("zhangsan", claims.get("sub"));
            assertEquals("EDITOR", claims.get("role"));
        }

        @Test
        @DisplayName("JWT-09: 不同角色生成不同 Token")
        void differentRolesDifferentTokens() {
            String viewerToken = jwtService.generateAccessToken("v1", Role.VIEWER);
            String adminToken = jwtService.generateAccessToken("a1", Role.ADMIN);

            assertEquals(Role.VIEWER, jwtService.getRoleFromToken(viewerToken));
            assertEquals(Role.ADMIN, jwtService.getRoleFromToken(adminToken));
        }
    }

    // ==================== 审计日志 ====================

    @Nested
    @DisplayName("AuditLogService - 审计日志")
    class AuditLogTests {

        private AuditLogService auditLogService;

        @BeforeEach
        void setUp() {
            auditLogService = new AuditLogService(new AuditLogRepository());
        }

        @Test
        @DisplayName("AUD-01: 记录操作审计日志")
        void logOperation() {
            AuditLog log = auditLogService.log("zhangsan", "CREATE", "RULE",
                "rule-001", "Created new rule");
            assertNotNull(log.getId());
            assertEquals("zhangsan", log.getOperator());
            assertEquals("CREATE", log.getAction());
            assertEquals("RULE", log.getTargetType());
        }

        @Test
        @DisplayName("AUD-02: 记录带快照的审计日志")
        void logWithSnapshot() {
            AuditLog log = auditLogService.log("zhangsan", "UPDATE", "RULE",
                "rule-001", 2, "{\"v\":1}", "{\"v\":2}", "Updated content");
            assertEquals(2, log.getTargetVersion());
            assertEquals("{\"v\":1}", log.getBeforeSnapshot());
            assertEquals("{\"v\":2}", log.getAfterSnapshot());
        }

        @Test
        @DisplayName("AUD-03: 记录登录审计")
        void logLogin() {
            AuditLog log = auditLogService.logLogin("zhangsan", "192.168.1.1");
            assertEquals("LOGIN", log.getAction());
            assertEquals("192.168.1.1", log.getIpAddress());
        }

        @Test
        @DisplayName("AUD-04: 查询目标审计历史")
        void getTargetHistory() {
            auditLogService.log("dev1", "CREATE", "RULE", "r1", "Created");
            auditLogService.log("dev1", "UPDATE", "RULE", "r1", "Updated");
            auditLogService.log("dev2", "CREATE", "RULE", "r2", "Created");

            List<AuditLog> history = auditLogService.getTargetHistory("RULE", "r1");
            assertEquals(2, history.size());
        }

        @Test
        @DisplayName("AUD-05: 查询操作人审计日志")
        void getOperatorHistory() {
            auditLogService.log("dev1", "CREATE", "RULE", "r1", "C");
            auditLogService.log("dev1", "UPDATE", "RULE", "r1", "U");
            auditLogService.log("dev2", "CREATE", "RULE", "r2", "C");

            assertEquals(2, auditLogService.getOperatorHistory("dev1").size());
            assertEquals(1, auditLogService.getOperatorHistory("dev2").size());
        }

        @Test
        @DisplayName("AUD-06: 分页查询")
        void pagination() {
            for (int i = 0; i < 15; i++) {
                auditLogService.log("op", "ACTION_" + i, "TYPE", "id_" + i, "detail");
            }

            assertEquals(10, auditLogService.list(0, 10).size());
            assertEquals(5, auditLogService.list(1, 10).size());
            assertEquals(15, auditLogService.count());
        }

        @Test
        @DisplayName("AUD-07: 按操作类型查询")
        void findByAction() {
            auditLogService.log("op", "APPROVE", "RULE", "r1", "Approved");
            auditLogService.log("op", "REJECT", "RULE", "r2", "Rejected");
            auditLogService.log("op", "APPROVE", "RULE", "r3", "Approved");

            assertEquals(2, auditLogService.findByAction("APPROVE").size());
            assertEquals(1, auditLogService.findByAction("REJECT").size());
        }
    }

    // ==================== UserRepository ====================

    @Nested
    @DisplayName("UserRepository - 用户仓库")
    class UserRepositoryTests {

        private UserRepository repo;

        @BeforeEach
        void setUp() {
            repo = new UserRepository();
        }

        @Test
        @DisplayName("UREP-01: 保存和查找")
        void saveAndFind() {
            User user = User.create("testuser", "Test", null, Role.VIEWER);
            user.setPassword("encoded");
            repo.save(user);

            assertTrue(repo.findByUsername("testuser").isPresent());
            assertFalse(repo.findByUsername("nonexistent").isPresent());
        }

        @Test
        @DisplayName("UREP-02: 按角色过滤")
        void findByRole() {
            User v = User.create("v1", "V", null, Role.VIEWER);
            v.setPassword("p");
            User e = User.create("e1", "E", null, Role.EDITOR);
            e.setPassword("p");
            repo.save(v);
            repo.save(e);

            assertEquals(1, repo.findByRole(Role.VIEWER).size());
            assertEquals(1, repo.findByRole(Role.EDITOR).size());
            assertEquals(0, repo.findByRole(Role.ADMIN).size());
        }

        @Test
        @DisplayName("UREP-03: 删除用户")
        void delete() {
            User user = User.create("todel", "T", null, Role.VIEWER);
            user.setPassword("p");
            repo.save(user);

            assertTrue(repo.deleteByUsername("todel"));
            assertFalse(repo.existsByUsername("todel"));
        }
    }
}
