#!/usr/bin/env python3
"""
决策引擎种子数据生成器。

按业务逻辑层次生成完整的风控决策配置数据：
  变量 L0-L3 → 规则集(黑名单+准入) → 评分卡(A/B/C) → 决策表(额度+定价)
  → 决策树 → 决策流 DAG(V1+V2) → 实验配置 → 灰度发布 → 审批记录 → 审计日志

所有数据写入 MySQL credit_platform 库的 rule_entity / grayscale_config /
approval_record / audit_log 表。

用法:
  python decision_seed.py              # 生成全部种子数据
  python decision_seed.py --dry-run    # 仅打印 JSON 不写入数据库
  python decision_seed.py --verbose    # 打印详细 JSON 内容
"""

import argparse
import json
import sys
import uuid
from datetime import datetime, timedelta

from config import get_config, beijing_now
from db import MySQLHelper

# ---------------------------------------------------------------------------
# 工具函数
# ---------------------------------------------------------------------------

def _now() -> str:
    return beijing_now()


def _uid() -> str:
    return uuid.uuid4().hex[:12]


# ---------------------------------------------------------------------------
# 变量体系生成 (Tasks 2.1 ~ 2.5)
# ---------------------------------------------------------------------------

def generate_variables(cfg: dict) -> list[dict]:
    """生成 L0-L3 四层变量定义，返回 rule_entity 行列表。"""
    variables: list[dict] = []
    now = _now()

    # === L0 INPUT 变量 ===
    l0_vars = [
        ("var_customer_id",       "客户ID",         "INPUT", "STRING",  "客户唯一标识"),
        ("var_application_id",    "申请ID",          "INPUT", "STRING",  "贷款申请唯一标识"),
        ("var_loan_amount",       "贷款金额",        "INPUT", "DECIMAL", "申请贷款金额(元)"),
        ("var_loan_purpose",      "贷款用途",        "INPUT", "STRING",  "CONSUMPTION/BUSINESS/MORTGAGE"),
        ("var_loan_term",         "贷款期限",        "INPUT", "INTEGER", "贷款期限(月)"),
        ("var_product_type",      "产品类型",        "INPUT", "STRING",  "P001/P002/P003"),
        ("var_channel",           "申请渠道",        "INPUT", "STRING",  "APP/WEB/WECHAT/OFFLINE/PARTNER"),
    ]
    for var_id, name, layer, dtype, desc in l0_vars:
        v = _make_var_entity(var_id, name, layer, dtype, "credit", desc, now)
        variables.append(v)

    # === L1 EXTERNAL 变量 ===
    l1_vars = [
        ("var_credit_score",      "征信评分",        "EXTERNAL", "INTEGER", "征信机构返回的信用评分 (300-850)"),
        ("var_blacklist_flag",    "黑名单标记",      "EXTERNAL", "BOOLEAN", "是否命中黑名单"),
        ("var_multi_loan_count",  "多头借贷次数",    "EXTERNAL", "INTEGER", "近3月跨平台借贷申请次数"),
        ("var_device_anomaly",    "设备指纹异常",    "EXTERNAL", "BOOLEAN", "设备指纹是否异常"),
        ("var_ip_anomaly",        "IP地址异常",      "EXTERNAL", "BOOLEAN", "IP地址是否异常"),
        ("var_phone_blacklist",   "通讯黑名单",      "EXTERNAL", "BOOLEAN", "手机号是否在黑名单中"),
        ("var_age",               "年龄",            "EXTERNAL", "INTEGER", "申请人年龄 (从身份证提取/OCR)"),
    ]
    for var_id, name, layer, dtype, desc in l1_vars:
        v = _make_var_entity(var_id, name, layer, dtype, "credit", desc, now)
        variables.append(v)

    # === L2 CACHED 变量 (Flink 预计算存入 Redis/HBase) ===
    l2_vars = [
        ("var_overdue_count_6m",  "近6月逾期次数",   "CACHED", "INTEGER", "近6个月逾期总次数"),
        ("var_debt_ratio",        "负债率",           "CACHED", "DECIMAL", "总负债/月收入比率"),
        ("var_income_monthly",    "月收入",           "CACHED", "DECIMAL", "月均收入(元)"),
        ("var_work_years",        "工作年限",         "CACHED", "INTEGER", "当前单位工作年数"),
        ("var_credit_query_count","征信查询次数",     "CACHED", "INTEGER", "近3月征信被查询次数"),
        ("var_asset_amount",      "资产规模",         "CACHED", "DECIMAL", "名下总资产(元)"),
        ("var_repayment_rate",    "还款率",           "CACHED", "DECIMAL", "历史按时还款比例 (0-1)"),
        ("var_account_active_months", "账户活跃月数", "CACHED", "INTEGER", "最近活跃账户持续月数"),
    ]
    for var_id, name, layer, dtype, desc in l2_vars:
        v = _make_var_entity(var_id, name, layer, dtype, "credit", desc, now)
        variables.append(v)

    # === L3 DERIVED 变量 ===
    l3_vars = [
        {
            "varId": "var_total_credit_score",
            "name": "综合信用评分",
            "layer": "DERIVED",
            "dataType": "INTEGER",
            "category": "derived",
            "expression": "math.round(var_credit_score * 0.6 + (1 - var_debt_ratio) * 200 + (var_repayment_rate != nil ? var_repayment_rate * 200 : 0))",
            "dependencies": ["var_credit_score", "var_debt_ratio", "var_repayment_rate"],
            "description": "信用评分 + 负债率 + 还款率的加权综合分",
        },
        {
            "varId": "var_risk_level",
            "name": "风险等级",
            "layer": "DERIVED",
            "dataType": "STRING",
            "category": "derived",
            "expression": "var_total_credit_score >= 650 ? 'A' : var_total_credit_score >= 550 ? 'B' : var_total_credit_score >= 500 ? 'C' : 'D'",
            "dependencies": ["var_total_credit_score"],
            "description": "基于综合信用评分的风险等级映射",
        },
        {
            "varId": "var_is_high_risk",
            "name": "是否高风险",
            "layer": "DERIVED",
            "dataType": "BOOLEAN",
            "category": "derived",
            "expression": "var_risk_level == 'D' || var_blacklist_flag == true || var_multi_loan_count >= 5 || var_overdue_count_6m >= 3",
            "dependencies": ["var_risk_level", "var_blacklist_flag", "var_multi_loan_count", "var_overdue_count_6m"],
            "description": "综合判断是否为高风险客户",
        },
        {
            "varId": "var_age_group",
            "name": "年龄分组",
            "layer": "DERIVED",
            "dataType": "STRING",
            "category": "derived",
            "expression": "var_age < 25 ? 'YOUNG' : var_age < 35 ? 'YOUTH' : var_age < 50 ? 'MIDDLE' : 'SENIOR'",
            "dependencies": ["var_age"],
            "description": "年龄分层: YOUNG/YOUTH/MIDDLE/SENIOR",
        },
        {
            "varId": "var_dti_category",
            "name": "负债收入比分类",
            "layer": "DERIVED",
            "dataType": "STRING",
            "category": "derived",
            "expression": "var_debt_ratio < 0.3 ? 'LOW' : var_debt_ratio < 0.5 ? 'MODERATE' : var_debt_ratio < 0.7 ? 'HIGH' : 'CRITICAL'",
            "dependencies": ["var_debt_ratio"],
            "description": "负债率分类: LOW/MODERATE/HIGH/CRITICAL",
        },
        {
            "varId": "var_credit_grade",
            "name": "信用等级(决策树)",
            "layer": "DERIVED",
            "dataType": "STRING",
            "category": "derived",
            "expression": "var_risk_level",
            "dependencies": ["var_risk_level"],
            "description": "信用等级别名，供决策树/决策表引用",
        },
        {
            "varId": "var_approval_decision",
            "name": "审批决策",
            "layer": "DERIVED",
            "dataType": "STRING",
            "category": "derived",
            "expression": "var_is_high_risk == true ? 'REJECT' : var_total_credit_score >= 550 ? 'PASS' : 'REVIEW'",
            "dependencies": ["var_is_high_risk", "var_total_credit_score"],
            "description": "基于风险和评分的初步审批决策",
        },
    ]
    for vdef in l3_vars:
        v = _make_var_entity(
            vdef["varId"], vdef["name"], vdef["layer"], vdef["dataType"],
            vdef["category"], vdef["description"], now,
            expression=vdef.get("expression"),
            dependencies=vdef.get("dependencies", []),
        )
        variables.append(v)

    return variables


