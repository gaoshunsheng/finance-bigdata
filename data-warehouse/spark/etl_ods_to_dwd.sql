-- ============================================================================
-- ETL脚本: ODS层 -> DWD层 清洗转换
-- 描述: 将原始数据(ODS)清洗、脱敏、标准化后写入明细数据层(DWD)
-- 执行方式: spark-sql -f etl_ods_to_dwd.sql -d dt=2026-06-05
-- 创建时间: 2026-06-06
-- ============================================================================

-- 开启自适应查询执行，提升大表关联性能
SET spark.sql.adaptive.enabled=true;
SET spark.sql.shuffle.partitions=200;

-- ============================================================================
-- 1. 贷款申请明细表 (dwd_loan_application_detail)
-- 描述: 关联客户信息，脱敏敏感字段，标准化渠道和学历编码
-- ============================================================================
INSERT OVERWRITE TABLE dwd.dwd_loan_application_detail PARTITION(dt='${hivevar:dt}')
SELECT
    a.id                    AS id,
    a.application_no        AS application_no,
    a.customer_id           AS customer_id,
    a.product_id            AS product_id,
    -- 渠道编码标准化: 统一映射为可读名称
    CASE a.channel
        WHEN 'APP'     THEN '手机APP'
        WHEN 'WEB'     THEN '官方网站'
        WHEN 'WECHAT'  THEN '微信公众号'
        WHEN 'OFFLINE' THEN '线下网点'
        WHEN 'PARTNER' THEN '合作渠道'
        ELSE '其他渠道'
    END                      AS channel,
    a.loan_amount           AS loan_amount,
    a.loan_term             AS loan_term,
    a.purpose               AS purpose,
    a.apply_time            AS apply_time,
    a.status                AS status,
    -- 关联客户信息维度
    c.customer_name         AS customer_name,
    -- 身份证号脱敏: 保留前3位和后4位，中间用*替换
    regexp_replace(c.id_card, '(^.{3})(.*)(.{4}$)', '$1**************$3') AS id_card_masked,
    -- 手机号脱敏: 保留前3位和后4位，中间用*替换
    regexp_replace(c.phone, '(^.{3})(.*)(.{4}$)', '$1****$3') AS phone_masked,
    c.gender                AS gender,
    c.birth_date            AS birth_date,
    -- 学历编码映射: 将编码转换为可读名称
    CASE c.education
        WHEN '1' THEN '高中及以下'
        WHEN '2' THEN '大专'
        WHEN '3' THEN '本科'
        WHEN '4' THEN '硕士'
        WHEN '5' THEN '博士'
        ELSE '未知'
    END                      AS education,
    c.marital_status        AS marital_status,
    c.address               AS address,
    c.employer              AS employer,
    c.industry              AS industry,
    c.annual_income         AS annual_income,
    current_timestamp()     AS etl_time
FROM ods.ods_loan_application a
LEFT JOIN ods.ods_customer_info c
    ON a.customer_id = c.customer_id
    AND c.dt = '${hivevar:dt}'
WHERE a.dt = '${hivevar:dt}';


-- ============================================================================
-- 2. 信用事件明细表 (dwd_credit_event)
-- 描述: 统一多种信用行为为事件流，便于后续分析和特征计算
-- 事件类型: CREDIT_QUERY(征信查询), OVERDUE(逾期), APPLY(申请), REPAY(还款)
-- ============================================================================
INSERT OVERWRITE TABLE dwd.dwd_credit_event PARTITION(dt='${hivevar:dt}')
SELECT * FROM (
    -- 2.1 征信查询事件
    SELECT
        id                    AS id,
        customer_id           AS customer_id,
        'CREDIT_QUERY'        AS event_type,
        query_time            AS event_time,
        query_institution     AS event_source,
        report_no             AS event_no,
        CAST(overdue_count AS STRING)  AS event_detail,
        current_timestamp()   AS etl_time
    FROM ods.ods_credit_report
    WHERE dt = '${hivevar:dt}'

    UNION ALL

    -- 2.2 逾期事件: 从还款记录中提取逾期天数 > 0 的记录
    SELECT
        id                              AS id,
        customer_id                     AS customer_id,
        'OVERDUE'                       AS event_type,
        CAST(due_date AS TIMESTAMP)     AS event_time,
        loan_id                         AS event_source,
        CONCAT('期次', installment_no)  AS event_no,
        CONCAT('逾期', overdue_days, '天') AS event_detail,
        current_timestamp()             AS etl_time
    FROM ods.ods_repayment_record
    WHERE dt = '${hivevar:dt}'
      AND overdue_days > 0

    UNION ALL

    -- 2.3 贷款申请事件
    SELECT
        id                    AS id,
        customer_id           AS customer_id,
        'APPLY'               AS event_type,
        apply_time            AS event_time,
        channel               AS event_source,
        application_no        AS event_no,
        CONCAT('申请金额:', loan_amount, ',期限:', loan_term, '月') AS event_detail,
        current_timestamp()   AS etl_time
    FROM ods.ods_loan_application
    WHERE dt = '${hivevar:dt}'

    UNION ALL

    -- 2.4 还款事件
    SELECT
        id                              AS id,
        customer_id                     AS customer_id,
        'REPAY'                         AS event_type,
        CAST(repay_date AS TIMESTAMP)   AS event_time,
        loan_id                         AS event_source,
        CONCAT('期次', installment_no)  AS event_no,
        CONCAT('还款金额:', repay_amount) AS event_detail,
        current_timestamp()             AS etl_time
    FROM ods.ods_repayment_record
    WHERE dt = '${hivevar:dt}'
) t;


-- ============================================================================
-- 3. 还款交易明细表 (dwd_transaction_detail)
-- 描述: 从还款记录中提取标准化交易明细
-- ============================================================================
INSERT OVERWRITE TABLE dwd.dwd_transaction_detail PARTITION(dt='${hivevar:dt}')
SELECT
    id                  AS id,
    loan_id             AS loan_id,
    customer_id         AS customer_id,
    installment_no      AS installment_no,
    due_date            AS due_date,
    repay_date          AS repay_date,
    repay_amount        AS repay_amount,
    principal           AS principal,
    interest            AS interest,
    overdue_days        AS overdue_days,
    status              AS status,
    -- 计算还款是否逾期
    CASE
        WHEN overdue_days > 0 THEN true
        ELSE false
    END                 AS is_overdue,
    -- 计算实际逾期金额（逾期时还款金额视为逾期金额）
    CASE
        WHEN overdue_days > 0 THEN repay_amount
        ELSE CAST(0 AS DECIMAL(18,2))
    END                 AS overdue_amount,
    current_timestamp() AS etl_time
FROM ods.ods_repayment_record
WHERE dt = '${hivevar:dt}';
