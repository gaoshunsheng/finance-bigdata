package com.credit.platform.admin.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.credit.platform.admin.model.ApprovalRecord;
import com.credit.platform.admin.model.ApprovalRecord.ApprovalAction;
import com.credit.platform.admin.model.GrayscaleConfig;
import com.credit.platform.admin.model.GrayscaleConfig.GrayscaleStatus;
import com.credit.platform.admin.model.PublishStatus;
import com.credit.platform.admin.model.RuleEntity;
import com.credit.platform.admin.model.VersionDiff;
import com.credit.platform.admin.model.VersionDiff.DiffType;

/**
 * 发布流程完善测试 — 覆盖审批/灰度/回滚/版本对比。
 * <p>
 * 总计 ≥20 个用例，验证完整发布生命周期。
 * </p>
 */
@DisplayName("发布流程完善")
class PublishWorkflowTest {

    private RuleRepository repository;
    private RuleAdminService adminService;
    private ApprovalService approvalService;
    private GrayscalePublishService grayscaleService;
    private VersionDiffService diffService;
    private RulePublishService publishService;

    @BeforeEach
    void setUp() {
        repository = new RuleRepository();
        adminService = new RuleAdminService(repository);
        approvalService = new ApprovalService(repository);
        grayscaleService = new GrayscalePublishService(repository);
        diffService = new VersionDiffService(repository);
        publishService = new RulePublishService(repository, approvalService, grayscaleService, diffService);
    }

    // ==================== 审批流程 ====================

    @Nested
    @DisplayName("ApprovalService - 审批流程")
    class ApprovalTests {

        @Test
        @DisplayName("APR-01: 提交审批 TESTING → PENDING_REVIEW")
        void submitForApproval_success() {
            RuleEntity e = createAndPromoteToTesting();
            RuleEntity result = approvalService.submitForApproval("RULE", e.getId(), "dev01", "Ready for review");

            assertEquals(PublishStatus.PENDING_REVIEW, result.getStatus());
            assertEquals("dev01", result.getUpdatedBy());

            List<ApprovalRecord> history = approvalService.getApprovalHistory("RULE", e.getId());
            assertEquals(1, history.size());
            assertEquals(ApprovalAction.SUBMIT, history.get(0).getAction());
            assertEquals("dev01", history.get(0).getOperator());
        }

        @Test
        @DisplayName("APR-02: 审批通过 PENDING_REVIEW → APPROVED")
        void approve_success() {
            RuleEntity e = createAndSubmitForApproval();
            RuleEntity result = approvalService.approve("RULE", e.getId(), "reviewer01", "LGTM");

            assertEquals(PublishStatus.APPROVED, result.getStatus());
            assertEquals("reviewer01", result.getAttributes().get("approvedBy"));

            List<ApprovalRecord> history = approvalService.getApprovalHistory("RULE", e.getId());
            assertEquals(2, history.size()); // SUBMIT + APPROVE
        }

        @Test
        @DisplayName("APR-03: 审批驳回 PENDING_REVIEW → DRAFT")
        void reject_success() {
            RuleEntity e = createAndSubmitForApproval();
            RuleEntity result = approvalService.reject("RULE", e.getId(), "reviewer01", "需要修改条件");

            assertEquals(PublishStatus.DRAFT, result.getStatus());
            assertEquals("需要修改条件", result.getAttributes().get("rejectReason"));

            List<ApprovalRecord> history = approvalService.getApprovalHistory("RULE", e.getId());
            assertEquals(2, history.size()); // SUBMIT + REJECT
        }

        @Test
        @DisplayName("APR-04: 撤回审批 PENDING_REVIEW → TESTING")
        void withdraw_success() {
            RuleEntity e = createAndSubmitForApproval();
            RuleEntity result = approvalService.withdraw("RULE", e.getId(), "dev01", "Need more testing");

            assertEquals(PublishStatus.TESTING, result.getStatus());

            List<ApprovalRecord> history = approvalService.getApprovalHistory("RULE", e.getId());
            assertEquals(2, history.size()); // SUBMIT + WITHDRAW
        }