def _make_var_entity(var_id: str, name: str, layer: str, data_type: str,
                     category: str, description: str, now: str,
                     expression: str = None, dependencies: list[str] = None) -> dict:
    """构造单个变量定义的 rule_entity 行。"""
    content = {
        "varId": var_id,
        "name": name,
        "layer": layer,
        "dataType": data_type,
        "category": category,
        "version": 1,
        "description": description,
    }
    if expression:
        content["expression"] = expression
    if dependencies:
        content["dependencies"] = dependencies

    return {
        "id": var_id.replace("var_", "VAR_"),
        "name": name,
        "type": "VARIABLE",
        "version": 1,
        "status": "RELEASED",
        "content": json.dumps(content, ensure_ascii=False),
        "description": description,
        "created_by": "system",
        "updated_by": "system",
        "created_at": now,
        "updated_at": now,
        "attributes": "{}",
    }


def validate_variable_dependencies(variables: list[dict]) -> tuple[bool, list[str]]:
    """校验所有 L3 变量的依赖是否可解析。"""
    all_var_ids = set()
    errors = []

    for v in variables:
        try:
            content = json.loads(v["content"]) if isinstance(v["content"], str) else v["content"]
            all_var_ids.add(content.get("varId", ""))
        except (json.JSONDecodeError, KeyError):
            pass

    for v in variables:
        try:
            content = json.loads(v["content"]) if isinstance(v["content"], str) else v["content"]
            if content.get("layer") == "DERIVED":
                deps = content.get("dependencies", [])
                for dep in deps:
                    if dep not in all_var_ids:
                        errors.append(f"变量 {content.get('varId')} 依赖的 {dep} 未定义")
        except (json.JSONDecodeError, KeyError):
            pass

    return len(errors) == 0, errors


# ---------------------------------------------------------------------------
# 规则集生成 (Tasks 3.1 ~ 3.3)
# ---------------------------------------------------------------------------

def generate_blacklist_ruleset() -> dict:
    """生成 RS_BLACKLIST 反欺诈规则集。"""
    rules = [
        {
            "ruleId": "R_BLACKLIST_ID",
            "name": "身份黑名单检查",
            "priority": 100,
            "conditions": {"field": "var_blacklist_flag", "op": "EQ", "value": True},
            "actions": [{"type": "REJECT", "reason": "身份命中黑名单", "code": "BL_001"}],
        },
        {
            "ruleId": "R_PHONE_BLACKLIST",
            "name": "通讯黑名单检查",
            "priority": 90,
            "conditions": {"field": "var_phone_blacklist", "op": "EQ", "value": True},
            "actions": [{"type": "REJECT", "reason": "手机号命中通讯黑名单", "code": "BL_002"}],
        },
        {
            "ruleId": "R_MULTI_LOAN",
            "name": "多头借贷检查",
            "priority": 80,
            "conditions": {
                "operator": "AND",
                "operands": [
                    {"field": "var_multi_loan_count", "op": "GTE", "value": 5},
                ],
            },
            "actions": [{"type": "REJECT", "reason": "多头借贷风险过高，近3月≥5次申请", "code": "BL_003"}],
        },
        {
            "ruleId": "R_DEVICE_ANOMALY",
            "name": "设备指纹异常检查",
            "priority": 70,
            "conditions": {"field": "var_device_anomaly", "op": "EQ", "value": True},
            "actions": [{"type": "REJECT", "reason": "设备指纹异常，疑似欺诈", "code": "BL_004"}],
        },
        {
            "ruleId": "R_IP_ANOMALY",
            "name": "IP地址异常检查",
            "priority": 60,
            "conditions": {"field": "var_ip_anomaly", "op": "EQ", "value": True},
            "actions": [{"type": "REJECT", "reason": "IP地址异常，疑似代理/VPN", "code": "BL_005"}],
        },
        {
            "ruleId": "R_MULTI_LOAN_WARN",
            "name": "多头借贷预警",
            "priority": 50,
            "conditions": {
                "operator": "AND",
                "operands": [
                    {"field": "var_multi_loan_count", "op": "GTE", "value": 3},
                    {"field": "var_multi_loan_count", "op": "LT", "value": 5},
                ],
            },
            "actions": [{"type": "REVIEW", "reason": "多头借贷次数偏高(3-4次)，建议人工复核", "code": "BL_WARN_001"}],
        },
    ]

    ruleset = {
        "ruleSetId": "RS_BLACKLIST",
        "name": "反欺诈黑名单规则集",
        "version": 1,
        "hitPolicy": "FIRST_HIT",
        "rules": rules,
    }

    return _make_rule_entity("RS_BLACKLIST", "反欺诈黑名单规则集", "RULE",
                             "FIRST_HIT 策略：任一命中即拒绝。覆盖身份黑名单、通讯黑名单、多头借贷、设备异常、IP异常场景。",
                             json.dumps(ruleset, ensure_ascii=False))


def generate_eligibility_ruleset() -> dict:
    """生成 RS_ELIGIBILITY 准入规则集。"""
    rules = [
        {
            "ruleId": "R_AGE_CHECK",
            "name": "年龄准入检查",
            "priority": 100,
            "conditions": {
                "operator": "AND",
                "operands": [
                    {"field": "var_age", "op": "GTE", "value": 22},
                    {"field": "var_age", "op": "LTE", "value": 60},
                ],
            },
            "actions": [],  # 满足条件时不触发动作 (PASS)
        },
        {
            "ruleId": "R_MIN_INCOME",
            "name": "最低收入检查",
            "priority": 90,
            "conditions": {
                "operator": "AND",
                "operands": [
                    {"field": "var_income_monthly", "op": "GTE", "value": 3000},
                ],
            },
            "actions": [],
        },
        {
            "ruleId": "R_CREDIT_QUERY_LIMIT",
            "name": "征信查询次数检查",
            "priority": 80,
            "conditions": {
                "operator": "AND",
                "operands": [
                    {"field": "var_credit_query_count", "op": "LTE", "value": 6},
                ],
            },
            "actions": [],
        },
        {
            "ruleId": "R_DEBT_RATIO_CHECK",
            "name": "负债率检查",
            "priority": 70,
            "conditions": {
                "operator": "AND",
                "operands": [
                    {"field": "var_debt_ratio", "op": "LTE", "value": 0.70},
                ],
            },
            "actions": [],
        },
        {
            "ruleId": "R_ELIGIBILITY_FAIL",
            "name": "准入不通过汇总",
            "priority": 0,
            "conditions": {"field": "__eligibility_pass", "op": "EQ", "value": False},
            "actions": [{"type": "REJECT", "reason": "未通过基本准入条件审核", "code": "ELIG_001"}],
        },
    ]

    ruleset = {
        "ruleSetId": "RS_ELIGIBILITY",
        "name": "准入规则集",
        "version": 1,
        "hitPolicy": "ALL",
        "rules": rules,
    }

    return _make_rule_entity("RS_ELIGIBILITY", "准入规则集", "RULE",
                             "ALL 策略：全部通过才进入评分环节。覆盖年龄、收入、征信查询次数、负债率。",
                             json.dumps(ruleset, ensure_ascii=False))


