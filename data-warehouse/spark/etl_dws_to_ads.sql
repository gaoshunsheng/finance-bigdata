-- ============================================================================
-- ETL脚本: DWS层 -> ADS层 应用数据层
-- 描述: 面向业务应用的高度聚合宽表和指标报表
-- 执行方式: spark-sql -f etl_dws_to_ads.sql --hivevar dt=2026-06-05
-- 创建时间: 2026-06-06
-- ============================================================================

SET spark.sql.adaptive.enabled=true;
SET spark.sql.shuffle.partitions=8;

-- ============================================================================
-- 1. 信用评分宽表 (ads_credit_score_wide_table)
-- DDL: customer_id, stat_date, age, gender, education, annual_income,
--      loan_count_12m, credit_query_count_3m, overdue_count_6m,
--      max_overdue_days_12m, apply_count_1m, repay_on_time_rate,
--      total_debt, credit_score, risk_level, etl_time
-- ============================================================================
INSERT OVERWRITE TABLE ads.ads_credit_score_wide_table PARTITION(dt='${hivevar:dt}')
SELECT
    p.customer_id,
    '${hivevar:dt}'             AS stat_date,
    p.age,
    p.gender                    AS education,
    p.education_name            AS education,
    p.annual_income,
    COALESCE(s.loan_count_12m, 0) AS loan_count_12m,
    COALESCE(s.credit_query_count_3m, 0) AS credit_query_count_3m,
    COALESCE(s.overdue_count_6m, 0) AS overdue_count_6m,
    COALESCE(s.max_overdue_days_12m, 0) AS max_overdue_days_12m,
    COALESCE(s.apply_count_1m, 0) AS apply_count_1m,
    COALESCE(s.repay_on_time_rate_12m, CAST(1.0 AS DECIMAL(5,4))) AS repay_on_time_rate,
    CAST(0 AS DECIMAL(18,2))    AS total_debt,
    CAST(0 AS DECIMAL(10,2))    AS credit_score,
    CASE
        WHEN COALESCE(s.overdue_count_3m, 0) >= 3 OR COALESCE(s.credit_query_count_1m, 0) >= 5 THEN 'HIGH'
        WHEN COALESCE(s.overdue_count_3m, 0) >= 1 OR COALESCE(s.credit_query_count_3m, 0) >= 10 THEN 'MEDIUM'
        ELSE 'LOW'
    END AS risk_level,
    current_timestamp()         AS etl_time
FROM dwd.dwd_customer_profile p
LEFT JOIN dws.dws_customer_credit_summary s
    ON p.customer_id = s.customer_id AND s.dt = '${hivevar:dt}'
WHERE p.dt = '${hivevar:dt}';

-- ============================================================================
-- 2. 风险指标汇总表 (ads_risk_indicator_summary)
-- DDL: stat_date, product_id, product_name, total_apply_count, approve_count,
--      approve_rate, total_loan_amount, overdue_30_count, overdue_30_rate,
--      overdue_90_count, overdue_90_rate, avg_credit_score, ks_value, auc_value, etl_time
-- ============================================================================
INSERT OVERWRITE TABLE ads.ads_risk_indicator_summary PARTITION(dt='${hivevar:dt}')
SELECT
    '${hivevar:dt}'             AS stat_date,
    product_id,
    product_name,
    apply_count                 AS total_apply_count,
    approve_count,
    approve_rate,
    total_loan_amount,
    0                           AS overdue_30_count,
    CAST(0 AS DECIMAL(5,4))     AS overdue_30_rate,
    0                           AS overdue_90_count,
    CAST(0 AS DECIMAL(5,4))     AS overdue_90_rate,
    CAST(0 AS DECIMAL(10,2))    AS avg_credit_score,
    CAST(0 AS DECIMAL(5,4))     AS ks_value,
    CAST(0 AS DECIMAL(5,4))     AS auc_value,
    current_timestamp()         AS etl_time
FROM dws.dws_product_loan_summary
WHERE dt = '${hivevar:dt}';

-- ============================================================================
-- 3. 决策分析表 (ads_decision_analysis)
-- DDL: stat_date, flow_id, flow_name, flow_version, decision_count, pass_count,
--      reject_count, manual_review_count, pass_rate, avg_score, avg_duration_ms,
--      top_hit_rule_1/2/3, etl_time
-- ============================================================================
INSERT OVERWRITE TABLE ads.ads_decision_analysis PARTITION(dt='${hivevar:dt}')
SELECT
    '${hivevar:dt}'             AS stat_date,
    'default_flow'              AS flow_id,
    '默认决策流'                 AS flow_name,
    1                           AS flow_version,
    COUNT(1)                    AS decision_count,
    SUM(CASE WHEN decision_result='PASS' THEN 1 ELSE 0 END) AS pass_count,
    SUM(CASE WHEN decision_result='REJECT' THEN 1 ELSE 0 END) AS reject_count,
    SUM(CASE WHEN decision_result='MANUAL_REVIEW' THEN 1 ELSE 0 END) AS manual_review_count,
    CASE WHEN COUNT(1)>0 THEN CAST(SUM(CASE WHEN decision_result='PASS' THEN 1 ELSE 0 END) AS DECIMAL(5,4))/COUNT(1) ELSE CAST(0 AS DECIMAL(5,4)) END AS pass_rate,
    COALESCE(AVG(total_score), 0) AS avg_score,
    COALESCE(CAST(AVG(duration_ms) AS BIGINT), 0) AS avg_duration_ms,
    CAST(NULL AS STRING)        AS top_hit_rule_1,
    CAST(NULL AS STRING)        AS top_hit_rule_2,
    CAST(NULL AS STRING)        AS top_hit_rule_3,
    current_timestamp()         AS etl_time
FROM ods.ods_decision_log
WHERE dt = '${hivevar:dt}'
GROUP BY flow_id, flow_version;
