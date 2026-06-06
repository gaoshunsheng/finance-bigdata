package com.credit.platform.admin.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.credit.platform.admin.model.*;
import com.credit.platform.admin.security.JwtService;
import com.credit.platform.admin.security.Role;
import com.credit.platform.admin.security.User;
import com.credit.platform.admin.security.UserService;
import com.credit.platform.admin.service.RuleAdminService;
import com.credit.platform.admin.service.RulePublishService;

import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Controller unit tests -- direct invocation with mocked dependencies.
 * No Spring context needed.
 */
class ControllerTest {

    // =====================================================================
    //  PublishController Tests
    // =====================================================================

    @Nested
    class PublishControllerTest {

        private RulePublishService publishService;
        private PublishController controller;

        @BeforeEach
        void setUp() {
            publishService = Mockito.mock(RulePublishService.class);
            controller = new PublishController(publishService);
            // Mock SecurityContext so getCurrentUser() returns "admin"
            Authentication auth = Mockito.mock(Authentication.class);
            when(auth.getName()).thenReturn("admin");
            SecurityContext ctx = Mockito.mock(SecurityContext.class);
            when(ctx.getAuthentication()).thenReturn(auth);
            SecurityContextHolder.setContext(ctx);
        }

        @AfterEach
        void tearDown() {
            SecurityContextHolder.clearContext();
        }

        private RuleEntity buildEntity(String id, String type, int version, PublishStatus status) {
            RuleEntity entity = RuleEntity.create(id, "test-rule", type, "{\"rule\":true}");
            entity.setVersion(version);
            entity.setStatus(status);
            entity.setCreatedBy("tester");
            entity.setUpdatedBy("tester");
            return entity;
        }

        // ---------- promote-to-testing ----------

        @Test
        void promoteToTesting_returnsOkWithSummary() {
            RuleEntity entity = buildEntity("R001", "RULE", 1, PublishStatus.TESTING);
            when(publishService.promoteToTesting("RULE", "R001", "admin")).thenReturn(entity);

            var response = controller.promoteToTesting("RULE", "R001");

            assertEquals(200, response.getStatusCode().value());
            ApiResponse<Map<String, Object>> body = response.getBody();
            assertNotNull(body);
            assertEquals(200, body.getCode());
            assertEquals("R001", body.getData().get("id"));
            assertEquals("TESTING", body.getData().get("status"));

            verify(publishService).promoteToTesting("RULE", "R001", "admin");
        }

        @Test
        void promoteToTesting_serviceThrows_propagatesException() {
            when(publishService.promoteToTesting("RULE", "R999", "admin"))
                    .thenThrow(new IllegalArgumentException("RULE not found: R999"));

            assertThrows(IllegalArgumentException.class, () ->
                    controller.promoteToTesting("RULE", "R999"));
        }

        // ---------- submit-approval ----------

        @Test
        void submitForApproval_returnsOkWithSummary() {
            RuleEntity entity = buildEntity("R001", "RULE", 1, PublishStatus.PENDING_REVIEW);
            when(publishService.submitForApproval("RULE", "R001", "admin", "ready for review"))
                    .thenReturn(entity);

            var response = controller.submitForApproval("RULE", "R001",
                    new PublishController.ApprovalRequest("ready for review"));

            assertEquals(200, response.getStatusCode().value());
            assertEquals("PENDING_REVIEW", response.getBody().getData().get("status"));

            verify(publishService).submitForApproval("RULE", "R001", "admin", "ready for review");
        }

        // ---------- approve ----------

        @Test
        void approve_returnsOkWithSummary() {
            RuleEntity entity = buildEntity("R001", "RULE", 1, PublishStatus.APPROVED);
            when(publishService.approve("RULE", "R001", "admin", "looks good"))
                    .thenReturn(entity);

            var response = controller.approve("RULE", "R001",
                    new PublishController.ApprovalRequest("looks good"));

            assertEquals(200, response.getStatusCode().value());
            assertEquals("APPROVED", response.getBody().getData().get("status"));
        }

        // ---------- reject ----------