# ---------------------------------------------------------------------------
# 评分卡生成 (Tasks 4.1 ~ 4.4)
# ---------------------------------------------------------------------------

def generate_scorecard_a() -> dict:
    """生成 SC_CREDIT_A 主信用评分卡。"""
    card = {
        "scorecardId": "SC_CREDIT_A",
        "name": "主信用评分卡 A",
        "version": 1,
        "initialScore": 500,
        "characteristics": [
            {
                "name": "年龄",
                "field": "var_age",
                "bins": [
                    {"range": [None, 22],  "score": -15, "label": "[min, 22)",  "reason": "年龄过小"},
                    {"range": [22, 30],   "score": 10,  "label": "[22, 30)"},
                    {"range": [30, 45],   "score": 25,  "label": "[30, 45)"},
                    {"range": [45, 60],   "score": 15,  "label": "[45, 60)"},
                    {"range": [60, None], "score": -10, "label": "[60, max)", "reason": "年龄偏大"},
                ],
            },
            {
                "name": "征信评分",
                "field": "var_credit_score",
                "bins": [
                    {"range": [None, 500], "score": -30, "label": "[300, 500)"},
                    {"range": [500, 600],  "score": -10, "label": "[500, 600)"},
                    {"range": [600, 700],  "score": 15,  "label": "[600, 700)"},
                    {"range": [700, 800],  "score": 30,  "label": "[700, 800)"},
                    {"range": [800, None], "score": 40,  "label": "[800, 850]"},
                ],
            },
            {
                "name": "工作年限",
                "field": "var_work_years",
                "bins": [
                    {"range": [None, 1],  "score": -10, "label": "<1年"},
                    {"range": [1, 3],     "score": 0,   "label": "1-3年"},
                    {"range": [3, 10],    "score": 15,  "label": "3-10年"},
                    {"range": [10, None], "score": 20,  "label": "≥10年"},
                ],
            },
            {
                "name": "月收入",
                "field": "var_income_monthly",
                "bins": [
                    {"range": [None, 5000],   "score": -20, "label": "<5000"},
                    {"range": [5000, 10000],  "score": 0,   "label": "5k-10k"},
                    {"range": [10000, 20000], "score": 15,  "label": "10k-20k"},
                    {"range": [20000, 50000], "score": 25,  "label": "20k-50k"},
                    {"range": [50000, None],  "score": 35,  "label": "≥50k"},
                ],
            },
        ],
        "cutoff": {"reject": 500, "review": 550, "pass": 550},
    }
    return _make_rule_entity("SC_CREDIT_A", "主信用评分卡 A", "SCORECARD",
                             "主评分卡：年龄+征信分+工作年限+月收入，initialScore=500，<500拒/500-549人工/≥550通过",
                             json.dumps(card, ensure_ascii=False))


def generate_scorecard_b() -> dict:
    """生成 SC_CREDIT_B 行为评分卡。"""
    card = {
        "scorecardId": "SC_CREDIT_B",
        "name": "行为评分卡 B",
        "version": 1,
        "initialScore": 0,
        "characteristics": [
            {
                "name": "近6月逾期次数",
                "field": "var_overdue_count_6m",
                "bins": [
                    {"range": [None, 0], "score": 50,  "label": "0次"},
                    {"range": [0, 1],    "score": 30,  "label": "0-1次(不含)"},
                    {"range": [1, 3],    "score": 0,   "label": "1-3次"},
                    {"range": [3, 6],    "score": -30, "label": "3-6次"},
                    {"range": [6, None], "score": -60, "label": "≥6次"},
                ],
            },
            {
                "name": "还款率",
                "field": "var_repayment_rate",
                "bins": [
                    {"range": [None, 0.8],   "score": -30, "label": "<80%"},
                    {"range": [0.8, 0.95],   "score": 10,  "label": "80%-95%"},
                    {"range": [0.95, 1.0],   "score": 30,  "label": "95%-100%"},
                    {"range": [1.0, None],   "score": 40,  "label": "100%"},
                ],
            },
            {
                "name": "账户活跃月数",
                "field": "var_account_active_months",
                "bins": [
                    {"range": [None, 6],   "score": -10, "label": "<6月"},
                    {"range": [6, 12],     "score": 0,   "label": "6-12月"},
                    {"range": [12, 36],    "score": 15,  "label": "12-36月"},
                    {"range": [36, None],  "score": 20,  "label": "≥36月"},
                ],
            },
        ],
        "cutoff": {"reject": 300, "review": 350, "pass": 350},
    }
    return _make_rule_entity("SC_CREDIT_B", "行为评分卡 B", "SCORECARD",
                             "行为评分卡：逾期次数+还款率+账户活跃度，initialScore=0",
                             json.dumps(card, ensure_ascii=False))


def generate_scorecard_c() -> dict:
    """生成 SC_CREDIT_C 收入评分卡。"""
    card = {
        "scorecardId": "SC_CREDIT_C",
        "name": "收入评分卡 C",
        "version": 1,
        "initialScore": 0,
        "characteristics": [
            {
                "name": "月收入",
                "field": "var_income_monthly",
                "bins": [
                    {"range": [None, 5000],   "score": -20, "label": "<5000"},
                    {"range": [5000, 10000],  "score": 10,  "label": "5k-10k"},
                    {"range": [10000, 20000], "score": 25,  "label": "10k-20k"},
                    {"range": [20000, 50000], "score": 35,  "label": "20k-50k"},
                    {"range": [50000, None],  "score": 45,  "label": "≥50k"},
                ],
            },
            {
                "name": "负债率",
                "field": "var_debt_ratio",
                "bins": [
                    {"range": [None, 0.2],  "score": 40,  "label": "<20%"},
                    {"range": [0.2, 0.4],   "score": 25,  "label": "20%-40%"},
                    {"range": [0.4, 0.6],   "score": 5,   "label": "40%-60%"},
                    {"range": [0.6, 0.8],   "score": -20, "label": "60%-80%"},
                    {"range": [0.8, None],  "score": -40, "label": "≥80%"},
                ],
            },
            {
                "name": "资产规模",
                "field": "var_asset_amount",
                "bins": [
                    {"range": [None, 100000],   "score": -10, "label": "<10万"},
                    {"range": [100000, 500000],  "score": 10,  "label": "10-50万"},
                    {"range": [500000, 1000000], "score": 25,  "label": "50-100万"},
                    {"range": [1000000, None],   "score": 35,  "label": "≥100万"},
                ],
            },
        ],
        "cutoff": {"reject": 250, "review": 300, "pass": 300},
    }
    return _make_rule_entity("SC_CREDIT_C", "收入评分卡 C", "SCORECARD",
                             "收入评分卡：月收入+负债率+资产规模，initialScore=0",
                             json.dumps(card, ensure_ascii=False))


