-- ============================================================================
-- ODS层 - 征信报告原始表
-- 描述: 从征信系统同步的个人征信报告原始数据
-- 数据来源: 征信查询系统
-- 分区字段: dt (按天分区, 格式 yyyy-MM-dd)
-- 创建时间: 2026-06-06
-- ============================================================================

CREATE TABLE IF NOT EXISTS ods.ods_credit_report (
    id                    BIGINT          COMMENT '主键ID',
    customer_id           STRING          COMMENT '客户ID',
    report_no             STRING          COMMENT '征信报告编号',
    query_institution     STRING          COMMENT '查询机构',
    query_purpose         STRING          COMMENT '查询用途',
    query_time            TIMESTAMP       COMMENT '查询时间',
    loan_count            INT             COMMENT '贷款笔数',
    credit_card_count     INT             COMMENT '信用卡数量',
    overdue_count         INT             COMMENT '逾期次数',
    total_debt            DECIMAL(18,2)   COMMENT '总负债金额(元)',
    latest_overdue_date   STRING          COMMENT '最近一次逾期日期',
    source_system         STRING          COMMENT '来源系统标识',
    etl_time              TIMESTAMP       COMMENT 'ETL处理时间'
)
COMMENT '征信报告原始表 - 个人征信报告数据同步'
PARTITIONED BY (dt STRING COMMENT '日期分区, 格式: yyyy-MM-dd')
STORED AS ORC
TBLPROPERTIES (
    'orc.compress' = 'SNAPPY',
    'orc.create.index' = 'true',
    'transient_lastDdlTime' = '0'
);
