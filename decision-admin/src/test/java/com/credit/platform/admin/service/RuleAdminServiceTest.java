package com.credit.platform.admin.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.credit.platform.admin.model.PublishStatus;
import com.credit.platform.admin.model.RuleEntity;

/**
 * 规则管理服务测试。
 */
@DisplayName("RuleAdminService CRUD + 发布流程")
class RuleAdminServiceTest {

    private RuleAdminService service;

    @BeforeEach
    void setUp() {
        service = new RuleAdminService(new RuleRepository());
    }

    @Nested
    @DisplayName("CRUD")
    class CrudTest {

        @Test
        @DisplayName("1. 创建规则")
        void create_rule() {
            RuleEntity e = service.create("RULE", "黑名单规则", "{}", "测试");
            assertNotNull(e.getId());
            assertEquals("黑名单规则", e.getName());
            assertEquals(1, e.getVersion());
            assertEquals(PublishStatus.DRAFT, e.getStatus());
        }

        @Test
        @DisplayName("2. 列出和查询")
        void listAndGet() {
            service.create("RULE", "R1", "{}", null);
            service.create("RULE", "R2", "{}", null);

            List<RuleEntity> list = service.list("RULE");
            assertEquals(2, list.size());

            List<RuleEntity> scorecards = service.list("SCORECARD");
            assertTrue(scorecards.isEmpty());
        }

        @Test
        @DisplayName("3. 更新和版本历史")
        void updateAndVersions() {
            RuleEntity e = service.create("SCORECARD", "SC1", "{\"v\":1}", null);
            service.update("SCORECARD", e.getId(), "{\"v\":2}");
            service.createNewVersion("SCORECARD", e.getId());

            List<RuleEntity> versions = service.listVersions("SCORECARD", e.getId());
            assertEquals(2, versions.size());
        }

        @Test
        @DisplayName("4. 删除")
        void delete() {
            RuleEntity e = service.create("FLOW", "F1", "{}", null);
            assertTrue(service.delete("FLOW", e.getId(), 1));
            assertThrows(IllegalArgumentException.class, () -> service.getLatest("FLOW", e.getId()));
        }
    }

    @Nested
    @DisplayName("发布流程")
    class PublishTest {

        @Test
        @DisplayName("5. DRAFT → TESTING → PENDING_REVIEW → APPROVED → GRAYSCALE → RELEASED")
        void promote_fullLifecycle() {
            RuleEntity e = service.create("RULE", "R1", "{}", null);
            assertEquals(PublishStatus.DRAFT, e.getStatus());

            e = service.promote("RULE", e.getId());
            assertEquals(PublishStatus.TESTING, e.getStatus());

            e = service.promote("RULE", e.getId());
            assertEquals(PublishStatus.PENDING_REVIEW, e.getStatus());

            e = service.promote("RULE", e.getId());
            assertEquals(PublishStatus.APPROVED, e.getStatus());

            e = service.promote("RULE", e.getId());
            assertEquals(PublishStatus.GRAYSCALE, e.getStatus());

            e = service.promote("RULE", e.getId());
            assertEquals(PublishStatus.RELEASED, e.getStatus());

            final String releasedId = e.getId();
            assertThrows(IllegalStateException.class, () -> service.promote("RULE", releasedId));
        }

        @Test
        @DisplayName("6. 驳回回到 DRAFT")
        void reject_backToDraft() {
            RuleEntity e = service.create("RULE", "R1", "{}", null);
            service.promote("RULE", e.getId()); // TESTING
            service.promote("RULE", e.getId()); // PENDING_REVIEW

            e = service.reject("RULE", e.getId(), "逻辑有误");
            assertEquals(PublishStatus.DRAFT, e.getStatus());
            assertEquals("逻辑有误", e.getAttributes().get("rejectReason"));
        }

        @Test
        @DisplayName("7. 回滚创建新版本")
        void rollback_createsNewVersion() {
            RuleEntity e = service.create("RULE", "R1", "{\"v\":1}", null);
            service.createNewVersion("RULE", e.getId()); // v2
            service.update("RULE", e.getId(), "{\"v\":3}");

            RuleEntity rolled = service.rollback("RULE", e.getId(), 1);
            assertEquals(3, rolled.getVersion());
            assertEquals(PublishStatus.DRAFT, rolled.getStatus());
        }
    }
}
