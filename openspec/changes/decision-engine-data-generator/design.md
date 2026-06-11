## Context

当前项目状态：决策引擎核心 (`engine-core`) 已完成全部编译器和执行器开发（规则、评分卡、决策表、决策树、DAG 流程、变量引擎、实验分流、追踪系统），`decision-admin` 提供完整 CRUD API（含状态流转：DRAFT → TEST → REVIEW → GRAYSCALE → RELEASED），`decision-server` 提供决策执行 API (`POST /api/v1/decision/execute`)。但 `credit_platform.rule_entity` 表为空——无任何配置数据。

数据生成器 (`tools/data-generator/`) 已完成业务数据生成：批量生成 1 万客户 × 12 个月数据（客户、贷款、还款、征信共 4 表），实时生成器支持三路径写入（MySQL + Kafka + 决策引擎 HTTP）。决策引擎验证器 (`verifiers/decision_check.py`) 仅做健康检查和单次 API 调用，不验证业务闭环。

目标：利用已有基础设施，通过 Python 脚本直接写入 MySQL `rule_entity` 表生成决策引擎配置数据，再通过端到端执行器将已有的业务数据喂给决策引擎，产出决策日志，形成完整闭环。

## Goals / Non-Goals

**Goals:**
- 生成一套完整、有逻辑、覆盖信贷全场景的决策引擎配置数据（规则、评分卡、决策表、决策流、变量、实验），使得业务数据可以真正运行决策
- 端到端执行器：复用已有业务数据（客户、贷款申请），逐条构造决策请求调用 decision-server，收集响应并写入 ES 和 Hive ODS 决策日志表
- 决策日志报表：按日/周/月维度汇总决策统计数据，输出结构化报表文件
- 所有生成逻辑以 Python 脚本形式集成到 `tools/data-generator/`，与现有业务数据生成器共用 `config.yaml` 和 `db.py`/`models.py`

**Non-Goals:**
- 不修改 `engine-core` Java 代码
- 不修改 `decision-server` 或 `decision-admin` 服务代码
- 不修改 MySQL 表结构或 ES 索引 mapping
- 不生成前端 UI 测试数据
- 不生成模型平台 (model-platform) 的模型文件

## Decisions

### D1: 通过 Admin API 写入 vs 直接 SQL INSERT

**选择**: 直接 SQL INSERT 到 `rule_entity` 表

**理由**:
- `rule_entity` 表结构简单（id, name, type, version, status, content JSON, description），JSON 格式与引擎编译器期望格式一致，SQL 写入无歧义
- Admin API 有 JWT 鉴权和业务校验，批量生成时增加复杂度；直接 SQL 写入跳过鉴权且支持批量 INSERT
- 数据生成完成后，引擎通过 `decision-server` 的 `ArtifactCompiler` 热加载 JSON 到 Caffeine 缓存，不依赖 Admin 中间状态
- **替代方案**: 通过 `decision-admin` REST API 逐条创建——优点是通过了业务校验层，更接近真实操作；缺点是批量生成慢（HTTP 调用开销），且需要处理 JWT token。结论：Admin API 方式更适合少量手工创建，批量生成用 SQL。

### D2: 决策配置数据结构设计

**选择**: 按信贷风控业务场景分层设计

**业务场景分层**:
1. **反欺诈/黑名单层**: 规则集 `RS_BLACKLIST`（身份黑名单、通讯黑名单、多头借贷标记）→ hitPolicy=FIRST_HIT，任一命中即 REJECT
2. **准入规则层**: 规则集 `RS_ELIGIBILITY`（年龄、收入、征信查询次数）→ hitPolicy=ALL，全部通过才进入下一层
3. **评分卡层**: 评分卡 `SC_CREDIT_A`（主评分卡）、`SC_CREDIT_B`（行为评分卡）、`SC_CREDIT_C`（收入评分卡）→ 加权合并计算总分
4. **额度决策层**: 决策表 `DT_LOAN_AMOUNT`（客户等级 × 产品类型 × 收入水平 → 额度区间）→ FIRST_MATCH
5. **定价决策层**: 决策表 `DT_PRICING`（信用等级 × 贷款期限 × 担保方式 → 利率）→ FIRST_MATCH
6. **决策流**: `FLOW_CREDIT_MAIN` 主流程 DAG，串联上述 5 层