        @Test
        void reject_returnsOkWithSummary() {
            RuleEntity entity = buildEntity("R001", "RULE", 1, PublishStatus.DRAFT);
            when(publishService.reject("RULE", "R001", "admin", "needs work"))
                    .thenReturn(entity);

            var response = controller.reject("RULE", "R001",
                    new PublishController.RejectRequest("needs work"));

            assertEquals(200, response.getStatusCode().value());
            assertEquals("DRAFT", response.getBody().getData().get("status"));

            verify(publishService).reject("RULE", "R001", "admin", "needs work");
        }

        // ---------- withdraw ----------

        @Test
        void withdraw_returnsOkWithSummary() {
            RuleEntity entity = buildEntity("R001", "RULE", 1, PublishStatus.TESTING);
            when(publishService.withdrawApproval("RULE", "R001", "admin", "withdraw"))
                    .thenReturn(entity);

            var response = controller.withdraw("RULE", "R001",
                    new PublishController.ApprovalRequest("withdraw"));

            assertEquals(200, response.getStatusCode().value());
            assertEquals("TESTING", response.getBody().getData().get("status"));
        }

        // ---------- approval-history ----------

        @SuppressWarnings("unchecked")
        @Test
        void getApprovalHistory_returnsOk() {
            List<? extends Object> history = List.of(
                    Map.of("action", "APPROVE", "operator", "admin"));
            doReturn(history).when(publishService).getApprovalHistory("RULE", "R001");

            var response = controller.getApprovalHistory("RULE", "R001");

            assertEquals(200, response.getStatusCode().value());
            assertNotNull(response.getBody().getData());
        }

        // ---------- pending-approvals ----------

        @Test
        void getPendingApprovals_returnsOkWithSummaries() {
            RuleEntity e1 = buildEntity("R001", "RULE", 1, PublishStatus.PENDING_REVIEW);
            RuleEntity e2 = buildEntity("R002", "RULE", 1, PublishStatus.PENDING_REVIEW);
            when(publishService.getPendingApprovals()).thenReturn(List.of(e1, e2));

            var response = controller.getPendingApprovals();

            assertEquals(200, response.getStatusCode().value());
            List<Map<String, Object>> data = response.getBody().getData();
            assertEquals(2, data.size());
            assertEquals("R001", data.get(0).get("id"));
            assertEquals("R002", data.get(1).get("id"));
        }

        @Test
        void getPendingApprovals_emptyList_returnsOkEmpty() {
            when(publishService.getPendingApprovals()).thenReturn(List.of());

            var response = controller.getPendingApprovals();

            assertEquals(200, response.getStatusCode().value());
            assertTrue(response.getBody().getData().isEmpty());
        }

        // ---------- grayscale/start ----------

        @Test
        void startGrayscale_returnsOkWithConfig() {
            GrayscaleConfig config = GrayscaleConfig.create("RULE", "R001", 1);
            config.startGrayscale(10, "admin");
            when(publishService.startGrayscale("RULE", "R001", 10, "admin")).thenReturn(config);

            var response = controller.startGrayscale("RULE", "R001",
                    new PublishController.GrayscaleStartRequest(10));

            assertEquals(200, response.getStatusCode().value());
            GrayscaleConfig data = response.getBody().getData();
            assertNotNull(data);
            assertEquals(10, data.getPercentage());
        }

        // ---------- grayscale/ramp-up ----------

        @Test
        void rampUpGrayscale_returnsOkWithConfig() {
            GrayscaleConfig config = GrayscaleConfig.create("RULE", "R001", 1);
            config.startGrayscale(10, "admin");
            config.adjustPercentage(25, "admin");
            when(publishService.rampUpGrayscale("RULE", "R001", "admin")).thenReturn(config);

            var response = controller.rampUpGrayscale("RULE", "R001");

            assertEquals(200, response.getStatusCode().value());
            assertNotNull(response.getBody().getData());
        }

        // ---------- grayscale/adjust ----------

        @Test
        void adjustGrayscale_returnsOkWithConfig() {
            GrayscaleConfig config = GrayscaleConfig.create("RULE", "R001", 1);
            config.startGrayscale(10, "admin");
            config.adjustPercentage(50, "admin");
            when(publishService.adjustGrayscale("RULE", "R001", 50, "admin")).thenReturn(config);

            var response = controller.adjustGrayscale("RULE", "R001",
                    new PublishController.GrayscaleAdjustRequest(50));

            assertEquals(200, response.getStatusCode().value());
            assertEquals(50, response.getBody().getData().getPercentage());
        }

