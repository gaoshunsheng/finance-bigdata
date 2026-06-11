# 决策引擎数据生成器使用文档

> 版本: 1.0 | 创建: 2026-06-07 | 关联: [数据生成器](../tools/data-generator/)

## 概述

决策引擎数据生成器是在已有业务数据生成器基础上扩展的决策配置数据生成工具。它解决"有引擎无规则"的核心问题：生成完整的风控决策配置（规则、评分卡、决策表、决策流、变量、实验），并支持端到端将业务数据喂给决策引擎执行，形成"业务数据 → 决策引擎 → 决策日志 → 报表分析"完整闭环。

## 模块架构

```
tools/data-generator/
├── decision_models.py     # Pydantic 数据模型 (RuleEntity, ScorecardConfig, FlowDAGConfig 等)
├── decision_seed.py       # 种子数据生成器 (配置数据)
├── decision_runner.py     # 端到端执行器 (业务数据跑决策)
├── decision_report.py     # 决策日志报表生成器
├── config.yaml            # 配置文件 (含决策引擎场景参数)
└── output/
    ├── decision_logs_*.jsonl    # 决策日志 (JSONL)
    ├── decision_summary_*.csv   # 决策汇总 (CSV)
    ├── decision_errors_*.jsonl  # 错误日志
    └── reports/
        ├── decision_report_*.md        # Markdown 报告
        ├── decision_stats_*.csv        # 总览统计
        ├── rule_hit_stats_*.csv        # 规则命中率
        ├── score_distribution_*.csv    # 评分分布
        └── daily_trend_*.csv          # 每日趋势
```

## 快速开始

### 前置条件

1. MySQL `credit_platform` 库已创建（含 `rule_entity`, `grayscale_config`, `approval_record`, `audit_log` 表）
2. decision-server 已启动（默认 `http://localhost:18080`）
3. Elasticsearch 已启动（可选，默认 `http://localhost:9200`）
4. 已有业务数据（`customer_info` + `loan_application` 表有数据）

### 第一步：生成决策配置种子数据

```bash
cd tools/data-generator

# 生成全部配置数据（写入 MySQL）
python decision_seed.py

# 仅预览 JSON 不写入数据库
python decision_seed.py --dry-run

# 打印详细 JSON 内容
python decision_seed.py --verbose
```

生成的数据覆盖：

| 类型 | 数量 | 内容 |
|------|------|------|
| VARIABLE | 29 | L0(7) + L1(7) + L2(8) + L3(7) 四层变量体系 |
| RULE | 2 规则集 | RS_BLACKLIST (6条, FIRST_HIT) + RS_ELIGIBILITY (5条, ALL) |
| SCORECARD | 4 | SC_CREDIT_A/B/C + SC_CREDIT_A_V2 (实验版) |
| DECISION_TABLE | 2 | DT_LOAN_AMOUNT (17行) + DT_PRICING (14行) |
| DECISION_TREE | 1 | DTREE_CREDIT_GRADE |
| FLOW | 2 | FLOW_CREDIT_MAIN (V1) + FLOW_CREDIT_MAIN_V2 (实验) |
| EXPERIMENT | 2 | EXP_CREDIT_STRATEGY + EXP_PRICING_MODEL |
| 灰度记录 | 5 | RS_BLACKLIST 10→50→100% 上线过程 |
| 审批记录 | 10 | 覆盖 SUBMIT/APPROVE/REJECT 操作 |
| 审计日志 | 45 | 每条配置的 CREATE/PUBLISH 记录 |

### 第二步：端到端决策执行

```bash
# 批量执行全部历史数据（约 27,000 条贷款申请）
python decision_runner.py

# 抽样 1000 条测试
python decision_runner.py --sample 1000

# 8 并发加速
python decision_runner.py --sample 1000 --concurrency 8

# 执行后自动生成报表
python decision_runner.py --sample 1000 --auto-report

# 实时模式（配合 realtime_gen.py）
python decision_runner.py --mode realtime
```

### 第三步：生成决策报表

```bash
# 自动检测数据源（优先 ES，回退本地文件）
python decision_report.py

# 指定从 ES 读取
python decision_report.py --source es

# 指定从本地文件读取
python decision_report.py --source local --input output/decision_logs_*.jsonl
```

## 决策流架构

