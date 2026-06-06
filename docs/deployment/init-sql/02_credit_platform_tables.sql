-- ============================================================================
-- Finance Bigdata Platform - 业务源表建表脚本
-- 描述: 创建 credit_platform 数据库的业务源表，供 DataX 同步到 HDFS/ODS 层
-- 文件路径: init-sql/02_credit_platform_tables.sql
-- 创建时间: 2026-06-07
-- ============================================================================

USE credit_platform;

-- ---------------------------------------------------------------------------
-- 客户信息表
-- 来源: CRM 系统，DataX 同步到 HDFS /data/ods/ods_customer_info
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `customer_info` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `customer_id`     VARCHAR(32)  NOT NULL COMMENT '客户唯一标识，如 CUST_00001',
    `customer_name`   VARCHAR(64)  DEFAULT NULL COMMENT '客户姓名',
    `id_card`         VARCHAR(18)  DEFAULT NULL COMMENT '身份证号(18位)',
    `phone`           VARCHAR(20)  DEFAULT NULL COMMENT '手机号码',
    `gender`          VARCHAR(4)   DEFAULT NULL COMMENT '性别(男/女)',
    `birth_date`      VARCHAR(10)  DEFAULT NULL COMMENT '出生日期 yyyy-MM-dd',
    `education`       VARCHAR(16)  DEFAULT NULL COMMENT '学历(高中及以下/大专/本科/硕士/博士)',
    `marital_status`  VARCHAR(16)  DEFAULT NULL COMMENT '婚姻状况(未婚/已婚/离异)',
    `address`         VARCHAR(256) DEFAULT NULL COMMENT '居住地址',
    `employer`        VARCHAR(128) DEFAULT NULL COMMENT '工作单位',
    `industry`        VARCHAR(64)  DEFAULT NULL COMMENT '所属行业',
    `annual_income`   DECIMAL(18,2) DEFAULT NULL COMMENT '年收入(元)',
    `source_system`   VARCHAR(32)  DEFAULT 'CREDIT_PLATFORM' COMMENT '来源系统',
    `etl_time`        DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT 'ETL时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_customer_id` (`customer_id`),
    KEY `idx_etl_time` (`etl_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户信息表';

-- ---------------------------------------------------------------------------
-- 贷款申请表
-- 来源: 贷款系统，DataX 同步到 HDFS /data/ods/ods_loan_application
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `loan_application` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `application_no`  VARCHAR(32)   NOT NULL COMMENT '申请编号，如 LA_20250101001',
    `customer_id`     VARCHAR(32)   NOT NULL COMMENT '客户唯一标识',
    `product_id`      VARCHAR(16)   DEFAULT NULL COMMENT '产品编号(P001消费贷/P002经营贷/P003信用卡分期)',
    `channel`         VARCHAR(16)   DEFAULT NULL COMMENT '申请渠道(APP/WEB/WECHAT/OFFLINE/PARTNER)',
    `loan_amount`     DECIMAL(18,2) DEFAULT NULL COMMENT '贷款金额(元)',
    `loan_term`       INT           DEFAULT NULL COMMENT '贷款期限(月)',
    `purpose`         VARCHAR(64)   DEFAULT NULL COMMENT '贷款用途',
    `apply_time`      DATETIME      NOT NULL COMMENT '申请时间',
    `status`          VARCHAR(16)   DEFAULT NULL COMMENT '申请状态(PENDING/APPROVED/REJECTED/DISBURSED/CLOSED)',
    `source_system`   VARCHAR(32)   DEFAULT 'CREDIT_PLATFORM' COMMENT '来源系统',
    `etl_time`        DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT 'ETL时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_application_no` (`application_no`),
    KEY `idx_customer_id` (`customer_id`),
    KEY `idx_apply_time` (`apply_time`),
    KEY `idx_etl_time` (`etl_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='贷款申请表';

-- ---------------------------------------------------------------------------
-- 还款记录表
-- 来源: 还款系统，DataX 同步到 HDFS /data/ods/ods_repayment_record
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `repayment_record` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `loan_id`         VARCHAR(32)   NOT NULL COMMENT '贷款编号(关联loan_application.application_no)',
    `customer_id`     VARCHAR(32)   NOT NULL COMMENT '客户唯一标识',
    `installment_no`  INT           DEFAULT NULL COMMENT '期数(第N期)',
    `due_date`        DATE          DEFAULT NULL COMMENT '应还日期',
    `repay_date`      DATE          DEFAULT NULL COMMENT '实际还款日期',
    `repay_amount`    DECIMAL(18,2) DEFAULT NULL COMMENT '还款金额(元)',
    `principal`       DECIMAL(18,2) DEFAULT NULL COMMENT '本金(元)',
    `interest`        DECIMAL(18,2) DEFAULT NULL COMMENT '利息(元)',
    `overdue_days`    INT           DEFAULT 0 COMMENT '逾期天数(0=正常)',
    `status`          VARCHAR(16)   DEFAULT NULL COMMENT '还款状态(PAID/OVERDUE/PENDING)',
    `source_system`   VARCHAR(32)   DEFAULT 'CREDIT_PLATFORM' COMMENT '来源系统',
    `etl_time`        DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT 'ETL时间',
    PRIMARY KEY (`id`),
    KEY `idx_loan_id` (`loan_id`),
    KEY `idx_customer_id` (`customer_id`),
    KEY `idx_etl_time` (`etl_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='还款记录表';

-- ---------------------------------------------------------------------------
-- 征信报告表
-- 来源: 征信系统，Canal CDC 同步到 Kafka topic cdc_credit_query
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `credit_report` (
    `id`                  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `customer_id`         VARCHAR(32)   NOT NULL COMMENT '客户唯一标识',
    `report_no`           VARCHAR(32)   NOT NULL COMMENT '征信报告编号',
    `query_institution`   VARCHAR(64)   DEFAULT NULL COMMENT '查询机构',
    `query_purpose`       VARCHAR(64)   DEFAULT NULL COMMENT '查询用途(贷款审批/信用卡审批/本人查询/担保审查/其他)',
    `query_time`          DATETIME      NOT NULL COMMENT '查询时间',
    `loan_count`          INT           DEFAULT NULL COMMENT '贷款账户数',
    `credit_card_count`   INT           DEFAULT NULL COMMENT '信用卡账户数',
    `overdue_count`       INT           DEFAULT NULL COMMENT '逾期次数',
    `total_debt`          DECIMAL(18,2) DEFAULT NULL COMMENT '负债总额(元)',
    `latest_overdue_date` VARCHAR(10)   DEFAULT NULL COMMENT '最近逾期日期 yyyy-MM-dd',
    `source_system`       VARCHAR(32)   DEFAULT 'CREDIT_PLATFORM' COMMENT '来源系统',
    `etl_time`            DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT 'ETL时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_report_no` (`report_no`),
    KEY `idx_customer_id` (`customer_id`),
    KEY `idx_query_time` (`query_time`),
    KEY `idx_etl_time` (`etl_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='征信报告表';

-- ---------------------------------------------------------------------------
-- 创建 ETL 用户 (供 DataX 读取使用)
-- ---------------------------------------------------------------------------
CREATE USER IF NOT EXISTS 'etl_user'@'%' IDENTIFIED BY 'etl_password_123';
GRANT SELECT ON credit_platform.* TO 'etl_user'@'%';
FLUSH PRIVILEGES;

-- ---------------------------------------------------------------------------
-- 创建 Canal 用户 (供 Canal 读取 binlog 使用)
-- ---------------------------------------------------------------------------
CREATE USER IF NOT EXISTS 'canal'@'%' IDENTIFIED BY 'canal123';
GRANT SELECT, REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'canal'@'%';
FLUSH PRIVILEGES;
