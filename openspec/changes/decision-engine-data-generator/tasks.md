## 1. 基础设施准备

- [x] 1.1 创建 `decision_models.py`，定义决策引擎配置数据 Pydantic 模型（RuleEntity, ConditionRule, RuleSet, ScorecardConfig, DecisionTableConfig, FlowDAGConfig, VariableDefinition, ExperimentConfig, GrayscaleRecord, AuditLogEntry, ApprovalRecord）
- [x] 1.2 在 `config.yaml` 中新增决策引擎场景参数段（场景开关、评分区间、利率基准、额度矩阵、客户分层阈值映射等）

## 2. 变量体系生成

- [x] 2.1 实现 L0 INPUT 变量生成（customer_id, loan_amount, loan_purpose, loan_term, product_type, application_id, channel）
- [x] 2.2 实现 L1 EXTERNAL 变量生成（credit_score, blacklist_flag, multi_loan_count, device_anomaly, ip_anomaly, phone_blacklist）
- [x] 2.3 实现 L2 CACHED 变量生成（overdue_count_6m, debt_ratio, income_monthly, work_years, credit_query_count, asset_amount）
- [x] 2.4 实现 L3 DERIVED 变量生成（risk_level, is_high_risk, total_credit_score, age_group, dti_category, credit_grade），含 Aviator 表达式和依赖声明
- [x] 2.5 实现变量依赖完整性校验：写入前验证所有依赖引用可解析

## 3. 规则与规则集生成

- [x] 3.1 实现 `RS_BLACKLIST` 反欺诈规则集生成（5+ 条规则：身份黑名单、通讯黑名单、多头借贷、设备指纹异常、IP 异常），hitPolicy=FIRST_HIT
- [x] 3.2 实现 `RS_ELIGIBILITY` 准入规则集生成（4+ 条规则：年龄、月收入、征信查询次数、负债率），hitPolicy=ALL
- [x] 3.3 实现规则集 JSON 内容序列化，写入 `rule_entity` 表（type=RULE, status=RELEASED）

## 4. 评分卡生成

- [x] 4.1 实现 `SC_CREDIT_A` 主信用评分卡生成（4 个特征：年龄、信用分、工作年限、月收入，各含 4-5 个分箱和评分权重），定义 cutoff 阈值
- [x] 4.2 实现 `SC_CREDIT_B` 行为评分卡生成（3 个特征：近6月逾期次数、还款率、账户活跃度）
- [x] 4.3 实现 `SC_CREDIT_C` 收入评分卡生成（3 个特征：月收入、负债率、资产规模）
- [x] 4.4 评分卡 JSON 序列化写入 `rule_entity` 表（type=SCORECARD, status=RELEASED）

## 5. 决策表生成

- [x] 5.1 实现 `DT_LOAN_AMOUNT` 额度决策表（列：信用等级 × 产品类型 × 收入水平，约 30+ 行含通配符），hitPolicy=FIRST_MATCH
- [x] 5.2 实现 `DT_PRICING` 定价决策表（列：信用等级 × 贷款期限 × 担保方式，约 20+ 行），hitPolicy=FIRST_MATCH
- [x] 5.3 决策表 JSON 序列化写入 `rule_entity` 表（type=DECISION_TABLE, status=RELEASED）

## 6. 决策树生成

- [x] 6.1 实现 `DTREE_CREDIT_GRADE` 信用等级决策树（根：信用评分 → 中间：逾期次数/负债率 → 叶子：A/B/C/D 等级）
- [x] 6.2 决策树 JSON 序列化写入 `rule_entity` 表（type=DECISION_TREE, status=RELEASED）

## 7. 决策流 DAG 生成

- [x] 7.1 实现 `FLOW_CREDIT_MAIN` 主决策流 DAG 定义（节点：DATA_PREP → RULE_SET(黑名单) → RULE_SET(准入) → SCORECARD(A) → SCORECARD(B) → SCORECARD(C) → DECISION_TABLE(额度) → DECISION_TABLE(定价) → ACTION(PASS/REJECT/REVIEW)），含条件边
- [x] 7.2 实现 `FLOW_CREDIT_MAIN_V2` 实验版决策流（与 V1 结构相同但评分卡权重不同或阈值不同）
- [x] 7.3 决策流 JSON 序列化写入 `rule_entity` 表（type=FLOW, status=RELEASED）