# ---------------------------------------------------------------------------
# 决策表生成 (Tasks 5.1 ~ 5.3)
# ---------------------------------------------------------------------------

def generate_loan_amount_table() -> dict:
    """生成 DT_LOAN_AMOUNT 额度决策表。"""
    table = {
        "tableId": "DT_LOAN_AMOUNT",
        "name": "贷款额度决策表",
        "version": 1,
        "columns": [
            {"name": "信用等级", "field": "var_credit_grade"},
            {"name": "产品类型", "field": "var_product_type"},
            {"name": "收入水平", "field": "var_dti_category"},
        ],
        "rows": [
            # A级 — 各级收入 + 各产品
            {"conditions": ["A", "P001", "LOW"],      "result": {"action": "APPROVE", "maxAmount": 100000,  "amountRange": "1-10万"}},
            {"conditions": ["A", "P001", "MODERATE"],  "result": {"action": "APPROVE", "maxAmount": 200000,  "amountRange": "5-20万"}},
            {"conditions": ["A", "P001", "HIGH"],      "result": {"action": "APPROVE", "maxAmount": 500000,  "amountRange": "10-50万"}},
            {"conditions": ["A", "P001", "*"],         "result": {"action": "APPROVE", "maxAmount": 500000,  "amountRange": "1-50万"}},
            {"conditions": ["A", "P002", "*"],         "result": {"action": "APPROVE", "maxAmount": 1000000, "amountRange": "5-100万"}},
            {"conditions": ["A", "P003", "*"],         "result": {"action": "APPROVE", "maxAmount": 3000000, "amountRange": "10-300万"}},
            # B级
            {"conditions": ["B", "P001", "LOW"],      "result": {"action": "APPROVE", "maxAmount": 50000,   "amountRange": "1-5万"}},
            {"conditions": ["B", "P001", "MODERATE"],  "result": {"action": "APPROVE", "maxAmount": 100000,  "amountRange": "3-10万"}},
            {"conditions": ["B", "P001", "HIGH"],      "result": {"action": "APPROVE", "maxAmount": 300000,  "amountRange": "5-30万"}},
            {"conditions": ["B", "P001", "*"],         "result": {"action": "APPROVE", "maxAmount": 300000,  "amountRange": "1-30万"}},
            {"conditions": ["B", "P002", "*"],         "result": {"action": "APPROVE", "maxAmount": 500000,  "amountRange": "5-50万"}},
            {"conditions": ["B", "P003", "*"],         "result": {"action": "APPROVE", "maxAmount": 1500000, "amountRange": "10-150万"}},
            # C级 — 限额
            {"conditions": ["C", "P001", "*"],         "result": {"action": "APPROVE", "maxAmount": 50000,   "amountRange": "1-5万"}},
            {"conditions": ["C", "P002", "*"],         "result": {"action": "APPROVE", "maxAmount": 100000,  "amountRange": "3-10万"}},
            {"conditions": ["C", "P003", "*"],         "result": {"action": "REVIEW",  "maxAmount": 300000,  "amountRange": "5-30万(需复核)"}},
            # D级 / 高风险 — 通配拒绝
            {"conditions": ["D", "*", "*"],            "result": {"action": "REJECT",  "maxAmount": 0,       "reason": "信用等级D，暂不受理"}},
            {"conditions": ["*", "*", "CRITICAL"],     "result": {"action": "REJECT",  "maxAmount": 0,       "reason": "负债率过高(CRITICAL)，暂不受理"}},
        ],
        "hitPolicy": "FIRST_MATCH",
    }
    return _make_rule_entity("DT_LOAN_AMOUNT", "贷款额度决策表", "DECISION_TABLE",
                             "FIRST_MATCH策略：信用等级×产品类型×收入水平→额度。A级最高50万-300万，D级直接拒绝。",
                             json.dumps(table, ensure_ascii=False))


def generate_pricing_table() -> dict:
    """生成 DT_PRICING 定价决策表。"""
    table = {
        "tableId": "DT_PRICING",
        "name": "贷款定价决策表",
        "version": 1,
        "columns": [
            {"name": "信用等级", "field": "var_credit_grade"},
            {"name": "贷款期限", "field": "var_loan_term_category"},
            {"name": "担保方式", "field": "var_guarantee_type"},
        ],
        "rows": [
            # A级
            {"conditions": ["A", "SHORT",   "CREDIT"],    "result": {"rateRange": "4.5%-8.0%",  "baseRate": 6.0}},
            {"conditions": ["A", "SHORT",   "GUARANTEE"], "result": {"rateRange": "3.5%-7.0%",  "baseRate": 5.0}},
            {"conditions": ["A", "MEDIUM",  "CREDIT"],    "result": {"rateRange": "5.0%-9.0%",  "baseRate": 7.0}},
            {"conditions": ["A", "MEDIUM",  "GUARANTEE"], "result": {"rateRange": "4.0%-8.0%",  "baseRate": 6.0}},
            {"conditions": ["A", "LONG",    "CREDIT"],    "result": {"rateRange": "6.0%-10.0%", "baseRate": 8.0}},
            {"conditions": ["A", "LONG",    "GUARANTEE"], "result": {"rateRange": "5.0%-9.0%",  "baseRate": 7.0}},
            # B级
            {"conditions": ["B", "SHORT",   "*"],         "result": {"rateRange": "6.0%-10.0%", "baseRate": 8.0}},
            {"conditions": ["B", "MEDIUM",  "*"],         "result": {"rateRange": "7.0%-12.0%", "baseRate": 9.5}},
            {"conditions": ["B", "LONG",    "*"],         "result": {"rateRange": "8.0%-14.0%", "baseRate": 11.0}},
            # C级
            {"conditions": ["C", "SHORT",   "*"],         "result": {"rateRange": "10.0%-15.0%","baseRate": 12.0}},
            {"conditions": ["C", "MEDIUM",  "*"],         "result": {"rateRange": "12.0%-17.0%","baseRate": 14.0}},
            {"conditions": ["C", "LONG",    "*"],         "result": {"rateRange": "14.0%-20.0%","baseRate": 16.0}},
            # D级 — 拒绝
            {"conditions": ["D", "*",       "*"],         "result": {"rateRange": "N/A",        "baseRate": 0,  "action": "REJECT", "reason": "信用等级D，不予定价"}},
            # 兜底
            {"conditions": ["*", "*",       "*"],         "result": {"rateRange": "8.0%-15.0%", "baseRate": 10.0}},
        ],
        "hitPolicy": "FIRST_MATCH",
    }
    return _make_rule_entity("DT_PRICING", "贷款定价决策表", "DECISION_TABLE",
                             "FIRST_MATCH策略：信用等级×贷款期限×担保方式→利率。A级最低4.5%，D级拒绝。",
                             json.dumps(table, ensure_ascii=False))


# ---------------------------------------------------------------------------
# 决策树生成 (Tasks 6.1 ~ 6.2)
# ---------------------------------------------------------------------------

