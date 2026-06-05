-- ============================================================================
-- ODS层 - 决策日志原始表
-- 描述: 从决策引擎系统同步的风控决策执行日志
-- 数据来源: 风控决策引擎
-- 分区字段: dt (按天分区, 格式 yyyy-MM-dd)
-- 创建时间: 2026-06-06
-- ============================================================================

CREATE TABLE IF NOT EXISTS ods.ods_decision_log (
    id              BIGINT          COMMENT '主键ID',
    trace_id        STRING          COMMENT '追踪ID',
    customer_id     STRING          COMMENT '客户ID',
    flow_id         STRING          COMMENT '决策流ID',
    flow_version    INT             COMMENT '决策流版本号',
    decision_result STRING          COMMENT '决策结果(PASS/REJECT/MANUAL_REVIEW)',
    total_score     DECIMAL(10,2)   COMMENT '综合评分',
    duration_ms     BIGINT          COMMENT '决策耗时(毫秒)',
    node_count      INT             COMMENT '执行节点数',
    hit_rules       STRING          COMMENT '命中规则列表(JSON数组)',
    request_time    TIMESTAMP       COMMENT '请求时间',
    source_system   STRING          COMMENT '来源系统标识',
    etl_time        TIMESTAMP       COMMENT 'ETL处理时间'
)
COMMENT '决策日志原始表 - 风控决策引擎执行日志'
PARTITIONED BY (dt STRING COMMENT '日期分区, 格式: yyyy-MM-dd')
STORED AS ORC
TBLPROPERTIES (
    'orc.compress' = 'SNAPPY',
    'orc.create.index' = 'true',
    'transient_lastDdlTime' = '0'
);
