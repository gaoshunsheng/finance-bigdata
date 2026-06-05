-- ============================================================================
-- DWS层 - 产品维度汇总表
-- 描述: 按产品维度聚合的贷款业务指标汇总，用于产品分析
-- 数据来源: dwd.dwd_loan_application_detail + dwd.dwd_transaction_detail
-- 汇总维度: 产品ID + 统计日期
-- 分区字段: dt (按天分区, 格式 yyyy-MM-dd)
-- 创建时间: 2026-06-06
-- ============================================================================

CREATE TABLE IF NOT EXISTS dws.dws_product_loan_summary (
    product_id          STRING          COMMENT '产品ID',
    product_name        STRING          COMMENT '产品名称',
    stat_date           STRING          COMMENT '统计日期(yyyy-MM-dd)',
    apply_count         INT             COMMENT '申请笔数',
    approve_count       INT             COMMENT '通过笔数',
    reject_count        INT             COMMENT '拒绝笔数',
    approve_rate        DECIMAL(5,4)    COMMENT '通过率(0~1)',
    total_loan_amount   DECIMAL(18,2)   COMMENT '贷款总金额(元)',
    avg_loan_amount     DECIMAL(18,2)   COMMENT '平均贷款金额(元)',
    avg_loan_term       DECIMAL(10,2)   COMMENT '平均贷款期限(月)',
    overdue_count       INT             COMMENT '逾期笔数',
    overdue_rate        DECIMAL(5,4)    COMMENT '逾期率(0~1)',
    etl_time            TIMESTAMP       COMMENT 'ETL处理时间'
)
COMMENT '产品维度汇总表 - 按产品聚合的贷款业务指标'
PARTITIONED BY (dt STRING COMMENT '日期分区, 格式: yyyy-MM-dd')
STORED AS ORC
TBLPROPERTIES (
    'orc.compress' = 'SNAPPY',
    'orc.create.index' = 'true',
    'transient_lastDdlTime' = '0'
);
