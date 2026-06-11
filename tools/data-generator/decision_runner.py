#!/usr/bin/env python3
"""
决策引擎端到端执行器。

从 MySQL 业务表读取客户和贷款数据，逐条构造 DecisionRequest，
调用 decision-server API 执行决策，收集响应写入 ES + 本地 JSONL/CSV。

用法:
  python decision_runner.py                        # 批量执行全部历史数据
  python decision_runner.py --mode batch --sample 500 --concurrency 4
  python decision_runner.py --mode realtime         # 实时模式 (配合 realtime_gen.py)
  python decision_runner.py --auto-report           # 执行后自动生成报表
"""

import argparse
import csv
import json
import os
import sys
import time
import uuid
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime
from pathlib import Path

import httpx
from loguru import logger

from config import get_config, beijing_now
from db import MySQLHelper

# 输出目录
OUTPUT_DIR = Path(__file__).parent / "output"
OUTPUT_DIR.mkdir(exist_ok=True)


# ---------------------------------------------------------------------------
# 健康检查 (Task 9.1)
# ---------------------------------------------------------------------------
class HealthChecker:
    """前置健康检查。"""

    def __init__(self, cfg: dict):
        self.cfg = cfg
        self.errors: list[str] = []
        self.warnings: list[str] = []

    def check_all(self) -> bool:
        """执行全部检查，返回是否通过。"""
        self._check_decision_server()
        self._check_mysql_rules()
        self._check_es()
        self._check_business_data()

        if self.warnings:
            print("⚠ Warnings:")
            for w in self.warnings:
                print(f"  - {w}")

        if self.errors:
            print("❌ 前置检查失败:")
            for e in self.errors:
                print(f"  - {e}")
            return False

        print("✓ 全部前置检查通过")
        return True

    def _check_decision_server(self):
        decision_cfg = self.cfg.get("decision", {})
        url = decision_cfg.get("url", "http://localhost:18080/api/v1/decision")
        try:
            resp = httpx.get(f"{url}/health", timeout=5.0)
            if resp.status_code == 200:
                print(f"✓ decision-server 健康: {url}/health")
            else:
                self.errors.append(f"decision-server 返回 {resp.status_code}")
        except Exception as e:
            self.errors.append(f"decision-server 不可用 ({url}): {e}")

    def _check_mysql_rules(self):
        try:
            db = MySQLHelper(self.cfg)
            count = db.query_count("rule_entity", "status='RELEASED'")
            db.close()
            if count > 0:
                print(f"✓ MySQL rule_entity 有 {count} 条已发布配置")
            else:
                self.errors.append("MySQL rule_entity 无已发布配置，请先运行 decision_seed.py")
        except Exception as e:
            self.errors.append(f"MySQL 检查失败: {e}")

    def _check_es(self):
        es_cfg = self.cfg.get("elasticsearch", {})
        host = es_cfg.get("host", "localhost")
        port = es_cfg.get("port", 9200)
        try:
            resp = httpx.get(f"http://{host}:{port}", timeout=5.0)
            if resp.status_code == 200:
                print(f"✓ ES 可连接: {host}:{port}")
        except Exception:
            self.warnings.append(f"ES 不可用 ({host}:{port})，将仅写入本地文件")

    def _check_business_data(self):
        try:
            db = MySQLHelper(self.cfg)
            customers = db.query_count("customer_info")
            loans = db.query_count("loan_application")
            db.close()
            if customers > 0 and loans > 0:
                print(f"✓ 业务数据: {customers} 客户, {loans} 贷款申请")
            else:
                self.errors.append(f"业务数据不足 (客户:{customers}, 贷款:{loans})")
        except Exception as e:
            self.errors.append(f"业务数据检查失败: {e}")


# ---------------------------------------------------------------------------
# DecisionRequest 构造器 (Task 9.2)
# ---------------------------------------------------------------------------

