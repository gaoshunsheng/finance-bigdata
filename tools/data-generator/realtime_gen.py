#!/usr/bin/env python3
"""
实时数据模拟器 — 持续生成实时事件，双路径写入 MySQL + Kafka。

路径 A: MySQL INSERT → 触发 Canal CDC → 长名 Kafka topics (数据归档)
路径 B: Kafka 扁平事件 → 短名 topics → Flink 消费 (实时特征)
路径 C: 决策引擎 HTTP 调用 → ES 日志 (10% 事件)

用法:
    python realtime_gen.py                        # 默认每秒 20 事件，持续运行
    python realtime_gen.py --rate 50 --duration 60  # 每秒 50 事件，运行 60 秒
"""

import argparse
import json
import random
import signal
import sys
import time
from datetime import datetime, timedelta, timezone
from typing import Optional

import httpx
from loguru import logger

from config import get_config, BEIJING_TZ, beijing_now
from db import MySQLHelper
from kafka_helper import KafkaHelper
from models import (
    CreditQueryEvent, OverdueEvent, ApplyEvent, TransactionEvent,
    DecisionRequest, DecisionResponse,
)

# ---------------------------------------------------------------------------
# 常量
# ---------------------------------------------------------------------------

# 事件类型及权重
EVENT_TYPES = [
    ("loan_application", 0.40),
    ("repayment_record", 0.30),
    ("credit_report", 0.20),
    ("decision_request", 0.10),
]

PRODUCTS = ["P001", "P002", "P003"]
CHANNELS = ["APP", "WEB", "WECHAT", "OFFLINE", "PARTNER"]
INSTITUTIONS = ["工商银行", "建设银行", "招商银行", "消费金融A", "小贷公司A", "其他"]
QUERY_TYPES = ["PBOC", "反欺诈", "多头查询"]
QUERY_PURPOSES = ["贷款审批", "信用卡审批", "本人查询", "担保审查"]
PAYMENT_TYPES = ["PAYMENT", "DISBURSEMENT", "FEE"]


# ---------------------------------------------------------------------------
# 实时数据生成器
# ---------------------------------------------------------------------------

