-- ============================================================================
-- ETL脚本: DWD层 -> DWS层 聚合汇总
-- 描述: 对明细数据进行多维度聚合，生成汇总宽表
-- 执行方式: spark-sql -f etl_dwd_to_dws.sql --hivevar dt=2026-06-05
-- 创建时间: 2026-06-06
-- ============================================================================

SET spark.sql.adaptive.enabled=true;
SET spark.sql.shuffle.partitions=8;

-- ============================================================================
-- 1. 客户信用汇总表 (dws_customer_credit_summary)
-- DDL: customer_id, stat_month, loan_count_1m/3m/6m/12m, loan_amount_total_12m,
--      credit_query_count_1m/3m, overdue_count_1m/3m/6m/12m, max_overdue_days_12m,
--      apply_count_1m/3m, repay_on_time_rate_12m, avg_loan_amount_12m, etl_time
-- ============================================================================
INSERT OVERWRITE TABLE dws.dws_customer_credit_summary PARTITION(dt='${hivevar:dt}')
SELECT
    t.customer_id,
    SUBSTR('${hivevar:dt}', 1, 7) AS stat_month,
    SUM(CASE WHEN t.event_type='APPLY' AND t.event_time >= date_sub('${hivevar:dt}',30) THEN 1 ELSE 0 END) AS loan_count_1m,
    SUM(CASE WHEN t.event_type='APPLY' AND t.event_time >= date_sub('${hivevar:dt}',90) THEN 1 ELSE 0 END) AS loan_count_3m,
    SUM(CASE WHEN t.event_type='APPLY' AND t.event_time >= date_sub('${hivevar:dt}',180) THEN 1 ELSE 0 END) AS loan_count_6m,
    SUM(CASE WHEN t.event_type='APPLY' AND t.event_time >= date_sub('${hivevar:dt}',365) THEN 1 ELSE 0 END) AS loan_count_12m,
    COALESCE(SUM(CASE WHEN t.event_type='APPLY' AND t.event_time >= date_sub('${hivevar:dt}',365) THEN t.amount ELSE 0 END), 0) AS loan_amount_total_12m,
    SUM(CASE WHEN t.event_type='CREDIT_QUERY' AND t.event_time >= date_sub('${hivevar:dt}',30) THEN 1 ELSE 0 END) AS credit_query_count_1m,
    SUM(CASE WHEN t.event_type='CREDIT_QUERY' AND t.event_time >= date_sub('${hivevar:dt}',90) THEN 1 ELSE 0 END) AS credit_query_count_3m,
    SUM(CASE WHEN t.event_type='OVERDUE' AND t.event_time >= date_sub('${hivevar:dt}',30) THEN 1 ELSE 0 END) AS overdue_count_1m,
    SUM(CASE WHEN t.event_type='OVERDUE' AND t.event_time >= date_sub('${hivevar:dt}',90) THEN 1 ELSE 0 END) AS overdue_count_3m,
    SUM(CASE WHEN t.event_type='OVERDUE' AND t.event_time >= date_sub('${hivevar:dt}',180) THEN 1 ELSE 0 END) AS overdue_count_6m,
    SUM(CASE WHEN t.event_type='OVERDUE' AND t.event_time >= date_sub('${hivevar:dt}',365) THEN 1 ELSE 0 END) AS overdue_count_12m,
    COALESCE(MAX(CASE WHEN t.event_type='OVERDUE' AND t.event_time >= date_sub('${hivevar:dt}',365) THEN t.overdue_days ELSE 0 END), 0) AS max_overdue_days_12m,
    SUM(CASE WHEN t.event_type='APPLY' AND t.event_time >= date_sub('${hivevar:dt}',30) THEN 1 ELSE 0 END) AS apply_count_1m,
    SUM(CASE WHEN t.event_type='APPLY' AND t.event_time >= date_sub('${hivevar:dt}',90) THEN 1 ELSE 0 END) AS apply_count_3m,
    CASE
        WHEN SUM(CASE WHEN t.event_type='REPAY' THEN 1 ELSE 0 END) > 0
        THEN CAST(SUM(CASE WHEN t.event_type='REPAY' AND t.overdue_days=0 THEN 1 ELSE 0 END) AS DECIMAL(5,4))
             / SUM(CASE WHEN t.event_type='REPAY' THEN 1 ELSE 0 END)
        ELSE CAST(1.0 AS DECIMAL(5,4))
    END AS repay_on_time_rate_12m,
    CASE
        WHEN SUM(CASE WHEN t.event_type='APPLY' AND t.event_time >= date_sub('${hivevar:dt}',365) THEN 1 ELSE 0 END) > 0
        THEN COALESCE(SUM(CASE WHEN t.event_type='APPLY' AND t.event_time >= date_sub('${hivevar:dt}',365) THEN t.amount ELSE 0 END), 0)
             / SUM(CASE WHEN t.event_type='APPLY' AND t.event_time >= date_sub('${hivevar:dt}',365) THEN 1 ELSE 0 END)
        ELSE CAST(0 AS DECIMAL(18,2))
    END AS avg_loan_amount_12m,
    current_timestamp() AS etl_time
