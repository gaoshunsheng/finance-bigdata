-- ============================================================================
-- ODS层 - 外部数据原始表
-- 描述: 从外部数据源采集的第三方数据原始记录
-- 数据来源: 征信/工商/司法/运营商/税务/社保等外部接口
-- 分区字段: dt (按天分区, 格式 yyyy-MM-dd)
-- 创建时间: 2026-06-06
-- ============================================================================

CREATE TABLE IF NOT EXISTS ods.ods_external_data (
    id              BIGINT          COMMENT '主键ID',
    customer_id     STRING          COMMENT '客户ID',
    data_source     STRING          COMMENT '数据来源(征信/工商/司法/运营商/税务/社保)',
    raw_content     STRING          COMMENT '原始数据内容(JSON格式)',
    fetch_time      TIMESTAMP       COMMENT '数据获取时间',
    api_request_id  STRING          COMMENT 'API请求ID',
    source_system   STRING          COMMENT '来源系统标识',
    etl_time        TIMESTAMP       COMMENT 'ETL处理时间'
)
COMMENT '外部数据原始表 - 第三方数据源采集数据'
PARTITIONED BY (dt STRING COMMENT '日期分区, 格式: yyyy-MM-dd')
STORED AS ORC
TBLPROPERTIES (
    'orc.compress' = 'SNAPPY',
    'orc.create.index' = 'true',
    'transient_lastDdlTime' = '0'
);
