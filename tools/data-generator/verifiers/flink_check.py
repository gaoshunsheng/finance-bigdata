"""
CP5: Flink 实时特征验证 — Redis key 存在性 + HBase 行检查。
"""

import json

import redis as redis_lib

from loguru import logger

from .base import BaseVerifier, CheckStatus


class FlinkCheck(BaseVerifier):
    checkpoint = "CP5"
    name = "Flink 实时特征 (Redis + HBase)"

    def _check(self):
        # 1. Redis 检查
        self._check_redis()

        # 2. HBase 检查
        self._check_hbase()

        self.result.compute_status()

    def _check_redis(self):
        """检查 Redis 中的 Flink 特征。"""
        redis_cfg = self.cfg.get("redis", {})
        host = redis_cfg.get("host", "localhost")
        port = redis_cfg.get("port", 6379)
        password = redis_cfg.get("password")
        key_prefix = redis_cfg.get("key_prefix", "feature:")
        expected_ttl = redis_cfg.get("ttl", 86400)

        feature_types = ["credit_query_3m", "overdue_6m", "apply_freq_1m", "transaction_summary_1h"]

        try:
            r = redis_lib.Redis(host=host, port=port, password=password,
                                decode_responses=True, socket_timeout=5)
            r.ping()

            self.result.add_detail("Redis 连接", CheckStatus.PASS,
                                    message=f"{host}:{port}")

            # 查找已有的特征 key
            sample_keys_found = 0
            sample_customer = None

            for ft in feature_types:
                pattern = f"{key_prefix}{ft}:*"
                keys = list(r.scan_iter(match=pattern, count=100))
                if keys:
                    self.result.add_detail(
                        f"Redis 特征: {ft}", CheckStatus.PASS,
                        actual=str(len(keys)), message=f"{len(keys)} 个 key"
                    )
                    if not sample_customer and keys:
                        # 从第一个 key 提取 customerId
                        parts = keys[0].split(":")
                        if len(parts) >= 3:
                            sample_customer = parts[2]
                else:
                    self.result.add_detail(
                        f"Redis 特征: {ft}", CheckStatus.WARN,
                        expected="有数据", actual="无数据",
                        message=f"未找到 {pattern} (Flink 可能未运行或窗口未触发)"
                    )

            # TTL 和值检查
            if sample_customer:
                for ft in feature_types:
                    key = f"{key_prefix}{ft}:{sample_customer}"
                    if r.exists(key):
                        ttl = r.ttl(key)
                        value = r.get(key)

                        ttl_ok = ttl is not None and 0 < ttl <= expected_ttl
                        ttl_status = CheckStatus.PASS if ttl_ok else CheckStatus.WARN

                        self.result.add_detail(
                            f"TTL ({ft})", ttl_status,
                            expected=f"<{expected_ttl}s", actual=f"{ttl}s"
                        )

                        # 值 JSON 检查
                        if value:
                            try:
                                data = json.loads(value)
                                if "customerId" in data:
                                    self.result.add_detail(
                                        f"值格式 ({ft})", CheckStatus.PASS,
                                        actual=str(list(data.keys())[:5]),
                                        message="合法 JSON"
                                    )
                                else:
                                    self.result.add_detail(
                                        f"值格式 ({ft})", CheckStatus.WARN,
                                        message=f"缺少 customerId: {list(data.keys())}"
                                    )
                            except json.JSONDecodeError:
                                self.result.add_detail(
                                    f"值格式 ({ft})", CheckStatus.WARN,
                                    message="值不是有效 JSON"
                                )

            r.close()

        except redis_lib.ConnectionError as e:
            self.result.add_detail("Redis 连接", CheckStatus.SKIP,
                                    message=f"无法连接 Redis: {e}")
        except Exception as e:
            self.result.add_detail("Redis 检查", CheckStatus.SKIP,
                                    message=str(e))

    def _check_hbase(self):
        """检查 HBase 中的特征数据。"""
        hbase_cfg = self.cfg.get("hbase", {})
        zk_quorum = hbase_cfg.get("zk_quorum", "localhost")
        zk_port = hbase_cfg.get("zk_port", 2181)
        table_name = hbase_cfg.get("table", "customer_feature")
        cf = hbase_cfg.get("column_family", "cf")

        try:
            import happybase
            connection = happybase.Connection(zk_quorum, port=int(zk_port), timeout=5000)

            tables = connection.tables()
            if table_name.encode() not in tables and table_name not in tables:
                self.result.add_detail("HBase 表", CheckStatus.WARN,
                                        expected=table_name,
                                        message=f"表不存在 (可用表: {[t.decode() for t in tables][:5]})")
                connection.close()
                return

            self.result.add_detail("HBase 连接", CheckStatus.PASS,
                                    message=f"ZK: {zk_quorum}:{zk_port}")

            # Scan 几行验证格式
            table = connection.table(table_name)
            rows = list(table.scan(limit=5))

            if rows:
                self.result.add_detail("HBase 数据", CheckStatus.PASS,
                                        actual=str(len(rows)),
                                        message=f"{len(rows)} 行 (限制扫描 5 行)")

                # 检查第一行的 rowKey 格式和值
                first_key = rows[0][0].decode() if isinstance(rows[0][0], bytes) else rows[0][0]
                if "_" in first_key:
                    parts = first_key.split("_")
                    if len(parts) >= 3:
                        self.result.add_detail("HBase RowKey 格式", CheckStatus.PASS,
                                                actual=first_key,
                                                message="格式: {reversedCustomerId}_{featureType}_{windowEnd}")
                    else:
                        self.result.add_detail("HBase RowKey 格式", CheckStatus.WARN,
                                                actual=first_key)
                else:
                    self.result.add_detail("HBase RowKey 格式", CheckStatus.WARN,
                                            actual=first_key,
                                            message="RowKey 格式不符合预期")

                # 检查 cf:value 列
                first_data = rows[0][1]
                value_col = f"{cf}:value".encode()
                if value_col in first_data:
                    try:
                        json.loads(first_data[value_col])
                        self.result.add_detail("HBase 值格式", CheckStatus.PASS,
                                                message="cf:value 为合法 JSON")
                    except json.JSONDecodeError:
                        self.result.add_detail("HBase 值格式", CheckStatus.WARN,
                                                message="cf:value 不是有效 JSON")
            else:
                self.result.add_detail("HBase 数据", CheckStatus.WARN,
                                        message="表为空 (Flink 可能未写入)")

            connection.close()

        except ImportError:
            self.result.add_detail("HBase 检查", CheckStatus.SKIP,
                                    message="happybase 库未安装")
        except Exception as e:
            self.result.add_detail("HBase 检查", CheckStatus.SKIP,
                                    message=f"连接失败: {e}")
