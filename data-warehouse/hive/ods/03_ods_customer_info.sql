-- ============================================================================
-- ODS层 - 客户信息原始表
-- 描述: 从客户管理系统同步的客户基本信息原始数据
-- 数据来源: 客户管理系统(CRM)
-- 分区字段: dt (按天分区, 格式 yyyy-MM-dd)
-- 创建时间: 2026-06-06
-- ============================================================================

CREATE TABLE IF NOT EXISTS ods.ods_customer_info (
    id              BIGINT          COMMENT '主键ID',
    customer_id     STRING          COMMENT '客户ID',
    customer_name   STRING          COMMENT '客户姓名',
    id_card         STRING          COMMENT '身份证号',
    phone           STRING          COMMENT '手机号码',
    gender          STRING          COMMENT '性别',
    birth_date      STRING          COMMENT '出生日期',
    education       STRING          COMMENT '学历',
    marital_status  STRING          COMMENT '婚姻状况',
    address         STRING          COMMENT '居住地址',
    employer        STRING          COMMENT '工作单位',
    industry        STRING          COMMENT '所属行业',
    annual_income   DECIMAL(18,2)   COMMENT '年收入(元)',
    source_system   STRING          COMMENT '来源系统标识',
    etl_time        TIMESTAMP       COMMENT 'ETL处理时间'
)
COMMENT '客户信息原始表 - 客户基本信息数据同步'
PARTITIONED BY (dt STRING COMMENT '日期分区, 格式: yyyy-MM-dd')
STORED AS ORC
TBLPROPERTIES (
    'orc.compress' = 'SNAPPY',
    'orc.create.index' = 'true',
    'transient_lastDdlTime' = '0'
);
