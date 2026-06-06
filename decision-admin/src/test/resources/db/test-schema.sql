-- ============================================================================
-- H2-compatible test schema for decision-admin
-- Mirrors V1__init_schema.sql but with H2-compatible syntax
-- ============================================================================

CREATE TABLE IF NOT EXISTS rule_entity (
    id          VARCHAR(64)   NOT NULL,
    name        VARCHAR(128)  NOT NULL,
    type        VARCHAR(32)   NOT NULL,
    version     INT           NOT NULL,
    status      VARCHAR(32)   NOT NULL DEFAULT 'DRAFT',
    content     CLOB          NULL,
    description VARCHAR(512)  NULL,
    created_by  VARCHAR(64)   NULL,
    updated_by  VARCHAR(64)   NULL,
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    attributes  CLOB          NULL,

    PRIMARY KEY (type, id, version)
);

CREATE INDEX idx_rule_type_status ON rule_entity (type, status);

CREATE TABLE IF NOT EXISTS sys_user (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    username      VARCHAR(64)   NOT NULL,
    password      VARCHAR(256)  NOT NULL,
    display_name  VARCHAR(128)  NULL,
    email         VARCHAR(128)  NULL,
    role          VARCHAR(32)   NOT NULL DEFAULT 'VIEWER',
    enabled       BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP     NULL,

    PRIMARY KEY (id),
    CONSTRAINT uk_sys_user_username UNIQUE (username)
);

CREATE TABLE IF NOT EXISTS audit_log (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    operator        VARCHAR(64)   NOT NULL,
    action          VARCHAR(32)   NOT NULL,
    target_type     VARCHAR(32)   NULL,
    target_id       VARCHAR(64)   NULL,
    target_version  INT           NULL,
    before_snapshot CLOB          NULL,
    after_snapshot  CLOB          NULL,
    details         CLOB          NULL,
    ip_address      VARCHAR(45)   NULL,
    operated_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id)
);

CREATE INDEX idx_audit_operator ON audit_log (operator);
CREATE INDEX idx_audit_target ON audit_log (target_type, target_id);

CREATE TABLE IF NOT EXISTS approval_record (
    record_id       VARCHAR(64)   NOT NULL,
    target_type     VARCHAR(32)   NOT NULL,
    target_id       VARCHAR(64)   NOT NULL,
    target_version  INT           NOT NULL,
    action          VARCHAR(16)   NOT NULL,
    operator        VARCHAR(64)   NOT NULL,
    "comment"       CLOB          NULL,
    operated_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (record_id)
);

CREATE INDEX idx_approval_target ON approval_record (target_type, target_id);

CREATE TABLE IF NOT EXISTS grayscale_config (
    config_id            VARCHAR(64)   NOT NULL,
    target_type          VARCHAR(32)   NOT NULL,
    target_id            VARCHAR(64)   NOT NULL,
    target_version       INT           NOT NULL,
    percentage           INT           NOT NULL DEFAULT 0,
    previous_percentage  INT           NOT NULL DEFAULT 0,
    operator             VARCHAR(64)   NULL,
    started_at           TIMESTAMP     NULL,
    updated_at           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    grayscale_status     VARCHAR(16)   NOT NULL DEFAULT 'NOT_STARTED',

    PRIMARY KEY (config_id)
);

CREATE INDEX idx_grayscale_target ON grayscale_config (target_type, target_id);
