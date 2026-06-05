-- ============================================================================
-- DWS层 - 客户信用维度汇总表
-- 描述: 按客户维度聚合的信用相关指标汇总，用于风控模型特征输入
-- 数据来源: dwd.dwd_credit_event + dwd.dwd_loan_application_detail
-- 汇总维度: 客户ID + 统计月份
-- 统计周期: 1个月/3个月/6个月/12个月滚动窗口
-- 分区字段: dt (按天分区, 格式 yyyy-MM-dd)
-- 创建时间: 2026-06-06
-- ============================================================================

CREATE TABLE IF NOT EXISTS dws.dws_customer_credit_summary (
    customer_id             STRING          COMMENT '客户ID',
    stat_month              STRING          COMMENT '统计月份(yyyy-MM)',
    loan_count_1m           INT             COMMENT '近1个月贷款笔数',
    loan_count_3m           INT             COMMENT '近3个月贷款笔数',
    loan_count_6m           INT             COMMENT '近6个月贷款笔数',
    loan_count_12m          INT             COMMENT '近12个月贷款笔数',
    loan_amount_total_12m   DECIMAL(18,2)   COMMENT '近12个月贷款总额(元)',
    credit_query_count_1m   INT             COMMENT '近1个月征信查询次数',
    credit_query_count_3m   INT             COMMENT '近3个月征信查询次数',
    overdue_count_1m        INT             COMMENT '近1个月逾期次数',
    overdue_count_3m        INT             COMMENT '近3个月逾期次数',
    overdue_count_6m        INT             COMMENT '近6个月逾期次数',
    overdue_count_12m       INT             COMMENT '近12个月逾期次数',
    max_overdue_days_12m    INT             COMMENT '近12个月最大逾期天数',
    apply_count_1m          INT             COMMENT '近1个月申请次数',
    apply_count_3m          INT             COMMENT '近3个月申请次数',
    repay_on_time_rate_12m  DECIMAL(5,4)    COMMENT '近12个月按时还款率(0~1)',
    avg_loan_amount_12m     DECIMAL(18,2)   COMMENT '近12个月平均贷款金额(元)',
    etl_time                TIMESTAMP       COMMENT 'ETL处理时间'
)
COMMENT '客户信用维度汇总表 - 按客户聚合的信用指标, 多时间窗口统计'
PARTITIONED BY (dt STRING COMMENT '日期分区, 格式: yyyy-MM-dd')
STORED AS ORC
TBLPROPERTIES (
    'orc.compress' = 'SNAPPY',
    'orc.create.index' = 'true',
    'transient_lastDdlTime' = '0'
);
