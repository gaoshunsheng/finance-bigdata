#!/usr/bin/env python3
"""
批量历史数据生成器 — 生成 10,000 客户 × 12 个月的金融业务数据。

生成顺序: customer_info → credit_report → loan_application → repayment_record
输出: output/expected_counts.json (验证基线)

用法:
    python batch_data_gen.py
    python batch_data_gen.py --customers 1000 --months 6 --seed 123
"""

import argparse
import json
import random
from datetime import datetime, timedelta, date
from pathlib import Path
from typing import Optional

from faker import Faker
from loguru import logger

from config import get_config, BEIJING_TZ
from db import MySQLHelper
from models import (
    CustomerInfo, LoanApplication, RepaymentRecord, CreditReport,
    CustomerTier, ExpectedCounts, TierStats, MonthlyDistribution,
)

# ---------------------------------------------------------------------------
# 常量定义
# ---------------------------------------------------------------------------

# 产品定义: (product_id, 名称, 金额范围(万), 可选期限)
PRODUCTS = [
    ("P001", "消费贷",   (0.5, 5),   [3, 6, 12, 24]),
    ("P002", "经营贷",   (5, 50),    [6, 12, 24, 36]),
    ("P003", "信用卡分期", (0.5, 3),  [3, 6, 12, 24]),
]

# 渠道及权重
CHANNELS = ["APP", "WEB", "WECHAT", "OFFLINE", "PARTNER"]
CHANNEL_WEIGHTS = [40, 20, 20, 10, 10]

# 贷款用途
PURPOSES = ["装修", "教育", "医疗", "经营周转", "日常消费", "购车", "旅游", "婚庆", "其他"]

# 学历及权重
EDUCATIONS = ["高中及以下", "大专", "本科", "硕士", "博士"]
EDUCATION_WEIGHTS = [15, 25, 40, 15, 5]

# 行业
INDUSTRIES = [
    "金融", "IT/互联网", "制造业", "教育", "零售/批发", "医疗/健康",
    "房地产", "建筑", "交通运输", "餐饮", "文化传媒", "政府/事业单位",
    "农业", "能源/矿业", "电信", "法律", "咨询", "物流", "旅游", "其他",
]

# 省份-城市 (经济发达地区加权)
CITIES = [
    ("北京市", "北京市"), ("上海市", "上海市"), ("广东省", "深圳市"),
    ("广东省", "广州市"), ("浙江省", "杭州市"), ("江苏省", "南京市"),
    ("江苏省", "苏州市"), ("四川省", "成都市"), ("湖北省", "武汉市"),
    ("福建省", "福州市"), ("山东省", "济南市"), ("河南省", "郑州市"),
    ("湖南省", "长沙市"), ("安徽省", "合肥市"), ("重庆市", "重庆市"),
    ("天津市", "天津市"), ("陕西省", "西安市"), ("辽宁省", "大连市"),
    ("浙江省", "宁波市"), ("广东省", "东莞市"),
]

# 征信查询机构
INSTITUTIONS = ["工商银行", "建设银行", "招商银行", "消费金融A", "消费金融B",
                "小贷公司A", "小贷公司B", "信用卡中心", "农商行", "其他"]

# 征信查询用途
QUERY_PURPOSES = ["贷款审批", "信用卡审批", "本人查询", "担保审查", "贷后管理", "其他"]
QUERY_PURPOSE_WEIGHTS = [40, 15, 20, 10, 10, 5]

# 客户分层配置
TIER_CONFIG = {
    CustomerTier.PREMIUM: {
        "income_range": (300000, 1000000),     # 30w-100w
        "age_range": (30, 50),
        "loan_count_range": (2, 5),
        "overdue_prob": 0.05,
        "max_overdue_days": 5,
        "credit_query_range": (0, 3),
        "approve_rate": 0.85,
    },
    CustomerTier.NORMAL: {
        "income_range": (100000, 500000),      # 10w-50w
        "age_range": (25, 55),
        "loan_count_range": (1, 3),
        "overdue_prob": 0.30,
        "max_overdue_days": 30,
        "credit_query_range": (1, 5),
        "approve_rate": 0.65,
    },
    CustomerTier.HIGH_RISK: {
        "income_range": (30000, 150000),       # 3w-15w
        "age_range": (20, 40),
        "loan_count_range": (2, 4),
        "overdue_prob": 0.70,
        "max_overdue_days": 90,
        "credit_query_range": (3, 15),
        "approve_rate": 0.40,
    },
    CustomerTier.ANOMALY: {
        "income_range": (10000, 2000000),      # 极端值
        "age_range": (18, 65),
        "loan_count_range": (0, 2),
        "overdue_prob": 0.90,
        "max_overdue_days": 180,
        "credit_query_range": (5, 20),
        "approve_rate": 0.25,
    },
}

