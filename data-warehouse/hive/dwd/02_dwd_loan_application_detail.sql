-- ============================================================================
-- DWD层 - 贷款申请明细表
-- 描述: 关联客户信息并标准化编码后的贷款申请明细数据
-- 数据来源: ods.ods_loan_application + ods.ods_customer_info
-- 处理逻辑:
--   1. 关联客户信息表获取客户画像字段
--   2. 标准化渠道编码和产品编码
--   3. 过滤无效和重复数据
-- 分区字段: dt (按天分区, 格式 yyyy-MM-dd)
-- 创建时间: 2026-06-06
-- ============================================================================

CREATE TABLE IF NOT EXISTS dwd.dwd_loan_application_detail (
    application_no      STRING          COMMENT '申请编号',
    customer_id         STRING          COMMENT '客户ID',
    product_id          STRING          COMMENT '产品ID',
    product_name        STRING          COMMENT '产品名称',
    channel_code        STRING          COMMENT '渠道编码',
    channel_name        STRING          COMMENT '渠道名称',
    loan_amount         DECIMAL(18,2)   COMMENT '申请贷款金额(元)',
    loan_term           INT             COMMENT '贷款期限(月)',
    purpose             STRING          COMMENT '贷款用途',
    apply_time          TIMESTAMP       COMMENT '申请时间',
    status              STRING          COMMENT '申请状态',
    customer_age        INT             COMMENT '客户年龄',
    customer_gender     STRING          COMMENT '客户性别',
    customer_education  STRING          COMMENT '客户学历',
    etl_time            TIMESTAMP       COMMENT 'ETL处理时间'
)
COMMENT '贷款申请明细表 - 标准化后的贷款申请数据, 关联客户画像'
PARTITIONED BY (dt STRING COMMENT '日期分区, 格式: yyyy-MM-dd')
STORED AS ORC
TBLPROPERTIES (
    'orc.compress' = 'SNAPPY',
    'orc.create.index' = 'true',
    'transient_lastDdlTime' = '0'
);