        // ---------- grayscale/pause ----------

        @Test
        void pauseGrayscale_returnsOkWithConfig() {
            GrayscaleConfig config = GrayscaleConfig.create("RULE", "R001", 1);
            config.startGrayscale(10, "admin");
            config.pause("admin");
            when(publishService.pauseGrayscale("RULE", "R001", "admin")).thenReturn(config);

            var response = controller.pauseGrayscale("RULE", "R001");

            assertEquals(200, response.getStatusCode().value());
            assertEquals(GrayscaleConfig.GrayscaleStatus.PAUSED,
                    response.getBody().getData().getGrayscaleStatus());
        }

        // ---------- grayscale/resume ----------

        @Test
        void resumeGrayscale_returnsOkWithConfig() {
            GrayscaleConfig config = GrayscaleConfig.create("RULE", "R001", 1);
            config.startGrayscale(10, "admin");
            config.pause("admin");
            config.resume("admin");
            when(publishService.resumeGrayscale("RULE", "R001", "admin")).thenReturn(config);

            var response = controller.resumeGrayscale("RULE", "R001");

            assertEquals(200, response.getStatusCode().value());
            assertEquals(GrayscaleConfig.GrayscaleStatus.IN_PROGRESS,
                    response.getBody().getData().getGrayscaleStatus());
        }

        // ---------- grayscale (GET) ----------

        @Test
        void getGrayscaleConfig_returnsOk() {
            GrayscaleConfig config = GrayscaleConfig.create("RULE", "R001", 1);
            when(publishService.getGrayscaleConfig("RULE", "R001")).thenReturn(config);

            var response = controller.getGrayscaleConfig("RULE", "R001");

            assertEquals(200, response.getStatusCode().value());
            assertNotNull(response.getBody().getData());
        }

        // ---------- rollback ----------

        @Test
        void rollback_returnsOkWithSummary() {
            RuleEntity entity = buildEntity("R001", "RULE", 2, PublishStatus.DRAFT);
            entity.getAttributes().put("rollbackFrom", 1);
            when(publishService.rollback("RULE", "R001", 1, "admin")).thenReturn(entity);

            var response = controller.rollback("RULE", "R001", 1);

            assertEquals(200, response.getStatusCode().value());
            assertEquals("R001", response.getBody().getData().get("id"));
            assertEquals("DRAFT", response.getBody().getData().get("status"));

            verify(publishService).rollback("RULE", "R001", 1, "admin");
        }

        // ---------- grayscale/rollback ----------

        @Test
        void rollbackGrayscale_returnsOkWithSummary() {
            RuleEntity entity = buildEntity("R001", "RULE", 1, PublishStatus.APPROVED);
            when(publishService.rollbackGrayscale("RULE", "R001", "admin")).thenReturn(entity);

            var response = controller.rollbackGrayscale("RULE", "R001");

            assertEquals(200, response.getStatusCode().value());
            assertEquals("APPROVED", response.getBody().getData().get("status"));
        }

        // ---------- diff ----------

        @Test
        void diff_returnsOkWithVersionDiff() {
            VersionDiff diff = VersionDiff.builder()
                    .targetType("RULE").targetId("R001")
                    .sourceVersion(1).targetVersion(2)
                    .addDiff(new VersionDiff.FieldDiff("threshold",
                            VersionDiff.DiffType.MODIFIED, "80", "90"))
                    .build();
            when(publishService.diff("RULE", "R001", 1, 2)).thenReturn(diff);

            var response = controller.diff("RULE", "R001", 1, 2);

            assertEquals(200, response.getStatusCode().value());
            VersionDiff data = response.getBody().getData();
            assertEquals("RULE", data.getTargetType());
            assertEquals(1, data.getModifiedCount());
            assertTrue(data.hasChanges());
        }

        // ---------- diff-latest ----------

        @Test
        void diffLatest_returnsOk() {
            VersionDiff diff = VersionDiff.builder()
                    .targetType("RULE").targetId("R001")
                    .sourceVersion(1).targetVersion(2)
                    .build();
            when(publishService.diffLatest("RULE", "R001")).thenReturn(diff);

            var response = controller.diffLatest("RULE", "R001");

            assertEquals(200, response.getStatusCode().value());
            assertNotNull(response.getBody().getData());
        }