## 8. 实验与发布数据生成

- [x] 8.1 实现 `EXP_CREDIT_STRATEGY` AB 实验配置生成（control=FLOW_CREDIT_MAIN, experiment=FLOW_CREDIT_MAIN_V2, 50/50 分流）
- [x] 8.2 实现 `EXP_PRICING_MODEL` 定价实验配置生成
- [x] 8.3 实现灰度发布记录生成到 `grayscale_config` 表（至少 3 条，模拟 10→50→100% 上线过程）
- [x] 8.4 实现审批工作流记录生成到 `approval_record` 表（至少 5 条，覆盖 APPROVE/REJECT 操作）
- [x] 8.5 实现审计日志生成到 `audit_log` 表（每条配置操作的 CREATE/SUBMIT/APPROVE/PUBLISH 审计记录）
- [x] 8.6 实验配置 JSON 序列化写入 `rule_entity` 表（type=EXPERIMENT）

## 9. 端到端决策执行器

- [x] 9.1 实现前置健康检查（decision-server, MySQL rule_entity, ES, 业务数据表逐项检查）
- [x] 9.2 实现 `DecisionRequest` 构造器：从 MySQL 业务表读取客户+贷款数据 → 映射 L0 变量 + 模拟 L1 外部数据 → 生成 traceId → 构造完整 JSON
- [x] 9.3 实现批量执行引擎：支持 `--mode batch --sample N --concurrency M`，ThreadPoolExecutor 并发调用 decision-server
- [x] 9.4 实现错误处理与重试：指数退避（1s→2s→4s），最多 3 次重试，失败记录到 error log
- [x] 9.5 实现决策响应持久化：写入 ES `decision-log-{yyyy.mm}` 索引 + 本地 JSONL 文件 + 本地 CSV 汇总
- [x] 9.6 实现实时模式 (`--mode realtime`)：作为 `realtime_gen.py` 的协同消费端，实时事件触发决策

## 10. 决策日志报表生成

- [x] 10.1 实现决策总览统计（总请求、通过/拒绝/复核率、评分均值/中位数、P50/P95/P99 延迟）
- [x] 10.2 实现规则命中率统计（按规则维度聚合 hitRules，命中次数 + 命中率排名）
- [x] 10.3 实现评分分布统计（5 个分数段计数 + 与决策结果交叉分析）
- [x] 10.4 实现时间维度趋势统计（按小时 + 按日汇总）
- [x] 10.5 实现 Markdown 报告输出（总览表 + 规则 TOP10 + 评分分布表 + 趋势表）
- [x] 10.6 实现 CSV 数据导出（4 个 CSV 文件：总览、规则命中、评分分布、每日趋势）
- [x] 10.7 实现 `--auto-report` 参数联动：端到端执行完成后自动调用报表生成
- [x] 10.8 实现数据源回退机制：优先 ES 查询，ES 不可用时回退到本地 JSONL 文件

## 11. 集成与验证

- [x] 11.1 创建 `decision_seed.py` 主入口脚本，串联全部种子数据生成步骤（变量→规则→评分卡→决策表→决策树→决策流→实验→审批→审计），支持 `--dry-run` 仅校验不写入
- [x] 11.2 创建 `decision_runner.py` 主入口脚本，封装端到端执行器完整流程
- [x] 11.3 创建 `decision_report.py` 主入口脚本，封装报表生成完整流程
- [x] 11.4 端到端验收测试：按序执行 seed → runner → report，验证决策日志总数、通过率在合理范围（65-80%）、ES 索引有数据、报表文件内容完整
- [x] 11.5 更新 `docs/` 目录，添加决策数据生成使用文档（含命令示例和输出说明）
