package com.credit.platform.data.flink.common;

import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;

import java.util.List;

/**
 * Kafka Source 统一工厂 — 创建各作业共享的 Kafka Source 配置。
 *
 * <p>统一配置:
 * <ul>
 *   <li>bootstrapServers — Kafka 集群地址</li>
 *   <li>groupId — 消费者组（每个作业独立）</li>
 *   <li>topics — 订阅的 Topic 列表</li>
 *   <li>起始偏移量 — Latest（实时作业从最新开始）</li>
 * </ul>
 *
 * <p>使用 Flink Kafka Connector 3.1.0-1.18 API，通过 {@link SimpleStringSchema} 进行反序列化，
 * 各作业在 ProcessFunction 中使用 Jackson 完成 JSON → POJO 转换。
 */
public class KafkaSourceFactory {

    private KafkaSourceFactory() {
    }

    /**
     * 创建 KafkaSource，使用默认配置。
     *
     * @param bootstrapServers Kafka 集群地址（如 "localhost:9092"）
     * @param groupId          消费者组 ID
     * @param topics           订阅的 Topic 列表
     * @return KafkaSource&lt;String&gt;
     */
    public static KafkaSource<String> create(
            String bootstrapServers,
            String groupId,
            List<String> topics) {
        return KafkaSource.<String>builder()
                .setBootstrapServers(bootstrapServers)
                .setGroupId(groupId)
                .setTopics(topics)
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();
    }
}
