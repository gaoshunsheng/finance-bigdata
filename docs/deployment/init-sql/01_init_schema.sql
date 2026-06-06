-- ============================================================================
-- MySQL auto-init script for Docker Compose
-- Mounted at: ./init-sql:/docker-entrypoint-initdb.d
--
-- This file is executed automatically when MySQL container starts for the first
-- time. It creates the credit_platform database and all required tables.
-- ============================================================================

-- Ensure database exists
CREATE DATABASE IF NOT EXISTS `credit_platform` DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `credit_platform`;

-- ---------------------------------------------------------------------------
-- 1. rule_entity — 规则实体表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `rule_entity` (
    `id`          VARCHAR(64)   NOT NULL COMMENT '规则实体 ID',
    `name`        VARCHAR(128)  NOT NULL COMMENT '规则名称',
    `type`        VARCHAR(32)   NOT NULL COMMENT '类型: RULE / SCORECARD / DECISION_TABLE / DECISION_TREE / FLOW / VARIABLE',
    `version`     INT           NOT NULL COMMENT '版本号，从 1 开始递增',
    `status`      VARCHAR(32)   NOT NULL DEFAULT 'DRAFT' COMMENT '发布状态',
    `content`     MEDIUMTEXT    NULL     COMMENT '规则 JSON 定义内容',
    `description` VARCHAR(512)  NULL     COMMENT '规则描述',
    `created_by`  VARCHAR(64)   NULL     COMMENT '创建人',
    `updated_by`  VARCHAR(64)   NULL     COMMENT '最后修改人',
    `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `attributes`  JSON          NULL     COMMENT '扩展属性',

    PRIMARY KEY (`type`, `id`, `version`),
    UNIQUE INDEX `uk_rule_entity_type_id_version` (`type`, `id`, `version`),
    INDEX `idx_rule_entity_type_status` (`type`, `status`),
    INDEX `idx_rule_entity_created_by` (`created_by`),
    INDEX `idx_rule_entity_updated_at` (`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='规则实体表';

-- ---------------------------------------------------------------------------
-- 2. sys_user — 系统用户表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_user` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '用户 ID',
    `username`      VARCHAR(64)   NOT NULL COMMENT '登录用户名',
    `password`      VARCHAR(256)  NOT NULL COMMENT 'BCrypt 加密密码',
    `display_name`  VARCHAR(128)  NULL     COMMENT '显示名称',
    `email`         VARCHAR(128)  NULL     COMMENT '邮箱地址',
    `role`          VARCHAR(32)   NOT NULL DEFAULT 'VIEWER' COMMENT '角色',
    `enabled`       TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '是否启用',
    `created_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `last_login_at` DATETIME      NULL     COMMENT '最后登录时间',

    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_sys_user_username` (`username`),
    INDEX `idx_sys_user_role` (`role`),
    INDEX `idx_sys_user_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

-- Insert default admin user (password: admin123 — BCrypt encoded)
INSERT IGNORE INTO `sys_user` (`username`, `password`, `display_name`, `role`, `enabled`)
VALUES ('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '系统管理员', 'ADMIN', 1);

-- ---------------------------------------------------------------------------
-- 3. audit_log — 审计日志表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `audit_log` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '日志 ID',
    `operator`        VARCHAR(64)   NOT NULL COMMENT '操作人',
    `action`          VARCHAR(32)   NOT NULL COMMENT '操作类型',
    `target_type`     VARCHAR(32)   NULL     COMMENT '目标类型',
    `target_id`       VARCHAR(64)   NULL     COMMENT '目标 ID',
    `target_version`  INT           NULL     COMMENT '目标版本号',
    `before_snapshot` MEDIUMTEXT    NULL     COMMENT '变更前快照 (JSON)',
    `after_snapshot`  MEDIUMTEXT    NULL     COMMENT '变更后快照 (JSON)',
    `details`         TEXT          NULL     COMMENT '操作详情描述',
    `ip_address`      VARCHAR(45)   NULL     COMMENT '操作来源 IP 地址',
    `operated_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',

    PRIMARY KEY (`id`),
    INDEX `idx_audit_log_operator` (`operator`),
    INDEX `idx_audit_log_target` (`target_type`, `target_id`),
    INDEX `idx_audit_log_action` (`action`),
    INDEX `idx_audit_log_operated_at` (`operated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审计日志表';

-- ---------------------------------------------------------------------------
-- 4. approval_record — 审批记录表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `approval_record` (
    `record_id`       VARCHAR(64)   NOT NULL COMMENT '审批记录 ID',
    `target_type`     VARCHAR(32)   NOT NULL COMMENT '关联规则类型',
    `target_id`       VARCHAR(64)   NOT NULL COMMENT '关联规则 ID',
    `target_version`  INT           NOT NULL COMMENT '关联规则版本号',
    `action`          VARCHAR(16)   NOT NULL COMMENT '操作类型',
    `operator`        VARCHAR(64)   NOT NULL COMMENT '操作人',
    `comment`         TEXT          NULL     COMMENT '审批意见/原因',
    `operated_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',

    PRIMARY KEY (`record_id`),
    INDEX `idx_approval_record_target` (`target_type`, `target_id`),
    INDEX `idx_approval_record_operator` (`operator`),
    INDEX `idx_approval_record_operated_at` (`operated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审批记录表';

-- ---------------------------------------------------------------------------
-- 5. grayscale_config — 灰度发布配置表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `grayscale_config` (
    `config_id`            VARCHAR(64)   NOT NULL COMMENT '灰度配置 ID',
    `target_type`          VARCHAR(32)   NOT NULL COMMENT '关联规则类型',
    `target_id`            VARCHAR(64)   NOT NULL COMMENT '关联规则 ID',
    `target_version`       INT           NOT NULL COMMENT '关联规则版本号',
    `percentage`           INT           NOT NULL DEFAULT 0 COMMENT '当前灰度百分比',
    `previous_percentage`  INT           NOT NULL DEFAULT 0 COMMENT '上一次灰度百分比',
    `operator`             VARCHAR(64)   NULL     COMMENT '操作人',
    `started_at`           DATETIME      NULL     COMMENT '灰度开始时间',
    `updated_at`           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后调整时间',
    `grayscale_status`     VARCHAR(16)   NOT NULL DEFAULT 'NOT_STARTED' COMMENT '灰度状态',

    PRIMARY KEY (`config_id`),
    INDEX `idx_grayscale_config_target` (`target_type`, `target_id`),
    INDEX `idx_grayscale_config_status` (`grayscale_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='灰度发布配置表';

-- ---------------------------------------------------------------------------
-- 6. DolphinScheduler database
-- ---------------------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS `dolphinscheduler` DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 7. Hive Metastore database
-- ---------------------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS `hive_metastore` DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 8. Model Platform database
-- ---------------------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS `model_platform` DEFAULT CHARSET utf8mb4 COLLATE=utf8mb4_unicode_ci;
