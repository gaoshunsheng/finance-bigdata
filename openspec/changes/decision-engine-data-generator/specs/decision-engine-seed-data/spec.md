# 决策引擎种子数据生成规格

## ADDED Requirements

### Requirement: 黑名单与反欺诈规则集生成
系统 SHALL 生成一个名为 `RS_BLACKLIST` 的规则集，包含至少 5 条条件规则，覆盖身份黑名单、通讯黑名单、多头借贷标记、设备指纹异常、IP 地址异常等反欺诈场景。规则集使用 `FIRST_HIT` 策略，任一规则命中则整体拒绝。

#### Scenario: 身份黑名单命中
- **WHEN** 请求中 `blacklist_flag` 变量值为 `true`
- **THEN** 规则 `R_BLACKLIST_ID` 命中，返回 `REJECT`，拒绝原因 "身份命中黑名单"，错误码 `BL_001`

#### Scenario: 多头借贷标记命中
- **WHEN** 请求中 `multi_loan_count` 变量值 ≥ 5
- **THEN** 规则 `R_MULTI_LOAN` 命中，返回 `REJECT`，拒绝原因 "多头借贷风险过高"，错误码 `BL_003`

#### Scenario: 无黑名单命中
- **WHEN** 请求中所有反欺诈变量均正常（`blacklist_flag`=false, `multi_loan_count`<5, `device_anomaly`=false, `ip_anomaly`=false, `phone_blacklist`=false）
- **THEN** 规则集中无任何规则命中，决策流继续执行下一节点

### Requirement: 准入规则集生成
系统 SHALL 生成一个名为 `RS_ELIGIBILITY` 的规则集，包含至少 4 条条件规则，覆盖年龄、月收入、征信查询次数、负债率等基本准入条件。规则集使用 `ALL` 策略，全部通过才允许进入评分环节。

#### Scenario: 年龄不符合准入要求
- **WHEN** 请求中 `age` 变量值 < 22 或 > 60
- **THEN** 规则 `R_AGE_CHECK` 命中，返回 `REJECT`，拒绝原因 "年龄不符合准入要求 (22-60)"

#### Scenario: 月收入不满足最低要求
- **WHEN** 请求中 `income_monthly` 变量值 < 3000
- **THEN** 规则 `R_MIN_INCOME` 命中，返回 `REJECT`，拒绝原因 "月收入不满足最低要求"

#### Scenario: 征信查询次数超标
- **WHEN** 请求中 `credit_query_count` 变量值 > 6
- **THEN** 规则 `R_CREDIT_QUERY` 命中，返回 `REJECT`，拒绝原因 "近期征信查询次数过多"

#### Scenario: 全部准入条件通过
- **WHEN** 请求中所有准入变量均在阈值范围内（22≤age≤60, income_monthly≥3000, credit_query_count≤6, debt_ratio≤0.7）
- **THEN** 规则集中无任何规则命中，决策流继续执行至评分卡节点

### Requirement: 评分卡定义生成
系统 SHALL 生成三张评分卡：`SC_CREDIT_A`（主信用评分卡，含年龄、收入、征信等级、工作年限 4 个特征，总分映射到信用等级 A/B/C/D）、`SC_CREDIT_B`（行为评分卡，含逾期次数、还款率、账户活跃度 3 个特征）、`SC_CREDIT_C`（收入评分卡，含月收入、负债率、资产规模 3 个特征）。每张评分卡 SHALL 定义 `initialScore`、特征分箱 (`characteristics[].bins[]`) 和截止阈值 (`cutoff`)。

#### Scenario: 主评分卡计算
- **WHEN** 请求传入 `age`=35, `credit_score`=680, `work_years`=8, `income_monthly`=15000
- **THEN** `SC_CREDIT_A` 根据各特征分箱计算总分，返回评分结果（含总分、各特征得分明细、信用等级）

#### Scenario: 评分低于拒绝阈值
- **WHEN** `SC_CREDIT_A` 计算总分 < 500（reject 阈值）
- **THEN** 返回 `REJECT` 决策，附带评分明细

#### Scenario: 评分在人工复核区间
- **WHEN** `SC_CREDIT_A` 计算总分在 [500, 550) 区间（review 阈值）
- **THEN** 返回 `REVIEW` 决策，要求人工复核