def generate_credit_grade_tree() -> dict:
    """生成 DTREE_CREDIT_GRADE 信用等级决策树。"""
    tree = {
        "treeId": "DTREE_CREDIT_GRADE",
        "name": "信用等级判定决策树",
        "version": 1,
        "rootNode": {
            "type": "BRANCH",
            "field": "var_credit_score",
            "name": "征信评分",
            "branches": [
                {
                    "condition": ">= 700",
                    "child": {
                        "type": "LEAF",
                        "result": {"creditGrade": "A", "riskLabel": "低风险"},
                    },
                },
                {
                    "condition": ">= 600 and < 700",
                    "child": {
                        "type": "BRANCH",
                        "field": "var_overdue_count_6m",
                        "name": "近6月逾期次数",
                        "branches": [
                            {
                                "condition": "== 0",
                                "child": {"type": "LEAF", "result": {"creditGrade": "A", "riskLabel": "低风险"}},
                            },
                            {
                                "condition": ">= 1 and <= 2",
                                "child": {"type": "LEAF", "result": {"creditGrade": "B", "riskLabel": "中低风险"}},
                            },
                            {
                                "condition": ">= 3",
                                "child": {"type": "LEAF", "result": {"creditGrade": "C", "riskLabel": "中高风险"}},
                            },
                        ],
                    },
                },
                {
                    "condition": ">= 500 and < 600",
                    "child": {
                        "type": "BRANCH",
                        "field": "var_debt_ratio",
                        "name": "负债率",
                        "branches": [
                            {
                                "condition": "< 0.4",
                                "child": {"type": "LEAF", "result": {"creditGrade": "B", "riskLabel": "中低风险"}},
                            },
                            {
                                "condition": ">= 0.4 and < 0.7",
                                "child": {"type": "LEAF", "result": {"creditGrade": "C", "riskLabel": "中高风险"}},
                            },
                            {
                                "condition": ">= 0.7",
                                "child": {"type": "LEAF", "result": {"creditGrade": "D", "riskLabel": "高风险"}},
                            },
                        ],
                    },
                },
                {
                    "condition": "< 500",
                    "child": {
                        "type": "BRANCH",
                        "field": "var_overdue_count_6m",
                        "name": "近6月逾期次数",
                        "branches": [
                            {
                                "condition": "== 0",
                                "child": {"type": "LEAF", "result": {"creditGrade": "C", "riskLabel": "中高风险"}},
                            },
                            {
                                "condition": ">= 1",
                                "child": {"type": "LEAF", "result": {"creditGrade": "D", "riskLabel": "高风险"}},
                            },
                        ],
                    },
                },
            ],
        },
    }
    return _make_rule_entity("DTREE_CREDIT_GRADE", "信用等级判定决策树", "DECISION_TREE",
                             "嵌套分支节点决策树：征信评分→逾期次数/负债率→A/B/C/D等级",
                             json.dumps(tree, ensure_ascii=False))


# ---------------------------------------------------------------------------
# 决策流 DAG 生成 (Tasks 7.1 ~ 7.3)
# ---------------------------------------------------------------------------

def generate_flow_main() -> dict:
    """生成 FLOW_CREDIT_MAIN 主决策流 DAG。"""
    flow = {
        "flowId": "FLOW_CREDIT_MAIN",
        "name": "信贷审批主决策流 V1",
        "version": 1,
        "nodes": [
            {"id": "data_prep",      "type": "DATA_PREP",  "config": {"prefetchVars": True}},
            {"id": "blacklist",      "type": "RULE_SET",   "config": {"ruleSetId": "RS_BLACKLIST"}},
            {"id": "eligibility",    "type": "RULE_SET",   "config": {"ruleSetId": "RS_ELIGIBILITY"}},
            {"id": "scorecard_a",    "type": "SCORECARD",  "config": {"scorecardId": "SC_CREDIT_A"}},
            {"id": "scorecard_b",    "type": "SCORECARD",  "config": {"scorecardId": "SC_CREDIT_B"}},
            {"id": "scorecard_c",    "type": "SCORECARD",  "config": {"scorecardId": "SC_CREDIT_C"}},
            {"id": "loan_amount",    "type": "DECISION",   "config": {"tableId": "DT_LOAN_AMOUNT"}},
            {"id": "pricing",        "type": "DECISION",   "config": {"tableId": "DT_PRICING"}},
            {"id": "action_reject",  "type": "ACTION",     "config": {"action": "REJECT",  "reasonCode": "AUTO_REJECT"}},
            {"id": "action_review",  "type": "ACTION",     "config": {"action": "REVIEW",  "reasonCode": "AUTO_REVIEW"}},
            {"id": "action_pass",    "type": "ACTION",     "config": {"action": "PASS",    "reasonCode": "AUTO_PASS"}},
        ],
        "edges": [
            {"from": "data_prep",    "to": "blacklist"},
            {"from": "blacklist",    "to": "action_reject", "condition": "blacklist_hit == true"},
            {"from": "blacklist",    "to": "eligibility",   "condition": "blacklist_hit == false"},
            {"from": "eligibility",  "to": "action_reject", "condition": "eligibility_pass == false"},
            {"from": "eligibility",  "to": "scorecard_a",   "condition": "eligibility_pass == true"},
            {"from": "scorecard_a",  "to": "action_reject", "condition": "scorecard_a_result == 'REJECT'"},
            {"from": "scorecard_a",  "to": "action_review", "condition": "scorecard_a_result == 'REVIEW'"},
            {"from": "scorecard_a",  "to": "scorecard_b",   "condition": "scorecard_a_result == 'PASS'"},
            {"from": "scorecard_b",  "to": "scorecard_c"},
            {"from": "scorecard_c",  "to": "loan_amount"},
            {"from": "loan_amount",  "to": "action_reject", "condition": "loan_amount_action == 'REJECT'"},
            {"from": "loan_amount",  "to": "pricing",       "condition": "loan_amount_action != 'REJECT'"},
            {"from": "pricing",      "to": "action_reject", "condition": "pricing_action == 'REJECT'"},
            {"from": "pricing",      "to": "action_pass",   "condition": "pricing_action != 'REJECT'"},
        ],
    }
    return _make_rule_entity("FLOW_CREDIT_MAIN", "信贷审批主决策流 V1", "FLOW",
                             "完整信贷审批DAG：DATA_PREP→黑名单→准入→评分卡A/B/C→额度→定价→PASS/REJECT/REVIEW",
                             json.dumps(flow, ensure_ascii=False))


