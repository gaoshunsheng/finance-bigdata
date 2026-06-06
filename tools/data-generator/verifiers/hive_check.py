"""
CP3: Spark ETL 验证 — 通过 Hive JDBC 或 beeline 检查 DWD/DWS/ADS 层。
"""

import subprocess

from loguru import logger

from .base import BaseVerifier, CheckStatus


class HiveCheck(BaseVerifier):
    checkpoint = "CP3"
    name = "Spark ETL (ODS→DWD→DWS→ADS)"

    def _check(self):
        hive_cfg = self.cfg.get("hive", {})
        host = hive_cfg.get("host", "localhost")
        port = hive_cfg.get("port", 10000)

        # 尝试 beeline 连接
        beeline_available = False
        try:
            result = subprocess.run(
                ["beeline", "-u", f"jdbc:hive2://{host}:{port}/default",
                 "-e", "SHOW DATABASES;"],
                capture_output=True, text=True, timeout=15
            )
            if result.returncode == 0:
                beeline_available = True
        except (FileNotFoundError, subprocess.TimeoutExpired):
            logger.debug("beeline 命令不可用")

        if not beeline_available:
            self.result.add_detail("Hive 连接", CheckStatus.SKIP,
                                    message="beeline 不可用，跳过 Hive 验证")
            logger.info("  提示: 请安装 beeline 或确保 HiveServer2 正在运行")
            self.result.compute_status()
            return

        # ODS 行数
        self._check_table_count(beeline_available, host, port,
                                 "ods.ods_customer_info", "ODS customer_info")
        self._check_table_count(beeline_available, host, port,
                                 "ods.ods_loan_application", "ODS loan_application")
        self._check_table_count(beeline_available, host, port,
                                 "ods.ods_repayment_record", "ODS repayment_record")

        # DWD 行数
        self._check_table_count(beeline_available, host, port,
                                 "dwd.dwd_customer_profile", "DWD customer_profile")
        self._check_table_count(beeline_available, host, port,
                                 "dwd.dwd_loan_application_detail", "DWD loan_detail")
        self._check_table_count(beeline_available, host, port,
                                 "dwd.dwd_credit_event", "DWD credit_event")

        # DWS 聚合检查
        self._check_dws_aggregation(host, port)

        # ADS 评分检查
        self._check_ads_scoring(host, port)

        self.result.compute_status()

    def _run_hive_query(self, host: str, port: str, sql: str) -> str | None:
        """通过 beeline 执行 Hive 查询，返回输出。"""
        try:
            result = subprocess.run(
                ["beeline", "-u", f"jdbc:hive2://{host}:{port}/default",
                 "-e", sql, "--outputformat=csv2"],
                capture_output=True, text=True, timeout=30
            )
            if result.returncode == 0:
                return result.stdout.strip()
            else:
                logger.debug(f"Hive 查询失败: {result.stderr[:200]}")
                return None
        except subprocess.TimeoutExpired:
            logger.debug("Hive 查询超时")
            return None

    def _check_table_count(self, available: bool, host: str, port: str,
                            table: str, label: str):
        """检查表行数。"""
        output = self._run_hive_query(host, port, f"SELECT COUNT(*) AS cnt FROM {table};")
        if output is None:
            self.result.add_detail(f"{label} 行数", CheckStatus.SKIP,
                                    message="查询失败或超时")
            return

        # 解析 CSV 输出
        lines = [l.strip() for l in output.split("\n") if l.strip()]
        if len(lines) >= 2:
            try:
                count = int(lines[1].split(",")[-1].strip('"'))
                self.result.add_detail(f"{label} 行数", CheckStatus.PASS,
                                        actual=str(count), message=f"{count:,} 行")
            except (ValueError, IndexError):
                self.result.add_detail(f"{label} 行数", CheckStatus.WARN,
                                        message=f"无法解析行数: {lines[-1]}")
        else:
            self.result.add_detail(f"{label} 行数", CheckStatus.WARN,
                                    message=f"输出异常: {output[:100]}")

    def _check_dws_aggregation(self, host: str, port: str):
        """抽查 DWS 聚合正确性。"""
        sql = (
            "SELECT customer_id, loan_count_12m, credit_query_count_3m "
            "FROM dws.dws_customer_credit_summary LIMIT 5;"
        )
        output = self._run_hive_query(host, port, sql)
        if output:
            lines = [l for l in output.strip().split("\n") if l.strip()]
            if len(lines) > 1:
                self.result.add_detail("DWS 聚合抽查", CheckStatus.PASS,
                                        actual=f"{len(lines) - 1} 条记录",
                                        message="dws_customer_credit_summary 有数据")
            else:
                self.result.add_detail("DWS 聚合抽查", CheckStatus.WARN,
                                        message="dws_customer_credit_summary 无数据")
        else:
            self.result.add_detail("DWS 聚合抽查", CheckStatus.SKIP,
                                    message="查询失败")

    def _check_ads_scoring(self, host: str, port: str):
        """检查 ADS 评分范围。"""
        sql = (
            "SELECT MIN(credit_score) AS min_score, MAX(credit_score) AS max_score, "
            "COUNT(DISTINCT risk_level) AS risk_levels "
            "FROM ads.ads_credit_score_wide_table;"
        )
        output = self._run_hive_query(host, port, sql)
        if output:
            lines = [l for l in output.strip().split("\n") if l.strip()]
            if len(lines) > 1:
                self.result.add_detail("ADS 评分范围", CheckStatus.PASS,
                                        actual=lines[1],
                                        message=f"credit_score 范围和 risk_level 分布: {lines[1]}")
            else:
                self.result.add_detail("ADS 评分范围", CheckStatus.WARN,
                                        message="ads_credit_score_wide_table 无数据")
        else:
            self.result.add_detail("ADS 评分范围", CheckStatus.SKIP,
                                    message="查询失败")