### Requirement: 决策表定义生成
系统 SHALL 生成两张决策表：`DT_LOAN_AMOUNT`（额度决策表，列：客户等级 × 产品类型 × 收入水平，结果：额度区间和最大金额）和 `DT_PRICING`（定价决策表，列：信用等级 × 贷款期限 × 担保方式，结果：年化利率区间）。两张表均使用 `FIRST_MATCH` 命中策略。

#### Scenario: 额度决策——优质客户
- **WHEN** 客户等级=A, 产品类型=P001（消费贷）, 收入水平=HIGH
- **THEN** `DT_LOAN_AMOUNT` 返回 maxAmount=500000, amountRange="10万-50万"

#### Scenario: 额度决策——高风险客户
- **WHEN** 客户等级=D
- **THEN** `DT_LOAN_AMOUNT` 返回 action=REJECT，无论产品类型和收入水平为何（通配符 `*` 行）

#### Scenario: 定价决策——优质客户短期
- **WHEN** 信用等级=A, 贷款期限=SHORT（≤12月）, 担保方式=CREDIT（信用）
- **THEN** `DT_PRICING` 返回 rateRange="4.5%-8.0%", baseRate=6.0

### Requirement: 决策流 DAG 定义生成
系统 SHALL 生成名为 `FLOW_CREDIT_MAIN` 的决策流，包含至少 8 个节点和对应的条件边，串联反欺诈 → 准入检查 → 评分卡 → 额度决策 → 定价决策的完整信贷审批链路。流程 SHALL 支持条件分支（如黑名单命中直接 REJECT，评分过低直接 REJECT，评分中等人 REVIEW）。

#### Scenario: 完整主流程执行——自动通过
- **WHEN** 客户通过黑名单检查、准入检查，评分卡得分 680（≥550 PASS），额度决策返回 30 万，定价决策返回利率 6.5%
- **THEN** 决策流执行完全部节点，最终返回 `PASS`，附带决策详情（评分、额度、利率）

#### Scenario: 主流程执行——黑名单拒绝
- **WHEN** 客户命中黑名单规则 `R_BLACKLIST_ID`
- **THEN** 决策流在反欺诈节点后立即走 REJECT 分支，后续评分卡和额度定价节点不执行

#### Scenario: 主流程执行——评分不足人工复核
- **WHEN** 客户通过准入，主评分卡得分 520（在 REVIEW 区间）
- **THEN** 决策流返回 `REVIEW`，附带评分明细和触发的规则列表

### Requirement: 变量定义生成
系统 SHALL 生成覆盖 L0-L3 四层至少 20 个变量定义。L0 INPUT 变量至少 5 个（customer_id, loan_amount, loan_purpose, loan_term, product_type），L1 EXTERNAL 变量至少 5 个（credit_score, blacklist_flag, multi_loan_count, device_anomaly, ip_anomaly），L2 CACHED 变量至少 5 个（overdue_count_6m, debt_ratio, income_monthly, work_years, phone_blacklist），L3 DERIVED 变量至少 5 个（risk_level, is_high_risk, total_credit_score, age_group, dti_category）。每个变量 SHALL 包含 varId、name、layer、dataType、description 字段，L3 变量 SHALL 包含 expression 和 dependencies 字段。

#### Scenario: L3 派生变量解析
- **WHEN** 变量引擎解析 `risk_level`（expression: `total_credit_score >= 650 ? 'A' : total_credit_score >= 550 ? 'B' : total_credit_score >= 500 ? 'C' : 'D'`）
- **THEN** 先按拓扑序解析依赖变量 L0/L1/L2 → 再计算 L3 表达式 → 返回正确 risk_level 值

#### Scenario: 变量依赖完整性校验
- **WHEN** 所有变量定义写入完成后运行校验
- **THEN** 检查每个 L3 变量的 dependencies 中引用的 varId 是否存在于已定义的变量集合中，若缺失则报错

### Requirement: 决策树定义生成
系统 SHALL 生成名为 `DTREE_CREDIT_GRADE` 的决策树，以树状结构判定客户信用等级。根节点按信用评分分叉，中间节点按逾期次数、负债率等进一步分叉，叶子节点返回信用等级 A/B/C/D。

#### Scenario: 决策树判定
- **WHEN** 客户 credit_score≥650, overdue_count=0, debt_ratio=0.3
- **THEN** 决策树沿路径判定，返回信用等级 A