FROM dwd.dwd_credit_event t
WHERE t.dt = '${hivevar:dt}'
GROUP BY t.customer_id;

-- ============================================================================
-- 2. 产品贷款汇总表 (dws_product_loan_summary)
-- DDL: product_id, product_name, stat_date, apply_count, approve_count,
--      reject_count, approve_rate, total_loan_amount, avg_loan_amount,
--      avg_loan_term, overdue_count, overdue_rate, etl_time
-- ============================================================================
INSERT OVERWRITE TABLE dws.dws_product_loan_summary PARTITION(dt='${hivevar:dt}')
SELECT
    product_id,
    product_name,
    '${hivevar:dt}'             AS stat_date,
    COUNT(1)                    AS apply_count,
    SUM(CASE WHEN status='APPROVED' THEN 1 ELSE 0 END) AS approve_count,
    SUM(CASE WHEN status='REJECTED' THEN 1 ELSE 0 END) AS reject_count,
    CASE WHEN COUNT(1)>0 THEN CAST(SUM(CASE WHEN status='APPROVED' THEN 1 ELSE 0 END) AS DECIMAL(5,4))/COUNT(1) ELSE CAST(0 AS DECIMAL(5,4)) END AS approve_rate,
    COALESCE(SUM(loan_amount), 0) AS total_loan_amount,
    COALESCE(AVG(loan_amount), 0) AS avg_loan_amount,
    COALESCE(AVG(loan_term), 0)   AS avg_loan_term,
    0                           AS overdue_count,
    CAST(0 AS DECIMAL(5,4))     AS overdue_rate,
    current_timestamp()         AS etl_time
FROM dwd.dwd_loan_application_detail
WHERE dt = '${hivevar:dt}'
GROUP BY product_id, product_name;

-- ============================================================================
-- 3. 渠道汇总表 (dws_channel_summary)
-- DDL: channel_code, channel_name, stat_date, apply_count, approve_count,
--      approve_rate, total_loan_amount, avg_process_time_hours, customer_count,
--      new_customer_count, etl_time
-- ============================================================================
INSERT OVERWRITE TABLE dws.dws_channel_summary PARTITION(dt='${hivevar:dt}')
SELECT
    channel_code,
    channel_name,
    '${hivevar:dt}'             AS stat_date,
    COUNT(1)                    AS apply_count,
    SUM(CASE WHEN status='APPROVED' THEN 1 ELSE 0 END) AS approve_count,
    CASE WHEN COUNT(1)>0 THEN CAST(SUM(CASE WHEN status='APPROVED' THEN 1 ELSE 0 END) AS DECIMAL(5,4))/COUNT(1) ELSE CAST(0 AS DECIMAL(5,4)) END AS approve_rate,
    COALESCE(SUM(loan_amount), 0) AS total_loan_amount,
    CAST(0 AS DECIMAL(10,2))    AS avg_process_time_hours,
    COUNT(DISTINCT customer_id) AS customer_count,
    COUNT(DISTINCT customer_id) AS new_customer_count,
    current_timestamp()         AS etl_time
FROM dwd.dwd_loan_application_detail
WHERE dt = '${hivevar:dt}'
GROUP BY channel_code, channel_name;