        // ---------- publish-history ----------

        @Test
        void getPublishHistory_returnsOkWithSummaries() {
            RuleEntity v1 = buildEntity("R001", "RULE", 1, PublishStatus.RELEASED);
            RuleEntity v2 = buildEntity("R001", "RULE", 2, PublishStatus.DRAFT);
            when(publishService.getPublishHistory("RULE", "R001")).thenReturn(List.of(v1, v2));

            var response = controller.getPublishHistory("RULE", "R001");

            assertEquals(200, response.getStatusCode().value());
            List<Map<String, Object>> data = response.getBody().getData();
            assertEquals(2, data.size());
            assertEquals(1, data.get(0).get("version"));
            assertEquals(2, data.get(1).get("version"));
        }

        // ---------- promote (legacy) ----------

        @Test
        void promote_returnsOkWithSummary() {
            RuleEntity entity = buildEntity("R001", "RULE", 1, PublishStatus.TESTING);
            when(publishService.promoteToTesting("RULE", "R001", "admin")).thenReturn(entity);

            var response = controller.promote("RULE", "R001");

            assertEquals(200, response.getStatusCode().value());
            assertEquals("TESTING", response.getBody().getData().get("status"));
        }

        // ---------- cross-type scenarios ----------

        @Test
        void promoteToTesting_scorecardType_works() {
            RuleEntity entity = buildEntity("SC001", "SCORECARD", 1, PublishStatus.TESTING);
            when(publishService.promoteToTesting("SCORECARD", "SC001", "admin")).thenReturn(entity);

            var response = controller.promoteToTesting("SCORECARD", "SC001");

            assertEquals(200, response.getStatusCode().value());
            assertEquals("SCORECARD", response.getBody().getData().get("type"));
        }
    }

    // =====================================================================
    //  AuthController Tests
    // =====================================================================

    @Nested
    class AuthControllerTest {

        private UserService userService;
        private JwtService jwtService;
        private AuthController controller;

        @BeforeEach
        void setUp() {
            userService = Mockito.mock(UserService.class);
            jwtService = new JwtService("test-secret-key-for-unit-tests-12345");
            controller = new AuthController(userService, jwtService);
        }

        private User buildUser(String username, Role role) {
            User user = User.create(username, username + " Display", username + "@test.com", role);
            user.setId(1L);
            return user;
        }

        // ---------- login ----------

        @Test
        void login_success_returnsTokens() {
            User user = buildUser("admin", Role.ADMIN);
            when(userService.findByUsername("admin")).thenReturn(user);
            when(userService.checkPassword("admin", "pass123")).thenReturn(true);

            var response = controller.login(
                    new AuthController.LoginRequest("admin", "pass123"));

            assertEquals(200, response.getStatusCode().value());
            Map<String, Object> data = response.getBody().getData();
            assertNotNull(data.get("accessToken"));
            assertNotNull(data.get("refreshToken"));
            assertEquals("Bearer", data.get("tokenType"));
            assertEquals(7200, data.get("expiresIn"));
            assertEquals("admin", data.get("username"));
            assertEquals("ADMIN", data.get("role"));
        }

        @Test
        void login_userNotFound_returns401() {
            when(userService.findByUsername("unknown"))
                    .thenThrow(new IllegalArgumentException("User not found"));

            var response = controller.login(
                    new AuthController.LoginRequest("unknown", "pass"));

            assertEquals(401, response.getStatusCode().value());
            assertEquals(401, response.getBody().getCode());
        }

        @Test
        void login_wrongPassword_returns401() {
            User user = buildUser("admin", Role.ADMIN);
            when(userService.findByUsername("admin")).thenReturn(user);
            when(userService.checkPassword("admin", "wrong")).thenReturn(false);

            var response = controller.login(
                    new AuthController.LoginRequest("admin", "wrong"));

            assertEquals(401, response.getStatusCode().value());
            assertEquals(401, response.getBody().getCode());
        }

        @Test
        void login_disabledUser_returns403() {
            User user = buildUser("disabled", Role.VIEWER);
            user.setEnabled(false);
            when(userService.findByUsername("disabled")).thenReturn(user);

            var response = controller.login(
                    new AuthController.LoginRequest("disabled", "pass"));

            assertEquals(403, response.getStatusCode().value());
            assertEquals(403, response.getBody().getCode());
            assertEquals("User is disabled", response.getBody().getMessage());
        }