class RequestBuilder:
    """从 MySQL 业务数据构造 DecisionRequest。"""

    TIER_CREDIT_SCORE = {
        "PREMIUM": (650, 850),
        "NORMAL": (550, 700),
        "HIGH_RISK": (400, 600),
        "ANOMALY": (300, 500),
    }
    TIER_MULTI_LOAN = {
        "PREMIUM": (0, 2),
        "NORMAL": (1, 4),
        "HIGH_RISK": (3, 10),
        "ANOMALY": (5, 15),
    }
    TIER_OVERDUE = {
        "PREMIUM": (0, 1),
        "NORMAL": (0, 3),
        "HIGH_RISK": (1, 6),
        "ANOMALY": (3, 12),
    }
    TIER_DEBT_RATIO = {
        "PREMIUM": (0.05, 0.30),
        "NORMAL": (0.15, 0.50),
        "HIGH_RISK": (0.30, 0.80),
        "ANOMALY": (0.50, 1.20),
    }
    PURPOSE_MAP = {"CONSUMPTION": "消费贷", "BUSINESS": "经营贷", "MORTGAGE": "房贷"}

    def __init__(self, cfg: dict):
        self.cfg = cfg
        self.runner_cfg = cfg.get("decision_runner", {})
        self.default_strategy = self.runner_cfg.get("default_strategy", "FLOW_CREDIT_MAIN")
        self.default_channel = self.runner_cfg.get("default_channel", "APP")

    def build_from_row(self, loan: dict, customer: dict, seed: int = 0) -> dict:
        """从一条贷款记录 + 客户记录构造 DecisionRequest。"""
        # 客户层级推断
        tier = self._infer_tier(customer, loan)
        import random
        rng = random.Random(seed if seed else hash(loan.get("customer_id", "")))

        # 模拟 L1 外部数据
        cs_range = self.TIER_CREDIT_SCORE.get(tier, (500, 650))
        credit_score = rng.randint(cs_range[0], cs_range[1])
        ml_range = self.TIER_MULTI_LOAN.get(tier, (1, 5))
        multi_loan_count = rng.randint(ml_range[0], ml_range[1])
        od_range = self.TIER_OVERDUE.get(tier, (0, 3))
        overdue_count_6m = rng.randint(od_range[0], od_range[1])
        dr_min, dr_max = self.TIER_DEBT_RATIO.get(tier, (0.1, 0.6))
        debt_ratio = round(rng.uniform(dr_min, dr_max), 2)

        # 黑名单和异常标记
        blacklist_flag = tier == "ANOMALY" and rng.random() < 0.8
        device_anomaly = tier == "ANOMALY" and rng.random() < 0.3
        ip_anomaly = tier == "ANOMALY" and rng.random() < 0.2
        phone_blacklist = tier == "ANOMALY" and rng.random() < 0.6
        income_monthly = float(customer.get("annual_income", 60000)) / 12 if customer.get("annual_income") else 5000.0
        work_years = rng.randint(0, 20)
        credit_query_count = rng.randint(0, 12)
        asset_amount = income_monthly * 12 * rng.uniform(0.5, 10.0)
        repayment_rate = round(rng.uniform(0.6, 1.0), 2)
        account_active_months = rng.randint(1, 60)

        trace_id = f"GEN-{uuid.uuid4().hex[:24]}"

        request = {
            "strategyId": self.default_strategy,
            "channel": self.default_channel,
            "applicant": {
                "customerId": customer.get("customer_id", ""),
                "applicationId": loan.get("application_no", ""),
                "age": self._calc_age(customer.get("birth_date")),
                "income": income_monthly,
                "loanAmount": float(loan.get("loan_amount", 0)),
                "loanPurpose": loan.get("purpose", "CONSUMPTION"),
                "loanTerm": loan.get("loan_term", 12),
                "productType": loan.get("product_id", "P001"),
                "channel": loan.get("channel", "APP"),
                "name": customer.get("customer_name", ""),
                "idNumber": customer.get("id_card", ""),
                "phone": customer.get("phone", ""),
            },
            "metadata": {
                "traceId": trace_id,
                "deviceFingerprint": f"fp_{customer.get('customer_id', 'unknown')}",
                "ipAddress": "127.0.0.1",
                "creditScore": credit_score,
                "blacklistFlag": blacklist_flag,
                "multiLoanCount": multi_loan_count,
                "overdueCount6m": overdue_count_6m,
                "debtRatio": debt_ratio,
                "deviceAnomaly": device_anomaly,
                "ipAnomaly": ip_anomaly,
                "phoneBlacklist": phone_blacklist,
                "incomeMonthly": income_monthly,
                "workYears": work_years,
                "creditQueryCount": credit_query_count,
                "assetAmount": round(asset_amount, 2),
                "repaymentRate": repayment_rate,
                "accountActiveMonths": account_active_months,
                "tier": tier,
            },
        }

        return request

    @staticmethod
    def _infer_tier(customer: dict, loan: dict) -> str:
        """从业务数据推断客户层级。"""
        income = customer.get("annual_income", 0) or 0
        status = loan.get("status", "")
        if status == "REJECTED" or (income > 0 and income < 30000):
            return "ANOMALY" if income < 10000 else "HIGH_RISK"
        if income > 200000:
            return "PREMIUM"
        if income > 80000:
            return "NORMAL"
        return "HIGH_RISK"

    @staticmethod
    def _calc_age(birth_date: str | None) -> int:
        """从出生日期字符串计算年龄。"""
        if not birth_date:
            return 30
        try:
            bd = datetime.strptime(str(birth_date)[:10], "%Y-%m-%d")
            today = datetime.now()
            age = today.year - bd.year - ((today.month, today.day) < (bd.month, bd.day))
            return max(18, min(65, age))
        except (ValueError, TypeError):
            return 30


