# 决策引擎端到端执行器规格

## ADDED Requirements

### Requirement: 健康检查与前置校验
系统 SHALL 在执行决策请求之前完成以下前置检查：decision-server 健康检查 (`GET /api/v1/decision/health` 返回 200)、MySQL `rule_entity` 表有已发布的配置数据（至少 1 条 status=RELEASED 的记录）、Elasticsearch 集群可达、业务数据表 (`customer_info`, `loan_application`) 有可用数据。任一检查失败则终止执行并输出明确错误信息。

#### Scenario: 所有前置检查通过
- **WHEN** decision-server 健康接口返回 200，MySQL 有已发布规则，ES 可连接，业务数据表非空
- **THEN** 执行器进入决策执行阶段

#### Scenario: 决策引擎不可用
- **WHEN** decision-server 健康检查失败（连接超时或返回非 200）
- **THEN** 执行器终止，输出 "决策引擎不可用: <错误详情>"

#### Scenario: 无已发布配置
- **WHEN** MySQL `rule_entity` 表中没有任何 status=RELEASED 的记录
- **THEN** 执行器终止，输出 "无已发布的决策配置，请先运行 decision_seed.py 生成种子数据"

### Requirement: 批量决策执行
系统 SHALL 支持从 MySQL 业务数据表读取客户和贷款数据，逐条构造 `DecisionRequest` 并调用 `POST /api/v1/decision/execute`，收集 `DecisionResponse`。支持参数 `--sample N`（随机抽样 N 条）和 `--concurrency M`（并发度，默认 4）。

#### Scenario: 全量批量执行
- **WHEN** 执行 `--mode batch` 不带 sample 参数
- **THEN** 遍历全部 `loan_application` 记录（约 27,000 条），逐条构造决策请求，收集响应，输出进度条和最终统计

#### Scenario: 抽样执行
- **WHEN** 执行 `--mode batch --sample 1000`
- **THEN** 从 `loan_application` 表随机抽取 1000 条记录执行决策，其余数据跳过

#### Scenario: 并发执行性能
- **WHEN** 执行 `--mode batch --concurrency 8`
- **THEN** 使用 Python ThreadPoolExecutor（最多 8 个线程）并发调用决策引擎，总耗时 ≤ 单线程耗时的 1/3

### Requirement: 决策请求构造规范
系统 SHALL 为每条贷款申请构造符合 decision-server 接口规范的 `DecisionRequest` JSON。请求 SHALL 包含 `strategyId`（对应 `FLOW_CREDIT_MAIN`）、`variables`（L0 INPUT 变量：customer_id, loan_amount, loan_purpose, loan_term, product_type, application_id）、`externalData`（L1 EXTERNAL 变量：credit_score, blacklist_flag, multi_loan_count 等，从业务数据 + 随机合理值组合）、`traceId`（唯一追踪 ID）。

#### Scenario: 构造标准决策请求
- **WHEN** 读取 loan_application id="LOAN_00001"（customer_id="CUST_00001", amount=200000, purpose=CONSUMPTION, term=12, product_type=P001）和对应 customer_info（age=35, income=18000）
- **THEN** 构造 DecisionRequest 包含 strategyId="FLOW_CREDIT_MAIN"、variables 含 L0 字段、externalData 含模拟 L1 字段（credit_score 基于客户等级映射、blacklist_flag 基于客户类型等）

#### Scenario: traceId 唯一性
- **WHEN** 生成 N 条决策请求
- **THEN** 每条请求的 traceId 全局唯一（格式：GEN-{uuid}）

### Requirement: 决策响应收集与存储
系统 SHALL 收集每条决策请求的 `DecisionResponse`（含 decisionResult, score, hitRules[], traceId, durationMs, rejectReason 等字段），将响应写入两个目标：(1) Elasticsearch `decision-log-{yyyy.mm}` 索引（实时查询用），(2) 本地 JSONL 文件和 CSV 汇总文件（供后续导入 Hive ODS 和报表分析）。

#### Scenario: ES 索引写入
- **WHEN** 决策响应返回，decisionResult=PASS, score=680
- **THEN** 将完整 DecisionLogDocument JSON 写入 ES 索引 `decision-log-2026.06`，包含 requestSnapshot 和 responseSnapshot

#### Scenario: 本地文件持久化
- **WHEN** 批量执行完成
- **THEN** `output/decision_logs_<timestamp>.jsonl` 包含全部决策日志（每行一条 JSON），`output/decision_summary_<timestamp>.csv` 包含汇总字段（application_id, customer_id, decision_result, score, risk_level, duration_ms, hit_rules_count, timestamp）

### Requirement: 实时模式决策执行
系统 SHALL 支持 `--mode realtime` 模式，作为 `realtime_gen.py` 的消费者或协程配合运行。实时生成器产出事件时，执行器从中提取需要决策的事件类型（目前为 `application_event`），同步调用决策引擎并记录结果。

#### Scenario: 实时事件触发决策
- **WHEN** `realtime_gen.py` 生成一条 application_event（新贷款申请）
- **THEN** `--mode realtime` 执行器捕获该事件，提取 customer_id 和 loan_amount，构造 DecisionRequest 调用决策引擎，将决策日志写到 ES 和本地文件

### Requirement: 错误处理与重试
系统 SHALL 对决策引擎调用失败（网络错误、5xx 响应、超时）进行重试。每次重试间隔指数退避（1s → 2s → 4s），最多重试 3 次。3 次仍失败则记录错误日志并跳过该条，继续执行下一条。

#### Scenario: 临时网络错误恢复
- **WHEN** 某次决策请求因连接超时失败
- **THEN** 等待 1 秒后重试，若成功则记录正常日志；若 3 次均失败则记录到 `output/decision_errors_<timestamp>.jsonl`

#### Scenario: 全部重试失败
- **WHEN** 某条请求 3 次重试均失败
- **THEN** 在错误日志中记录该条请求的完整信息（application_id, customer_id, 错误原因, 时间戳），继续处理下一条，不中断整体执行