        @Test
        void login_responseContainsDisplayName() {
            User user = buildUser("admin", Role.ADMIN);
            user.setDisplayName("Administrator");
            when(userService.findByUsername("admin")).thenReturn(user);
            when(userService.checkPassword("admin", "pass")).thenReturn(true);

            var response = controller.login(
                    new AuthController.LoginRequest("admin", "pass"));

            assertEquals("Administrator", response.getBody().getData().get("displayName"));
        }

        @Test
        void login_nullDisplayName_fallsBackToUsername() {
            User user = buildUser("admin", Role.ADMIN);
            user.setDisplayName(null);
            when(userService.findByUsername("admin")).thenReturn(user);
            when(userService.checkPassword("admin", "pass")).thenReturn(true);

            var response = controller.login(
                    new AuthController.LoginRequest("admin", "pass"));

            assertEquals("admin", response.getBody().getData().get("displayName"));
        }

        // ---------- refresh ----------

        @Test
        void refresh_success_returnsNewTokens() {
            String refreshToken = jwtService.generateRefreshToken("admin");
            when(userService.findByUsername("admin")).thenReturn(buildUser("admin", Role.ADMIN));

            var response = controller.refresh(
                    new AuthController.RefreshRequest(refreshToken));

            assertEquals(200, response.getStatusCode().value());
            Map<String, Object> data = response.getBody().getData();
            assertNotNull(data.get("accessToken"));
            assertNotNull(data.get("refreshToken"));
            assertEquals("Bearer", data.get("tokenType"));
        }

        @Test
        void refresh_invalidToken_returns401() {
            var response = controller.refresh(
                    new AuthController.RefreshRequest("invalid-token"));

            assertEquals(401, response.getStatusCode().value());
            assertEquals(401, response.getBody().getCode());
        }

        @Test
        void refresh_userNotFound_returns401() {
            String refreshToken = jwtService.generateRefreshToken("ghost");
            when(userService.findByUsername("ghost"))
                    .thenThrow(new IllegalArgumentException("User not found"));

            var response = controller.refresh(
                    new AuthController.RefreshRequest(refreshToken));

            assertEquals(401, response.getStatusCode().value());
        }

        // ---------- logout ----------

        @Test
        void logout_returnsOk() {
            String refreshToken = jwtService.generateRefreshToken("admin");

            var response = controller.logout(
                    new AuthController.RefreshRequest(refreshToken));

            assertEquals(200, response.getStatusCode().value());
            assertNull(response.getBody().getData());

            // Verify token is revoked
            assertNull(jwtService.validateRefreshToken(refreshToken));
        }

        // ---------- me ----------

        @Test
        void me_validToken_returnsUserInfo() {
            User user = buildUser("admin", Role.ADMIN);
            user.setEmail("admin@test.com");
            String accessToken = jwtService.generateAccessToken("admin", Role.ADMIN);
            when(userService.findByUsername("admin")).thenReturn(user);

            var response = controller.me("Bearer " + accessToken);

            assertEquals(200, response.getStatusCode().value());
            Map<String, Object> data = response.getBody().getData();
            assertEquals("admin", data.get("username"));
            assertEquals("ADMIN", data.get("role"));
            assertEquals(true, data.get("enabled"));
        }

        @Test
        void me_missingToken_returns401() {
            var response = controller.me(null);

            assertEquals(401, response.getStatusCode().value());
            assertEquals("Missing token", response.getBody().getMessage());
        }

        @Test
        void me_invalidAuthHeader_returns401() {
            var response = controller.me("Basic abc123");

            assertEquals(401, response.getStatusCode().value());
            assertEquals("Missing token", response.getBody().getMessage());
        }

        @Test
        void me_invalidToken_returns401() {
            var response = controller.me("Bearer invalid.jwt.token");

            assertEquals(401, response.getStatusCode().value());
            assertEquals("Invalid or expired token", response.getBody().getMessage());
        }

        @Test
        void me_userDeletedAfterTokenIssued_returns401() {
            String accessToken = jwtService.generateAccessToken("deleted", Role.EDITOR);
            when(userService.findByUsername("deleted"))
                    .thenThrow(new IllegalArgumentException("User not found"));

            var response = controller.me("Bearer " + accessToken);

            assertEquals(401, response.getStatusCode().value());
            assertEquals("User not found", response.getBody().getMessage());
        }

