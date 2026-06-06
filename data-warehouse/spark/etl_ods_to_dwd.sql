-- ============================================================================
-- ETL脚本: ODS层 -> DWD层 清洗转换
-- 描述: 将原始数据(ODS)清洗、脱敏、标准化后写入明细数据层(DWD)
-- 执行方式: spark-sql -f etl_ods_to_dwd.sql --hivevar dt=2026-06-05
-- 创建时间: 2026-06-06
-- ============================================================================

SET spark.sql.adaptive.enabled=true;
SET spark.sql.shuffle.partitions=8;

-- ============================================================================
-- 1. 贷款申请明细表 (dwd_loan_application_detail)
-- DDL: application_no, customer_id, product_id, product_name, channel_code,
--       channel_name, loan_amount, loan_term, purpose, apply_time, status,
--       customer_age, customer_gender, customer_education, etl_time
-- ============================================================================
INSERT OVERWRITE TABLE dwd.dwd_loan_application_detail PARTITION(dt='${hivevar:dt}')
SELECT
    a.application_no,
    a.customer_id,
    a.product_id,
    CASE a.product_id
        WHEN 'P001' THEN '消费贷'
        WHEN 'P002' THEN '经营贷'
        WHEN 'P003' THEN '信用贷'
        ELSE '其他产品'
    END                          AS product_name,
    a.channel                    AS channel_code,
    CASE a.channel
        WHEN 'APP'     THEN '手机APP'
        WHEN 'WEB'     THEN '官方网站'
        WHEN 'WECHAT'  THEN '微信公众号'
        WHEN 'OFFLINE' THEN '线下网点'
        WHEN 'PARTNER' THEN '合作渠道'
        ELSE '其他渠道'
    END                          AS channel_name,
    a.loan_amount,
    a.loan_term,
    a.purpose,
    a.apply_time,
    a.status,
    -- 从出生日期计算年龄
    CAST(YEAR(CURRENT_DATE()) - YEAR(CAST(c.birth_date AS DATE)) AS INT) AS customer_age,
    c.gender                    AS customer_gender,
    c.education                 AS customer_education,
    a.etl_time
FROM ods.ods_loan_application a
LEFT JOIN ods.ods_customer_info c
    ON a.customer_id = c.customer_id AND c.dt = '${hivevar:dt}'
WHERE a.dt = '${hivevar:dt}';

-- ============================================================================
-- 2. 信用事件明细表 (dwd_credit_event)
-- DDL: event_id, customer_id, event_type, event_time, loan_id, amount,
--       overdue_days, institution, detail, etl_time
-- ============================================================================
INSERT OVERWRITE TABLE dwd.dwd_credit_event PARTITION(dt='${hivevar:dt}')
SELECT * FROM (
    -- 2.1 征信查询事件
    SELECT
        CONCAT('CQ_', id)        AS event_id,
        customer_id,
        'CREDIT_QUERY'           AS event_type,
        query_time               AS event_time,
        CAST(NULL AS STRING)     AS loan_id,
        CAST(NULL AS DECIMAL(18,2)) AS amount,
        0                        AS overdue_days,
        query_institution        AS institution,
        CONCAT('报告编号:', report_no, ',逾期次数:', overdue_count) AS detail,
        current_timestamp()      AS etl_time
    FROM ods.ods_credit_report
    WHERE dt = '${hivevar:dt}'

    UNION ALL

    -- 2.2 逾期事件
    SELECT
        CONCAT('OD_', id)        AS event_id,
        customer_id,
        'OVERDUE'                AS event_type,
        CAST(due_date AS TIMESTAMP) AS event_time,
        loan_id,
        repay_amount             AS amount,
        overdue_days,
        CAST(NULL AS STRING)     AS institution,
        CONCAT('期次', installment_no, ',逾期', overdue_days, '天') AS detail,
        current_timestamp()      AS etl_time
    FROM ods.ods_repayment_record
    WHERE dt = '${hivevar:dt}' AND overdue_days > 0

    UNION ALL

    -- 2.3 贷款申请事件
    SELECT
        CONCAT('AP_', id)        AS event_id,
        customer_id,
        'APPLY'                  AS event_type,
        apply_time               AS event_time,
        application_no           AS loan_id,
        loan_amount              AS amount,
        0                        AS overdue_days,
        channel                  AS institution,
        CONCAT('申请金额:', loan_amount, ',期限:', loan_term, '月') AS detail,
        current_timestamp()      AS etl_time
    FROM ods.ods_loan_application
    WHERE dt = '${hivevar:dt}'

    UNION ALL

    -- 2.4 还款事件
    SELECT
        CONCAT('RP_', id)        AS event_id,
        customer_id,
        'REPAY'                  AS event_type,
        CAST(repay_date AS TIMESTAMP) AS event_time,
        loan_id,
        repay_amount             AS amount,
        0                        AS overdue_days,
        CAST(NULL AS STRING)     AS institution,
        CONCAT('期次', installment_no, ',还款:', repay_amount) AS detail,
        current_timestamp()      AS etl_time
    FROM ods.ods_repayment_record
    WHERE dt = '${hivevar:dt}'
) t;

