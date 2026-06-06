"""
数据模型 — Pydantic BaseModel 定义。

包含:
- 4 张 MySQL 源表模型 (CustomerInfo, LoanApplication, RepaymentRecord, CreditReport)
- 4 种 Kafka 扁平事件模型 (匹配 Flink POJO)
- 客户分层枚举 (CustomerTier)
- 验证基线模型 (ExpectedCounts)
"""

from datetime import date, datetime
from enum import Enum
from typing import Optional

from pydantic import BaseModel, Field


# ---------------------------------------------------------------------------
# 客户分层枚举
# ---------------------------------------------------------------------------
class CustomerTier(str, Enum):
    PREMIUM = "PREMIUM"        # 优质客户 40%
    NORMAL = "NORMAL"          # 普通客户 30%
    HIGH_RISK = "HIGH_RISK"    # 高风险客户 20%
    ANOMALY = "ANOMALY"        # 异常客户 10%


# ---------------------------------------------------------------------------
# MySQL 源表模型
# ---------------------------------------------------------------------------
class CustomerInfo(BaseModel):
    """客户信息表 — 对应 MySQL customer_info"""
    id: Optional[int] = None
    customer_id: str = Field(..., description="客户唯一标识 CUST_XXXXX")
    customer_name: Optional[str] = None
    id_card: Optional[str] = None
    phone: Optional[str] = None
    gender: Optional[str] = None
    birth_date: Optional[str] = None          # yyyy-MM-dd
    education: Optional[str] = None
    marital_status: Optional[str] = None
    address: Optional[str] = None
    employer: Optional[str] = None
    industry: Optional[str] = None
    annual_income: Optional[float] = None
    source_system: str = "CREDIT_PLATFORM"
    etl_time: Optional[str] = None            # yyyy-MM-dd HH:mm:ss

    # 内部字段，不写入 MySQL
    tier: Optional[CustomerTier] = None

    class Config:
        # Pydantic v2
        populate_by_name = True


class LoanApplication(BaseModel):
    """贷款申请表 — 对应 MySQL loan_application"""
    id: Optional[int] = None
    application_no: str
    customer_id: str
    product_id: Optional[str] = None          # P001/P002/P003
    channel: Optional[str] = None             # APP/WEB/WECHAT/OFFLINE/PARTNER
    loan_amount: Optional[float] = None
    loan_term: Optional[int] = None           # 月
    purpose: Optional[str] = None
    apply_time: Optional[str] = None          # yyyy-MM-dd HH:mm:ss
    status: Optional[str] = None              # PENDING/APPROVED/REJECTED/DISBURSED/CLOSED
    source_system: str = "CREDIT_PLATFORM"
    etl_time: Optional[str] = None


class RepaymentRecord(BaseModel):
    """还款记录表 — 对应 MySQL repayment_record"""
    id: Optional[int] = None
    loan_id: str
    customer_id: str
    installment_no: Optional[int] = None
    due_date: Optional[str] = None            # yyyy-MM-dd
    repay_date: Optional[str] = None          # yyyy-MM-dd
    repay_amount: Optional[float] = None
    principal: Optional[float] = None
    interest: Optional[float] = None
    overdue_days: int = 0
    status: Optional[str] = None              # PAID/OVERDUE/PENDING
    source_system: str = "CREDIT_PLATFORM"
    etl_time: Optional[str] = None


class CreditReport(BaseModel):
    """征信报告表 — 对应 MySQL credit_report"""
    id: Optional[int] = None
    customer_id: str
    report_no: str
    query_institution: Optional[str] = None
    query_purpose: Optional[str] = None
    query_time: Optional[str] = None          # yyyy-MM-dd HH:mm:ss
    loan_count: Optional[int] = None
    credit_card_count: Optional[int] = None
    overdue_count: Optional[int] = None
    total_debt: Optional[float] = None
    latest_overdue_date: Optional[str] = None # yyyy-MM-dd
    source_system: str = "CREDIT_PLATFORM"
    etl_time: Optional[str] = None


# ---------------------------------------------------------------------------
# Kafka 扁平事件模型 (匹配 Flink POJO)
# 注意: 字段名用 camelCase 匹配 Flink Jackson 反序列化
# ---------------------------------------------------------------------------
class CreditQueryEvent(BaseModel):
    """征信查询事件 — 匹配 CreditQuery3mJob.CreditQueryEvent"""
    customerId: str
    eventTime: str = Field(description="LocalDateTime 格式: yyyy-MM-ddTHH:mm:ss")
    queryType: str
    institution: str


class OverdueEvent(BaseModel):
    """逾期事件 — 匹配 Overdue6mJob.OverdueEvent"""
    customerId: str
    loanId: str
    overdueDays: int
    overdueAmount: float
    eventTime: str


class ApplyEvent(BaseModel):
    """申请事件 — 匹配 ApplyFreq1mJob.ApplyEvent"""
    customerId: str
    productId: str
    channel: str
    eventTime: str


class TransactionEvent(BaseModel):
    """交易事件 — 匹配 TransactionSummaryJob.TransactionEvent
    注意: eventTime 使用 ISO-8601 Instant 格式 (Flink 用 Instant.parse())"""
    customerId: str
    transactionId: str
    amount: float
    type: str
    eventTime: str = Field(description="ISO-8601 Instant: yyyy-MM-ddTHH:mm:ssZ")


# ---------------------------------------------------------------------------
# 决策引擎请求/响应
# ---------------------------------------------------------------------------
class DecisionRequest(BaseModel):
    """决策引擎请求 — 匹配 DecisionController.DecisionRequestDto"""
    strategyId: str
    channel: str = "APP"
    applicant: dict
    metadata: Optional[dict] = None


class DecisionResponse(BaseModel):
    """决策引擎响应 — 匹配 DecisionResponse"""
    decisionId: Optional[str] = None
    result: Optional[str] = None             # PASS/REJECT/REVIEW/MANUAL
    score: Optional[int] = None
    extra: Optional[dict] = None
    rejectReason: Optional[str] = None
    rejectCode: Optional[str] = None
    traceId: Optional[str] = None
    durationMs: Optional[int] = None


# ---------------------------------------------------------------------------
# 验证基线模型
# ---------------------------------------------------------------------------
class TierStats(BaseModel):
    """按客户分层的统计"""
    count: int = 0
    avg_income: float = 0.0
    loan_count: int = 0
    overdue_rate: float = 0.0


class MonthlyDistribution(BaseModel):
    """月度数据分布"""
    month: str          # yyyy-MM
    customer_count: int = 0
    loan_count: int = 0
    repayment_count: int = 0
    credit_report_count: int = 0


class ExpectedCounts(BaseModel):
    """验证基线 — 批量生成时写入，验证时读取对比"""
    generated_at: str                              # 生成时间
    customer_count: int = 0
    loan_application_count: int = 0
    repayment_record_count: int = 0
    credit_report_count: int = 0
    tier_stats: dict[str, TierStats] = {}          # {tier_name: stats}
    monthly_distribution: list[MonthlyDistribution] = []
    overall_overdue_rate: float = 0.0
    avg_loan_amount: float = 0.0
    parameters: dict = {}                          # 生成参数快照
