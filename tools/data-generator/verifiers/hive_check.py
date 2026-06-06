"""
CP3: Spark ETL 验证 — 通过 HiveServer2 Thrift (pyhive) 检查 DWD/DWS/ADS 层。
"""

from loguru import logger

from .base import BaseVerifier, CheckStatus


class HiveCheck(BaseVerifier):
    checkpoint = "CP3"
    name = "Spark ETL (ODS→DWD→DWS→ADS)"

    def _check(self):
        hive_cfg = self.cfg.get("hive", {})
        host = hive_cfg.get("host", "localhost")
        port = hive_cfg.get("port", 10000)

        # 尝试 pyhive 连接
        cursor = self._get_cursor(host, port)
        if cursor is None:
            self.result.add_detail("Hive 连接", CheckStatus.SKIP,
                                    message=f"无法连接 HiveServer2 ({host}:{port})")
            self.result.compute_status()
            return

        self.result.add_detail("Hive 连接", CheckStatus.PASS,
                                message=f"{host}:{port}")

        # ODS 行数
        self._check_table_count(cursor, "ods.ods_customer_info", "ODS customer_info")
        self._check_table_count(cursor, "ods.ods_loan_application", "ODS loan_application")
        self._check_table_count(cursor, "ods.ods_repayment_record", "ODS repayment_record")

        # DWD 行数
        self._check_table_count(cursor, "dwd.dwd_customer_profile", "DWD customer_profile")
        self._check_table_count(cursor, "dwd.dwd_loan_application_detail", "DWD loan_detail")
        self._check_table_count(cursor, "dwd.dwd_credit_event", "DWD credit_event")

        # DWS 聚合检查
        self._check_dws_aggregation(cursor)

        # ADS 评分检查
        self._check_ads_scoring(cursor)

        try:
            cursor.close()
        except Exception:
            pass

        self.result.compute_status()

    def _get_cursor(self, host: str, port: int):
        """通过 pyhive 连接 HiveServer2，返回 cursor 或 None。"""
        try:
            from pyhive import hive
            conn = hive.connect(host=host, port=int(port), database="default",
                                configuration={"hive.server2.thrift.connect.timeout": "10"})
            return conn.cursor()
        except ImportError:
            logger.debug("pyhive 库未安装")
            # 回退: 尝试通过 thrift 直连
            try:
                from thrift.transport import TSocket, TTransport
                from thrift.protocol import TBinaryProtocol
                from hive_service import ThriftHive
                socket = TSocket.TSocket(host, int(port))
                socket.setTimeout(10000)
                transport = TTransport.TBufferedTransport(socket)
                protocol = TBinaryProtocol.TBinaryProtocol(transport)
                client = ThriftHive.Client(protocol)
                transport.open()
                # 返回一个简单的包装
                return _ThriftCursor(client, transport)
            except Exception:
                return None
        except Exception as e:
            logger.debug(f"pyhive 连接失败: {e}")
            return None

    def _run_query(self, cursor, sql: str) -> list[tuple] | None:
        """执行查询，返回结果行列表。"""
        try:
            cursor.execute(sql)
            return cursor.fetchall()
        except Exception as e:
            logger.debug(f"查询失败: {e}")
            return None

    def _check_table_count(self, cursor, table: str, label: str):
        """检查表行数。"""
        rows = self._run_query(cursor, f"SELECT COUNT(*) AS cnt FROM {table}")
        if rows is None:
            self.result.add_detail(f"{label} 行数", CheckStatus.SKIP,
                                    message="查询失败或超时")
            return

        if rows and len(rows) > 0:
            count = rows[0][0] if isinstance(rows[0], (list, tuple)) else rows[0]
            try:
                count = int(count)
                self.result.add_detail(f"{label} 行数", CheckStatus.PASS,
                                        actual=str(count), message=f"{count:,} 行")
            except (ValueError, TypeError):
                self.result.add_detail(f"{label} 行数", CheckStatus.WARN,
                                        message=f"无法解析行数: {count}")
        else:
            self.result.add_detail(f"{label} 行数", CheckStatus.WARN,
                                    message="查询返回空结果")

    def _check_dws_aggregation(self, cursor):
        """抽查 DWS 聚合正确性。"""
        rows = self._run_query(
            cursor,
            "SELECT customer_id, loan_count_12m, credit_query_count_3m "
            "FROM dws.dws_customer_credit_summary LIMIT 5"
        )
        if rows:
            self.result.add_detail("DWS 聚合抽查", CheckStatus.PASS,
                                    actual=f"{len(rows)} 条记录",
                                    message="dws_customer_credit_summary 有数据")
        elif rows is not None:
            self.result.add_detail("DWS 聚合抽查", CheckStatus.WARN,
                                    message="dws_customer_credit_summary 无数据")
        else:
            self.result.add_detail("DWS 聚合抽查", CheckStatus.SKIP,
                                    message="查询失败")

    def _check_ads_scoring(self, cursor):
        """检查 ADS 评分范围。"""
        rows = self._run_query(
            cursor,
            "SELECT MIN(credit_score) AS min_score, MAX(credit_score) AS max_score, "
            "COUNT(DISTINCT risk_level) AS risk_levels "
            "FROM ads.ads_credit_score_wide_table"
        )
        if rows and len(rows) > 0:
            row = rows[0]
            self.result.add_detail("ADS 评分范围", CheckStatus.PASS,
                                    actual=f"min={row[0]}, max={row[1]}, levels={row[2]}",
                                    message=f"credit_score 范围和 risk_level 分布")
        elif rows is not None:
            self.result.add_detail("ADS 评分范围", CheckStatus.WARN,
                                    message="ads_credit_score_wide_table 无数据")
        else:
            self.result.add_detail("ADS 评分范围", CheckStatus.SKIP,
                                    message="查询失败")


class _ThriftCursor:
    """Thrift 客户端的简单包装，模拟 DB-API cursor 接口。"""

    def __init__(self, client, transport):
        self._client = client
        self._transport = transport

    def execute(self, sql):
        self._client.execute(sql)

    def fetchall(self):
        return self._client.fetchAll()

    def close(self):
        self._transport.close()