        // ---------- createUser ----------

        @Test
        void createUser_success_returnsUserSummary() {
            User user = buildUser("newuser", Role.EDITOR);
            user.setId(10L);
            when(userService.createUser("newuser", "pass", "New User", "new@test.com", Role.EDITOR))
                    .thenReturn(user);

            var response = controller.createUser(
                    new AuthController.CreateUserRequest("newuser", "pass", "New User", "new@test.com", "EDITOR"));

            assertEquals(200, response.getStatusCode().value());
            Map<String, Object> data = response.getBody().getData();
            assertEquals(10L, data.get("id"));
            assertEquals("newuser", data.get("username"));
            assertEquals("EDITOR", data.get("role"));
            assertEquals(true, data.get("enabled"));
        }

        // ---------- listUsers ----------

        @Test
        void listUsers_returnsUserList() {
            User u1 = buildUser("admin", Role.ADMIN);
            u1.setId(1L);
            User u2 = buildUser("editor", Role.EDITOR);
            u2.setId(2L);
            when(userService.listUsers()).thenReturn(List.of(u1, u2));

            var response = controller.listUsers();

            assertEquals(200, response.getStatusCode().value());
            // The data is a List of Maps (returned as Object)
            assertNotNull(response.getBody().getData());
        }

        // ---------- change-password ----------

        @BeforeEach
        void setupSecurityContext() {
            // 设置模拟认证上下文 (changePassword 需要)
            Authentication auth = Mockito.mock(Authentication.class);
            when(auth.getName()).thenReturn("admin");
            var authorities = java.util.List.<org.springframework.security.core.GrantedAuthority>of(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"));
            Mockito.doReturn(authorities).when(auth).getAuthorities();
            SecurityContext ctx = Mockito.mock(SecurityContext.class);
            when(ctx.getAuthentication()).thenReturn(auth);
            SecurityContextHolder.setContext(ctx);
        }

        @AfterEach
        void clearSecurityContext() {
            SecurityContextHolder.clearContext();
        }

        @Test
        void changePassword_success_returnsOk() {
            var response = controller.changePassword(
                    new AuthController.ChangePasswordRequest("admin", "oldPass", "newPass"));

            assertEquals(200, response.getStatusCode().value());
            verify(userService).changePassword("admin", "oldPass", "newPass");
        }

        @Test
        void changePassword_wrongOldPassword_returns400() {
            doThrow(new IllegalArgumentException("Old password is incorrect"))
                    .when(userService).changePassword("admin", "wrongOld", "newPass");

            var response = controller.changePassword(
                    new AuthController.ChangePasswordRequest("admin", "wrongOld", "newPass"));

            assertEquals(400, response.getStatusCode().value());
            assertEquals(400, response.getBody().getCode());
        }

        @Test
        void changePassword_userNotFound_returns400() {
            doThrow(new IllegalArgumentException("User not found"))
                    .when(userService).changePassword("ghost", "old", "new");

            var response = controller.changePassword(
                    new AuthController.ChangePasswordRequest("ghost", "old", "new"));

            assertEquals(400, response.getStatusCode().value());
        }
    }

    // =====================================================================
    //  RuleController Tests
    // =====================================================================

    @Nested
    class RuleControllerTest {

        private RuleAdminService service;
        private RuleController controller;

        @BeforeEach
        void setUp() {
            service = Mockito.mock(RuleAdminService.class);
            controller = new RuleController(service);
        }

        private RuleEntity buildEntity(String id, String name, int version, PublishStatus status) {
            RuleEntity entity = RuleEntity.create(id, name, "RULE", "{\"threshold\":80}");
            entity.setVersion(version);
            entity.setStatus(status);
            entity.setDescription("A test rule");
            entity.setCreatedBy("tester");
            entity.setUpdatedBy("tester");
            return entity;
        }

        // ---------- list ----------

        @Test
        void list_returnsAllRules() {
            RuleEntity r1 = buildEntity("R001", "rule-1", 1, PublishStatus.DRAFT);
            RuleEntity r2 = buildEntity("R002", "rule-2", 1, PublishStatus.TESTING);
            when(service.list("RULE")).thenReturn(List.of(r1, r2));

            var response = controller.list();

            assertEquals(200, response.getStatusCode().value());
            List<Map<String, Object>> data = response.getBody().getData();
            assertEquals(2, data.size());

            verify(service).list("RULE");
        }