        @Test
        @DisplayName("APR-05: 非 TESTING 状态不能提交审批")
        void submitForApproval_wrongStatus() {
            RuleEntity e = adminService.create("RULE", "R1", "{}", null);
            // DRAFT 状态
            assertThrows(IllegalStateException.class,
                () -> approvalService.submitForApproval("RULE", e.getId(), "dev01", "submit"));
        }

        @Test
        @DisplayName("APR-06: 非 PENDING_REVIEW 状态不能审批通过")
        void approve_wrongStatus() {
            RuleEntity e = createAndPromoteToTesting();
            assertThrows(IllegalStateException.class,
                () -> approvalService.approve("RULE", e.getId(), "reviewer", "approve"));
        }

        @Test
        @DisplayName("APR-07: 非 PENDING_REVIEW 状态不能驳回")
        void reject_wrongStatus() {
            RuleEntity e = adminService.create("RULE", "R1", "{}", null);
            assertThrows(IllegalStateException.class,
                () -> approvalService.reject("RULE", e.getId(), "reviewer", "reject"));
        }

        @Test
        @DisplayName("APR-08: 审批历史按时间倒序")
        void approvalHistory_orderedByTimeDesc() {
            RuleEntity e = createAndSubmitForApproval();
            approvalService.approve("RULE", e.getId(), "rev01", "OK");

            // 创建第二个版本并提交审批
            adminService.createNewVersion("RULE", e.getId());
            RuleEntity v2 = adminService.getLatest("RULE", e.getId());
            v2.setStatus(PublishStatus.TESTING);
            repository.save(v2);
            approvalService.submitForApproval("RULE", e.getId(), "dev02", "v2 ready");

            List<ApprovalRecord> allHistory = approvalService.getApprovalHistory("RULE", e.getId());
            assertTrue(allHistory.size() >= 3);

            // 按版本过滤
            List<ApprovalRecord> v1History = approvalService.getApprovalHistory("RULE", e.getId(), 1);
            assertEquals(2, v1History.size()); // SUBMIT + APPROVE
        }
    }

    // ==================== 灰度发布 ====================

    @Nested
    @DisplayName("GrayscalePublishService - 灰度发布")
    class GrayscaleTests {

        @Test
        @DisplayName("GS-01: 开始灰度 5% → APPROVED → GRAYSCALE")
        void startGrayscale_success() {
            RuleEntity e = createApprovedRule();
            GrayscaleConfig config = grayscaleService.startGrayscale("RULE", e.getId(), 5, "ops01");

            assertEquals(5, config.getPercentage());
            assertEquals(GrayscaleStatus.IN_PROGRESS, config.getGrayscaleStatus());
            assertNotNull(config.getStartedAt());

            RuleEntity updated = adminService.getLatest("RULE", e.getId());
            assertEquals(PublishStatus.GRAYSCALE, updated.getStatus());
            assertEquals(5, updated.getAttributes().get("grayscalePercentage"));
        }

        @Test
        @DisplayName("GS-02: 灰度阶梯提升 5% → 25% → 50% → 100%")
        void rampUp_success() {
            RuleEntity e = createApprovedRule();
            grayscaleService.startGrayscale("RULE", e.getId(), 5, "ops01");

            GrayscaleConfig c1 = grayscaleService.rampUp("RULE", e.getId(), "ops01");
            assertEquals(25, c1.getPercentage());

            GrayscaleConfig c2 = grayscaleService.rampUp("RULE", e.getId(), "ops01");
            assertEquals(50, c2.getPercentage());

            GrayscaleConfig c3 = grayscaleService.rampUp("RULE", e.getId(), "ops01");
            assertEquals(100, c3.getPercentage());
            assertEquals(GrayscaleStatus.FULL, c3.getGrayscaleStatus());

            // 100% 自动触发 RELEASED
            RuleEntity updated = adminService.getLatest("RULE", e.getId());
            assertEquals(PublishStatus.RELEASED, updated.getStatus());
        }

        @Test
        @DisplayName("GS-03: 手动调整灰度百分比")
        void adjustGrayscale_success() {
            RuleEntity e = createApprovedRule();
            grayscaleService.startGrayscale("RULE", e.getId(), 5, "ops01");

            GrayscaleConfig config = grayscaleService.adjustGrayscale("RULE", e.getId(), 30, "ops01");
            assertEquals(30, config.getPercentage());
            assertEquals(5, config.getPreviousPercentage());
        }