-- ============================================================================
-- 3. 交易明细表 (dwd_transaction_detail)
-- DDL: transaction_id, customer_id, loan_id, transaction_type, amount,
--       principal, interest, transaction_time, channel, etl_time
-- ============================================================================
INSERT OVERWRITE TABLE dwd.dwd_transaction_detail PARTITION(dt='${hivevar:dt}')
SELECT
    CONCAT('TX_', id)            AS transaction_id,
    customer_id,
    loan_id,
    CASE
        WHEN overdue_days > 0 THEN 'REPAYMENT'
        ELSE 'REPAYMENT'
    END                          AS transaction_type,
    repay_amount                 AS amount,
    principal,
    interest,
    CAST(repay_date AS TIMESTAMP) AS transaction_time,
    'SYSTEM'                     AS channel,
    current_timestamp()          AS etl_time
FROM ods.ods_repayment_record
WHERE dt = '${hivevar:dt}';

-- ============================================================================
-- 4. 标准化客户画像表 (dwd_customer_profile)
-- DDL: customer_id, customer_name_masked, id_card_masked, phone_masked, gender,
--       age, education_code, education_name, marital_status, province, city,
--       industry_code, industry_name, annual_income, employer, etl_time
-- ============================================================================
INSERT OVERWRITE TABLE dwd.dwd_customer_profile PARTITION(dt='${hivevar:dt}')
SELECT
    customer_id,
    CONCAT(SUBSTR(customer_name, 1, 1), '**') AS customer_name_masked,
    regexp_replace(id_card, '(^.{3})(.*)(.{4}$)', '$1**************$3') AS id_card_masked,
    concat(substr(phone, 1, 3), '****', substr(phone, 7)) AS phone_masked,
    gender,
    CAST(YEAR(CURRENT_DATE()) - YEAR(CAST(birth_date AS DATE)) AS INT) AS age,
    education                  AS education_code,
    CASE education
        WHEN '高中及以下' THEN '1'
        WHEN '大专' THEN '2'
        WHEN '本科' THEN '3'
        WHEN '硕士' THEN '4'
        WHEN '博士' THEN '5'
        ELSE education
    END                        AS education_name,
    marital_status,
    -- 从地址解析省份（简单取前2-3个字符）
    CASE
        WHEN address LIKE '%市%' THEN '直辖市'
        WHEN address LIKE '%省%' THEN SUBSTR(address, 1, INSTR(address, '省'))
        ELSE SUBSTR(address, 1, 3)
    END                        AS province,
    CASE
        WHEN address LIKE '%区%' THEN SUBSTR(address, INSTR(address, '市')+1, INSTR(address, '区')-INSTR(address, '市'))
        WHEN address LIKE '%市%' THEN SUBSTR(address, 1, INSTR(address, '市'))
        ELSE SUBSTR(address, 1, 6)
    END                        AS city,
    industry                   AS industry_code,
    industry                   AS industry_name,
    annual_income,
    employer,
    current_timestamp()        AS etl_time
FROM ods.ods_customer_info
WHERE dt = '${hivevar:dt}';
