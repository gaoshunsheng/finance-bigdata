-- ============================================================================
-- ADS层 - 信用评分宽表
-- 描述: 面向信用评分场景的宽表，整合客户画像、信用历史和评分结果
-- 数据来源: dws.dws_customer_credit_summary + dwd.dwd_customer_profile + 评分模型输出
-- 用途: 风控模型特征输入、信用评分结果查询、风险等级划分
-- 风险等级: LOW(低风险) / MEDIUM(中风险) / HIGH(高风险)
-- 分区字段: dt (按天分区, 格式 yyyy-MM-dd)
-- 创建时间: 2026-06-06
-- ============================================================================

CREATE TABLE IF NOT EXISTS ads.ads_credit_score_wide_table (
    customer_id             STRING          COMMENT '客户ID',
    stat_date               STRING          COMMENT '统计日期(yyyy-MM-dd)',
    age                     INT             COMMENT '年龄',
    gender                  STRING          COMMENT '性别',
    education               STRING          COMMENT '学历',
    annual_income           DECIMAL(18,2)   COMMENT '年收入(元)',
    loan_count_12m          INT             COMMENT '近12个月贷款笔数',
    credit_query_count_3m   INT             COMMENT '近3个月征信查询次数',
    overdue_count_6m        INT             COMMENT '近6个月逾期次数',
    max_overdue_days_12m    INT             COMMENT '近12个月最大逾期天数',
    apply_count_1m          INT             COMMENT '近1个月申请次数',
    repay_on_time_rate      DECIMAL(5,4)    COMMENT '按时还款率(0~1)',
    total_debt              DECIMAL(18,2)   COMMENT '总负债金额(元)',
    credit_score            DECIMAL(10,2)   COMMENT '信用评分(0~1000)',
    risk_level              STRING          COMMENT '风险等级(LOW/MEDIUM/HIGH)',
    etl_time                TIMESTAMP       COMMENT 'ETL处理时间'
)
COMMENT '信用评分宽表 - 整合客户画像、信用历史和评分结果, 面向风控应用'
PARTITIONED BY (dt STRING COMMENT '日期分区, 格式: yyyy-MM-dd')
STORED AS ORC
TBLPROPERTIES (
    'orc.compress' = 'SNAPPY',
    'orc.create.index' = 'true',
    'transient_lastDdlTime' = '0'
);
