-- ============================================================================
-- DWS层 - 渠道维度汇总表
-- 描述: 按渠道维度聚合的业务指标汇总，用于渠道效果分析
-- 数据来源: dwd.dwd_loan_application_detail + dwd.dwd_customer_profile
-- 汇总维度: 渠道编码 + 统计日期
-- 分区字段: dt (按天分区, 格式 yyyy-MM-dd)
-- 创建时间: 2026-06-06
-- ============================================================================

CREATE TABLE IF NOT EXISTS dws.dws_channel_summary (
    channel_code            STRING          COMMENT '渠道编码',
    channel_name            STRING          COMMENT '渠道名称',
    stat_date               STRING          COMMENT '统计日期(yyyy-MM-dd)',
    apply_count             INT             COMMENT '申请笔数',
    approve_count           INT             COMMENT '通过笔数',
    approve_rate            DECIMAL(5,4)    COMMENT '通过率(0~1)',
    total_loan_amount       DECIMAL(18,2)   COMMENT '贷款总金额(元)',
    avg_process_time_hours  DECIMAL(10,2)   COMMENT '平均处理时长(小时)',
    customer_count          INT             COMMENT '客户总数',
    new_customer_count      INT             COMMENT '新客户数',
    etl_time                TIMESTAMP       COMMENT 'ETL处理时间'
)
COMMENT '渠道维度汇总表 - 按渠道聚合的业务指标, 支持渠道效果分析'
PARTITIONED BY (dt STRING COMMENT '日期分区, 格式: yyyy-MM-dd')
STORED AS ORC
TBLPROPERTIES (
    'orc.compress' = 'SNAPPY',
    'orc.create.index' = 'true',
    'transient_lastDdlTime' = '0'
);
