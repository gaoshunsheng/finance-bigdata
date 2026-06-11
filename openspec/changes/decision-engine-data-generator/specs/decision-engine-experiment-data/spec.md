# 决策引擎实验与发布数据生成规格

## ADDED Requirements

### Requirement: AB 实验配置生成
系统 SHALL 生成至少 2 个 AB 实验配置：`EXP_CREDIT_STRATEGY`（信用策略实验，对比 V1 和 V2 策略）和 `EXP_PRICING_MODEL`（定价模型实验，对比固定利率和动态利率）。每个实验 SHALL 包含 experimentId、name、trafficKey、groups（含流量比例和对应 strategyId）、enabled 状态。

#### Scenario: 信用策略 AB 实验流量分割
- **WHEN** 实验 `EXP_CREDIT_STRATEGY` 启用，trafficKey=customer_id，control 组比例 0.5（strategyId=FLOW_CREDIT_MAIN），experiment 组比例 0.5（strategyId=FLOW_CREDIT_MAIN_V2）
- **THEN** 同一 customer_id 始终被哈希到同一组（一致性哈希），两组流量各约 50%

#### Scenario: 实验禁用时默认行为
- **WHEN** 实验 `EXP_PRICING_MODEL` 的 enabled=false
- **THEN** 所有流量走第一个 group（control 组）的策略

### Requirement: 灰度发布记录生成
系统 SHALL 在 `grayscale_config` 表中生成至少 3 条灰度发布记录，模拟 `RS_BLACKLIST` 规则集从 10% → 50% → 100% 的逐步上线过程。每条记录 SHALL 包含 config_id、target_type、target_id、target_version、percentage、previous_percentage、operator、started_at、grayscale_status 字段。

#### Scenario: 规则集灰度上线流程
- **WHEN** `RS_BLACKLIST` v1 经历三个阶段：10%（开始灰度）、50%（扩大灰度）、100%（全量）
- **THEN** `grayscale_config` 表中有对应 3 条记录，percentage 依次为 10、50、100，grayscale_status 从 GRAYSCALE 过渡到 RELEASED

#### Scenario: 灰度百分比合规性
- **WHEN** 灰度发布记录的 percentage 从 10 更新到 50
- **THEN** previous_percentage 字段记录上一次的 10，operator 记录操作人

### Requirement: 审批工作流记录生成
系统 SHALL 在 `approval_record` 表中生成至少 5 条审批记录，覆盖规则、评分卡、决策流的提交审批、审批通过、审批拒绝等操作。每条记录 SHALL 包含 record_id、target_type、target_id、target_version、action、operator、comment、operated_at。

#### Scenario: 规则审批通过
- **WHEN** 操作员 `reviewer_zhang` 对 `RS_BLACKLIST` v1 执行 APPROVE 操作
- **THEN** `approval_record` 表新增一条 action=APPROVE 的记录，包含审批意见 "黑名单规则集审核通过，准入规则合理"

#### Scenario: 规则审批拒绝并退回修改
- **WHEN** 操作员 `reviewer_li` 对 `RS_ELIGIBILITY` v1 执行 REJECT 操作
- **THEN** `approval_record` 表新增一条 action=REJECT 的记录，包含退回意见 "年龄上限建议放宽至65岁"，对应 `rule_entity` 状态回退到 DRAFT

### Requirement: 审计日志生成
系统 SHALL 在 `audit_log` 表中生成所有配置操作的审计日志。对 `rule_entity` 表的每次 INSERT 或 UPDATE 操作，SHALL 记录操作人、操作类型（CREATE/UPDATE/SUBMIT/APPROVE/REJECT/PUBLISH）、目标类型和 ID、操作前后快照 JSON、IP 地址、操作时间。

#### Scenario: 配置创建审计
- **WHEN** 系统通过 SQL INSERT 创建一条评分卡配置 `SC_CREDIT_A` v1
- **THEN** `audit_log` 表新增一条 action=CREATE, target_type=SCORECARD, target_id=SC_CREDIT_A 的记录，after_snapshot 包含完整 JSON

#### Scenario: 发布操作审计
- **WHEN** 操作员 `admin` 将 `FLOW_CREDIT_MAIN` 状态从 REVIEW 变更为 RELEASED
- **THEN** `audit_log` 表新增一条 action=PUBLISH, target_type=FLOW 的记录，before_snapshot 和 after_snapshot 分别记录状态变更前后的内容快照