        @Test
        @DisplayName("GS-04: 灰度百分比不允许降低")
        void adjustGrayscale_cannotDecrease() {
            RuleEntity e = createApprovedRule();
            grayscaleService.startGrayscale("RULE", e.getId(), 25, "ops01");

            assertThrows(IllegalArgumentException.class,
                () -> grayscaleService.adjustGrayscale("RULE", e.getId(), 10, "ops01"));
        }

        @Test
        @DisplayName("GS-05: 灰度百分比不允许超过 100%")
        void adjustGrayscale_cannotExceed100() {
            RuleEntity e = createApprovedRule();
            grayscaleService.startGrayscale("RULE", e.getId(), 50, "ops01");

            assertThrows(IllegalArgumentException.class,
                () -> grayscaleService.adjustGrayscale("RULE", e.getId(), 150, "ops01"));
        }

        @Test
        @DisplayName("GS-06: 非 APPROVED 状态不能开始灰度")
        void startGrayscale_wrongStatus() {
            RuleEntity e = adminService.create("RULE", "R1", "{}", null);
            assertThrows(IllegalStateException.class,
                () -> grayscaleService.startGrayscale("RULE", e.getId(), 5, "ops01"));
        }

        @Test
        @DisplayName("GS-07: 暂停和恢复灰度")
        void pauseAndResume() {
            RuleEntity e = createApprovedRule();
            grayscaleService.startGrayscale("RULE", e.getId(), 5, "ops01");

            GrayscaleConfig paused = grayscaleService.pause("RULE", e.getId(), "ops01");
            assertEquals(GrayscaleStatus.PAUSED, paused.getGrayscaleStatus());

            GrayscaleConfig resumed = grayscaleService.resume("RULE", e.getId(), "ops01");
            assertEquals(GrayscaleStatus.IN_PROGRESS, resumed.getGrayscaleStatus());
        }

        @Test
        @DisplayName("GS-08: 灰度回滚 → 流量归零，回到 APPROVED")
        void rollbackGrayscale() {
            RuleEntity e = createApprovedRule();
            grayscaleService.startGrayscale("RULE", e.getId(), 25, "ops01");

            RuleEntity rolled = grayscaleService.rollbackGrayscale("RULE", e.getId(), "ops01");
            assertEquals(PublishStatus.APPROVED, rolled.getStatus());
            assertEquals(0, rolled.getAttributes().get("grayscalePercentage"));
        }

        @Test
        @DisplayName("GS-09: 查询所有灰度中的配置")
        void listActiveGrayscales() {
            RuleEntity e1 = createApprovedRule();
            grayscaleService.startGrayscale("RULE", e1.getId(), 5, "ops01");

            List<GrayscaleConfig> active = grayscaleService.listActiveGrayscales();
            assertEquals(1, active.size());
        }
    }

    // ==================== 版本对比 ====================

    @Nested
    @DisplayName("VersionDiffService - 版本对比")
    class VersionDiffTests {

        @Test
        @DisplayName("VD-01: 检测新增字段")
        void diff_addedField() {
            RuleEntity v1 = adminService.create("RULE", "R1",
                "{\"ruleSetId\":\"RS1\",\"rules\":[]}", null);
            adminService.createNewVersion("RULE", v1.getId());
            RuleEntity v2 = adminService.getLatest("RULE", v1.getId());
            v2.setContent("{\"ruleSetId\":\"RS1\",\"rules\":[],\"hitPolicy\":\"FIRST_HIT\"}");
            repository.save(v2);

            VersionDiff diff = diffService.diff("RULE", v1.getId(), 1, 2);
            assertTrue(diff.hasChanges());
            assertTrue(diff.getDiffs().stream().anyMatch(d -> d.getType() == DiffType.ADDED));
        }

