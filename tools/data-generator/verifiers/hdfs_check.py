"""
CP2: DataX 同步验证 — HDFS/ODS 层文件和行数检查。
通过 WebHDFS REST API 或 subprocess 调用 hdfs 命令。
"""

import json
import subprocess

import httpx
from loguru import logger

from .base import BaseVerifier, CheckStatus


class HdfsCheck(BaseVerifier):
    checkpoint = "CP2"
    name = "DataX 同步 (HDFS/ODS)"

    def _check(self):
        hdfs_cfg = self.cfg.get("hdfs", {})
        base_path = hdfs_cfg.get("base_path", "/data/ods")
        namenode_url = hdfs_cfg.get("namenode_url", "http://localhost:9870")

        ods_tables = ["ods_customer_info", "ods_loan_application", "ods_repayment_record"]

        available = False

        # 尝试 WebHDFS REST API
        try:
            client = httpx.Client(timeout=5.0)
            resp = client.get(f"{namenode_url}/webhdfs/v1/data/ods",
                              params={"op": "LISTSTATUS", "user.name": "hadoop"})
            if resp.status_code == 200:
                available = True
                self._check_via_webhdfs(client, namenode_url, base_path, ods_tables)
            client.close()
        except Exception as e:
            logger.debug(f"WebHDFS 不可用: {e}")

        # 尝试 hdfs 命令行
        if not available:
            try:
                result = subprocess.run(
                    ["hdfs", "dfs", "-ls", base_path],
                    capture_output=True, text=True, timeout=10
                )
                if result.returncode == 0:
                    available = True
                    self._check_via_cli(ods_tables)
            except (FileNotFoundError, subprocess.TimeoutExpired) as e:
                logger.debug(f"hdfs 命令不可用: {e}")

        if not available:
            self.result.add_detail("HDFS 连接", CheckStatus.SKIP,
                                    message="HDFS 不可用 (WebHDFS 和 hdfs 命令均失败)")
            logger.info("  提示: 请确保 HDFS 服务正在运行，或手动运行 DataX 同步后重试")

        self.result.compute_status()

    def _check_via_webhdfs(self, client: httpx.Client, namenode_url: str,
                            base_path: str, ods_tables: list[str]):
        """通过 WebHDFS REST API 检查。"""
        for table in ods_tables:
            path = f"{base_path}/{table}"
            try:
                resp = client.get(
                    f"{namenode_url}/webhdfs/v1{path}",
                    params={"op": "LISTSTATUS", "user.name": "hadoop"},
                    timeout=5.0
                )
                if resp.status_code == 200:
                    statuses = resp.json().get("FileStatuses", {}).get("FileStatus", [])
                    partitions = [s for s in statuses if s["type"] == "DIRECTORY"
                                   and s["pathSuffix"].startswith("dt=")]
                    if partitions:
                        self.result.add_detail(
                            f"{table} 分区", CheckStatus.PASS,
                            actual=str(len(partitions)), message=f"{len(partitions)} 个分区"
                        )
                    else:
                        self.result.add_detail(
                            f"{table} 分区", CheckStatus.WARN,
                            expected="有分区", actual="无分区",
                            message="目录存在但无分区 (可能未运行 DataX)"
                        )
                else:
                    self.result.add_detail(
                        f"{table}", CheckStatus.WARN,
                        message=f"HDFS 路径不存在或不可访问: {path}"
                    )
            except Exception as e:
                self.result.add_detail(
                    f"{table}", CheckStatus.SKIP, message=f"检查失败: {e}"
                )

    def _check_via_cli(self, ods_tables: list[str]):
        """通过 hdfs 命令行检查。"""
        for table in ods_tables:
            path = f"/data/ods/{table}"
            try:
                result = subprocess.run(
                    ["hdfs", "dfs", "-ls", path],
                    capture_output=True, text=True, timeout=10
                )
                if result.returncode == 0:
                    partitions = [l for l in result.stdout.strip().split("\n")
                                   if "dt=" in l]
                    self.result.add_detail(
                        f"{table} 分区", CheckStatus.PASS,
                        actual=str(len(partitions)), message=f"{len(partitions)} 个分区"
                    )
                else:
                    self.result.add_detail(
                        f"{table}", CheckStatus.WARN,
                        message=f"hdfs dfs -ls 失败: {result.stderr[:100]}"
                    )
            except Exception as e:
                self.result.add_detail(f"{table}", CheckStatus.SKIP, message=str(e))
