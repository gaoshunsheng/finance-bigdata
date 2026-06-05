-- ============================================================================
-- ETL脚本: DWS层 -> ADS层 应用数据层
-- 描述: 面向业务应用的高度聚合宽表和指标报表
-- 执行方式: spark-sql -f etl_dws_to_ads.sql -d dt=2026-06-05
-- 创建时间: 2026-06-06
-- ============================================================================

SET spark.sql.adaptive.enabled=true;
SET spark.sql.shuffle.partitions=200;

-- ============================================================================
-- 1. 信用评分宽表 (ads_credit_score_wide_table)
-- 描述: 整合客户画像、信用汇总、风险等级的全方位宽表
-- 用途: 风控模型特征输入、客户画像分析
-- ============================================================================
INSERT OVERWRITE TABLE ads.ads_credit_score_wide_table PARTITION(dt='${hivevar:dt}')
SELECT
    p.customer_id,
    p.customer_name,
    p.gender,
    p.birth_date,
    p.education,
    p.marital_status,
    p.industry,
    p.annual_income,

    -- ==================== 信用汇总指标 ====================
    s.total_apply_count,
    s.total_apply_amount,
    s.apply_count_1m,
    s.apply_amount_1m,
    s.apply_count_3m,
    s.apply_amount_3m,
    s.apply_count_6m,
    s.apply_amount_6m,
    s.apply_count_12m,
    s.apply_amount_12m,
    s.credit_query_count,
    s.credit_query_count_1m,
    s.credit_query_count_3m,
    s.credit_query_count_6m,
    s.credit_query_count_12m,
    s.overdue_count,
    s.overdue_count_1m,
    s.overdue_count_3m,
    s.overdue_count_6m,
    s.overdue_count_12m,
    s.repay_count,
    s.repay_count_1m,
    s.repay_count_3m,
    s.repay_count_6m,
    s.repay_count_12m,

    -- ==================== 风险等级计算 ====================
    -- 规则:
    --   - 高风险: 近3个月逾期次数 >= 3，或近1个月征信查询次数 >= 5
    --   - 中风险: 近3个月逾期次数 >= 1，或近3个月征信查询次数 >= 10
    --   - 低风险: 其他
    CASE
        WHEN s.overdue_count_3m >= 3 OR s.credit_query_count_1m >= 5 THEN 'HIGH'
        WHEN s.overdue_count_3m >= 1 OR s.credit_query_count_3m >= 10 THEN 'MEDIUM'
        ELSE 'LOW'
    END AS risk_level,

    -- ==================== 衍生特征 ====================
    -- 月均申请次数（近12个月）
    CAST(s.apply_count_12m / 12.0 AS DECIMAL(10,2)) AS avg_monthly_apply_count,
    -- 逾期率（逾期次数 / 还款次数）
    CASE
        WHEN s.repay_count > 0 THEN CAST(s.overdue_count AS DECIMAL(10,4)) / s.repay_count
        ELSE CAST(0 AS DECIMAL(10,4))
    END AS overdue_ratio,
    -- 征信查询密度（近6个月查询次数 / 月数）
    CAST(s.credit_query_count_6m / 6.0 AS DECIMAL(10,2)) AS query_density_6m,

    current_timestamp() AS etl_time
FROM (
    -- 子查询: 客户画像信息，取最新一天的分区
    SELECT
        customer_id,
        customer_name,
        gender,
        birth_date,
        education,
        marital_status,
        industry,
        annual_income
    FROM dwd.dwd_loan_application_detail
    WHERE dt = '${hivevar:dt}'
) p
LEFT JOIN (
    SELECT *
    FROM dws.dws_customer_credit_summary
    WHERE dt = '${hivevar:dt}'
) s ON p.customer_id = s.customer_id;