# 年利率 (用于等额本息计算)
ANNUAL_RATE = 0.06


# ---------------------------------------------------------------------------
# 工具函数
# ---------------------------------------------------------------------------

def generate_id_card(fake: Faker, birth_date: date, gender: str) -> str:
    """生成 18 位身份证号 (含校验码)。"""
    # 区域码 (使用常见区域码)
    area_codes = ["110101", "310101", "440305", "440106", "330102",
                  "320102", "510104", "420102", "350102", "370102"]
    area = random.choice(area_codes)
    birth_str = birth_date.strftime("%Y%m%d")
    seq = random.randint(10, 99)
    # 第 17 位: 奇数=男, 偶数=女
    gender_digit = random.choice([1, 3, 5, 7, 9]) if gender == "男" else random.choice([0, 2, 4, 6, 8])
    body = f"{area}{birth_str}{seq}{gender_digit}"
    # 校验码
    weights = [7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2]
    check_chars = "10X98765432"
    total = sum(int(body[i]) * weights[i] for i in range(17))
    return body + check_chars[total % 11]


def calc_monthly_payment(principal: float, months: int, annual_rate: float = ANNUAL_RATE) -> float:
    """等额本息月供计算。"""
    r = annual_rate / 12
    if r == 0:
        return round(principal / months, 2)
    payment = principal * r * (1 + r) ** months / ((1 + r) ** months - 1)
    return round(payment, 2)


def amortize_payment(principal: float, months: int, annual_rate: float = ANNUAL_RATE) -> list[dict]:
    """
    等额本息分期明细 — 返回每期的本金和利息。

    Returns:
        [{"principal": float, "interest": float}, ...]
    """
    r = annual_rate / 12
    monthly = calc_monthly_payment(principal, months, annual_rate)
    remaining = principal
    result = []
    for _ in range(months):
        interest = round(remaining * r, 2)
        princ = round(monthly - interest, 2)
        if remaining < princ:
            princ = round(remaining, 2)
        remaining = round(remaining - princ, 2)
        result.append({"principal": princ, "interest": interest})
    # 修正最后一期
    if result and remaining > 0:
        result[-1]["principal"] = round(result[-1]["principal"] + remaining, 2)
    return result


# ---------------------------------------------------------------------------
# 数据生成器
# ---------------------------------------------------------------------------

