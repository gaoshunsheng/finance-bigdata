-- ============================================================================
-- ETL脚本: DWD层 -> DWS层 聚合汇总
-- 描述: 对明细数据进行多维度聚合，生成汇总宽表
-- 执行方式: spark-sql -f etl_dwd_to_dws.sql -d dt=2026-06-05
-- 创建时间: 2026-06-06
-- ============================================================================

SET spark.sql.adaptive.enabled=true;
SET spark.sql.shuffle.partitions=200;

-- ============================================================================
-- 1. 客户信用汇总表 (dws_customer_credit_summary)
-- 描述: 按客户维度聚合信用行为，计算多时间窗口指标
-- ============================================================================
INSERT OVERWRITE TABLE dws.dws_customer_credit_summary PARTITION(dt='${hivevar:dt}')
SELECT
    t.customer_id,

    -- ==================== 申请指标 ====================
    -- 总申请次数
    COUNT(1) AS total_apply_count,
    -- 总申请金额
    SUM(t.loan_amount) AS total_apply_amount,
    -- 近1个月申请次数
    SUM(CASE WHEN t.apply_time >= date_sub('${hivevar:dt}', 30) THEN 1 ELSE 0 END) AS apply_count_1m,
    -- 近1个月申请金额
    SUM(CASE WHEN t.apply_time >= date_sub('${hivevar:dt}', 30) THEN t.loan_amount ELSE 0 END) AS apply_amount_1m,
    -- 近3个月申请次数
    SUM(CASE WHEN t.apply_time >= date_sub('${hivevar:dt}', 90) THEN 1 ELSE 0 END) AS apply_count_3m,
    -- 近3个月申请金额
    SUM(CASE WHEN t.apply_time >= date_sub('${hivevar:dt}', 90) THEN t.loan_amount ELSE 0 END) AS apply_amount_3m,
    -- 近6个月申请次数
    SUM(CASE WHEN t.apply_time >= date_sub('${hivevar:dt}', 180) THEN 1 ELSE 0 END) AS apply_count_6m,
    -- 近6个月申请金额
    SUM(CASE WHEN t.apply_time >= date_sub('${hivevar:dt}', 180) THEN t.loan_amount ELSE 0 END) AS apply_amount_6m,
    -- 近12个月申请次数
    SUM(CASE WHEN t.apply_time >= date_sub('${hivevar:dt}', 365) THEN 1 ELSE 0 END) AS apply_count_12m,
    -- 近12个月申请金额
    SUM(CASE WHEN t.apply_time >= date_sub('${hivevar:dt}', 365) THEN t.loan_amount ELSE 0 END) AS apply_amount_12m,

    -- ==================== 征信查询指标 ====================
    -- 总征信查询次数
    SUM(CASE WHEN t.event_type = 'CREDIT_QUERY' THEN 1 ELSE 0 END) AS credit_query_count,
    -- 近1个月征信查询次数
    SUM(CASE WHEN t.event_type = 'CREDIT_QUERY' AND t.event_time >= date_sub('${hivevar:dt}', 30) THEN 1 ELSE 0 END) AS credit_query_count_1m,
    -- 近3个月征信查询次数
    SUM(CASE WHEN t.event_type = 'CREDIT_QUERY' AND t.event_time >= date_sub('${hivevar:dt}', 90) THEN 1 ELSE 0 END) AS credit_query_count_3m,
    -- 近6个月征信查询次数
    SUM(CASE WHEN t.event_type = 'CREDIT_QUERY' AND t.event_time >= date_sub('${hivevar:dt}', 180) THEN 1 ELSE 0 END) AS credit_query_count_6m,
    -- 近12个月征信查询次数
    SUM(CASE WHEN t.event_type = 'CREDIT_QUERY' AND t.event_time >= date_sub('${hivevar:dt}', 365) THEN 1 ELSE 0 END) AS credit_query_count_12m,

    -- ==================== 逾期指标 ====================
    -- 总逾期次数
    SUM(CASE WHEN t.event_type = 'OVERDUE' THEN 1 ELSE 0 END) AS overdue_count,
    -- 近1个月逾期次数
    SUM(CASE WHEN t.event_type = 'OVERDUE' AND t.event_time >= date_sub('${hivevar:dt}', 30) THEN 1 ELSE 0 END) AS overdue_count_1m,
    -- 近3个月逾期次数
    SUM(CASE WHEN t.event_type = 'OVERDUE' AND t.event_time >= date_sub('${hivevar:dt}', 90) THEN 1 ELSE 0 END) AS overdue_count_3m,
    -- 近6个月逾期次数
    SUM(CASE WHEN t.event_type = 'OVERDUE' AND t.event_time >= date_sub('${hivevar:dt}', 180) THEN 1 ELSE 0 END) AS overdue_count_6m,
    -- 近12个月逾期次数
    SUM(CASE WHEN t.event_type = 'OVERDUE' AND t.event_time >= date_sub('${hivevar:dt}', 365) THEN 1 ELSE 0 END) AS overdue_count_12m,

    -- ==================== 还款指标 ====================
    -- 总还款次数
    SUM(CASE WHEN t.event_type = 'REPAY' THEN 1 ELSE 0 END) AS repay_count,
    -- 近1个月还款次数
    SUM(CASE WHEN t.event_type = 'REPAY' AND t.event_time >= date_sub('${hivevar:dt}', 30) THEN 1 ELSE 0 END) AS repay_count_1m,
    -- 近3个月还款次数
    SUM(CASE WHEN t.event_type = 'REPAY' AND t.event_time >= date_sub('${hivevar:dt}', 90) THEN 1 ELSE 0 END) AS repay_count_3m,
    -- 近6个月还款次数
    SUM(CASE WHEN t.event_type = 'REPAY' AND t.event_time >= date_sub('${hivevar:dt}', 180) THEN 1 ELSE 0 END) AS repay_count_6m,
    -- 近12个月还款次数
    SUM(CASE WHEN t.event_type = 'REPAY' AND t.event_time >= date_sub('${hivevar:dt}', 365) THEN 1 ELSE 0 END) AS repay_count_12m,

    current_timestamp() AS etl_time
