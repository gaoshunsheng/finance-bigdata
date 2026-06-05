-- ============================================================================
-- ODS层 - 还款记录原始表
-- 描述: 从还款系统同步的贷款还款明细原始数据
-- 数据来源: 还款管理系统
-- 分区字段: dt (按天分区, 格式 yyyy-MM-dd)
-- 创建时间: 2026-06-06
-- ============================================================================

CREATE TABLE IF NOT EXISTS ods.ods_repayment_record (
    id              BIGINT          COMMENT '主键ID',
    loan_id         STRING          COMMENT '贷款ID',
    customer_id     STRING          COMMENT '客户ID',
    installment_no  INT             COMMENT '期次号',
    due_date        DATE            COMMENT '应还日期',
    repay_date      DATE            COMMENT '实还日期',
    repay_amount    DECIMAL(18,2)   COMMENT '还款金额(元)',
    principal       DECIMAL(18,2)   COMMENT '还款本金(元)',
    interest        DECIMAL(18,2)   COMMENT '还款利息(元)',
    overdue_days    INT             COMMENT '逾期天数',
    status          STRING          COMMENT '还款状态',
    source_system   STRING          COMMENT '来源系统标识',
    etl_time        TIMESTAMP       COMMENT 'ETL处理时间'
)
COMMENT '还款记录原始表 - 贷款还款明细数据同步'
PARTITIONED BY (dt STRING COMMENT '日期分区, 格式: yyyy-MM-dd')
STORED AS ORC
TBLPROPERTIES (
    'orc.compress' = 'SNAPPY',
    'orc.create.index' = 'true',
    'transient_lastDdlTime' = '0'
);
