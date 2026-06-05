-- ============================================================================
-- ADS层 - 决策分析主题表
-- 描述: 面向决策引擎运营分析的主题表，统计决策流执行效果
-- 数据来源: ods.ods_decision_log + 决策引擎元数据
-- 用途: 决策流效果分析、规则命中分析、运营效率监控
-- 分区字段: dt (按天分区, 格式 yyyy-MM-dd)
-- 创建时间: 2026-06-06
-- ============================================================================

CREATE TABLE IF NOT EXISTS ads.ads_decision_analysis (
    stat_date           STRING          COMMENT '统计日期(yyyy-MM-dd)',
    flow_id             STRING          COMMENT '决策流ID',
    flow_name           STRING          COMMENT '决策流名称',
    flow_version        INT             COMMENT '决策流版本号',
    decision_count      INT             COMMENT '决策总次数',
    pass_count          INT             COMMENT '通过次数',
    reject_count        INT             COMMENT '拒绝次数',
    manual_review_count INT             COMMENT '人工审核次数',
    pass_rate           DECIMAL(5,4)    COMMENT '通过率(0~1)',
    avg_score           DECIMAL(10,2)   COMMENT '平均评分',
    avg_duration_ms     BIGINT          COMMENT '平均决策耗时(毫秒)',
    top_hit_rule_1      STRING          COMMENT '命中次数最多的规则TOP1',
    top_hit_rule_2      STRING          COMMENT '命中次数最多的规则TOP2',
    top_hit_rule_3      STRING          COMMENT '命中次数最多的规则TOP3',
    etl_time            TIMESTAMP       COMMENT 'ETL处理时间'
)
COMMENT '决策分析主题表 - 决策流执行效果统计, 含规则命中分析'
PARTITIONED BY (dt STRING COMMENT '日期分区, 格式: yyyy-MM-dd')
STORED AS ORC
TBLPROPERTIES (
    'orc.compress' = 'SNAPPY',
    'orc.create.index' = 'true',
    'transient_lastDdlTime' = '0'
);
