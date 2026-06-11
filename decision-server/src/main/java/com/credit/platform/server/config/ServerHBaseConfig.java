package com.credit.platform.server.config;

import java.io.IOException;

import org.apache.hadoop.hbase.HBaseConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * HBase 配置 — 用于 L2 缓存变量查询。
 *
 * <p>仅在 {@code feature.store.enabled=true} 时激活。
 * <p>表设计:
 * <ul>
 *   <li>customer_feature — RowKey: reversed(customerId)_featureType_timestamp</li>
 * </ul>
 */
@Configuration
@ConditionalOnProperty(name = "feature.store.enabled", havingValue = "true")
public class ServerHBaseConfig {

    private static final Logger log = LoggerFactory.getLogger(ServerHBaseConfig.class);

    @Bean
    public org.apache.hadoop.conf.Configuration hbaseConfiguration(FeatureProperties properties) {
        org.apache.hadoop.conf.Configuration config = HBaseConfiguration.create();
        config.set("hbase.zookeeper.quorum", properties.getHbase().getZkQuorum());
        config.set("hbase.zookeeper.property.clientPort", properties.getHbase().getZkPort());
        config.setInt("hbase.rpc.timeout", properties.getHbase().getRpcTimeoutMs());
        config.setInt("hbase.client.operation.timeout", properties.getHbase().getOperationTimeoutMs());
        log.info("HBase 配置初始化: zk={}:{}, table={}",
            properties.getHbase().getZkQuorum(),
            properties.getHbase().getZkPort(),
            properties.getHbase().getTableName());
        return config;
    }

    @Bean(destroyMethod = "close")
    public org.apache.hadoop.hbase.client.Connection hbaseConnection(
            org.apache.hadoop.conf.Configuration hbaseConfiguration) throws IOException {
        return org.apache.hadoop.hbase.client.ConnectionFactory.createConnection(hbaseConfiguration);
    }
}
