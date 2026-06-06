package com.credit.platform.data.flink.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * HBase 特征 Sink — 将窗口聚合结果持久化到 HBase customer_feature 表。
 *
 * <p>RowKey: {@code reversed(customerId) + "_" + featureType + "_" + timestamp}
 * <p>Column Family: cf
 * <p>Columns: value (JSON String), updatedAt
 *
 * <p>连接失败时日志告警并跳过，保证作业容错运行。
 */
public class HBaseFeatureSink extends RichSinkFunction<Map<String, Object>> {

    private static final Logger log = LoggerFactory.getLogger(HBaseFeatureSink.class);
    private static final long serialVersionUID = 1L;

    private static final byte[] CF = Bytes.toBytes("cf");
    private static final byte[] COL_VALUE = Bytes.toBytes("value");
    private static final byte[] COL_UPDATED = Bytes.toBytes("updatedAt");

    private final String zkQuorum;
    private final String zkPort;
    private final String featureType;

    private transient Connection hbaseConnection;
    private transient ObjectMapper objectMapper;

    /**
     * @param zkQuorum   ZooKeeper quorum 地址
     * @param zkPort     ZooKeeper 端口
     * @param featureType 特征类型
     */
    public HBaseFeatureSink(String zkQuorum, String zkPort, String featureType) {
        this.zkQuorum = zkQuorum;
        this.zkPort = zkPort;
        this.featureType = featureType;
    }

    @Override
    public void open(org.apache.flink.configuration.Configuration parameters) throws Exception {
        this.objectMapper = new ObjectMapper();
        try {
            org.apache.hadoop.conf.Configuration config = HBaseConfiguration.create();
            config.set("hbase.zookeeper.quorum", zkQuorum);
            config.set("hbase.zookeeper.property.clientPort", zkPort);
            this.hbaseConnection = ConnectionFactory.createConnection(config);
            log.info("[HBaseFeatureSink] 连接成功: zk={}:{}", zkQuorum, zkPort);
        } catch (Exception e) {
            log.warn("[HBaseFeatureSink] HBase 连接失败，将跳过写入: {}", e.getMessage());
        }
    }

    @Override
    public void invoke(Map<String, Object> value, Context context) throws Exception {
        if (hbaseConnection == null) {
            return;
        }
        try {
            String customerId = (String) value.get("customerId");
            if (customerId == null || customerId.isEmpty()) {
                return;
            }
            // Use deterministic RowKey from customerId + featureType + windowEnd for idempotency
            String windowEnd = value.getOrDefault("windowEnd", "").toString();
            String rowKey = FeatureKey.hbaseRowKey(customerId, featureType, windowEnd);
            String json = objectMapper.writeValueAsString(value);
            long timestamp = System.currentTimeMillis();

            Table table = hbaseConnection.getTable(TableName.valueOf("customer_feature"));
            Put put = new Put(Bytes.toBytes(rowKey));
            put.addColumn(CF, COL_VALUE, Bytes.toBytes(json));
            put.addColumn(CF, COL_UPDATED, Bytes.toBytes(String.valueOf(timestamp)));
            table.put(put);
            table.close();
        } catch (Exception e) {
            log.warn("[HBaseFeatureSink] 写入失败，跳过: {}", e.getMessage());
        }
    }

    @Override
    public void close() throws Exception {
        try {
            if (hbaseConnection != null) {
                hbaseConnection.close();
            }
        } catch (Exception e) {
            log.warn("[HBaseFeatureSink] 关闭连接异常: {}", e.getMessage());
        }
    }
}
