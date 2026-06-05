-- ============================================================================
-- DWD层 - 标准化客户画像表
-- 描述: 经过清洗、脱敏、标准化的客户画像数据
-- 数据来源: ods.ods_customer_info
-- 处理逻辑:
--   1. 对姓名、身份证、手机号等敏感字段进行脱敏处理
--   2. 标准化学历编码和行业编码
--   3. 从地址解析出省份和城市
--   4. 从出生日期计算年龄
-- 分区字段: dt (按天分区, 格式 yyyy-MM-dd)
-- 创建时间: 2026-06-06
-- ============================================================================

CREATE TABLE IF NOT EXISTS dwd.dwd_customer_profile (
    customer_id         STRING          COMMENT '客户ID',
    customer_name_masked STRING         COMMENT '客户姓名(脱敏, 如: 张**)',
    id_card_masked      STRING          COMMENT '身份证号(脱敏, 如: 310***********1234)',
    phone_masked        STRING          COMMENT '手机号码(脱敏, 如: 138****5678)',
    gender              STRING          COMMENT '性别',
    age                 INT             COMMENT '年龄',
    education_code      STRING          COMMENT '学历编码',
    education_name      STRING          COMMENT '学历名称',
    marital_status      STRING          COMMENT '婚姻状况',
    province            STRING          COMMENT '省份',
    city                STRING          COMMENT '城市',
    industry_code       STRING          COMMENT '行业编码',
    industry_name       STRING          COMMENT '行业名称',
    annual_income       DECIMAL(18,2)   COMMENT '年收入(元)',
    employer            STRING          COMMENT '工作单位',
    etl_time            TIMESTAMP       COMMENT 'ETL处理时间'
)
COMMENT '标准化客户画像表 - 清洗脱敏后的客户信息'
PARTITIONED BY (dt STRING COMMENT '日期分区, 格式: yyyy-MM-dd')
STORED AS ORC
TBLPROPERTIES (
    'orc.compress' = 'SNAPPY',
    'orc.create.index' = 'true',
    'transient_lastDdlTime' = '0'
);
