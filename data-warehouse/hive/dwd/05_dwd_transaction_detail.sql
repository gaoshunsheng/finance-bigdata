-- ============================================================================
-- DWD层 - 交易明细表
-- 描述: 标准化后的贷款相关交易流水明细
-- 数据来源: ods.ods_repayment_record + 放款系统 + 费用系统
-- 交易类型:
--   REPAYMENT    - 还款交易
--   DISBURSEMENT - 放款交易
--   FEE          - 费用交易
-- 分区字段: dt (按天分区, 格式 yyyy-MM-dd)
-- 创建时间: 2026-06-06
-- ============================================================================

CREATE TABLE IF NOT EXISTS dwd.dwd_transaction_detail (
    transaction_id      STRING          COMMENT '交易ID',
    customer_id         STRING          COMMENT '客户ID',
    loan_id             STRING          COMMENT '贷款ID',
    transaction_type    STRING          COMMENT '交易类型(REPAYMENT/DISBURSEMENT/FEE)',
    amount              DECIMAL(18,2)   COMMENT '交易金额(元)',
    principal           DECIMAL(18,2)   COMMENT '本金金额(元)',
    interest            DECIMAL(18,2)   COMMENT '利息金额(元)',
    transaction_time    TIMESTAMP       COMMENT '交易时间',
    channel             STRING          COMMENT '交易渠道',
    etl_time            TIMESTAMP       COMMENT 'ETL处理时间'
)
COMMENT '交易明细表 - 标准化后的贷款相关交易流水'
PARTITIONED BY (dt STRING COMMENT '日期分区, 格式: yyyy-MM-dd')
STORED AS ORC
TBLPROPERTIES (
    'orc.compress' = 'SNAPPY',
    'orc.create.index' = 'true',
    'transient_lastDdlTime' = '0'
);
