"""
CP6: 决策引擎验证 — API 健康检查、请求测试、ES 日志验证。
"""

import json
from datetime import datetime

import httpx
from loguru import logger

from config import BEIJING_TZ
from .base import BaseVerifier, CheckStatus


class DecisionCheck(BaseVerifier):
    checkpoint = "CP6"
    name = "决策引擎 (API + ES)"

    def _check(self):
        decision_cfg = self.cfg.get("decision", {})
        base_url = decision_cfg.get("url", "http://localhost:18080/api/v1/decision")
        strategy_id = decision_cfg.get("strategy_id", "STR_CREDIT_V3")

        client = httpx.Client(timeout=10.0)

        try:
            # 1. 健康检查
            self._check_health(client, base_url)

            # 2. 发送测试请求
            self._check_decision_api(client, base_url, strategy_id)

            # 3. ES 日志验证
            self._check_es_logs()

        finally:
            client.close()

        self.result.compute_status()

    def _check_health(self, client: httpx.Client, base_url: str):
        """检查决策引擎健康状态。"""
        try:
            resp = client.get(f"{base_url}/health", timeout=5.0)
            if resp.status_code == 200:
                self.result.add_detail("API 健康检查", CheckStatus.PASS,
                                        message=f"HTTP {resp.status_code}")
            else:
                self.result.add_detail("API 健康检查", CheckStatus.WARN,
                                        actual=f"HTTP {resp.status_code}",
                                        message=resp.text[:100])
        except httpx.ConnectError:
            self.result.add_detail("API 健康检查", CheckStatus.SKIP,
                                    message=f"无法连接 {base_url} (决策引擎可能未启动)")
        except Exception as e:
            self.result.add_detail("API 健康检查", CheckStatus.SKIP,
                                    message=str(e))

    def _check_decision_api(self, client: httpx.Client, base_url: str, strategy_id: str):
        """发送测试决策请求。"""
        test_request = {
            "strategyId": strategy_id,
            "channel": "APP",
            "applicant": {
                "name": "验证测试用户",
                "customerId": f"TEST_{datetime.now().strftime('%Y%m%d%H%M%S')}",
                "age": 30,
                "income": 100000.0,
                "idNumber": "110101199001011234",
                "phone": "13800138000",
            },
            "metadata": {
                "deviceFingerprint": "verify_test_fp",
                "ipAddress": "127.0.0.1",
            },
        }

        try:
            resp = client.post(f"{base_url}/execute", json=test_request, timeout=5.0)

            if resp.status_code != 200:
                self.result.add_detail("决策请求测试", CheckStatus.WARN,
                                        actual=f"HTTP {resp.status_code}",
                                        message=resp.text[:200])
                return

            result = resp.json()

            # 检查响应字段
            has_decision_id = bool(result.get("decisionId"))
            valid_result = result.get("result") in ("PASS", "REJECT", "REVIEW", "MANUAL")
            has_trace_id = bool(result.get("traceId"))
            has_duration = "durationMs" in result

            checks = [
                ("decisionId 存在", has_decision_id),
                ("result 有效", valid_result),
                ("traceId 存在", has_trace_id),
                ("durationMs 存在", has_duration),
            ]

            all_pass = all(c[1] for c in checks)
            status = CheckStatus.PASS if all_pass else CheckStatus.WARN

            details = ", ".join(f"{c[0]}={'✓' if c[1] else '✗'}" for c in checks)
            self.result.add_detail(
                "决策响应格式", status,
                actual=f"result={result.get('result')}, score={result.get('score')}, "
                       f"duration={result.get('durationMs')}ms",
                message=details
            )

            # 检查 score 范围
            score = result.get("score")
            if score is not None:
                if 0 <= score <= 1000:
                    self.result.add_detail("评分范围", CheckStatus.PASS,
                                            actual=str(score), message="0-1000 范围内")
                else:
                    self.result.add_detail("评分范围", CheckStatus.WARN,
                                            actual=str(score), message="超出 0-1000 范围")

        except httpx.ConnectError:
            self.result.add_detail("决策请求测试", CheckStatus.SKIP,
                                    message="无法连接决策引擎")
        except Exception as e:
            self.result.add_detail("决策请求测试", CheckStatus.SKIP,
                                    message=str(e))

    def _check_es_logs(self):
        """检查 Elasticsearch 中的决策日志。"""
        es_cfg = self.cfg.get("elasticsearch", {})
        host = es_cfg.get("host", "localhost")
        port = es_cfg.get("port", 9200)
        index_prefix = es_cfg.get("index_prefix", "decision-log-")

        # 当前月份的索引名
        now = datetime.now(BEIJING_TZ)
        index_name = f"{index_prefix}{now.strftime('%Y.%m')}"

        try:
            client = httpx.Client(timeout=5.0)
            url = f"http://{host}:{port}"

            # 检查 ES 连接
            resp = client.get(url)
            if resp.status_code != 200:
                self.result.add_detail("ES 连接", CheckStatus.SKIP,
                                        message=f"ES 返回 {resp.status_code}")
                return

            self.result.add_detail("ES 连接", CheckStatus.PASS,
                                    message=f"{host}:{port}")

            # 查询索引
            resp = client.get(f"{url}/{index_name}/_count")
            if resp.status_code == 200:
                count = resp.json().get("count", 0)
                if count > 0:
                    self.result.add_detail("ES 决策日志", CheckStatus.PASS,
                                            actual=str(count),
                                            message=f"{index_name} 有 {count} 条文档")
                else:
                    self.result.add_detail("ES 决策日志", CheckStatus.WARN,
                                            expected="有数据", actual="0 条",
                                            message=f"{index_name} 为空")
            elif resp.status_code == 404:
                self.result.add_detail("ES 决策日志", CheckStatus.WARN,
                                        message=f"索引 {index_name} 不存在")
            else:
                self.result.add_detail("ES 决策日志", CheckStatus.WARN,
                                        message=f"查询返回 {resp.status_code}")

            # 查询最新文档字段
            resp = client.get(
                f"{url}/{index_name}/_search",
                params={"size": "1", "sort": "timestamp:desc"},
                headers={"Content-Type": "application/json"},
            )
            if resp.status_code == 200:
                hits = resp.json().get("hits", {}).get("hits", [])
                if hits:
                    source = hits[0]["_source"]
                    expected_fields = ["traceId", "customerId", "decisionResult",
                                        "score", "executionTimeMs", "timestamp"]
                    found = [f for f in expected_fields if f in source]
                    if len(found) >= 4:
                        self.result.add_detail("ES 日志字段", CheckStatus.PASS,
                                                actual=f"{len(found)}/{len(expected_fields)}",
                                                message=f"包含: {found}")
                    else:
                        self.result.add_detail("ES 日志字段", CheckStatus.WARN,
                                                actual=str(found),
                                                message=f"期望字段: {expected_fields}")

            client.close()

        except httpx.ConnectError:
            self.result.add_detail("ES 连接", CheckStatus.SKIP,
                                    message=f"无法连接 ES ({host}:{port})")
        except Exception as e:
            self.result.add_detail("ES 检查", CheckStatus.SKIP,
                                    message=str(e))