```
                    FLOW_CREDIT_MAIN (主决策流)
                              │
                    ┌─────────▼─────────┐
                    │    DATA_PREP      │  ← 变量预取 (L0-L3)
                    └─────────┬─────────┘
                              │
                    ┌─────────▼─────────┐
                    │   RS_BLACKLIST    │  ← 反欺诈规则集 (FIRST_HIT)
                    └────┬────────┬─────┘
                  命中   │        │  未命中
               ┌────────▼──┐  ┌──▼──────────┐
               │  REJECT   │  │ RS_ELIGIBILITY│ ← 准入规则集 (ALL)
               └───────────┘  └──┬───────┬───┘
                           未通过│       │通过
                       ┌────────▼──┐ ┌──▼──────────┐
                       │  REJECT   │ │ SCORECARD_A  │ ← 主评分卡
                       └───────────┘ └──┬───────┬───┘
                                  REJECT│       │PASS
                              ┌─────────▼─┐ ┌───▼──────────┐
                              │  REVIEW   │ │ SCORECARD_B  │ ← 行为评分卡
                              └───────────┘ └───────┬───────┘
                                                    │
                                          ┌─────────▼─────────┐
                                          │   SCORECARD_C     │ ← 收入评分卡
                                          └─────────┬─────────┘
                                                    │
                                          ┌─────────▼─────────┐
                                          │  DT_LOAN_AMOUNT   │ ← 额度决策表
                                          └────┬─────────┬───┘
                                         REJECT│         │PASS
                                      ┌────────▼──┐ ┌────▼──────┐
                                      │  REJECT   │ │ DT_PRICING │ ← 定价决策表
                                      └───────────┘ └──┬─────┬──┘
                                                   REJECT│     │PASS
                                                ┌────────▼─┐ ┌─▼──────┐
                                                │  REJECT  │ │  PASS  │
                                                └──────────┘ └────────┘
```

## 业务场景覆盖

| 场景 | 规则/组件 | 触发条件示例 |
|------|-----------|-------------|
| 身份黑名单 | R_BLACKLIST_ID | blacklist_flag=true |
| 通讯黑名单 | R_PHONE_BLACKLIST | phone_blacklist=true |
| 多头借贷 | R_MULTI_LOAN | multi_loan_count ≥ 5 |
| 设备异常 | R_DEVICE_ANOMALY | device_anomaly=true |
| 年龄不符 | R_AGE_CHECK | age < 22 or age > 60 |
| 收入不足 | R_MIN_INCOME | income_monthly < 3000 |
| 征信查询过多 | R_CREDIT_QUERY_LIMIT | credit_query_count > 6 |
| 负债过高 | R_DEBT_RATIO_CHECK | debt_ratio > 0.70 |
| 评分不足 | SC_CREDIT_A cutoff | total_score < 500 |
| D级客户拒贷 | DT_LOAN_AMOUNT | credit_grade=D → reject |
| 高负债拒贷 | DT_LOAN_AMOUNT | dti_category=CRITICAL → reject |

## 配置参数说明

所有决策引擎种子数据参数在 `config.yaml` 的 `decision_seed` 段：

```yaml
decision_seed:
  scorecard:         # 评分卡阈值
  eligibility:       # 准入条件阈值
  blacklist:         # 反欺诈阈值
  loan_amount:       # 额度矩阵
  pricing:           # 定价基准
  experiment:        # 实验流量比例
  grayscale:         # 灰度时间线

decision_runner:
  default_strategy: "FLOW_CREDIT_MAIN"
  concurrency: 4
  retry_max: 3
  request_timeout: 10
```

## 故障排查

### decision-server 不可用

```
❌ decision-server 不可用: Connection refused
```

**解决**: 启动 decision-server 服务（默认端口 18080）：
```bash
docker-compose up -d decision-server
# 或
cd decision-server && mvn spring-boot:run
```

### 无已发布配置

```
❌ MySQL rule_entity 无已发布配置，请先运行 decision_seed.py
```

**解决**: 先运行种子数据生成器：
```bash
python decision_seed.py
```

### ES 不可用（非阻塞）

```
⚠ ES 不可用 (localhost:9200)，将仅写入本地文件
```

这是 Warning 而非 Error —— 决策日志仍会写入本地 JSONL/CSV 文件，报表生成时会自动回退到本地文件数据源。

### 业务数据为空

```
❌ 业务数据不足 (客户:0, 贷款:0)
```

**解决**: 先运行业务数据生成器：
```bash
python batch_data_gen.py
```
