package com.credit.platform.data.service.config;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import java.io.IOException;

/**
 * HBase 配置 — 用于历史特征查询和企业画像数据存储。
 *
 * <p>表设计:
 * <ul>
 *   <li>customer_feature — RowKey: reversed(customerId)_featureType_timestamp</li>
 *   <li>external_data — 外部数据存储</li>
 * </ul>
 *
 * <p>连接参数从 application.yml 读取
 */
@org.springframework.context.annotation.Configuration
public class HBaseConfig {

    private static final Logger log = LoggerFactory.getLogger(HBaseConfig.class);

    @Value("${hbase.zookeeper.quorum:localhost}")
    private String zkQuorum;

    @Value("${hbase.zookeeper.property.clientPort:2181}")
    private String zkPort;

    @Value("${hbase.rpc.timeout:30000}")
    private int rpcTimeout;

    @Bean
    public Configuration hbaseConfiguration() {
        Configuration config = HBaseConfiguration.create();
        config.set("hbase.zookeeper.quorum", zkQuorum);
        config.set("hbase.zookeeper.property.clientPort", zkPort);
        config.setInt("hbase.rpc.timeout", rpcTimeout);
        config.setInt("hbase.client.operation.timeout", rpcTimeout);
        log.info("HBase 配置初始化: zk={}, port={}", zkQuorum, zkPort);
        return config;
    }

    @Bean(destroyMethod = "close")
    public Connection hbaseConnection(Configuration hbaseConfiguration) throws IOException {
        return ConnectionFactory.createConnection(hbaseConfiguration);
    }
}