# ---------------------------------------------------------------------------
# 执行引擎 (Tasks 9.3 ~ 9.6)
# ---------------------------------------------------------------------------

class DecisionRunner:
    """批量/实时决策执行引擎。"""

    def __init__(self, cfg: dict):
        self.cfg = cfg
        self.runner_cfg = cfg.get("decision_runner", {})
        self.decision_cfg = cfg.get("decision", {})
        self.builder = RequestBuilder(cfg)
        self.results: list[dict] = []
        self.errors: list[dict] = []
        self.concurrency = self.runner_cfg.get("concurrency", 4)
        self.retry_max = self.runner_cfg.get("retry_max", 3)
        self.retry_backoff = self.runner_cfg.get("retry_backoff", [1, 2, 4])
        self.timeout = self.runner_cfg.get("request_timeout", 10)

    def run_batch(self, sample: int = None, concurrency: int = None):
        """批量模式：遍历全部贷款申请记录。"""
        if concurrency:
            self.concurrency = concurrency

        print(f"\n{'='*60}")
        print("批量决策执行")
        print(f"并发度: {self.concurrency}, 抽样: {sample or '全部'}")
        print(f"{'='*60}")

        with MySQLHelper(self.cfg) as db:
            # 查贷款数据
            sql = "SELECT * FROM loan_application"
            if sample:
                sql += f" ORDER BY RAND() LIMIT {sample}"
            loans = db.query(sql)
            print(f"读取贷款申请: {len(loans)} 条")

            # 预加载客户数据
            customer_ids = list(set(r["customer_id"] for r in loans))
            customers = {}
            for cid in customer_ids:
                c = db.query_one("SELECT * FROM customer_info WHERE customer_id = %s", (cid,))
                if c:
                    customers[cid] = c

            print(f"匹配客户: {len(customers)} 人")

        # 并发执行决策
        start_time = time.time()
        completed = 0
        total = len(loans)

        with ThreadPoolExecutor(max_workers=self.concurrency) as executor:
            futures = {}
            for i, loan in enumerate(loans):
                cid = loan["customer_id"]
                customer = customers.get(cid, {})
                future = executor.submit(self._execute_one, loan, customer, i)
                futures[future] = i

            for future in as_completed(futures):
                completed += 1
                if completed % 100 == 0 or completed == total:
                    elapsed = time.time() - start_time
                    rate = completed / elapsed if elapsed > 0 else 0
                    print(f"  进度: {completed}/{total} ({completed*100//total}%), "
                          f"{rate:.1f} 条/秒")

        elapsed = time.time() - start_time
        rate = total / elapsed if elapsed > 0 else 0
        print(f"\n完成: {total} 条, 耗时 {elapsed:.1f}秒, {rate:.1f} 条/秒")

    def _execute_one(self, loan: dict, customer: dict, index: int):
        """执行单条决策 (含重试)。"""
        request = self.builder.build_from_row(loan, customer, seed=index)
        base_url = self.decision_cfg.get("url", "http://localhost:18080/api/v1/decision")

        last_error = None
        for attempt in range(self.retry_max + 1):
            try:
                with httpx.Client(timeout=self.timeout) as client:
                    resp = client.post(f"{base_url}/execute", json=request)

                if resp.status_code == 200:
                    result = resp.json()
                    self._collect_result(request, result, index)
                    return
                else:
                    last_error = f"HTTP {resp.status_code}: {resp.text[:100]}"
            except Exception as e:
                last_error = str(e)

            if attempt < self.retry_max:
                wait = self.retry_backoff[min(attempt, len(self.retry_backoff) - 1)]
                time.sleep(wait)

        # 全部重试失败
        self.errors.append({
            "index": index,
            "application_id": loan.get("application_no", ""),
            "customer_id": loan.get("customer_id", ""),
            "error": last_error,
            "time": beijing_now(),
            "request": request,
        })

    def _collect_result(self, request: dict, response: dict, index: int):
        """收集决策响应。"""
        metadata = request.get("metadata", {})
        applicant = request.get("applicant", {})

        log_entry = {
            "traceId": response.get("traceId", metadata.get("traceId", "")),
            "customerId": applicant.get("customerId", ""),
            "applicationId": applicant.get("applicationId", ""),
            "strategyId": request.get("strategyId", ""),
            "decisionResult": response.get("result", "UNKNOWN"),
            "score": response.get("score"),
            "riskLevel": self._map_risk(response.get("score")),
            "rejectReason": response.get("rejectReason", ""),
            "rejectCode": response.get("rejectCode", ""),
            "hitRules": response.get("extra", {}).get("hitRules", []) if response.get("extra") else [],
            "durationMs": response.get("durationMs", 0),
            "timestamp": beijing_now(),
            "requestSnapshot": json.dumps(self._mask_pii(applicant), ensure_ascii=False),
            "responseSnapshot": json.dumps(response, ensure_ascii=False),
        }
        self.results.append(log_entry)

    @staticmethod
    def _map_risk(score) -> str:
        if score is None:
            return "UNKNOWN"
        if score >= 650:
            return "A"
        if score >= 550:
            return "B"
        if score >= 500:
            return "C"
        return "D"

    @staticmethod
    def _mask_pii(applicant: dict) -> dict:
        """脱敏处理。"""
        masked = dict(applicant)
        if "idNumber" in masked:
            v = str(masked["idNumber"])
            masked["idNumber"] = v[:3] + "****" + v[-4:] if len(v) >= 10 else "***"
        if "phone" in masked:
            v = str(masked["phone"])
            masked["phone"] = v[:3] + "****" + v[-4:] if len(v) >= 8 else "***"
        if "name" in masked:
            n = str(masked["name"])
            masked["name"] = n[0] + "*" * (len(n) - 1) if len(n) > 1 else n
        return masked