def generate_flow_main_v2() -> dict:
    """生成 FLOW_CREDIT_MAIN_V2 实验版决策流（评分卡权重调整版）。"""
    flow = {
        "flowId": "FLOW_CREDIT_MAIN_V2",
        "name": "信贷审批主决策流 V2 (实验)",
        "version": 1,
        "nodes": [
            {"id": "data_prep",      "type": "DATA_PREP",  "config": {"prefetchVars": True}},
            {"id": "blacklist",      "type": "RULE_SET",   "config": {"ruleSetId": "RS_BLACKLIST"}},
            {"id": "eligibility",    "type": "RULE_SET",   "config": {"ruleSetId": "RS_ELIGIBILITY"}},
            {"id": "scorecard_a",    "type": "SCORECARD",  "config": {"scorecardId": "SC_CREDIT_A_V2"}},  # 使用 V2 评分卡
            {"id": "scorecard_b",    "type": "SCORECARD",  "config": {"scorecardId": "SC_CREDIT_B"}},
            {"id": "scorecard_c",    "type": "SCORECARD",  "config": {"scorecardId": "SC_CREDIT_C"}},
            {"id": "loan_amount",    "type": "DECISION",   "config": {"tableId": "DT_LOAN_AMOUNT"}},
            {"id": "pricing",        "type": "DECISION",   "config": {"tableId": "DT_PRICING"}},
            {"id": "action_reject",  "type": "ACTION",     "config": {"action": "REJECT"}},
            {"id": "action_review",  "type": "ACTION",     "config": {"action": "REVIEW"}},
            {"id": "action_pass",    "type": "ACTION",     "config": {"action": "PASS"}},
        ],
        "edges": [
            {"from": "data_prep",    "to": "blacklist"},
            {"from": "blacklist",    "to": "action_reject", "condition": "blacklist_hit == true"},
            {"from": "blacklist",    "to": "eligibility",   "condition": "blacklist_hit == false"},
            {"from": "eligibility",  "to": "action_reject", "condition": "eligibility_pass == false"},
            {"from": "eligibility",  "to": "scorecard_a",   "condition": "eligibility_pass == true"},
            {"from": "scorecard_a",  "to": "action_reject", "condition": "scorecard_a_result == 'REJECT'"},
            {"from": "scorecard_a",  "to": "action_review", "condition": "scorecard_a_result == 'REVIEW'"},
            {"from": "scorecard_a",  "to": "scorecard_b",   "condition": "scorecard_a_result == 'PASS'"},
            {"from": "scorecard_b",  "to": "scorecard_c"},
            {"from": "scorecard_c",  "to": "loan_amount"},
            {"from": "loan_amount",  "to": "action_reject", "condition": "loan_amount_action == 'REJECT'"},
            {"from": "loan_amount",  "to": "pricing",       "condition": "loan_amount_action != 'REJECT'"},
            {"from": "pricing",      "to": "action_reject", "condition": "pricing_action == 'REJECT'"},
            {"from": "pricing",      "to": "action_pass",   "condition": "pricing_action != 'REJECT'"},
        ],
    }
    return _make_rule_entity("FLOW_CREDIT_MAIN_V2", "信贷审批主决策流 V2 (实验)", "FLOW",
                             "实验版：使用 SC_CREDIT_A_V2 评分卡（阈值调整为 reject<480, review<530）",
                             json.dumps(flow, ensure_ascii=False))


# ---------------------------------------------------------------------------
# V2 评分卡 (实验用)
# ---------------------------------------------------------------------------

def generate_scorecard_a_v2() -> dict:
    """生成 SC_CREDIT_A_V2 实验版评分卡（降低拒绝阈值，提高通过率）。"""
    card = {
        "scorecardId": "SC_CREDIT_A_V2",
        "name": "主信用评分卡 A (V2实验)",
        "version": 1,
        "initialScore": 520,  # V2 初始分更高
        "characteristics": [
            {
                "name": "年龄", "field": "var_age",
                "bins": [
                    {"range": [None, 22],  "score": -10, "label": "[min, 22)"},
                    {"range": [22, 30],   "score": 15,  "label": "[22, 30)"},
                    {"range": [30, 45],   "score": 25,  "label": "[30, 45)"},
                    {"range": [45, 60],   "score": 15,  "label": "[45, 60)"},
                    {"range": [60, None], "score": -5,  "label": "[60, max)"},
                ],
            },
            {
                "name": "征信评分", "field": "var_credit_score",
                "bins": [
                    {"range": [None, 500], "score": -25},
                    {"range": [500, 600],  "score": -5},
                    {"range": [600, 700],  "score": 20},
                    {"range": [700, 800],  "score": 35},
                    {"range": [800, None], "score": 45},
                ],
            },
            {
                "name": "工作年限", "field": "var_work_years",
                "bins": [
                    {"range": [None, 1],  "score": -5},
                    {"range": [1, 3],     "score": 5},
                    {"range": [3, 10],    "score": 15},
                    {"range": [10, None], "score": 20},
                ],
            },
            {
                "name": "月收入", "field": "var_income_monthly",
                "bins": [
                    {"range": [None, 5000],   "score": -15},
                    {"range": [5000, 10000],  "score": 5},
                    {"range": [10000, 20000], "score": 20},
                    {"range": [20000, 50000], "score": 30},
                    {"range": [50000, None],  "score": 40},
                ],
            },
        ],
        "cutoff": {"reject": 480, "review": 530, "pass": 530},  # V2 阈值更低
    }
    return _make_rule_entity("SC_CREDIT_A_V2", "主信用评分卡 A (V2实验)", "SCORECARD",
                             "V2实验版评分卡：initialScore=520, reject<480, review<530。相比V1整体通过率提升。",
                             json.dumps(card, ensure_ascii=False))


# ---------------------------------------------------------------------------
# 实验配置生成 (Tasks 8.1 ~ 8.6)
# ---------------------------------------------------------------------------

def generate_experiments() -> list[dict]:
    """生成 AB 实验配置。"""
    experiments = []

    # EXP_CREDIT_STRATEGY
    exp1 = {
        "experimentId": "EXP_CREDIT_STRATEGY",
        "name": "信用策略 AB 实验",
        "trafficKey": "customerId",
        "groups": [
            {"groupId": "control",    "name": "对照组(V1)", "trafficRatio": 0.5, "strategyId": "FLOW_CREDIT_MAIN"},
            {"groupId": "experiment", "name": "实验组(V2)", "trafficRatio": 0.5, "strategyId": "FLOW_CREDIT_MAIN_V2"},
        ],
        "startTimeMs": 1717200000000,
        "enabled": True,
        "terminationCondition": "p_value < 0.05",
    }
    experiments.append(_make_rule_entity("EXP_CREDIT_STRATEGY", "信用策略 AB 实验", "EXPERIMENT",
                                          "对比 V1/V2 决策流：V1 原评分卡 vs V2 低阈值评分卡，50/50 流量分割",
                                          json.dumps(exp1, ensure_ascii=False)))

    # EXP_PRICING_MODEL
    exp2 = {
        "experimentId": "EXP_PRICING_MODEL",
        "name": "定价模型 AB 实验",
        "trafficKey": "customerId",
        "groups": [
            {"groupId": "control",    "name": "固定利率",   "trafficRatio": 0.7, "strategyId": "FLOW_CREDIT_MAIN"},
            {"groupId": "experiment", "name": "动态利率V2","trafficRatio": 0.3, "strategyId": "FLOW_CREDIT_MAIN_V2"},
        ],
        "startTimeMs": 1717200000000,
        "enabled": False,
        "terminationCondition": None,
    }
    experiments.append(_make_rule_entity("EXP_PRICING_MODEL", "定价模型 AB 实验", "EXPERIMENT",
                                          "对比固定/动态利率：70/30 分流，当前关闭", json.dumps(exp2, ensure_ascii=False)))

    return experiments


# ---------------------------------------------------------------------------
# 灰度发布记录生成 (Task 8.3)
# ---------------------------------------------------------------------------

