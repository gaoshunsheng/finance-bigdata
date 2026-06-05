-- ============================================================================
-- ADS层 - 风控指标汇总表
-- 描述: 面向风控监控场景的指标汇总，含模型效果指标(KS/AUC)
-- 数据来源: dws.dws_product_loan_summary + ads.ads_credit_score_wide_table
-- 用途: 风控大盘报表、模型效果监控、逾期率追踪
-- 模型指标: KS值(区分度) / AUC值(准确度)
-- 分区字段: dt (按天分区, 格式 yyyy-MM-dd)
-- 创建时间: 2026-06-06
-- ============================================================================

CREATE TABLE IF NOT EXISTS ads.ads_risk_indicator_summary (
    stat_date           STRING          COMMENT '统计日期(yyyy-MM-dd)',
    product_id          STRING          COMMENT '产品ID',
    product_name        STRING          COMMENT '产品名称',
    total_apply_count   INT             COMMENT '总申请笔数',
    approve_count       INT             COMMENT '通过笔数',
    approve_rate        DECIMAL(5,4)    COMMENT '通过率(0~1)',
    total_loan_amount   DECIMAL(18,2)   COMMENT '贷款总金额(元)',
    overdue_30_count    INT             COMMENT '逾期30天+笔数',
    overdue_30_rate     DECIMAL(5,4)    COMMENT '逾期30天+率(0~1)',
    overdue_90_count    INT             COMMENT '逾期90天+笔数',
    overdue_90_rate     DECIMAL(5,4)    COMMENT '逾期90天+率(0~1)',
    avg_credit_score    DECIMAL(10,2)   COMMENT '平均信用评分',
    ks_value            DECIMAL(5,4)    COMMENT 'KS值(模型区分度指标)',
    auc_value           DECIMAL(5,4)    COMMENT 'AUC值(模型准确度指标)',
    etl_time            TIMESTAMP       COMMENT 'ETL处理时间'
)
COMMENT '风控指标汇总表 - 含逾期率、KS、AUC等风控监控指标'
PARTITIONED BY (dt STRING COMMENT '日期分区, 格式: yyyy-MM-dd')
STORED AS ORC
TBLPROPERTIES (
    'orc.compress' = 'SNAPPY',
    'orc.create.index' = 'true',
    'transient_lastDdlTime' = '0'
);