FROM (
    -- 子查询: 合并贷款申请明细和信用事件明细的数据
    SELECT
        d.customer_id,
        d.apply_time,
        d.loan_amount,
        CAST(NULL AS STRING) AS event_type,
        CAST(NULL AS TIMESTAMP) AS event_time
    FROM dwd.dwd_loan_application_detail d
    WHERE d.dt = '${hivevar:dt}'

    UNION ALL

    SELECT
        e.customer_id,
        CAST(NULL AS TIMESTAMP) AS apply_time,
        CAST(0 AS DECIMAL(18,2)) AS loan_amount,
        e.event_type,
        e.event_time
    FROM dwd.dwd_credit_event e
    WHERE e.dt = '${hivevar:dt}'
) t
GROUP BY t.customer_id;


-- ============================================================================
-- 2. 产品贷款汇总表 (dws_product_loan_summary)
-- 描述: 按产品维度聚合贷款申请和审批情况
-- ============================================================================
INSERT OVERWRITE TABLE dws.dws_product_loan_summary PARTITION(dt='${hivevar:dt}')
SELECT
    product_id,
    -- 总申请笔数
    COUNT(1)                                                          AS total_apply_count,
    -- 总申请金额
    SUM(loan_amount)                                                  AS total_apply_amount,
    -- 平均申请金额
    AVG(loan_amount)                                                  AS avg_apply_amount,
    -- 审批通过笔数
    SUM(CASE WHEN status IN ('APPROVED','PASS','通过') THEN 1 ELSE 0 END) AS approved_count,
    -- 审批通过金额
    SUM(CASE WHEN status IN ('APPROVED','PASS','通过') THEN loan_amount ELSE 0 END) AS approved_amount,
    -- 审批拒绝笔数
    SUM(CASE WHEN status IN ('REJECTED','REJECT','拒绝') THEN 1 ELSE 0 END) AS rejected_count,
    -- 人工复核笔数
    SUM(CASE WHEN status IN ('MANUAL_REVIEW','复核中') THEN 1 ELSE 0 END) AS manual_review_count,
    -- 审批通过率 (DECIMAL精度)
    CAST(
        SUM(CASE WHEN status IN ('APPROVED','PASS','通过') THEN 1 ELSE 0 END)
        / COUNT(1) AS DECIMAL(10,4)
    )                                                                 AS approve_rate,
    -- 平均贷款期限
    AVG(loan_term)                                                    AS avg_loan_term,
    current_timestamp()                                                AS etl_time
FROM dwd.dwd_loan_application_detail
WHERE dt = '${hivevar:dt}'
GROUP BY product_id;


-- ============================================================================
-- 3. 渠道汇总表 (dws_channel_summary)
-- 描述: 按申请渠道维度聚合贷款申请和审批情况
-- ============================================================================
INSERT OVERWRITE TABLE dws.dws_channel_summary PARTITION(dt='${hivevar:dt}')
SELECT
    channel,
    -- 总申请笔数
    COUNT(1)                                                          AS total_apply_count,
    -- 总申请金额
    SUM(loan_amount)                                                  AS total_apply_amount,
    -- 平均申请金额
    AVG(loan_amount)                                                  AS avg_apply_amount,
    -- 审批通过笔数
    SUM(CASE WHEN status IN ('APPROVED','PASS','通过') THEN 1 ELSE 0 END) AS approved_count,
    -- 审批拒绝笔数
    SUM(CASE WHEN status IN ('REJECTED','REJECT','拒绝') THEN 1 ELSE 0 END) AS rejected_count,
    -- 审批通过率
    CAST(
        SUM(CASE WHEN status IN ('APPROVED','PASS','通过') THEN 1 ELSE 0 END)
        / COUNT(1) AS DECIMAL(10,4)
    )                                                                 AS approve_rate,
    -- 关联还款数据计算逾期率
    -- 近30天逾期笔数（通过关联交易明细表）
    SUM(CASE WHEN overdue_flag = 1 THEN 1 ELSE 0 END)                AS overdue_count,
    -- 逾期率
    CAST(
        SUM(CASE WHEN overdue_flag = 1 THEN 1 ELSE 0 END)
        / COUNT(1) AS DECIMAL(10,4)
    )                                                                 AS overdue_rate,
    current_timestamp()                                                AS etl_time
FROM (
    SELECT
        a.channel,
        a.loan_amount,
        a.status,
        a.customer_id,
        -- 标记是否有逾期记录
        CASE WHEN t.overdue_cnt > 0 THEN 1 ELSE 0 END AS overdue_flag
    FROM dwd.dwd_loan_application_detail a
    LEFT JOIN (
        SELECT customer_id, COUNT(1) AS overdue_cnt
        FROM dwd.dwd_transaction_detail
        WHERE dt = '${hivevar:dt}' AND is_overdue = true
        GROUP BY customer_id
    ) t ON a.customer_id = t.customer_id
    WHERE a.dt = '${hivevar:dt}'
) sub
GROUP BY channel;
