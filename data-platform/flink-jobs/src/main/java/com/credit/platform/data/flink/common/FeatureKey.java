package com.credit.platform.data.flink.common;

/**
 * 特征 Key 命名规范 — 统一 Redis 和 HBase 中的 Key/RowKey 格式。
 *
 * <p>Redis Key 格式: {@code feature:{featureType}:{customerId}}
 * <p>HBase RowKey 格式: {@code {reversedCustomerId}_{featureType}_{timestamp}}
 *
 * <p>示例:
 * <ul>
 *   <li>Redis: {@code feature:credit_query_3m:CUST_001}</li>
 *   <li>HBase: {@code 100_TSUC_credit_query_3m_20260606120000}</li>
 * </ul>
 */
public final class FeatureKey {

    private FeatureKey() {
    }

    /**
     * 生成 Redis 特征 Key。
     *
     * @param featureType 特征类型（如 credit_query_3m）
     * @param customerId  客户 ID
     * @return Redis Key
     */
    public static String redisKey(String featureType, String customerId) {
        return "feature:" + featureType + ":" + customerId;
    }

    /**
     * 生成 HBase RowKey — 反转客户 ID 保证均衡分布，使用确定性标识符保证幂等。
     *
     * @param customerId  客户 ID
     * @param featureType 特征类型
     * @param windowId    窗口标识符（如 windowEnd 时间戳字符串），用于保证幂等
     * @return HBase RowKey
     */
    public static String hbaseRowKey(String customerId, String featureType, String windowId) {
        return reverse(customerId) + "_" + featureType + "_" + windowId;
    }

    /**
     * 反转字符串，用于 HBase RowKey 前缀打散热点。
     */
    public static String reverse(String str) {
        if (str == null) {
            return "";
        }
        return new StringBuilder(str).reverse().toString();
    }
}