def generate_grayscale_records(now: str = None) -> list[dict]:
    """生成灰度发布记录。"""
    if now is None:
        now = _now()
    base = datetime.strptime("2026-06-01 10:00:00", "%Y-%m-%d %H:%M:%S")

    return [
        {
            "config_id": "GS_001",
            "target_type": "RULE",
            "target_id": "RS_BLACKLIST",
            "target_version": 1,
            "percentage": 10,
            "previous_percentage": 0,
            "operator": "admin",
            "started_at": base.strftime("%Y-%m-%d %H:%M:%S"),
            "grayscale_status": "GRAYSCALE",
        },
        {
            "config_id": "GS_002",
            "target_type": "RULE",
            "target_id": "RS_BLACKLIST",
            "target_version": 1,
            "percentage": 50,
            "previous_percentage": 10,
            "operator": "admin",
            "started_at": (base + timedelta(days=1, hours=4)).strftime("%Y-%m-%d %H:%M:%S"),
            "grayscale_status": "GRAYSCALE",
        },
        {
            "config_id": "GS_003",
            "target_type": "RULE",
            "target_id": "RS_BLACKLIST",
            "target_version": 1,
            "percentage": 100,
            "previous_percentage": 50,
            "operator": "admin",
            "started_at": (base + timedelta(days=2, hours=8)).strftime("%Y-%m-%d %H:%M:%S"),
            "grayscale_status": "RELEASED",
        },
        {
            "config_id": "GS_004",
            "target_type": "SCORECARD",
            "target_id": "SC_CREDIT_A",
            "target_version": 1,
            "percentage": 100,
            "previous_percentage": 0,
            "operator": "admin",
            "started_at": (base + timedelta(days=1)).strftime("%Y-%m-%d %H:%M:%S"),
            "grayscale_status": "RELEASED",
        },
        {
            "config_id": "GS_005",
            "target_type": "FLOW",
            "target_id": "FLOW_CREDIT_MAIN",
            "target_version": 1,
            "percentage": 100,
            "previous_percentage": 0,
            "operator": "admin",
            "started_at": (base + timedelta(days=3)).strftime("%Y-%m-%d %H:%M:%S"),
            "grayscale_status": "RELEASED",
        },
    ]


# ---------------------------------------------------------------------------
# 审批记录生成 (Task 8.4)
# ---------------------------------------------------------------------------

def generate_approval_records(now: str = None) -> list[dict]:
    """生成审批工作流记录。"""
    if now is None:
        now = _now()
    base = datetime.strptime("2026-05-25 09:00:00", "%Y-%m-%d %H:%M:%S")

    return [
        {
            "record_id": f"APR_{i:04d}",
            "target_type": rec[0],
            "target_id": rec[1],
            "target_version": 1,
            "action": rec[2],
            "operator": rec[3],
            "comment": rec[4],
            "operated_at": (base + timedelta(hours=i * 3)).strftime("%Y-%m-%d %H:%M:%S"),
        }
        for i, rec in enumerate([
            ("RULE",       "RS_BLACKLIST",     "SUBMIT",  "developer_wang", "反欺诈规则集开发完成，提交审批"),
            ("RULE",       "RS_BLACKLIST",     "APPROVE", "reviewer_zhang", "黑名单规则集审核通过，准入规则合理，同意上线"),
            ("RULE",       "RS_ELIGIBILITY",   "SUBMIT",  "developer_wang", "准入规则集开发完成，请审核"),
            ("RULE",       "RS_ELIGIBILITY",   "REJECT",  "reviewer_li",    "年龄上限建议放宽至65岁，请修改后重新提交"),
            ("RULE",       "RS_ELIGIBILITY",   "SUBMIT",  "developer_wang", "已修改年龄上限至65岁，重新提交"),
            ("RULE",       "RS_ELIGIBILITY",   "APPROVE", "reviewer_li",    "修改确认无误，通过"),
            ("SCORECARD",  "SC_CREDIT_A",      "SUBMIT",  "analyst_chen",   "主评分卡参数配置完成，请审核"),
            ("SCORECARD",  "SC_CREDIT_A",      "APPROVE", "reviewer_zhang", "评分卡参数合理，分箱权重符合业务逻辑，通过"),
            ("FLOW",       "FLOW_CREDIT_MAIN", "SUBMIT",  "developer_wang", "主决策流配置完成，串联全部决策节点"),
            ("FLOW",       "FLOW_CREDIT_MAIN", "APPROVE", "reviewer_zhang", "决策流逻辑正确，黑名单→准入→评分→额度→定价链路完整，同意发布"),
        ])
    ]


# ---------------------------------------------------------------------------
# 审计日志生成 (Task 8.5)
# ---------------------------------------------------------------------------

def generate_audit_logs(rule_entities: list[dict]) -> list[dict]:
    """为每条规则实体生成 CREATE 审计日志。"""
    logs = []
    for i, entity in enumerate(rule_entities):
        logs.append({
            "operator": entity["created_by"],
            "action": "CREATE",
            "target_type": entity["type"],
            "target_id": entity["id"],
            "target_version": entity["version"],
            "before_snapshot": None,
            "after_snapshot": entity["content"],
            "details": f"创建 {entity['type']} 配置: {entity['name']}",
            "ip_address": "127.0.0.1",
            "operated_at": entity["created_at"],
        })

    # 追加审批相关的审计日志
    logs.append({
        "operator": "reviewer_zhang",
        "action": "APPROVE",
        "target_type": "RULE",
        "target_id": "RS_BLACKLIST",
        "target_version": 1,
        "before_snapshot": "",
        "after_snapshot": "",
        "details": "审批通过：黑名单规则集",
        "ip_address": "10.0.1.100",
        "operated_at": "2026-05-25 12:00:00",
    })
    logs.append({
        "operator": "admin",
        "action": "PUBLISH",
        "target_type": "RULE",
        "target_id": "RS_BLACKLIST",
        "target_version": 1,
        "before_snapshot": "",
        "after_snapshot": "",
        "details": "灰度发布：RS_BLACKLIST 10%→50%→100%",
        "ip_address": "10.0.1.1",
        "operated_at": "2026-06-03 18:00:00",
    })
    logs.append({
        "operator": "admin",
        "action": "PUBLISH",
        "target_type": "FLOW",
        "target_id": "FLOW_CREDIT_MAIN",
        "target_version": 1,
        "before_snapshot": "",
        "after_snapshot": "",
        "details": "全量发布：主决策流 FLOW_CREDIT_MAIN v1",
        "ip_address": "10.0.1.1",
        "operated_at": "2026-06-04 09:00:00",
    })

    return logs


# ---------------------------------------------------------------------------
# 辅助函数
# ---------------------------------------------------------------------------

def _make_rule_entity(id_: str, name: str, type_: str, desc: str,
                      content_json: str, status: str = "RELEASED") -> dict:
    """构造 rule_entity 行 dict。"""
    now = _now()
    return {
        "id": id_,
        "name": name,
        "type": type_,
        "version": 1,
        "status": status,
        "content": content_json,
        "description": desc,
        "created_by": "system",
        "updated_by": "system",
        "created_at": now,
        "updated_at": now,
        "attributes": "{}",
    }


# ---------------------------------------------------------------------------
# 数据库写入
# ---------------------------------------------------------------------------

RULE_ENTITY_COLS = ["id", "name", "type", "version", "status", "content",
                    "description", "created_by", "updated_by", "created_at",
                    "updated_at", "attributes"]
GRAYSCALE_COLS = ["config_id", "target_type", "target_id", "target_version",
                  "percentage", "previous_percentage", "operator", "started_at",
                  "grayscale_status"]
APPROVAL_COLS = ["record_id", "target_type", "target_id", "target_version",
                 "action", "operator", "comment", "operated_at"]
AUDIT_COLS = ["operator", "action", "target_type", "target_id",
              "target_version", "before_snapshot", "after_snapshot",
              "details", "ip_address", "operated_at"]


