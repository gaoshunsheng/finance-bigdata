-- ============================================================================
-- Flyway migration: V1__init_schema.sql
-- Decision-Admin 初始化数据库脚本
-- 包含: rule_entity, sys_user, audit_log, approval_record, grayscale_config
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. rule_entity — 规则实体表
-- 存储规则/评分卡/决策表/决策树/决策流/变量的版本化定义
-- 主键为 (type, id, version) 复合主键，与内存存储 type:id:version 键对应
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `rule_entity` (
    `id`          VARCHAR(64)   NOT NULL COMMENT '规则实体 ID',
    `name`        VARCHAR(128)  NOT NULL COMMENT '规则名称',
    `type`        VARCHAR(32)   NOT NULL COMMENT '类型: RULE / SCORECARD / DECISION_TABLE / DECISION_TREE / FLOW / VARIABLE',
    `version`     INT           NOT NULL COMMENT '版本号，从 1 开始递增',
    `status`      VARCHAR(32)   NOT NULL DEFAULT 'DRAFT' COMMENT '发布状态: DRAFT / TESTING / PENDING_REVIEW / APPROVED / GRAYSCALE / RELEASED / ROLLED_BACK',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='规则实体表 — 存储所有规则资产的版本化定义';


-- ---------------------------------------------------------------------------
-- 2. sys_user — 系统用户表
-- 基于 RBAC 四级角色: VIEWER / EDITOR / APPROVER / ADMIN
-- 密码使用 BCrypt 加密存储
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_user` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '用户 ID',
    `username`      VARCHAR(64)   NOT NULL COMMENT '登录用户名',
    `password`      VARCHAR(256)  NOT NULL COMMENT 'BCrypt 加密密码',
    `display_name`  VARCHAR(128)  NULL     COMMENT '显示名称',
    `email`         VARCHAR(128)  NULL     COMMENT '邮箱地址',
    `role`          VARCHAR(32)   NOT NULL DEFAULT 'VIEWER' COMMENT '角色: VIEWER / EDITOR / APPROVER / ADMIN',
    `enabled`       TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '是否启用: 1-启用 0-禁用',
    `created_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `last_login_at` DATETIME      NULL     COMMENT '最后登录时间',

    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_sys_user_username` (`username`),
    INDEX `idx_sys_user_role` (`role`),
    INDEX `idx_sys_user_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表 — 支持 RBAC 四级角色权限';


-- ---------------------------------------------------------------------------
-- 3. audit_log — 审计日志表
-- 记录所有配置变更操作，保留 5 年，不可修改/删除
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `audit_log` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '日志 ID',
    `operator`        VARCHAR(64)   NOT NULL COMMENT '操作人',
    `action`          VARCHAR(32)   NOT NULL COMMENT '操作类型: CREATE / UPDATE / DELETE / PUBLISH / APPROVE / REJECT / ROLLBACK / GRAYSCALE / LOGIN',
    `target_type`     VARCHAR(32)   NULL     COMMENT '目标类型: RULE / SCORECARD / DECISION_TABLE / DECISION_TREE / FLOW / VARIABLE / USER',
    `target_id`       VARCHAR(64)   NULL     COMMENT '目标 ID',
    `target_version`  INT           NULL     COMMENT '目标版本号',
    `before_snapshot` MEDIUMTEXT    NULL     COMMENT '变更前快照 (JSON)',
    `after_snapshot`  MEDIUMTEXT    NULL     COMMENT '变更后快照 (JSON)',
    `details`         TEXT          NULL     COMMENT '操作详情描述',
    `ip_address`      VARCHAR(45)   NULL     COMMENT '操作来源 IP 地址 (兼容 IPv6)',
    `operated_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',

    PRIMARY KEY (`id`),
    INDEX `idx_audit_log_operator` (`operator`),
    INDEX `idx_audit_log_target` (`target_type`, `target_id`),
    INDEX `idx_audit_log_action` (`action`),
    INDEX `idx_audit_log_operated_at` (`operated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审计日志表 — 记录所有配置变更操作，保留 5 年';


-- ---------------------------------------------------------------------------
-- 4. approval_record — 审批记录表
-- 记录每次审批操作: 提交/通过/驳回/撤回
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `approval_record` (
    `record_id`       VARCHAR(64)   NOT NULL COMMENT '审批记录 ID',
    `target_type`     VARCHAR(32)   NOT NULL COMMENT '关联规则类型: RULE / SCORECARD / DECISION_TABLE / DECISION_TREE / FLOW / VARIABLE',
    `target_id`       VARCHAR(64)   NOT NULL COMMENT '关联规则 ID',
    `target_version`  INT           NOT NULL COMMENT '关联规则版本号',
    `action`          VARCHAR(16)   NOT NULL COMMENT '操作类型: SUBMIT / APPROVE / REJECT / WITHDRAW',
    `operator`        VARCHAR(64)   NOT NULL COMMENT '操作人',
    `comment`         TEXT          NULL     COMMENT '审批意见/原因',
    `operated_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',

    PRIMARY KEY (`record_id`),
    INDEX `idx_approval_record_target` (`target_type`, `target_id`),
    INDEX `idx_approval_record_operator` (`operator`),
    INDEX `idx_approval_record_operated_at` (`operated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审批记录表 — 记录每次审批操作的完整历史';


-- ---------------------------------------------------------------------------
-- 5. grayscale_config — 灰度发布配置表
-- 按百分比逐步提升流量: 5% → 25% → 50% → 100% (全量发布)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `grayscale_config` (
    `config_id`            VARCHAR(64)   NOT NULL COMMENT '灰度配置 ID',
    `target_type`          VARCHAR(32)   NOT NULL COMMENT '关联规则类型: RULE / SCORECARD / DECISION_TABLE / DECISION_TREE / FLOW / VARIABLE',
    `target_id`            VARCHAR(64)   NOT NULL COMMENT '关联规则 ID',
    `target_version`       INT           NOT NULL COMMENT '关联规则版本号',
    `percentage`           INT           NOT NULL DEFAULT 0 COMMENT '当前灰度百分比 (0-100)',
    `previous_percentage`  INT           NOT NULL DEFAULT 0 COMMENT '上一次灰度百分比',
    `operator`             VARCHAR(64)   NULL     COMMENT '操作人',
    `started_at`           DATETIME      NULL     COMMENT '灰度开始时间',
    `updated_at`           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后调整时间',
    `grayscale_status`     VARCHAR(16)   NOT NULL DEFAULT 'NOT_STARTED' COMMENT '灰度状态: NOT_STARTED / IN_PROGRESS / FULL / PAUSED / ROLLED_BACK',

    PRIMARY KEY (`config_id`),
    INDEX `idx_grayscale_config_target` (`target_type`, `target_id`),
    INDEX `idx_grayscale_config_status` (`grayscale_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='灰度发布配置表 — 按百分比逐步提升流量的灰度发布管理';