class RealtimeGenerator:
    """实时数据模拟器"""

    def __init__(self, rate: int = 20, duration: int = 0, decision_pct: int = 10):
        self.rate = rate
        self.duration = duration
        self.decision_pct = decision_pct
        self.running = False

        # 统计
        self.stats = {
            "total_events": 0,
            "mysql_inserts": 0,
            "kafka_events": 0,
            "decision_calls": 0,
            "decision_results": {},
            "start_time": None,
            "errors": 0,
        }

        # 客户池
        self.customers: list[dict] = []
        self.active_loans: list[dict] = []

        # 组件 (延迟初始化)
        self.db: Optional[MySQLHelper] = None
        self.kafka: Optional[KafkaHelper] = None
        self.http: Optional[httpx.Client] = None

    def _load_customer_pool(self):
        """从 MySQL 加载已有客户池。"""
        logger.info("从 MySQL 加载客户池...")
        self.customers = self.db.query("SELECT customer_id, customer_name, annual_income FROM customer_info")
        self.active_loans = self.db.query(
            "SELECT application_no, customer_id, loan_amount, loan_term, apply_time "
            "FROM loan_application WHERE status IN ('APPROVED', 'DISBURSED', 'CLOSED')"
        )
        logger.info(f"客户池: {len(self.customers)} 客户, {len(self.active_loans)} 活跃贷款")

    def _random_customer(self) -> dict:
        """随机选择一个客户。"""
        return random.choice(self.customers) if self.customers else {
            "customer_id": f"CUST_{random.randint(1, 10000):05d}",
            "customer_name": "模拟客户",
            "annual_income": 100000.0,
        }

    def _random_loan(self) -> Optional[dict]:
        """随机选择一笔活跃贷款。"""
        return random.choice(self.active_loans) if self.active_loans else None

    def _now_str(self) -> str:
        """当前北京时间字符串。"""
        return beijing_now()

    def _event_time_str(self) -> str:
        """事件时间 (LocalDateTime 格式: yyyy-MM-ddTHH:mm:ss)。"""
        now = datetime.now(BEIJING_TZ).replace(tzinfo=None)
        # 微小随机偏移避免完全相同时间戳
        offset = random.uniform(0, 1)
        event_time = now + timedelta(seconds=offset)
        return event_time.strftime("%Y-%m-%dT%H:%M:%S")

    def _event_time_instant(self) -> str:
        """事件时间 (ISO-8601 Instant 格式: yyyy-MM-ddTHH:mm:ssZ)。"""
        now = datetime.now(tz=timezone.utc).replace(tzinfo=None)
        offset = random.uniform(0, 1)
        event_time = now + timedelta(seconds=offset)
        return event_time.strftime("%Y-%m-%dT%H:%M:%SZ")

    # ==================== 事件生成 ====================

    def _gen_loan_application(self) -> dict:
        """生成贷款申请事件。"""
        customer = self._random_customer()
        now = self._now_str()
        event_time = self._event_time_str()

        product_id = random.choice(PRODUCTS)
        channel = random.choice(CHANNELS)
        loan_amount = round(random.uniform(5000, 500000), 2)
        loan_term = random.choice([3, 6, 12, 24, 36])
        app_no = f"LA_{datetime.now().strftime('%Y%m%d%H%M%S')}{random.randint(1000, 9999)}"

        # 路径 A: MySQL INSERT
        try:
            self.db.execute(
                "INSERT INTO loan_application "
                "(application_no, customer_id, product_id, channel, loan_amount, "
                "loan_term, purpose, apply_time, status, source_system, etl_time) "
                "VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)",
                (app_no, customer["customer_id"], product_id, channel, loan_amount,
                 loan_term, "日常消费", now, "PENDING", "CREDIT_PLATFORM", now)
            )
            self.stats["mysql_inserts"] += 1
        except Exception as e:
            logger.error(f"MySQL INSERT 失败: {e}")
            self.stats["errors"] += 1

        # 路径 B: Kafka 扁平事件 → application_event
        apply_event = ApplyEvent(
            customerId=customer["customer_id"],
            productId=product_id,
            channel=channel,
            eventTime=event_time,
        )
        self.kafka.send_event("application_event", apply_event.model_dump(), key=customer["customer_id"])

        # 路径 B: Kafka 扁平事件 → transaction_event (贷款申请同时也是一笔交易)
        txn_event = TransactionEvent(
            customerId=customer["customer_id"],
            transactionId=f"TXN_{datetime.now().strftime('%Y%m%d%H%M%S')}{random.randint(100, 999)}",
            amount=loan_amount,
            type="DISBURSEMENT",
            eventTime=self._event_time_instant(),
        )
        self.kafka.send_event("transaction_event", txn_event.model_dump(), key=customer["customer_id"])

        self.stats["kafka_events"] += 2

        return {
            "type": "LOAN",
            "customer_id": customer["customer_id"],
            "app_no": app_no,
            "amount": loan_amount,
        }

    def _gen_repayment(self) -> dict:
        """生成还款事件。"""
        loan = self._random_loan()
        if not loan:
            return {"type": "SKIP", "reason": "无活跃贷款"}

        customer_id = loan["customer_id"]
        loan_id = loan["application_no"]
        now = self._now_str()
        event_time = self._event_time_str()

        # 还款金额 (简化: 按期数估算)
        monthly_payment = round(loan["loan_amount"] / loan["loan_term"] * 1.005, 2)
        overdue_days = random.choices([0, random.randint(1, 30)], weights=[70, 30], k=1)[0]

        # 路径 A: MySQL INSERT
        try:
            self.db.execute(
                "INSERT INTO repayment_record "
                "(loan_id, customer_id, installment_no, due_date, repay_date, "
                "repay_amount, principal, interest, overdue_days, status, source_system, etl_time) "
                "VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)",
                (loan_id, customer_id, random.randint(1, loan["loan_term"]),
                 now[:10], now[:10], monthly_payment,
                 round(monthly_payment * 0.8, 2), round(monthly_payment * 0.2, 2),
                 overdue_days, "PAID" if overdue_days == 0 else "OVERDUE",
                 "CREDIT_PLATFORM", now)
            )
            self.stats["mysql_inserts"] += 1
        except Exception as e:
            logger.error(f"MySQL INSERT 失败: {e}")
            self.stats["errors"] += 1

        # 路径 B: Kafka 扁平事件 → cdc_overdue_event (仅逾期记录)
        if overdue_days > 0:
            overdue_event = OverdueEvent(
                customerId=customer_id,
                loanId=loan_id,
                overdueDays=overdue_days,
                overdueAmount=round(monthly_payment * 0.5, 2),
                eventTime=event_time,
            )
            self.kafka.send_event("overdue_event", overdue_event.model_dump(), key=customer_id)
            self.stats["kafka_events"] += 1

        # 路径 B: Kafka → transaction_event
        txn_event = TransactionEvent(
            customerId=customer_id,
            transactionId=f"TXN_{datetime.now().strftime('%Y%m%d%H%M%S')}{random.randint(100, 999)}",
            amount=monthly_payment,
            type="PAYMENT",
            eventTime=self._event_time_instant(),
        )
        self.kafka.send_event("transaction_event", txn_event.model_dump(), key=customer_id)
        self.stats["kafka_events"] += 1

        status_str = "逾期" if overdue_days > 0 else "正常"
        return {
            "type": "REPAY",
            "customer_id": customer_id,
            "loan_id": loan_id,
            "amount": monthly_payment,
            "status": status_str,
            "overdue_days": overdue_days,
        }

    def _gen_credit_report(self) -> dict:
        """生成征信查询事件。"""
        customer = self._random_customer()
        now = self._now_str()
        event_time = self._event_time_str()

        report_no = f"CR_{customer['customer_id']}_{datetime.now().strftime('%Y%m%d%H%M%S')}"

        # 路径 A: MySQL INSERT
        try:
            self.db.execute(
                "INSERT INTO credit_report "
                "(customer_id, report_no, query_institution, query_purpose, query_time, "
                "loan_count, credit_card_count, overdue_count, total_debt, source_system, etl_time) "
                "VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)",
                (customer["customer_id"], report_no,
                 random.choice(INSTITUTIONS), random.choice(QUERY_PURPOSES), now,
                 random.randint(0, 10), random.randint(0, 5), random.randint(0, 3),
                 round(random.uniform(0, 500000), 2),
                 "CREDIT_PLATFORM", now)
            )
            self.stats["mysql_inserts"] += 1
        except Exception as e:
            logger.error(f"MySQL INSERT 失败: {e}")
            self.stats["errors"] += 1

        # 路径 B: Kafka → cdc_credit_query
        credit_event = CreditQueryEvent(
            customerId=customer["customer_id"],
            eventTime=event_time,
            queryType=random.choice(QUERY_TYPES),
            institution=random.choice(INSTITUTIONS),
        )
        self.kafka.send_event("credit_query", credit_event.model_dump(), key=customer["customer_id"])
        self.stats["kafka_events"] += 1

        return {
            "type": "CREDIT",
            "customer_id": customer["customer_id"],
            "report_no": report_no,
        }

    def _gen_decision_request(self) -> dict:
        """生成决策引擎请求。"""
        customer = self._random_customer()
        cfg = get_config()

        request_body = {
            "strategyId": cfg["decision"]["strategy_id"],
            "channel": random.choice(["APP", "WEB", "API", "PARTNER"]),
            "applicant": {
                "name": customer.get("customer_name", "测试用户"),
                "customerId": customer["customer_id"],
                "age": random.randint(22, 55),
                "income": customer.get("annual_income", 100000.0),
            },
            "metadata": {
                "deviceFingerprint": f"fp_{random.randint(100000, 999999)}",
                "ipAddress": f"192.168.{random.randint(1, 255)}.{random.randint(1, 255)}",
            },
        }

        try:
            resp = self.http.post(
                f"{cfg['decision']['url']}/execute",
                json=request_body,
                timeout=5.0,
            )
            if resp.status_code == 200:
                result = resp.json()
                result_str = result.get("result", "UNKNOWN")
                self.stats["decision_results"][result_str] = self.stats["decision_results"].get(result_str, 0) + 1
                self.stats["decision_calls"] += 1

                return {
                    "type": "DECIDE",
                    "customer_id": customer["customer_id"],
                    "result": result_str,
                    "score": result.get("score"),
                    "duration_ms": result.get("durationMs"),
                }
            else:
                logger.warning(f"决策引擎返回 {resp.status_code}: {resp.text[:100]}")
                self.stats["errors"] += 1
        except Exception as e:
            logger.warning(f"决策引擎调用失败: {e}")
            self.stats["errors"] += 1

        return {"type": "DECIDE", "customer_id": customer["customer_id"], "result": "ERROR"}

    # ==================== 主循环 ====================

    def _pick_event_type(self) -> str:
        """按权重随机选择事件类型。"""
        r = random.random()
        cumulative = 0.0
        for event_type, weight in EVENT_TYPES:
            cumulative += weight
            if r <= cumulative:
                return event_type
        return EVENT_TYPES[-1][0]

    def _generate_one_event(self) -> dict:
        """生成一条事件。"""
        event_type = self._pick_event_type()

        if event_type == "loan_application":
            return self._gen_loan_application()
        elif event_type == "repayment_record":
            return self._gen_repayment()
        elif event_type == "credit_report":
            return self._gen_credit_report()
        elif event_type == "decision_request":
            return self._gen_decision_request()
        else:
            return {"type": "UNKNOWN"}

    def _print_event(self, event: dict):
        """格式化打印事件。"""
        now = self._now_str()[11:19]  # HH:MM:SS
        etype = event.get("type", "?")
        cid = event.get("customer_id", "?")

        if etype == "LOAN":
            logger.info(f"[{now}] LOAN    {cid}  {event.get('app_no', '?')}  "
                         f"金额:{event.get('amount', 0):,.0f}")
        elif etype == "REPAY":
            logger.info(f"[{now}] REPAY   {cid}  {event.get('loan_id', '?')}  "
                         f"{event.get('status', '?')}  {event.get('amount', 0):,.2f}")
        elif etype == "CREDIT":
            logger.info(f"[{now}] CREDIT  {cid}  {event.get('report_no', '?')}")
        elif etype == "DECIDE":
            logger.info(f"[{now}] DECIDE  {cid}  结果:{event.get('result', '?')}  "
                         f"评分:{event.get('score', '-')}  {event.get('duration_ms', '-')}ms")
        elif etype == "SKIP":
            pass  # 跳过不打印

    def _print_stats(self):
        """打印统计摘要。"""
        if not self.stats["start_time"]:
            return

        elapsed = time.time() - self.stats["start_time"]
        rate = self.stats["total_events"] / elapsed if elapsed > 0 else 0

        logger.info("=" * 50)
        logger.info("实时生成器统计")
        logger.info("-" * 50)
        logger.info(f"  运行时长:       {elapsed:.1f}s")
        logger.info(f"  总事件数:       {self.stats['total_events']:,}")
        logger.info(f"  平均速率:       {rate:.1f} events/s")
        logger.info(f"  MySQL INSERTs:  {self.stats['mysql_inserts']:,}")
        logger.info(f"  Kafka Events:   {self.stats['kafka_events']:,}")
        logger.info(f"  决策调用:       {self.stats['decision_calls']:,}")
        if self.stats["decision_results"]:
            for result, count in self.stats["decision_results"].items():
                logger.info(f"    {result}: {count}")
        logger.info(f"  错误数:         {self.stats['errors']:,}")
        logger.info("=" * 50)

    def _handle_signal(self, signum, frame):
        """信号处理: 优雅停止。"""
        logger.info(f"\n收到信号 {signum}, 正在停止...")
        self.running = False

    def run(self):
        """启动实时数据生成。"""
        cfg = get_config()

        logger.info("=" * 60)
        logger.info(f"实时数据模拟器启动")
        logger.info(f"  速率: {self.rate} events/s")
        logger.info(f"  时长: {'持续运行' if self.duration == 0 else f'{self.duration}s'}")
        logger.info(f"  决策调用占比: {self.decision_pct}%")
        logger.info("=" * 60)

        # 初始化组件
        self.db = MySQLHelper(cfg)
        self.kafka = KafkaHelper(cfg)
        self.http = httpx.Client(timeout=5.0)

        # 加载客户池
        self._load_customer_pool()

        if not self.customers:
            logger.error("客户池为空! 请先运行 batch_data_gen.py 生成基础数据")
            return

        # 注册信号
        signal.signal(signal.SIGINT, self._handle_signal)
        signal.signal(signal.SIGTERM, self._handle_signal)

        self.running = True
        self.stats["start_time"] = time.time()
        interval = 1.0 / self.rate

        try:
            while self.running:
                event = self._generate_one_event()
                self.stats["total_events"] += 1
                self._print_event(event)

                # 频率控制: 基础间隔 + 随机抖动
                jitter = random.uniform(0.7, 1.3)
                sleep_time = interval * jitter
                time.sleep(sleep_time)

                # 时长检查
                if self.duration > 0:
                    elapsed = time.time() - self.stats["start_time"]
                    if elapsed >= self.duration:
                        logger.info(f"已运行 {self.duration}s, 停止生成")
                        break

        except Exception as e:
            logger.error(f"运行异常: {e}")
        finally:
            self.running = False
            self._print_stats()
            if self.kafka:
                self.kafka.close()
            if self.db:
                self.db.close()
            if self.http:
                self.http.close()
            logger.info("实时生成器已停止")


# ---------------------------------------------------------------------------
# 入口
# ---------------------------------------------------------------------------
def main():
    parser = argparse.ArgumentParser(description="实时数据模拟器")
    parser.add_argument("--rate", type=int, default=20, help="每秒事件数 (默认 20)")
    parser.add_argument("--duration", type=int, default=0, help="运行时长秒数 (0=持续运行)")
    parser.add_argument("--decision-pct", type=int, default=10, help="决策调用占比 (默认 10)")
    args = parser.parse_args()

    gen = RealtimeGenerator(
        rate=args.rate,
        duration=args.duration,
        decision_pct=args.decision_pct,
    )
    gen.run()


if __name__ == "__main__":
    main()
