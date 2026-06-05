-- ============================================================================
-- DWD层 - 信用事件明细表
-- 描述: 统一信用事件模型，将征信查询、逾期、申请、还款等事件整合
-- 数据来源: ods.ods_credit_report + ods.ods_repayment_record + ods.ods_loan_application
-- 事件类型:
--   CREDIT_QUERY  - 征信查询事件
--   OVERDUE       - 逾期事件
--   APPLY         - 贷款申请事件
--   REPAY         - 还款事件
-- 分区字段: dt (按天分区, 格式 yyyy-MM-dd)
-- 创建时间: 2026-06-06
-- ============================================================================

CREATE TABLE IF NOT EXISTS dwd.dwd_credit_event (
    event_id        STRING          COMMENT '事件唯一ID',
    customer_id     STRING          COMMENT '客户ID',
    event_type      STRING          COMMENT '事件类型(CREDIT_QUERY/OVERDUE/APPLY/REPAY)',
    event_time      TIMESTAMP       COMMENT '事件发生时间',
    loan_id         STRING          COMMENT '关联贷款ID',
    amount          DECIMAL(18,2)   COMMENT '关联金额(元)',
    overdue_days    INT             COMMENT '逾期天数(仅OVERDUE类型)',
    institution     STRING          COMMENT '关联机构',
    detail          STRING          COMMENT '事件详情',
    etl_time        TIMESTAMP       COMMENT 'ETL处理时间'
)
COMMENT '信用事件明细表 - 统一信用事件模型, 整合多源信用相关事件'
PARTITIONED BY (dt STRING COMMENT '日期分区, 格式: yyyy-MM-dd')
STORED AS ORC
TBLPROPERTIES (
    'orc.compress' = 'SNAPPY',
    'orc.create.index' = 'true',
    'transient_lastDdlTime' = '0'
);