def write_all(db: MySQLHelper, entities: list[dict], grayscale: list[dict],
              approvals: list[dict], audits: list[dict], dry_run: bool = False):
    """写入全部种子数据到数据库。"""

    if dry_run:
        print(f"\n{'='*60}")
        print("DRY RUN 模式 — 仅打印 JSON，不写入数据库")
        print(f"{'='*60}")
        for e in entities:
            print(f"\n[{e['type']}] {e['id']} ({e['name']})")
            print(f"  status={e['status']}, version={e['version']}")
            try:
                content = json.loads(e["content"]) if isinstance(e["content"], str) else e["content"]
                print(f"  content keys: {list(content.keys()) if isinstance(content, dict) else 'N/A'}")
            except json.JSONDecodeError:
                print(f"  content: {e['content'][:100]}...")
        print(f"\n--- 灰度记录: {len(grayscale)} 条 ---")
        print(f"--- 审批记录: {len(approvals)} 条 ---")
        print(f"--- 审计日志: {len(audits)} 条 ---")
        return

    # 清空旧数据 (保留 sys_user)
    for table in ["grayscale_config", "approval_record", "audit_log"]:
        db.execute(f"DELETE FROM `{table}`")
    db.execute("DELETE FROM `rule_entity`")
    print("已清空旧数据: rule_entity, grayscale_config, approval_record, audit_log")

    # 写入 rule_entity
    n = db.batch_insert("rule_entity", RULE_ENTITY_COLS, entities)
    print(f"已写入 rule_entity: {n} 条")

    # 写入灰度记录
    n = db.batch_insert("grayscale_config", GRAYSCALE_COLS, grayscale)
    print(f"已写入 grayscale_config: {n} 条")

    # 写入审批记录
    n = db.batch_insert("approval_record", APPROVAL_COLS, approvals)
    print(f"已写入 approval_record: {n} 条")

    # 写入审计日志
    n = db.batch_insert("audit_log", AUDIT_COLS, audits)
    print(f"已写入 audit_log: {n} 条")


# ---------------------------------------------------------------------------
# 汇总统计
# ---------------------------------------------------------------------------

def print_summary(entities: list[dict]):
    """打印生成汇总。"""
    type_counts = {}
    for e in entities:
        t = e["type"]
        type_counts[t] = type_counts.get(t, 0) + 1

    print(f"\n{'='*60}")
    print("决策引擎种子数据生成完成")
    print(f"{'='*60}")
    print(f"规则实体 (rule_entity): {len(entities)} 条")
    for t, c in sorted(type_counts.items()):
        print(f"  {t}: {c}")
    print()


# ---------------------------------------------------------------------------
# 主入口
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(description="决策引擎种子数据生成器")
    parser.add_argument("--dry-run", action="store_true", help="仅打印 JSON 不写入数据库")
    parser.add_argument("--verbose", "-v", action="store_true", help="打印详细 JSON 内容")
    args = parser.parse_args()

    cfg = get_config()
    now = _now()

    print("\n" + "=" * 60)
    print("决策引擎种子数据生成器")
    print(f"开始时间: {now}")
    print("=" * 60)

    # 按业务逻辑层级生成
    all_entities: list[dict] = []

    # 1. 变量 (Tasks 2.1-2.5)
    print("\n[1/8] 生成变量体系 (L0-L3)...")
    variables = generate_variables(cfg)
    all_entities.extend(variables)
    print(f"  变量: {len(variables)} 个 (L0:{sum(1 for v in variables if json.loads(v['content'] if isinstance(v['content'], str) else v['content']).get('layer')=='INPUT')}, "
          f"L1:{sum(1 for v in variables if json.loads(v['content'] if isinstance(v['content'], str) else v['content']).get('layer')=='EXTERNAL')}, "
          f"L2:{sum(1 for v in variables if json.loads(v['content'] if isinstance(v['content'], str) else v['content']).get('layer')=='CACHED')}, "
          f"L3:{sum(1 for v in variables if json.loads(v['content'] if isinstance(v['content'], str) else v['content']).get('layer')=='DERIVED')})")

    # 变量依赖校验
    ok, errors = validate_variable_dependencies(variables)
    if not ok:
        print("  ❌ 变量依赖校验失败:")
        for e in errors:
            print(f"    - {e}")
        sys.exit(1)
    print("  ✓ 变量依赖完整性校验通过")

    # 2. 规则集 (Tasks 3.1-3.3)
    print("\n[2/8] 生成规则集...")
    all_entities.append(generate_blacklist_ruleset())
    print(f"  RS_BLACKLIST: 6 条规则 (FIRST_HIT)")
    all_entities.append(generate_eligibility_ruleset())
    print(f"  RS_ELIGIBILITY: 5 条规则 (ALL)")

    # 3. 评分卡 (Tasks 4.1-4.4)
    print("\n[3/8] 生成评分卡...")
    all_entities.append(generate_scorecard_a())
    print(f"  SC_CREDIT_A: 4 特征, initialScore=500")
    all_entities.append(generate_scorecard_b())
    print(f"  SC_CREDIT_B: 3 特征, initialScore=0")
    all_entities.append(generate_scorecard_c())
    print(f"  SC_CREDIT_C: 3 特征, initialScore=0")
    all_entities.append(generate_scorecard_a_v2())
    print(f"  SC_CREDIT_A_V2: 4 特征, initialScore=520 (实验)")

    # 4. 决策表 (Tasks 5.1-5.3)
    print("\n[4/8] 生成决策表...")
    all_entities.append(generate_loan_amount_table())
    print(f"  DT_LOAN_AMOUNT: 3列×17行")
    all_entities.append(generate_pricing_table())
    print(f"  DT_PRICING: 3列×14行")

    # 5. 决策树 (Tasks 6.1-6.2)
    print("\n[5/8] 生成决策树...")
    all_entities.append(generate_credit_grade_tree())
    print(f"  DTREE_CREDIT_GRADE: 1 棵")

    # 6. 决策流 (Tasks 7.1-7.3)
    print("\n[6/8] 生成决策流 DAG...")
    all_entities.append(generate_flow_main())
    print(f"  FLOW_CREDIT_MAIN: 11 节点, 13 条边")
    all_entities.append(generate_flow_main_v2())
    print(f"  FLOW_CREDIT_MAIN_V2: 11 节点, 13 条边 (实验)")

    # 7. 实验 (Tasks 8.1-8.6)
    print("\n[7/8] 生成实验与运维数据...")
    experiments = generate_experiments()
    all_entities.extend(experiments)
    print(f"  实验: {len(experiments)} 个")

    # 8. 灰度+审批+审计
    grayscale = generate_grayscale_records(now)
    approvals = generate_approval_records(now)
    audits = generate_audit_logs(all_entities)
    print(f"  灰度记录: {len(grayscale)} 条")
    print(f"  审批记录: {len(approvals)} 条")
    print(f"  审计日志: {len(audits)} 条")

    # 写入数据库
    print("\n[8/8] 写入数据库...")
    with MySQLHelper(cfg) as db:
        write_all(db, all_entities, grayscale, approvals, audits, dry_run=args.dry_run)

    print_summary(all_entities)

    if args.verbose:
        print("\n" + "=" * 60)
        print("详细 JSON 内容")
        print("=" * 60)
        for e in all_entities:
            print(f"\n--- {e['type']}: {e['id']} ---")
            try:
                print(json.dumps(json.loads(e["content"]), ensure_ascii=False, indent=2))
            except json.JSONDecodeError:
                print(e["content"])


if __name__ == "__main__":
    main()