-- ============================================================================
-- 2. 风险指标汇总表 (ads_risk_indicator_summary)
-- 描述: 按产品和日期维度聚合风险指标，用于风险监控看板
-- ============================================================================
INSERT OVERWRITE TABLE ads.ads_risk_indicator_summary PARTITION(dt='${hivevar:dt}')
SELECT
    p.product_id,
    p.total_apply_count,
    p.total_apply_amount,
    p.avg_apply_amount,
    p.approved_count,
    p.approved_amount,
    p.approve_rate,
    p.avg_loan_term,

    -- 产品维度的逾期指标（关联交易明细）
    COALESCE(t.overdue_loan_count, 0)   AS overdue_loan_count,
    COALESCE(t.overdue_total_amount, 0) AS overdue_total_amount,
    -- 产品逾期率
    CASE
        WHEN p.approved_count > 0
        THEN CAST(COALESCE(t.overdue_loan_count, 0) AS DECIMAL(10,4)) / p.approved_count
        ELSE CAST(0 AS DECIMAL(10,4))
    END AS overdue_rate,

    current_timestamp() AS etl_time
FROM (
    -- 产品汇总数据
    SELECT *
    FROM dws.dws_product_loan_summary
    WHERE dt = '${hivevar:dt}'
) p
LEFT JOIN (
    -- 逾期统计: 按客户聚合后再汇总到产品
    -- 此处简化处理，通过loan_application_detail关联产品ID
    SELECT
        d.product_id,
        COUNT(DISTINCT d.customer_id) AS overdue_loan_count,
        SUM(CASE WHEN td.is_overdue THEN td.overdue_amount ELSE 0 END) AS overdue_total_amount
    FROM dwd.dwd_transaction_detail td
    INNER JOIN dwd.dwd_loan_application_detail d
        ON td.customer_id = d.customer_id
        AND d.dt = '${hivevar:dt}'
    WHERE td.dt = '${hivevar:dt}'
      AND td.is_overdue = true
    GROUP BY d.product_id
) t ON p.product_id = t.product_id;


-- ============================================================================
-- 3. 决策分析表 (ads_decision_analysis)
-- 描述: 汇总决策引擎执行日志，分析决策效果和规则命中情况
-- ============================================================================
INSERT OVERWRITE TABLE ads.ads_decision_analysis PARTITION(dt='${hivevar:dt}')
SELECT
    flow_id,
    flow_version,
    -- 总决策次数
    COUNT(1)                                                    AS total_decision_count,
    -- 通过次数
    SUM(CASE WHEN decision_result = 'PASS' THEN 1 ELSE 0 END)  AS pass_count,
    -- 拒绝次数
    SUM(CASE WHEN decision_result = 'REJECT' THEN 1 ELSE 0 END) AS reject_count,
    -- 人工复核次数
    SUM(CASE WHEN decision_result = 'MANUAL_REVIEW' THEN 1 ELSE 0 END) AS manual_review_count,
    -- 通过率
    CAST(
        SUM(CASE WHEN decision_result = 'PASS' THEN 1 ELSE 0 END)
        / COUNT(1) AS DECIMAL(10,4)
    )                                                           AS pass_rate,
    -- 拒绝率
    CAST(
        SUM(CASE WHEN decision_result = 'REJECT' THEN 1 ELSE 0 END)
        / COUNT(1) AS DECIMAL(10,4)
    )                                                           AS reject_rate,
    -- 平均综合评分
    AVG(total_score)                                            AS avg_score,
    -- 平均决策耗时(毫秒)
    AVG(duration_ms)                                            AS avg_duration_ms,
    -- 最大决策耗时
    MAX(duration_ms)                                            AS max_duration_ms,
    -- 平均执行节点数
    AVG(node_count)                                             AS avg_node_count,
    -- 最小综合评分
    MIN(total_score)                                            AS min_score,
    -- 最大综合评分
    MAX(total_score)                                            AS max_score,
    current_timestamp()                                         AS etl_time
FROM ods.ods_decision_log
WHERE dt = '${hivevar:dt}'
GROUP BY flow_id, flow_version;