# ---------------------------------------------------------------------------
# 持久化 (Task 9.5)
# ---------------------------------------------------------------------------

class ResultPersister:
    """决策结果持久化：ES + 本地 JSONL + CSV。"""

    def __init__(self, cfg: dict):
        self.cfg = cfg

    def persist_all(self, results: list[dict], errors: list[dict]):
        """持久化全部结果。"""
        if not results:
            print("无决策结果需要持久化")
            return

        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")

        # 写 JSONL
        jsonl_path = OUTPUT_DIR / f"decision_logs_{timestamp}.jsonl"
        with open(jsonl_path, "w", encoding="utf-8") as f:
            for r in results:
                f.write(json.dumps(r, ensure_ascii=False) + "\n")
        print(f"✓ JSONL 日志: {jsonl_path} ({len(results)} 条)")

        # 写 CSV 汇总
        csv_path = OUTPUT_DIR / f"decision_summary_{timestamp}.csv"
        fieldnames = ["traceId", "customerId", "applicationId", "decisionResult",
                      "score", "riskLevel", "rejectReason", "durationMs", "timestamp"]
        with open(csv_path, "w", newline="", encoding="utf-8") as f:
            writer = csv.DictWriter(f, fieldnames=fieldnames, extrasaction="ignore")
            writer.writeheader()
            for r in results:
                row = {k: r.get(k, "") for k in fieldnames}
                row["hitRules"] = json.dumps(r.get("hitRules", []), ensure_ascii=False)
                writer.writerow(row)
        print(f"✓ CSV 汇总: {csv_path} ({len(results)} 条)")

        # 写 ES (尝试)
        es_written = self._write_es(results)
        if es_written:
            print(f"✓ ES 写入: {es_written} 条")
        else:
            print("⚠ ES 写入跳过 (不可用)")

        # 写错误日志
        if errors:
            err_path = OUTPUT_DIR / f"decision_errors_{timestamp}.jsonl"
            with open(err_path, "w", encoding="utf-8") as f:
                for e in errors:
                    f.write(json.dumps(e, ensure_ascii=False) + "\n")
            print(f"✓ 错误日志: {err_path} ({len(errors)} 条)")

    def _write_es(self, results: list[dict]) -> int:
        """批量写入 ES。"""
        es_cfg = self.cfg.get("elasticsearch", {})
        host = es_cfg.get("host", "localhost")
        port = es_cfg.get("port", 9200)
        index_prefix = es_cfg.get("index_prefix", "decision-log-")
        now = datetime.now()
        index_name = f"{index_prefix}{now.strftime('%Y.%m')}"

        es_batch = self.cfg.get("decision_runner", {}).get("es_batch_size", 100)
        written = 0

        try:
            with httpx.Client(timeout=10.0) as client:
                for i in range(0, len(results), es_batch):
                    batch = results[i:i + es_batch]
                    body_lines = []
                    for r in batch:
                        body_lines.append(json.dumps({"index": {"_index": index_name}}))
                        body_lines.append(json.dumps(r, ensure_ascii=False))
                    body = "\n".join(body_lines) + "\n"

                    resp = client.post(
                        f"http://{host}:{port}/_bulk",
                        content=body,
                        headers={"Content-Type": "application/x-ndjson"},
                    )
                    if resp.status_code == 200:
                        resp_data = resp.json()
                        if not resp_data.get("errors", True):
                            written += len(batch)
        except Exception as e:
            logger.warning(f"ES 写入失败: {e}")

        return written