class BatchDataGenerator:
    """批量历史数据生成器"""

    def __init__(self, customer_count: int = 10000, months: int = 12,
                 start_date: str = "2025-07-01", seed: int = 42):
        self.customer_count = customer_count
        self.months = months
        self.start_date = datetime.strptime(start_date, "%Y-%m-%d")
        self.end_date = self.start_date + timedelta(days=months * 30)
        self.seed = seed

        random.seed(seed)
        self.fake = Faker("zh_CN")
        Faker.seed(seed)

        # 统计
        self.stats = ExpectedCounts(
            generated_at=datetime.now(BEIJING_TZ).strftime("%Y-%m-%d %H:%M:%S"),
            parameters={"customer_count": customer_count, "months": months,
                        "start_date": start_date, "seed": seed},
        )

    def _assign_tier(self, index: int) -> CustomerTier:
        """按比例分配客户分层。"""
        pct = index / self.customer_count
        if pct < 0.40:
            return CustomerTier.PREMIUM
        elif pct < 0.70:
            return CustomerTier.NORMAL
        elif pct < 0.90:
            return CustomerTier.HIGH_RISK
        else:
            return CustomerTier.ANOMALY

    def generate_customers(self) -> list[dict]:
        """生成客户信息。"""
        logger.info(f"开始生成 {self.customer_count} 条客户信息...")
        rows = []
        tier_counts = {t.value: 0 for t in CustomerTier}

        for i in range(self.customer_count):
            tier = self._assign_tier(i)
            tc = TIER_CONFIG[tier]
            tier_counts[tier.value] += 1

            # 年龄
            age = random.randint(tc["age_range"][0], tc["age_range"][1])
            birth_year = datetime.now().year - age
            birth_date = date(birth_year, random.randint(1, 12), random.randint(1, 28))

            # 性别
            gender = random.choice(["男", "女"])

            # 姓名
            customer_name = self.fake.name()

            # 收入 (按分层 + 学历影响)
            edu = random.choices(EDUCATIONS, weights=EDUCATION_WEIGHTS, k=1)[0]
            income_low, income_high = tc["income_range"]
            edu_bonus = {"博士": 1.5, "硕士": 1.3, "本科": 1.0, "大专": 0.8, "高中及以下": 0.6}
            income = round(random.uniform(income_low, income_high) * edu_bonus.get(edu, 1.0), 2)

            # 城市
            province, city = random.choice(CITIES)
            address = f"{province}{city}{self.fake.street_address()}"

            # 行业 + 单位
            industry = random.choice(INDUSTRIES)
            employer = f"{self.fake.company()}{self.fake.company_suffix()}"

            # 婚姻状况 (与年龄相关)
            if age < 25:
                marital = random.choices(["未婚", "已婚"], weights=[80, 20], k=1)[0]
            elif age < 40:
                marital = random.choices(["未婚", "已婚", "离异"], weights=[20, 65, 15], k=1)[0]
            else:
                marital = random.choices(["未婚", "已婚", "离异"], weights=[10, 70, 20], k=1)[0]

            # 身份证号
            id_card = generate_id_card(self.fake, birth_date, gender)

            # 手机号
            phone_prefix = random.choice(["13", "15", "18", "19"])
            phone = f"{phone_prefix}{random.randint(100000000, 999999999)}"

            # 创建时间 (在 start_date 之前)
            etl_time = (self.start_date - timedelta(days=random.randint(30, 365))).strftime("%Y-%m-%d %H:%M:%S")

            customer = {
                "customer_id": f"CUST_{i + 1:05d}",
                "customer_name": customer_name,
                "id_card": id_card,
                "phone": phone,
                "gender": gender,
                "birth_date": birth_date.strftime("%Y-%m-%d"),
                "education": edu,
                "marital_status": marital,
                "address": address,
                "employer": employer,
                "industry": industry,
                "annual_income": income,
                "source_system": "CREDIT_PLATFORM",
                "etl_time": etl_time,
            }
            rows.append(customer)

        # 记录分层统计
        for tier_name, count in tier_counts.items():
            self.stats.tier_stats[tier_name] = TierStats(count=count)

        self.stats.customer_count = len(rows)
        logger.info(f"客户信息生成完成: {len(rows)} 条")
        return rows

    def generate_loan_applications(self, customers: list[dict]) -> list[dict]:
        """生成贷款申请 (依赖客户数据)。"""
        logger.info("开始生成贷款申请...")
        rows = []
        app_no_counter = 0
        monthly = {}

        for cust in customers:
            tier = self._assign_tier(int(cust["customer_id"].split("_")[1]) - 1)
            tc = TIER_CONFIG[tier]
            num_loans = random.randint(*tc["loan_count_range"])

            for _ in range(num_loans):
                # 时间点 (均匀分布在时间范围内，避开周末低谷)
                days_offset = random.randint(0, int((self.end_date - self.start_date).total_seconds() / 86400))
                apply_dt = self.start_date + timedelta(days=days_offset)
                # 周末低谷: 周末申请概率降低 50%
                if apply_dt.weekday() >= 5 and random.random() < 0.5:
                    apply_dt -= timedelta(days=random.randint(1, 2))

                apply_time = apply_dt.replace(
                    hour=random.randint(8, 20),
                    minute=random.randint(0, 59),
                    second=random.randint(0, 59),
                )

                # 产品
                product = random.choice(PRODUCTS)
                product_id, _, (amt_low, amt_high), terms = product
                loan_amount = round(random.uniform(amt_low * 10000, amt_high * 10000), 2)
                loan_term = random.choice(terms)

                # 渠道
                channel = random.choices(CHANNELS, weights=CHANNEL_WEIGHTS, k=1)[0]
                purpose = random.choice(PURPOSES)

                # 状态 (按分层审批率)
                if random.random() < tc["approve_rate"]:
                    status = random.choices(
                        ["APPROVED", "DISBURSED", "CLOSED"],
                        weights=[20, 40, 40], k=1)[0]
                else:
                    status = "REJECTED"

                app_no_counter += 1
                app_no = f"LA_{apply_time.strftime('%Y%m%d')}{app_no_counter:04d}"

                row = {
                    "application_no": app_no,
                    "customer_id": cust["customer_id"],
                    "product_id": product_id,
                    "channel": channel,
                    "loan_amount": loan_amount,
                    "loan_term": loan_term,
                    "purpose": purpose,
                    "apply_time": apply_time.strftime("%Y-%m-%d %H:%M:%S"),
                    "status": status,
                    "source_system": "CREDIT_PLATFORM",
                    "etl_time": apply_time.strftime("%Y-%m-%d %H:%M:%S"),
                }
                rows.append(row)

                # 月度统计
                month_key = apply_time.strftime("%Y-%m")
                monthly.setdefault(month_key, {"customer_count": 0, "loan_count": 0,
                                                "repayment_count": 0, "credit_report_count": 0})
                monthly[month_key]["loan_count"] += 1

        self.stats.loan_application_count = len(rows)
        self._monthly = monthly
        logger.info(f"贷款申请生成完成: {len(rows)} 条")
        return rows

    def generate_repayment_records(self, loans: list[dict]) -> list[dict]:
        """生成还款记录 (依赖贷款申请，仅为 APPROVED/DISBURSED/CLOSED 状态)。"""
        logger.info("开始生成还款记录...")
        rows = []
        active_loans = [l for l in loans if l["status"] in ("APPROVED", "DISBURSED", "CLOSED")]

        for loan in active_loans:
            tier = self._assign_tier(int(loan["customer_id"].split("_")[1]) - 1)
            tc = TIER_CONFIG[tier]

            loan_amount = loan["loan_amount"]
            loan_term = loan["loan_term"]
            apply_time = datetime.strptime(loan["apply_time"], "%Y-%m-%d %H:%M:%S")
            disburse_date = apply_time + timedelta(days=random.randint(3, 10))

            # 等额本息分期明细
            schedule = amortize_payment(loan_amount, loan_term)

            for installment in range(loan_term):
                due_date = disburse_date + timedelta(days=30 * (installment + 1))

                # 只生成已到期的还款记录
                if due_date.date() > datetime.now().date():
                    continue

                detail = schedule[installment]

                # 逾期天数
                if random.random() < tc["overdue_prob"]:
                    overdue_days = random.randint(1, tc["max_overdue_days"])
                else:
                    overdue_days = 0

                repay_date = due_date + timedelta(days=overdue_days if overdue_days > 0 else 0)

                if overdue_days == 0:
                    repay_date_val = due_date + timedelta(days=random.randint(0, 2))
                    status = "PAID"
                else:
                    repay_date_val = repay_date
                    status = "OVERDUE" if overdue_days > 0 else "PAID"

                etl_time = repay_date_val + timedelta(hours=random.randint(0, 12))

                row = {
                    "loan_id": loan["application_no"],
                    "customer_id": loan["customer_id"],
                    "installment_no": installment + 1,
                    "due_date": due_date.strftime("%Y-%m-%d"),
                    "repay_date": repay_date_val.strftime("%Y-%m-%d"),
                    "repay_amount": round(detail["principal"] + detail["interest"], 2),
                    "principal": detail["principal"],
                    "interest": detail["interest"],
                    "overdue_days": overdue_days,
                    "status": status,
                    "source_system": "CREDIT_PLATFORM",
                    "etl_time": etl_time.strftime("%Y-%m-%d %H:%M:%S"),
                }
                rows.append(row)

                # 月度统计
                month_key = due_date.strftime("%Y-%m")
                self._monthly.setdefault(month_key, {"customer_count": 0, "loan_count": 0,
                                                       "repayment_count": 0, "credit_report_count": 0})
                self._monthly[month_key]["repayment_count"] += 1

        self.stats.repayment_record_count = len(rows)
        logger.info(f"还款记录生成完成: {len(rows)} 条")
        return rows

    def generate_credit_reports(self, customers: list[dict],
                                 loans: list[dict]) -> list[dict]:
        """生成征信报告 (与贷款申请时间关联)。"""
        logger.info("开始生成征信报告...")
        rows = []
        report_no_counter = 0

        # 按客户分组贷款
        customer_loans: dict[str, list[dict]] = {}
        for loan in loans:
            customer_loans.setdefault(loan["customer_id"], []).append(loan)

        for cust in customers:
            tier = self._assign_tier(int(cust["customer_id"].split("_")[1]) - 1)
            tc = TIER_CONFIG[tier]

            # 查询次数
            num_queries = random.randint(*tc["credit_query_range"])

            # 已有的贷款时间点
            cust_loans = customer_loans.get(cust["customer_id"], [])

            for q in range(num_queries):
                # 查询时间: 如果有贷款，部分在申请前 1-30 天; 否则随机
                if cust_loans and random.random() < 0.6:
                    ref_loan = random.choice(cust_loans)
                    apply_time = datetime.strptime(ref_loan["apply_time"], "%Y-%m-%d %H:%M:%S")
                    query_time = apply_time - timedelta(days=random.randint(1, 30))
                else:
                    days_offset = random.randint(0, int((self.end_date - self.start_date).total_seconds() / 86400))
                    query_time = self.start_date + timedelta(days=days_offset)
                    query_time = query_time.replace(
                        hour=random.randint(8, 18), minute=random.randint(0, 59))

                report_no_counter += 1

                # 贷款账户数/逾期数 (与分层一致)
                overdue_count = 0
                if random.random() < tc["overdue_prob"]:
                    overdue_count = random.randint(1, min(5, tc["max_overdue_days"] // 10 + 1))

                loan_count = random.randint(0, 5) if tier != CustomerTier.HIGH_RISK else random.randint(3, 15)
                credit_card_count = random.randint(0, 5) if tier != CustomerTier.HIGH_RISK else random.randint(3, 10)

                # 总负债
                total_debt = sum(
                    l["loan_amount"] for l in cust_loans
                    if l["status"] in ("APPROVED", "DISBURSED", "CLOSED")
                ) * random.uniform(0.3, 0.8)

                latest_overdue = None
                if overdue_count > 0:
                    latest_overdue = (query_time - timedelta(days=random.randint(1, 180))).strftime("%Y-%m-%d")

                row = {
                    "customer_id": cust["customer_id"],
                    "report_no": f"CR_{cust['customer_id']}_{report_no_counter:04d}",
                    "query_institution": random.choice(INSTITUTIONS),
                    "query_purpose": random.choices(QUERY_PURPOSES, weights=QUERY_PURPOSE_WEIGHTS, k=1)[0],
                    "query_time": query_time.strftime("%Y-%m-%d %H:%M:%S"),
                    "loan_count": loan_count,
                    "credit_card_count": credit_card_count,
                    "overdue_count": overdue_count,
                    "total_debt": round(total_debt, 2),
                    "latest_overdue_date": latest_overdue,
                    "source_system": "CREDIT_PLATFORM",
                    "etl_time": query_time.strftime("%Y-%m-%d %H:%M:%S"),
                }
                rows.append(row)

                # 月度统计
                month_key = query_time.strftime("%Y-%m")
                self._monthly.setdefault(month_key, {"customer_count": 0, "loan_count": 0,
                                                       "repayment_count": 0, "credit_report_count": 0})
                self._monthly[month_key]["credit_report_count"] += 1

        self.stats.credit_report_count = len(rows)
        logger.info(f"征信报告生成完成: {len(rows)} 条")
        return rows

    def save_expected_counts(self, output_dir: str = "output"):
        """将验证基线保存到 expected_counts.json。"""
        # 月度分布
        for month_key in sorted(self._monthly.keys()):
            m = self._monthly[month_key]
            self.stats.monthly_distribution.append(
                MonthlyDistribution(month=month_key, **m)
            )

        # 总体统计
        output_path = Path(__file__).parent / output_dir / "expected_counts.json"
        output_path.parent.mkdir(parents=True, exist_ok=True)

        with open(output_path, "w", encoding="utf-8") as f:
            json.dump(self.stats.model_dump(), f, ensure_ascii=False, indent=2)

        logger.info(f"验证基线已保存: {output_path}")

    def run(self):
        """执行完整的批量数据生成流程。"""
        logger.info("=" * 60)
        logger.info(f"批量数据生成开始: {self.customer_count} 客户, {self.months} 个月")
        logger.info(f"时间范围: {self.start_date.strftime('%Y-%m-%d')} ~ "
                     f"{self.end_date.strftime('%Y-%m-%d')}")
        logger.info(f"随机种子: {self.seed}")
        logger.info("=" * 60)

        cfg = get_config()
        batch_size = cfg["generation"]["batch_size"]

        with MySQLHelper(cfg) as db:
            # Step 1: 客户信息
            customers = self.generate_customers()
            customer_columns = [
                "customer_id", "customer_name", "id_card", "phone", "gender",
                "birth_date", "education", "marital_status", "address", "employer",
                "industry", "annual_income", "source_system", "etl_time",
            ]
            count = db.batch_insert("customer_info", customer_columns, customers, batch_size)
            logger.info(f"✅ customer_info: {count} 行已插入")

            # Step 2: 贷款申请
            loans = self.generate_loan_applications(customers)
            loan_columns = [
                "application_no", "customer_id", "product_id", "channel",
                "loan_amount", "loan_term", "purpose", "apply_time", "status",
                "source_system", "etl_time",
            ]
            count = db.batch_insert("loan_application", loan_columns, loans, batch_size)
            logger.info(f"✅ loan_application: {count} 行已插入")

            # Step 3: 还款记录
            repayments = self.generate_repayment_records(loans)
            repayment_columns = [
                "loan_id", "customer_id", "installment_no", "due_date", "repay_date",
                "repay_amount", "principal", "interest", "overdue_days", "status",
                "source_system", "etl_time",
            ]
            count = db.batch_insert("repayment_record", repayment_columns, repayments, batch_size)
            logger.info(f"✅ repayment_record: {count} 行已插入")

            # Step 4: 征信报告
            credit_reports = self.generate_credit_reports(customers, loans)
            credit_columns = [
                "customer_id", "report_no", "query_institution", "query_purpose",
                "query_time", "loan_count", "credit_card_count", "overdue_count",
                "total_debt", "latest_overdue_date", "source_system", "etl_time",
            ]
            count = db.batch_insert("credit_report", credit_columns, credit_reports, batch_size)
            logger.info(f"✅ credit_report: {count} 行已插入")

        # 保存验证基线
        self.save_expected_counts()

        logger.info("=" * 60)
        logger.info("批量数据生成完成!")
        logger.info(f"  客户:         {self.stats.customer_count:,}")
        logger.info(f"  贷款申请:     {self.stats.loan_application_count:,}")
        logger.info(f"  还款记录:     {self.stats.repayment_record_count:,}")
        logger.info(f"  征信报告:     {self.stats.credit_report_count:,}")
        logger.info("=" * 60)


# ---------------------------------------------------------------------------
# 入口
# ---------------------------------------------------------------------------
def main():
    parser = argparse.ArgumentParser(description="批量历史数据生成器")
    parser.add_argument("--customers", type=int, default=10000, help="客户数量 (默认 10000)")
    parser.add_argument("--months", type=int, default=12, help="月数 (默认 12)")
    parser.add_argument("--start-date", type=str, default="2025-07-01", help="起始日期 (默认 2025-07-01)")
    parser.add_argument("--seed", type=int, default=42, help="随机种子 (默认 42)")
    args = parser.parse_args()

    gen = BatchDataGenerator(
        customer_count=args.customers,
        months=args.months,
        start_date=args.start_date,
        seed=args.seed,
    )
    gen.run()


if __name__ == "__main__":
    main()
