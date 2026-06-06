"""
Kafka Producer 辅助模块。

封装 kafka-python KafkaProducer，提供 send_event / flush 方法。
用于 realtime_gen 向短名 Kafka topics 发送扁平业务事件 JSON。
"""

import json
from typing import Any

from kafka import KafkaProducer
from loguru import logger

from config import get_config


class KafkaHelper:
    """Kafka Producer 辅助类"""

    def __init__(self, cfg: dict | None = None):
        kafka_cfg = (cfg or get_config())["kafka"]
        bootstrap = kafka_cfg["bootstrap_servers"]
        self._topics = kafka_cfg["topics"]
        self._producer = KafkaProducer(
            bootstrap_servers=bootstrap,
            value_serializer=lambda v: json.dumps(v, ensure_ascii=False, default=str).encode("utf-8"),
            key_serializer=lambda k: k.encode("utf-8") if k else None,
            acks="all",
            retries=3,
            linger_ms=10,
        )
        logger.info(f"Kafka Producer 已连接: {bootstrap}")

    def send_event(self, topic_key: str, event: dict[str, Any], key: str | None = None) -> None:
        """
        发送事件到 Kafka topic。

        Args:
            topic_key: config.yaml 中的 topic 键名 (如 "credit_query", "overdue_event")
            event: 事件字典 (将被序列化为 JSON)
            key: 可选的消息 Key (通常用 customerId)
        """
        topic_name = self._topics.get(topic_key)
        if not topic_name:
            logger.warning(f"未知的 topic 键名: {topic_key}")
            return

        future = self._producer.send(topic_name, value=event, key=key)
        future.add_callback(
            lambda metadata, t=topic_key: logger.trace(
                f"Kafka 发送成功: topic={t}, partition={metadata.partition}, offset={metadata.offset}"
            )
        )
        future.add_errback(
            lambda exc, t=topic_key: logger.error(f"Kafka 发送失败: topic={t}, 错误: {exc}")
        )

    def flush(self):
        """刷新缓冲区，确保所有消息发送完成。"""
        self._producer.flush()
        logger.info("Kafka Producer 缓冲区已刷新")

    def close(self):
        """关闭 Producer。"""
        self.flush()
        self._producer.close()
        logger.info("Kafka Producer 已关闭")

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.close()