# ---------------------------------------------------------------------------
# 实时模式 (Task 9.6)
# ---------------------------------------------------------------------------

def run_realtime(cfg: dict):
    """实时模式 — 从最近 N 分钟的数据中间隔执行决策。"""
    print("\n实时决策执行模式 (每分钟轮询最近1分钟的新贷款申请)")
    print("按 Ctrl+C 停止\n")

    runner = DecisionRunner(cfg)
    persister = ResultPersister(cfg)

    try:
        while True:
            with MySQLHelper(cfg) as db:
                recent = db.query(
                    "SELECT * FROM loan_application ORDER BY id DESC LIMIT 20"
                )
            if recent:
                print(f"[{beijing_now()}] 处理 {len(recent)} 条...")
                for i, loan in enumerate(recent):
                    c = db.query_one("SELECT * FROM customer_info WHERE customer_id = %s",
                                     (loan["customer_id"],))
                    runner._execute_one(loan, c or {}, i)
                persister.persist_all(runner.results, runner.errors)
                runner.results.clear()
                runner.errors.clear()
            time.sleep(60)
    except KeyboardInterrupt:
        print("\n实时模式已停止")


# ---------------------------------------------------------------------------
# 统计输出
# ---------------------------------------------------------------------------

def print_execution_stats(results: list[dict]):
    """打印执行统计。"""
    if not results:
        return

    total = len(results)
    passed = sum(1 for r in results if r.get("decisionResult") == "PASS")
    rejected = sum(1 for r in results if r.get("decisionResult") == "REJECT")
    reviewed = sum(1 for r in results if r.get("decisionResult") == "REVIEW")
    manual = sum(1 for r in results if r.get("decisionResult") == "MANUAL")
    scores = [r.get("score") for r in results if r.get("score") is not None]
    durations = [r.get("durationMs") for r in results if r.get("durationMs")]

    print(f"\n{'='*60}")
    print("执行统计")
    print(f"{'='*60}")
    print(f"总请求数:   {total}")
    print(f"通过 (PASS):   {passed} ({passed*100//total if total else 0}%)")
    print(f"拒绝 (REJECT): {rejected} ({rejected*100//total if total else 0}%)")
    print(f"复核 (REVIEW): {reviewed} ({reviewed*100//total if total else 0}%)")
    print(f"人工 (MANUAL): {manual} ({manual*100//total if total else 0}%)")

    if scores:
        scores_sorted = sorted(scores)
        p50 = scores_sorted[len(scores_sorted)//2]
        p95 = scores_sorted[int(len(scores_sorted)*0.95)]
        p99 = scores_sorted[int(len(scores_sorted)*0.99)]
        print(f"平均评分:   {sum(scores)/len(scores):.0f}")
        print(f"评分 P50/P95/P99: {p50}/{p95}/{p99}")

    if durations:
        dur_sorted = sorted(durations)
        print(f"平均延迟:   {sum(durations)/len(durations):.1f}ms")
        print(f"延迟 P99:   {dur_sorted[int(len(dur_sorted)*0.99)]}ms")


# ---------------------------------------------------------------------------
# 主入口
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(description="决策引擎端到端执行器")
    parser.add_argument("--mode", choices=["batch", "realtime"], default="batch",
                        help="执行模式 (默认: batch)")
    parser.add_argument("--sample", type=int, default=None,
                        help="抽样数量 (默认: 全部)")
    parser.add_argument("--concurrency", type=int, default=None,
                        help="并发度 (默认: 4)")
    parser.add_argument("--auto-report", action="store_true",
                        help="执行完成后自动生成报表")
    args = parser.parse_args()

    cfg = get_config()

    # 前置健康检查
    checker = HealthChecker(cfg)
    if not checker.check_all():
        sys.exit(1)

    runner = DecisionRunner(cfg)
    persister = ResultPersister(cfg)

    if args.mode == "batch":
        runner.run_batch(sample=args.sample, concurrency=args.concurrency)
    elif args.mode == "realtime":
        run_realtime(cfg)
        return

    # 持久化
    persister.persist_all(runner.results, runner.errors)

    # 统计
    print_execution_stats(runner.results)

    # 自动报表
    if args.auto_report and runner.results:
        print("\n--- 自动生成报表 ---")
        try:
            from decision_report import ReportGenerator
            reporter = ReportGenerator(cfg)
            report_path = reporter.generate(runner.results)
            print(f"报表已生成: {report_path}")
        except ImportError:
            print("无法导入 decision_report 模块")

    # 返回结果数量供外部使用
    return len(runner.results)


if __name__ == "__main__":
    main()
