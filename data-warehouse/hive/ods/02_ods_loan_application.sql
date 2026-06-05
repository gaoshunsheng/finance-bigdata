-- ============================================================================
-- ODS层 - 贷款申请原始表
-- 描述: 从业务系统同步的贷款申请原始数据
-- 数据来源: 贷款申请系统
-- 分区字段: dt (按天分区, 格式 yyyy-MM-dd)
-- 创建时间: 2026-06-06
-- ============================================================================

CREATE TABLE IF NOT EXISTS ods.ods_loan_application (
    id              BIGINT          COMMENT '主键ID',
    application_no  STRING          COMMENT '申请编号',
    customer_id     STRING          COMMENT '客户ID',
    product_id      STRING          COMMENT '产品ID',
    channel         STRING          COMMENT '申请渠道',
    loan_amount     DECIMAL(18,2)   COMMENT '申请贷款金额',
    loan_term       INT             COMMENT '贷款期限(月)',
    purpose         STRING          COMMENT '贷款用途',
    apply_time      TIMESTAMP       COMMENT '申请时间',
    status          STRING          COMMENT '申请状态',
    source_system   STRING          COMMENT '来源系统标识',
    etl_time        TIMESTAMP       COMMENT 'ETL处理时间'
)
COMMENT '贷款申请原始表 - 业务系统贷款申请数据同步'
PARTITIONED BY (dt STRING COMMENT '日期分区, 格式: yyyy-MM-dd')
STORED AS ORC
TBLPROPERTIES (
    'orc.compress' = 'SNAPPY',
    'orc.create.index' = 'true',
    'transient_lastDdlTime' = '0'
);