        @Test
        void list_empty_returnsOkEmpty() {
            when(service.list("RULE")).thenReturn(List.of());

            var response = controller.list();

            assertEquals(200, response.getStatusCode().value());
            assertTrue(response.getBody().getData().isEmpty());
        }

        // ---------- create ----------

        @Test
        void create_returnsCreatedRuleSummary() {
            RuleEntity entity = buildEntity("R001", "new-rule", 1, PublishStatus.DRAFT);
            when(service.create("RULE", "new-rule", "{\"threshold\":80}", "A test rule"))
                    .thenReturn(entity);

            var response = controller.create(
                    new RuleController.CreateRequest("new-rule", "{\"threshold\":80}", "A test rule"));

            assertEquals(200, response.getStatusCode().value());
            Map<String, Object> data = response.getBody().getData();
            assertEquals("R001", data.get("id"));
            assertEquals("new-rule", data.get("name"));
            assertEquals("DRAFT", data.get("status"));

            verify(service).create("RULE", "new-rule", "{\"threshold\":80}", "A test rule");
        }

        // ---------- get ----------

        @Test
        void get_returnsLatestVersion() {
            RuleEntity entity = buildEntity("R001", "test-rule", 2, PublishStatus.DRAFT);
            when(service.getLatest("RULE", "R001")).thenReturn(entity);

            var response = controller.get("R001");

            assertEquals(200, response.getStatusCode().value());
            RuleEntity data = response.getBody().getData();
            assertEquals("R001", data.getId());
            assertEquals(2, data.getVersion());
        }

        @Test
        void get_notFound_throwsException() {
            when(service.getLatest("RULE", "R999"))
                    .thenThrow(new IllegalArgumentException("RULE not found: R999"));

            assertThrows(IllegalArgumentException.class, () -> controller.get("R999"));
        }

        // ---------- update ----------

        @Test
        void update_returnsUpdatedSummary() {
            RuleEntity entity = buildEntity("R001", "test-rule", 1, PublishStatus.DRAFT);
            when(service.update("RULE", "R001", "{\"threshold\":90}")).thenReturn(entity);

            var response = controller.update("R001",
                    new RuleController.UpdateRequest("{\"threshold\":90}"));

            assertEquals(200, response.getStatusCode().value());
            verify(service).update("RULE", "R001", "{\"threshold\":90}");
        }

        @Test
        void update_nonDraftStatus_throwsException() {
            when(service.update("RULE", "R001", "content"))
                    .thenThrow(new IllegalStateException("Cannot update rule in TESTING status"));

            assertThrows(IllegalStateException.class, () ->
                    controller.update("R001", new RuleController.UpdateRequest("content")));
        }

        // ---------- delete ----------

        @Test
        void delete_returnsOk() {
            var response = controller.delete("R001", 1);

            assertEquals(200, response.getStatusCode().value());
            assertNull(response.getBody().getData());

            verify(service).delete("RULE", "R001", 1);
        }

        // ---------- versions ----------

        @Test
        void versions_returnsAllVersions() {
            RuleEntity v1 = buildEntity("R001", "test-rule", 1, PublishStatus.RELEASED);
            RuleEntity v2 = buildEntity("R001", "test-rule", 2, PublishStatus.DRAFT);
            when(service.listVersions("RULE", "R001")).thenReturn(List.of(v1, v2));

            var response = controller.versions("R001");

            assertEquals(200, response.getStatusCode().value());
            List<Map<String, Object>> data = response.getBody().getData();
            assertEquals(2, data.size());
        }

        // ---------- newVersion ----------

        @Test
        void newVersion_returnsNewVersionSummary() {
            RuleEntity v2 = buildEntity("R001", "test-rule", 2, PublishStatus.DRAFT);
            when(service.createNewVersion("RULE", "R001")).thenReturn(v2);

            var response = controller.newVersion("R001");

            assertEquals(200, response.getStatusCode().value());
            assertEquals(2, response.getBody().getData().get("version"));
            assertEquals("DRAFT", response.getBody().getData().get("status"));
        }
    }
}
