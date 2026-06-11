## Why

风控决策平台 (decision-server + decision-admin) 已完成引擎核心开发（engine-core 含规则编译器、评分卡、决策表、DAG流程、变量引擎、实验分流、追踪系统等全部模块），但 `rule_entity` 表为空——没有任何规则、评分卡、决策流、决策表、变量定义。同时，数据生成器（`tools/data-generator/`）已能生成 1 万客户 × 12 个月的信贷业务数据（客户、贷款申请、还款记录、征信报告），并通过实时生成器三个写入路径（MySQL、Kafka、决策引擎 HTTP）产数据。缺少决策引擎配置数据导致：业务数据无法真正跑决策流程、无法验证引擎端到端能力、无法产出决策日志和报表，整个平台只是"有引擎无规则"的空壳。

## What Changes

- **新增** 决策引擎种子数据生成模块：生成覆盖信贷全场景的规则体系（黑名单、反欺诈、准入、额度、定价）、评分卡（A/B/C 三张卡）、决策表（额度矩阵、定价矩阵）、决策流（主流程 DAG + 子流程）、变量定义（L0-L3 四层变量体系）
- **新增** 实验与发布数据：AB 实验配置（对照组 vs 实验组）、灰度发布记录、审批记录、审计日志
- **新增** 端到端决策执行器：批量离线数据 + 实时生成数据 → 调用决策引擎 API → 产出决策日志（MySQL + ES + Hive ODS），形成"业务数据 → 决策引擎 → 决策结果 → 日志报表"闭环
- **新增** 决策日志报告生成：按日/周/月维度汇总决策统计（通过率、拒绝率、人工复核率、各规则命中率、评分分布），产出可查询报表

## Capabilities

### New Capabilities

- `decision-engine-seed-data`: 决策引擎配置种子数据生成——规则（条件规则 + 规则集）、评分卡、决策表、决策流 DAG、变量定义，覆盖完整信贷风控场景，数据真实有逻辑
- `decision-engine-experiment-data`: AB 实验与灰度发布数据生成——实验配置、流量分割、灰度上线记录、审批工作流记录
- `decision-engine-e2e-runner`: 端到端决策执行器——复用已有业务数据，批量/实时调用决策引擎执行决策，产出决策日志到 MySQL + Elasticsearch + Hive ODS，形成数据闭环
- `decision-engine-reports`: 决策日志报表生成——按时间维度汇总决策统计数据，产出结构化报表

### Modified Capabilities

<!-- 本次不修改现有 spec，仅新增能力 -->

## Impact

- **Affected code**: `tools/data-generator/` 目录新增 `decision_seed.py`（种子数据生成）、`decision_runner.py`（端到端执行器）、`decision_report.py`（报表生成器）；`config.yaml` 新增决策引擎场景参数；`models.py` 新增决策引擎配置数据模型
- **Affected systems**: decision-server (port 8080，执行决策 API)、decision-admin (port 8081，管理端 API 创建配置)、MySQL `credit_platform` 库 `rule_entity` 表（写入配置数据）、Elasticsearch `decision-log-*` 索引（写入决策日志）、Hive `ods.ods_decision_log` 表（写入离线决策日志）
- **Affected data pipeline**: 决策日志通过 Hive ODS 表接入数仓，可被 Spark ETL 加工到 DWD/DWS/ADS 层，形成完整的决策分析链路
- **Dependencies**: 需要 decision-server 和 decision-admin 服务运行、MySQL `rule_entity` 表已建、ES `decision-log-*` 索引模板已配