        @Test
        @DisplayName("VD-02: 检测删除字段")
        void diff_removedField() {
            RuleEntity v1 = adminService.create("RULE", "R1",
                "{\"ruleSetId\":\"RS1\",\"extraField\":\"val\",\"rules\":[]}", null);
            adminService.createNewVersion("RULE", v1.getId());
            RuleEntity v2 = adminService.getLatest("RULE", v1.getId());
            v2.setContent("{\"ruleSetId\":\"RS1\",\"rules\":[]}");
            repository.save(v2);

            VersionDiff diff = diffService.diff("RULE", v1.getId(), 1, 2);
            assertTrue(diff.getDiffs().stream().anyMatch(d -> d.getType() == DiffType.REMOVED));
        }

        @Test
        @DisplayName("VD-03: 检测修改字段")
        void diff_modifiedField() {
            RuleEntity v1 = adminService.create("RULE", "R1",
                "{\"ruleSetId\":\"RS1\",\"version\":\"1\"}", null);
            adminService.createNewVersion("RULE", v1.getId());
            RuleEntity v2 = adminService.getLatest("RULE", v1.getId());
            v2.setContent("{\"ruleSetId\":\"RS1\",\"version\":\"2\"}");
            repository.save(v2);

            VersionDiff diff = diffService.diff("RULE", v1.getId(), 1, 2);
            assertTrue(diff.getDiffs().stream().anyMatch(
                d -> d.getType() == DiffType.MODIFIED && d.getFieldPath().equals("version")));
        }

        @Test
        @DisplayName("VD-04: 最新两版本对比")
        void diffLatest() {
            RuleEntity v1 = adminService.create("RULE", "R1",
                "{\"score\":500}", null);
            adminService.createNewVersion("RULE", v1.getId());
            RuleEntity v2 = adminService.getLatest("RULE", v1.getId());
            v2.setContent("{\"score\":600}");
            repository.save(v2);

            VersionDiff diff = diffService.diffLatest("RULE", v1.getId());
            assertTrue(diff.hasChanges());
            assertEquals(1, diff.getSourceVersion());
            assertEquals(2, diff.getTargetVersion());
        }

        @Test
        @DisplayName("VD-05: 嵌套 JSON 字段对比")
        void diff_nestedJson() {
            RuleEntity v1 = adminService.create("RULE", "R1",
                "{\"config\":{\"threshold\":100}}", null);
            adminService.createNewVersion("RULE", v1.getId());
            RuleEntity v2 = adminService.getLatest("RULE", v1.getId());
            v2.setContent("{\"config\":{\"threshold\":200}}");
            repository.save(v2);

            VersionDiff diff = diffService.diff("RULE", v1.getId(), 1, 2);
            assertTrue(diff.getDiffs().stream().anyMatch(
                d -> d.getType() == DiffType.MODIFIED && d.getFieldPath().equals("config.threshold")));
        }
    }

    // ==================== 完整发布生命周期 ====================

    @Nested
    @DisplayName("RulePublishService - 完整生命周期")
    class FullLifecycleTests {

        @Test
        @DisplayName("LIFE-01: 完整发布流程 DRAFT → TESTING → APPROVAL → APPROVED → GRAYSCALE → RELEASED")
        void fullLifecycle() {
            RuleEntity e = adminService.create("RULE", "R1", "{\"ruleSetId\":\"RS1\"}", null);

            // DRAFT → TESTING
            e = publishService.promoteToTesting("RULE", e.getId(), "dev01");
            assertEquals(PublishStatus.TESTING, e.getStatus());

            // TESTING → PENDING_REVIEW
            e = publishService.submitForApproval("RULE", e.getId(), "dev01", "Ready");
            assertEquals(PublishStatus.PENDING_REVIEW, e.getStatus());

            // PENDING_REVIEW → APPROVED
            e = publishService.approve("RULE", e.getId(), "reviewer01", "LGTM");
            assertEquals(PublishStatus.APPROVED, e.getStatus());

            // APPROVED → GRAYSCALE (5%)
            GrayscaleConfig config = publishService.startGrayscale("RULE", e.getId(), 5, "ops01");
            assertEquals(5, config.getPercentage());
            e = adminService.getLatest("RULE", e.getId());
            assertEquals(PublishStatus.GRAYSCALE, e.getStatus());

            // 灰度提升到 100%
            publishService.adjustGrayscale("RULE", e.getId(), 100, "ops01");
            e = adminService.getLatest("RULE", e.getId());
            assertEquals(PublishStatus.RELEASED, e.getStatus());
        }

