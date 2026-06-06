"""
CP1: MySQL 源数据验证 — 行数、外键完整性、时序正确性、值域检查。
"""

import json
from pathlib import Path

from loguru import logger

from db import MySQLHelper
from .base import BaseVerifier, CheckStatus


class MySQLCheck(BaseVerifier):
    checkpoint = "CP1"
    name = "MySQL 源数据"

    def __init__(self, cfg: dict, expected_path: str = "output/expected_counts.json"):
        super().__init__(cfg)
        self.expected_path = expected_path

    def _check(self):
        db = MySQLHelper(self.cfg)
        try:
            # 1. 加载预期值
            expected = self._load_expected()

            # 2. 表行数检查
            tables = ["customer_info", "loan_application", "repayment_record", "credit_report"]
            for table in tables:
                count = db.query_count(table)
                if expected:
                    expected_count = expected.get(f"{table}_count", 0)
                    if expected_count > 0:
                        ratio = abs(count - expected_count) / expected_count
                        if ratio <= 0.05:
                            self.result.add_detail(
                                f"{table} 行数", CheckStatus.PASS,
                                expected=str(expected_count), actual=str(count),
                                message=f"{count:,} 行 (匹配)"
                            )
                        elif ratio <= 0.15:
                            self.result.add_detail(
                                f"{table} 行数", CheckStatus.WARN,
                                expected=str(expected_count), actual=str(count),
                                message=f"{count:,} 行 (偏差 {ratio:.1%})"
                            )
                        else:
                            self.result.add_detail(
                                f"{table} 行数", CheckStatus.FAIL,
                                expected=str(expected_count), actual=str(count),
                                message=f"{count:,} 行 (偏差 {ratio:.1%})"
                            )
                    else:
                        self.result.add_detail(
                            f"{table} 行数", CheckStatus.PASS,
                            actual=str(count), message=f"{count:,} 行"
                        )
                else:
                    self.result.add_detail(
                        f"{table} 行数", CheckStatus.PASS,
                        actual=str(count), message=f"{count:,} 行"
                    )

            # 3. 外键完整性
            orphan_loans = db.query_one(
                "SELECT COUNT(*) AS cnt FROM loan_application l "
                "LEFT JOIN customer_info c ON l.customer_id = c.customer_id "
                "WHERE c.customer_id IS NULL"
            )["cnt"]
            if orphan_loans == 0:
                self.result.add_detail("外键完整性 (loan→customer)", CheckStatus.PASS,
                                        message="0 条孤立记录")
            else:
                self.result.add_detail("外键完整性 (loan→customer)", CheckStatus.FAIL,
                                        expected="0", actual=str(orphan_loans),
                                        message=f"{orphan_loans} 条孤立贷款记录")

            orphan_repay = db.query_one(
                "SELECT COUNT(*) AS cnt FROM repayment_record r "
                "LEFT JOIN customer_info c ON r.customer_id = c.customer_id "
                "WHERE c.customer_id IS NULL"
            )["cnt"]
            if orphan_repay == 0:
                self.result.add_detail("外键完整性 (repay→customer)", CheckStatus.PASS,
                                        message="0 条孤立记录")
            else:
                self.result.add_detail("外键完整性 (repay→customer)", CheckStatus.FAIL,
                                        expected="0", actual=str(orphan_repay),
                                        message=f"{orphan_repay} 条孤立还款记录")

            # 4. 时序正确性
            bad_temporal = db.query_one(
                "SELECT COUNT(*) AS cnt FROM repayment_record r "
                "JOIN loan_application l ON r.loan_id = l.application_no "
                "WHERE r.due_date < l.apply_time"
            )["cnt"]
            if bad_temporal == 0:
                self.result.add_detail("时序正确性", CheckStatus.PASS,
                                        message="还款日期均晚于贷款申请日期")
            else:
                self.result.add_detail("时序正确性", CheckStatus.WARN,
                                        expected="0", actual=str(bad_temporal),
                                        message=f"{bad_temporal} 条还款早于申请")

            # 5. 值域检查
            negative_income = db.query_one(
                "SELECT COUNT(*) AS cnt FROM customer_info WHERE annual_income < 0"
            )["cnt"]
            negative_amount = db.query_one(
                "SELECT COUNT(*) AS cnt FROM loan_application WHERE loan_amount < 0"
            )["cnt"]
            negative_overdue = db.query_one(
                "SELECT COUNT(*) AS cnt FROM repayment_record WHERE overdue_days < 0"
            )["cnt"]

            value_ok = (negative_income == 0 and negative_amount == 0 and negative_overdue == 0)
            if value_ok:
                self.result.add_detail("值域检查", CheckStatus.PASS,
                                        message="annual_income/loan_amount/overdue_days 值域正常")
            else:
                issues = []
                if negative_income > 0: issues.append(f"收入<0: {negative_income}")
                if negative_amount > 0: issues.append(f"金额<0: {negative_amount}")
                if negative_overdue > 0: issues.append(f"逾期<0: {negative_overdue}")
                self.result.add_detail("值域检查", CheckStatus.WARN,
                                        message=", ".join(issues))

        finally:
            db.close()

        self.result.compute_status()

    def _load_expected(self) -> dict | None:
        """加载预期值文件。"""
        path = Path(__file__).parent.parent / self.expected_path
        if not path.exists():
            logger.debug(f"预期值文件不存在: {path}")
            return None
        with open(path, "r", encoding="utf-8") as f:
            return json.load(f)