**变量体系** (L0-L3 四层):
- L0 INPUT: `customer_id`, `loan_amount`, `loan_purpose`, `loan_term`, `product_type`
- L1 EXTERNAL: `credit_score`（征信分）, `blacklist_flag`（黑名单标记）, `multi_loan_count`（多头借贷次数）
- L2 CACHED: `overdue_count_6m`（近6月逾期次数）, `debt_ratio`（负债率）, `income_monthly`（月收入）
- L3 DERIVED: `risk_level`（风险等级，由评分卡总分映射）, `is_high_risk`（是否高风险）, `approval_decision`（审批决策）

### D3: 端到端执行器架构

**选择**: 流式批量执行，从 MySQL 业务表读取客户/贷款数据，逐条构造 `DecisionRequest` JSON 调用 decision-server

**数据流**:
```
MySQL (customer_info + loan_application)
  → 构造 DecisionRequest (strategyId=FLOW_CREDIT_MAIN, variables={L0 vars}, externalData={L1 vars})
  → POST /api/v1/decision/execute
  → DecisionResponse (decisionResult, score, hitRules, traceId, durationMs)
  → 写入 ES (decision-log-{yyyy.mm} 索引)
  → 写入 CSV/JSON 文件 (供后续导入 Hive ODS)
```

**执行模式**:
- `--mode batch`: 遍历全部历史数据（1万客户 × 平均 2.7 笔贷款），串行/可控并发执行
- `--mode realtime`: 配合 `realtime_gen.py` 运行，消费实时事件流中的决策请求

### D4: 报表生成策略

**选择**: 基于 ES 决策日志索引 + 本地 JSON/CSV 日志文件双源查询，产出 Markdown + CSV 报表

**报表内容**:
1. 决策总览：总请求数、通过率、拒绝率、人工复核率、平均评分、P99 延迟
2. 规则命中统计：每条规则的命中次数和命中率（从 `hitRules` 字段聚合）
3. 评分分布：按分数段统计（<500 / 500-550 / 550-650 / 650-750 / >750）
4. 时间趋势：按小时/日粒度的请求量、通过率变化

## Risks / Trade-offs

- **[R1] decision-server 可能未启动或配置不完整** → Mitigation: 端到端执行器启动时先调用 `/api/v1/decision/health` 做健康检查，失败则终止并给出明确提示
- **[R2] 外部 API 适配器为 mock 模式，L1 变量返回固定值** → Mitigation: 种子数据中 L1 变量通过 `externalData` 字段直接注入到 DecisionRequest，跳过真实外部调用；同时在 `config.yaml` 中配置 mock 数据源
- **[R3] 批量执行 2.7 万次决策可能耗时较长（按 50ms/次估算约 22 分钟）** → Mitigation: 支持 `--sample N` 参数做抽样执行，支持 `--concurrency M` 参数并发执行（默认 4 并发，约 6 分钟完成）
- **[R4] ES 未配置索引模板导致决策日志写入失败** → Mitigation: 执行器内置索引创建逻辑（检查索引是否存在，不存在则自动创建 mapping）
- **[R5] 变量引用链断裂** — L3 变量依赖 L1/L2 变量，若依赖变量未定义则执行失败 → Mitigation: 种子数据生成时按拓扑序写入变量（L0 → L1 → L2 → L3），生成后做依赖完整性校验

## Open Questions

1. 模型平台 (model-platform) 的 ML 模型是否已部署？若已部署，可在 DAG 中加入 MODEL 节点做模型评分
2. 是否需要生成决策树 (Decision Tree) 类型？当前引擎代码已支持，但信贷场景下决策表通常更直观
3. Hive ODS 决策日志表的映射关系——当前 `ods.ods_decision_log` 字段定义在 `data-warehouse/hive/` 中，需确认与 DecisionResponse 字段对齐
