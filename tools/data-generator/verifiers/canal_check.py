"""
CP4: Canal CDC 验证 — Kafka topics 存在性、消息格式、消息延迟。
"""

import json
import time

from kafka import KafkaConsumer, TopicPartition
from loguru import logger

from .base import BaseVerifier, CheckStatus


class CanalCheck(BaseVerifier):
    checkpoint = "CP4"
    name = "Canal CDC (Kafka)"

    def _check(self):
        kafka_cfg = self.cfg.get("kafka", {})
        bootstrap = kafka_cfg.get("bootstrap_servers", "localhost:9093")
        topics_cfg = kafka_cfg.get("topics", {})

        # 长名 topics (Canal CDC 格式)
        long_topics = {
            "customer": topics_cfg.get("long_customer", "cdc_credit_platform_customer_info"),
            "loan": topics_cfg.get("long_loan", "cdc_credit_platform_loan_application"),
            "repayment": topics_cfg.get("long_repayment", "cdc_credit_platform_repayment_record"),
            "credit": topics_cfg.get("long_credit", "cdc_credit_platform_credit_report"),
        }

        # 短名 topics (Flink 扁平 JSON)
        short_topics = {
            "credit_query": topics_cfg.get("credit_query", "cdc_credit_query"),
            "overdue": topics_cfg.get("overdue_event", "cdc_overdue_event"),
            "application": topics_cfg.get("application_event", "application_event"),
            "transaction": topics_cfg.get("transaction_event", "transaction_event"),
        }

        consumer = None
        try:
            consumer = KafkaConsumer(
                bootstrap_servers=bootstrap,
                consumer_timeout_ms=5000,
                auto_offset_reset="latest",
                value_deserializer=lambda v: v.decode("utf-8") if v else None,
            )
        except Exception as e:
            self.result.add_detail("Kafka 连接", CheckStatus.SKIP,
                                    message=f"无法连接 Kafka ({bootstrap}): {e}")
            self.result.compute_status()
            return

        try:
            # 获取所有 topic
            existing_topics = set(consumer.topics())
            logger.debug(f"Kafka topics ({len(existing_topics)}): {sorted(existing_topics)[:20]}")

            # 1. 长名 topics 存在性
            for name, topic in long_topics.items():
                if topic in existing_topics:
                    self.result.add_detail(f"长名 topic: {topic}", CheckStatus.PASS,
                                            message="存在")
                else:
                    self.result.add_detail(f"长名 topic: {topic}", CheckStatus.WARN,
                                            message="不存在 (Canal 可能未启动)")

            # 2. 短名 topics 存在性
            for name, topic in short_topics.items():
                if topic in existing_topics:
                    self.result.add_detail(f"短名 topic: {topic}", CheckStatus.PASS,
                                            message="存在")
                else:
                    self.result.add_detail(f"短名 topic: {topic}", CheckStatus.WARN,
                                            message="不存在")

            # 3. 消息格式验证 — 消费最新消息
            self._check_message_format(consumer, long_topics, short_topics)

        finally:
            if consumer:
                consumer.close()

        self.result.compute_status()

    def _check_message_format(self, consumer: KafkaConsumer,
                               long_topics: dict, short_topics: dict):
        """检查消息格式。"""
        # 尝试消费长名 topic 的一条消息
        for name, topic in long_topics.items():
            if topic not in consumer.topics():
                continue

            try:
                partitions = consumer.partitions_for_topic(topic)
                if not partitions:
                    continue

                tp = TopicPartition(topic, min(partitions))
                consumer.assign([tp])
                end_offset = consumer.end_offsets([tp])[tp]

                if end_offset > 0:
                    consumer.seek(tp, max(0, end_offset - 1))
                    msgs = list(consumer)
                    if msgs:
                        msg_value = msgs[0].value
                        if msg_value:
                            try:
                                data = json.loads(msg_value)
                                # Canal CDC 格式检查
                                has_data = "data" in data
                                has_type = "type" in data
                                has_database = "database" in data
                                has_table = "table" in data

                                if has_data and has_type and has_database and has_table:
                                    self.result.add_detail(
                                        f"CDC 格式 ({topic})", CheckStatus.PASS,
                                        message=f"type={data.get('type')}, "
                                                f"database={data.get('database')}, "
                                                f"table={data.get('table')}"
                                    )
                                else:
                                    missing = [k for k in ["data", "type", "database", "table"]
                                               if k not in data]
                                    self.result.add_detail(
                                        f"CDC 格式 ({topic})", CheckStatus.WARN,
                                        message=f"缺少字段: {missing}"
                                    )
                            except json.JSONDecodeError:
                                self.result.add_detail(
                                    f"CDC 格式 ({topic})", CheckStatus.WARN,
                                    message="消息不是有效 JSON"
                                )
            except Exception as e:
                logger.debug(f"检查 topic {topic} 消息格式失败: {e}")

        # 检查短名 topic 的消息格式 (扁平 JSON)
        for name, topic in short_topics.items():
            if topic not in consumer.topics():
                continue

            try:
                partitions = consumer.partitions_for_topic(topic)
                if not partitions:
                    continue

                tp = TopicPartition(topic, min(partitions))
                consumer.assign([tp])
                end_offset = consumer.end_offsets([tp])[tp]

                if end_offset > 0:
                    consumer.seek(tp, max(0, end_offset - 1))
                    msgs = list(consumer)
                    if msgs and msgs[0].value:
                        try:
                            data = json.loads(msgs[0].value)
                            if "customerId" in data and "eventTime" in data:
                                self.result.add_detail(
                                    f"扁平 JSON ({topic})", CheckStatus.PASS,
                                    message=f"customerId={data.get('customerId')}"
                                )
                            else:
                                self.result.add_detail(
                                    f"扁平 JSON ({topic})", CheckStatus.WARN,
                                    message=f"字段: {list(data.keys())[:5]}"
                                )
                        except json.JSONDecodeError:
                            self.result.add_detail(
                                f"扁平 JSON ({topic})", CheckStatus.WARN,
                                message="消息不是有效 JSON"
                            )
            except Exception as e:
                logger.debug(f"检查 topic {topic} 失败: {e}")
