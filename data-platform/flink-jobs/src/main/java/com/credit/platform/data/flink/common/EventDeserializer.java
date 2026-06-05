package com.credit.platform.data.flink.common;

import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.connector.kafka.source.reader.deserializer.KafkaRecordDeserializationSchema;
import org.apache.flink.util.Collector;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * JSON 事件反序列化器 — 将 Kafka 消息的 value 部分反序列化为 String。
 *
 * <p>各 Flink 作业使用 Jackson 在 ProcessFunction 中完成具体的 JSON → POJO 转换，
 * 此反序列化器仅负责 byte[] → String 的基础转换。
 */
public class EventDeserializer implements KafkaRecordDeserializationSchema<String> {

    private static final long serialVersionUID = 1L;

    @Override
    public void deserialize(ConsumerRecord<byte[], byte[]> record, Collector<String> out) throws IOException {
        if (record.value() != null) {
            out.collect(new String(record.value(), StandardCharsets.UTF_8));
        }
    }

    @Override
    public TypeInformation<String> getProducedType() {
        return TypeInformation.of(String.class);
    }
}