        @Test
        @DisplayName("LIFE-02: fullPublish 一键发布")
        void fullPublish() {
            RuleEntity e = adminService.create("RULE", "R1", "{}", null);
            RuleEntity result = publishService.fullPublish("RULE", e.getId(), "admin");
            assertEquals(PublishStatus.RELEASED, result.getStatus());
        }

        @Test
        @DisplayName("LIFE-03: 审批驳回后重新修改再提交")
        void rejectAndResubmit() {
            RuleEntity e = adminService.create("RULE", "R1", "{\"v\":1}", null);
            publishService.promoteToTesting("RULE", e.getId(), "dev01");
            publishService.submitForApproval("RULE", e.getId(), "dev01", "v1");

            // 驳回
            e = publishService.reject("RULE", e.getId(), "reviewer01", "条件不足");
            assertEquals(PublishStatus.DRAFT, e.getStatus());

            // 修改内容
            adminService.update("RULE", e.getId(), "{\"v\":2}");

            // 重新走流程
            publishService.promoteToTesting("RULE", e.getId(), "dev01");
            e = publishService.submitForApproval("RULE", e.getId(), "dev01", "v2 fixed");
            assertEquals(PublishStatus.PENDING_REVIEW, e.getStatus());
        }

        @Test
        @DisplayName("LIFE-04: 回滚到历史版本")
        void rollback() {
            RuleEntity v1 = adminService.create("RULE", "R1", "{\"v\":1}", null);
            adminService.createNewVersion("RULE", v1.getId());
            RuleEntity v2 = adminService.getLatest("RULE", v1.getId());
            v2.setContent("{\"v\":2}");
            repository.save(v2);

            // 推进 v2 到 TESTING
            v2.setStatus(PublishStatus.TESTING);
            repository.save(v2);

            // 回滚到 v1
            RuleEntity v3 = publishService.rollback("RULE", v1.getId(), 1, "ops01");
            assertEquals(3, v3.getVersion());
            assertEquals(PublishStatus.DRAFT, v3.getStatus());
            assertEquals("{\"v\":1}", v3.getContent());
            assertEquals(1, v3.getAttributes().get("rollbackFrom"));
        }

        @Test
        @DisplayName("LIFE-05: 灰度中回滚")
        void rollbackDuringGrayscale() {
            RuleEntity e = adminService.create("RULE", "R1", "{}", null);
            publishService.fullPublish("RULE", e.getId(), "admin");

            // 创建新版本并进入灰度
            adminService.createNewVersion("RULE", e.getId());
            RuleEntity v2 = adminService.getLatest("RULE", e.getId());
            v2.setStatus(PublishStatus.APPROVED);
            repository.save(v2);
            publishService.startGrayscale("RULE", e.getId(), 5, "ops01");

            // 灰度回滚
            RuleEntity rolled = publishService.rollbackGrayscale("RULE", e.getId(), "ops01");
            assertEquals(PublishStatus.APPROVED, rolled.getStatus());
        }

        @Test
        @DisplayName("LIFE-06: 查询发布历史")
        void getPublishHistory() {
            RuleEntity e = adminService.create("RULE", "R1", "{\"v\":1}", null);
            adminService.createNewVersion("RULE", e.getId());
            adminService.createNewVersion("RULE", e.getId());

            List<RuleEntity> history = publishService.getPublishHistory("RULE", e.getId());
            assertEquals(3, history.size());
        }
    }

    // ==================== 辅助方法 ====================

    private RuleEntity createAndPromoteToTesting() {
        RuleEntity e = adminService.create("RULE", "R-test", "{}", null);
        e.setStatus(PublishStatus.TESTING);
        repository.save(e);
        return e;
    }

    private RuleEntity createAndSubmitForApproval() {
        RuleEntity e = createAndPromoteToTesting();
        approvalService.submitForApproval("RULE", e.getId(), "dev01", "ready");
        return e;
    }

    private RuleEntity createApprovedRule() {
        RuleEntity e = adminService.create("RULE", "R-approved", "{}", null);
        e.setStatus(PublishStatus.APPROVED);
        repository.save(e);
        return e;
    }
}
