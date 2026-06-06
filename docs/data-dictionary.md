# 数据字典

## 概述

本项目数仓采用标准 4 层架构：ODS（原始层）→ DWD（明细层）→ DWS（汇总层）→ ADS（应用层），加上 HBase 实时存储、MySQL 业务库、Elasticsearch 搜索引擎。共计 24 张表。

## 数仓分层说明

| 层级 | 职责 | 存储引擎 | 表数 |
|------|------|---------|------|
| ODS | 原始数据同步，不做清洗 | Hive ORC + SNAPPY | 6 |
| DWD | 标准化清洗脱敏，关联维度 | Hive ORC + SNAPPY | 4 |
| DWS | 多时间窗口聚合统计 | Hive ORC + SNAPPY | 3 |
| ADS | 面向应用的宽表/指标 | Hive ORC + SNAPPY | 3 |
| HBase | 实时特征/历史/外部数据 | HBase SNAPPY | 3 |
| MySQL | 业务主数据 | InnoDB | 5 |

所有 Hive 表使用 ORC + SNAPPY 压缩，按 dt (yyyy-MM-dd) 字符串分区。

---

## ODS 层 (6 张表)

### ods_loan_application — 贷款申请原始表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键ID |
| application_no | STRING | 申请编号 |
| customer_id | STRING | 客户ID |
| product_id | STRING | 产品ID |
| channel | STRING | 申请渠道 |
| loan_amount | DECIMAL(18,2) | 申请贷款金额(元) |
| loan_term | INT | 贷款期限(月) |
| purpose | STRING | 贷款用途 |
| apply_time | TIMESTAMP | 申请时间 |
| status | STRING | 申请状态 |
| source_system | STRING | 来源系统标识 |
| etl_time | TIMESTAMP | ETL处理时间 |

### ods_customer_info — 客户信息原始表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键ID |
| customer_id | STRING | 客户ID |
| customer_name | STRING | 客户姓名 |
| id_card | STRING | 身份证号 |
| phone | STRING | 手机号码 |
| gender | STRING | 性别 |
| birth_date | STRING | 出生日期 |
| education | STRING | 学历 |
| marital_status | STRING | 婚姻状况 |
| address | STRING | 居住地址 |
| employer | STRING | 工作单位 |
| industry | STRING | 所属行业 |
| annual_income | DECIMAL(18,2) | 年收入(元) |
| source_system | STRING | 来源系统 |
| etl_time | TIMESTAMP | ETL处理时间 |

### ods_credit_report — 征信报告原始表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键ID |
| customer_id | STRING | 客户ID |
| report_no | STRING | 征信报告编号 |
| query_institution | STRING | 查询机构 |
| query_purpose | STRING | 查询用途 |
| query_time | TIMESTAMP | 查询时间 |
| loan_count | INT | 贷款笔数 |
| credit_card_count | INT | 信用卡数量 |
| overdue_count | INT | 逾期次数 |
| total_debt | DECIMAL(18,2) | 总负债金额(元) |
| latest_overdue_date | STRING | 最近逾期日期 |
| source_system | STRING | 来源系统 |
| etl_time | TIMESTAMP | ETL处理时间 |

### ods_repayment_record — 还款记录原始表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键ID |
| loan_id | STRING | 贷款ID |
| customer_id | STRING | 客户ID |
| installment_no | INT | 期次号 |
| due_date | DATE | 应还日期 |
| repay_date | DATE | 实还日期 |
| repay_amount | DECIMAL(18,2) | 还款金额(元) |
| principal | DECIMAL(18,2) | 还款本金(元) |
| interest | DECIMAL(18,2) | 还款利息(元) |
| overdue_days | INT | 逾期天数 |
| status | STRING | 还款状态 |
| source_system | STRING | 来源系统 |
| etl_time | TIMESTAMP | ETL处理时间 |

### ods_external_data — 外部数据原始表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键ID |
| customer_id | STRING | 客户ID |
| data_source | STRING | 数据来源(征信/工商/司法/运营商/税务/社保) |
| raw_content | STRING | 原始数据内容(JSON) |
| fetch_time | TIMESTAMP | 数据获取时间 |
| api_request_id | STRING | API请求ID |
| source_system | STRING | 来源系统 |
| etl_time | TIMESTAMP | ETL处理时间 |

### ods_decision_log — 决策日志原始表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键ID |
| trace_id | STRING | 追踪ID |
| customer_id | STRING | 客户ID |
| flow_id | STRING | 决策流ID |
| flow_version | INT | 决策流版本号 |
| decision_result | STRING | 决策结果(PASS/REJECT/MANUAL_REVIEW) |
| total_score | DECIMAL(10,2) | 综合评分 |
| duration_ms | BIGINT | 决策耗时(毫秒) |
| node_count | INT | 执行节点数 |
| hit_rules | STRING | 命中规则列表(JSON) |
| request_time | TIMESTAMP | 请求时间 |
| source_system | STRING | 来源系统 |
| etl_time | TIMESTAMP | ETL处理时间 |

---

## DWD 层 (4 张表)

### dwd_loan_application_detail — 贷款申请明细表

| 字段 | 类型 | 说明 |
|------|------|------|
| application_no | STRING | 申请编号 |
| customer_id | STRING | 客户ID |
| product_id | STRING | 产品ID |
| product_name | STRING | 产品名称 |
| channel_code | STRING | 渠道编码 |
| channel_name | STRING | 渠道名称 |
| loan_amount | DECIMAL(18,2) | 申请贷款金额(元) |
| loan_term | INT | 贷款期限(月) |
| purpose | STRING | 贷款用途 |
| apply_time | TIMESTAMP | 申请时间 |
| status | STRING | 申请状态 |
| customer_age | INT | 客户年龄 |
| customer_gender | STRING | 客户性别 |
| customer_education | STRING | 客户学历 |
| etl_time | TIMESTAMP | ETL处理时间 |

### dwd_customer_profile — 标准化客户画像表

| 字段 | 类型 | 说明 |
|------|------|------|
| customer_id | STRING | 客户ID |
| customer_name_masked | STRING | 客户姓名(脱敏: 张**) |
| id_card_masked | STRING | 身份证号(脱敏: 310***1234) |
| phone_masked | STRING | 手机号码(脱敏: 138****5678) |
| gender | STRING | 性别 |
| age | INT | 年龄 |
| education_code | STRING | 学历编码 |
| education_name | STRING | 学历名称 |
| marital_status | STRING | 婚姻状况 |
| province | STRING | 省份 |
| city | STRING | 城市 |
| industry_code | STRING | 行业编码 |
| industry_name | STRING | 行业名称 |
| annual_income | DECIMAL(18,2) | 年收入(元) |
| employer | STRING | 工作单位 |
| etl_time | TIMESTAMP | ETL处理时间 |

### dwd_credit_event — 信用事件明细表

| 字段 | 类型 | 说明 |
|------|------|------|
| event_id | STRING | 事件唯一ID |
| customer_id | STRING | 客户ID |
| event_type | STRING | 事件类型(CREDIT_QUERY/OVERDUE/APPLY/REPAY) |
| event_time | TIMESTAMP | 事件发生时间 |
| loan_id | STRING | 关联贷款ID |
| amount | DECIMAL(18,2) | 关联金额(元) |
| overdue_days | INT | 逾期天数(仅OVERDUE) |
| institution | STRING | 关联机构 |
| detail | STRING | 事件详情 |
| etl_time | TIMESTAMP | ETL处理时间 |

### dwd_transaction_detail — 交易明细表

| 字段 | 类型 | 说明 |
|------|------|------|
| transaction_id | STRING | 交易ID |
| customer_id | STRING | 客户ID |
| loan_id | STRING | 贷款ID |
| transaction_type | STRING | 交易类型(REPAYMENT/DISBURSEMENT/FEE) |
| amount | DECIMAL(18,2) | 交易金额(元) |
| principal | DECIMAL(18,2) | 本金金额(元) |
| interest | DECIMAL(18,2) | 利息金额(元) |
| transaction_time | TIMESTAMP | 交易时间 |
| channel | STRING | 交易渠道 |
| etl_time | TIMESTAMP | ETL处理时间 |

---

## DWS 层 (3 张表)

### dws_customer_credit_summary — 客户信用维度汇总表

| 字段 | 类型 | 说明 |
|------|------|------|
| customer_id | STRING | 客户ID |
| stat_month | STRING | 统计月份(yyyy-MM) |
| loan_count_1m | INT | 近1个月贷款笔数 |
| loan_count_3m | INT | 近3个月贷款笔数 |
| loan_count_6m | INT | 近6个月贷款笔数 |
| loan_count_12m | INT | 近12个月贷款笔数 |
| loan_amount_total_12m | DECIMAL(18,2) | 近12个月贷款总额(元) |
| credit_query_count_1m | INT | 近1个月征信查询次数 |
| credit_query_count_3m | INT | 近3个月征信查询次数 |
| overdue_count_1m | INT | 近1个月逾期次数 |
| overdue_count_3m | INT | 近3个月逾期次数 |
| overdue_count_6m | INT | 近6个月逾期次数 |
| overdue_count_12m | INT | 近12个月逾期次数 |
| max_overdue_days_12m | INT | 近12个月最大逾期天数 |
| apply_count_1m | INT | 近1个月申请次数 |
| apply_count_3m | INT | 近3个月申请次数 |
| repay_on_time_rate_12m | DECIMAL(5,4) | 近12个月按时还款率 |
| avg_loan_amount_12m | DECIMAL(18,2) | 近12个月平均贷款金额(元) |
| etl_time | TIMESTAMP | ETL处理时间 |

### dws_product_loan_summary — 产品维度汇总表

| 字段 | 类型 | 说明 |
|------|------|------|
| product_id | STRING | 产品ID |
| product_name | STRING | 产品名称 |
| stat_date | STRING | 统计日期(yyyy-MM-dd) |
| apply_count | INT | 申请笔数 |
| approve_count | INT | 通过笔数 |
| reject_count | INT | 拒绝笔数 |
| approve_rate | DECIMAL(5,4) | 通过率 |
| total_loan_amount | DECIMAL(18,2) | 贷款总金额(元) |
| avg_loan_amount | DECIMAL(18,2) | 平均贷款金额(元) |
| avg_loan_term | DECIMAL(10,2) | 平均贷款期限(月) |
| overdue_count | INT | 逾期笔数 |
| overdue_rate | DECIMAL(5,4) | 逾期率 |
| etl_time | TIMESTAMP | ETL处理时间 |

### dws_channel_summary — 渠道维度汇总表

| 字段 | 类型 | 说明 |
|------|------|------|
| channel_code | STRING | 渠道编码 |
| channel_name | STRING | 渠道名称 |
| stat_date | STRING | 统计日期(yyyy-MM-dd) |
| apply_count | INT | 申请笔数 |
| approve_count | INT | 通过笔数 |
| approve_rate | DECIMAL(5,4) | 通过率 |
| total_loan_amount | DECIMAL(18,2) | 贷款总金额(元) |
| avg_process_time_hours | DECIMAL(10,2) | 平均处理时长(小时) |
| customer_count | INT | 客户总数 |
| new_customer_count | INT | 新客户数 |
| etl_time | TIMESTAMP | ETL处理时间 |

---

## ADS 层 (3 张表)

### ads_credit_score_wide_table — 信用评分宽表

| 字段 | 类型 | 说明 |
|------|------|------|
| customer_id | STRING | 客户ID |
| stat_date | STRING | 统计日期 |
| age | INT | 年龄 |
| gender | STRING | 性别 |
| education | STRING | 学历 |
| annual_income | DECIMAL(18,2) | 年收入(元) |
| loan_count_12m | INT | 近12个月贷款笔数 |
| credit_query_count_3m | INT | 近3个月征信查询次数 |
| overdue_count_6m | INT | 近6个月逾期次数 |
| max_overdue_days_12m | INT | 近12个月最大逾期天数 |
| apply_count_1m | INT | 近1个月申请次数 |
| repay_on_time_rate | DECIMAL(5,4) | 按时还款率 |
| total_debt | DECIMAL(18,2) | 总负债金额(元) |
| credit_score | DECIMAL(10,2) | 信用评分(0~1000) |
| risk_level | STRING | 风险等级(LOW/MEDIUM/HIGH) |
| etl_time | TIMESTAMP | ETL处理时间 |

### ads_risk_indicator_summary — 风控指标汇总表

| 字段 | 类型 | 说明 |
|------|------|------|
| stat_date | STRING | 统计日期 |
| product_id | STRING | 产品ID |
| product_name | STRING | 产品名称 |
| total_apply_count | INT | 总申请笔数 |
| approve_count | INT | 通过笔数 |
| approve_rate | DECIMAL(5,4) | 通过率 |
| total_loan_amount | DECIMAL(18,2) | 贷款总金额(元) |
| overdue_30_count | INT | 逾期30天+笔数 |
| overdue_30_rate | DECIMAL(5,4) | 逾期30天+率 |
| overdue_90_count | INT | 逾期90天+笔数 |
| overdue_90_rate | DECIMAL(5,4) | 逾期90天+率 |
| avg_credit_score | DECIMAL(10,2) | 平均信用评分 |
| ks_value | DECIMAL(5,4) | KS值(模型区分度) |
| auc_value | DECIMAL(5,4) | AUC值(模型准确度) |
| etl_time | TIMESTAMP | ETL处理时间 |

### ads_decision_analysis — 决策分析主题表

| 字段 | 类型 | 说明 |
|------|------|------|
| stat_date | STRING | 统计日期 |
| flow_id | STRING | 决策流ID |
| flow_name | STRING | 决策流名称 |
| flow_version | INT | 决策流版本号 |
| decision_count | INT | 决策总次数 |
| pass_count | INT | 通过次数 |
| reject_count | INT | 拒绝次数 |
| manual_review_count | INT | 人工审核次数 |
| pass_rate | DECIMAL(5,4) | 通过率 |
| avg_score | DECIMAL(10,2) | 平均评分 |
| avg_duration_ms | BIGINT | 平均决策耗时(毫秒) |
| top_hit_rule_1 | STRING | 命中最多规则TOP1 |
| top_hit_rule_2 | STRING | 命中最多规则TOP2 |
| top_hit_rule_3 | STRING | 命中最多规则TOP3 |
| etl_time | TIMESTAMP | ETL处理时间 |

---

## HBase 表 (3 张)

### customer_feature — 客户实时特征

- **列族**: cf, **版本数**: 3, **压缩**: SNAPPY, **TTL**: 3年, **预分区**: 16
- **RowKey**: `reversed(customerId) + '_' + featureType + '_' + timestamp`
- **列**:
  - `cf:credit_score`
  - `cf:overdue_count_3m`
  - `cf:query_count_1m`
  - `cf:risk_level`
  - `cf:apply_count_6m`
  - `cf:avg_loan_amount`
  - `cf:device_fingerprint`
  - `cf:ip_address`

### customer_feature_history — 特征变更历史

- **列族**: cf, **版本数**: 10, **压缩**: SNAPPY, **TTL**: 3年, **预分区**: 16
- 同 customer_feature 结构，版本数 10 保留更长历史

### external_data — 外部数据

- **列族**: data + meta, **版本数**: 1, **压缩**: SNAPPY, **TTL**: 1年, **预分区**: 16
- **RowKey**: `dataSource + '_' + reversed(customerId) + '_' + timestamp`
- **data 列**:
  - `data:credit_report` (JSON)
  - `data:business_info` (JSON)
  - `data:judicial_info` (JSON)
  - `data:carrier_info` (JSON)
  - `data:tax_info` (JSON)
  - `data:social_security` (JSON)
- **meta 列**:
  - `meta:api_request_id`
  - `meta:fetch_time`
  - `meta:data_quality` (GOOD/FAIR/POOR)
  - `meta:source_system`
  - `meta:expire_time`

---

## MySQL 业务表 (5 张)

### rule_entity — 规则实体表

- **主键**: (type, id, version)
- **索引**: uk_type_id_version, idx_type_status, idx_created_by, idx_updated_at

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(64) | 规则实体ID |
| name | VARCHAR(128) | 规则名称 |
| type | VARCHAR(32) | 类型: RULE/SCORECARD/DECISION_TABLE/DECISION_TREE/FLOW/VARIABLE |
| version | INT | 版本号 |
| status | VARCHAR(32) | 状态: DRAFT/TESTING/PENDING_REVIEW/APPROVED/GRAYSCALE/RELEASED/ROLLED_BACK |
| content | MEDIUMTEXT | 规则JSON定义 |
| description | VARCHAR(512) | 描述 |
| created_by | VARCHAR(64) | 创建人 |
| updated_by | VARCHAR(64) | 修改人 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |
| attributes | JSON | 扩展属性 |

### sys_user — 系统用户表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT AUTO_INCREMENT | 用户ID |
| username | VARCHAR(64) | 登录用户名(UNIQUE) |
| password | VARCHAR(256) | BCrypt加密密码 |
| display_name | VARCHAR(128) | 显示名称 |
| email | VARCHAR(128) | 邮箱 |
| role | VARCHAR(32) | 角色: VIEWER/EDITOR/APPROVER/ADMIN |
| enabled | TINYINT(1) | 是否启用 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |
| last_login_at | DATETIME | 最后登录时间 |

### audit_log — 审计日志表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT AUTO_INCREMENT | 日志ID |
| operator | VARCHAR(64) | 操作人 |
| action | VARCHAR(32) | 操作类型: CREATE/UPDATE/DELETE/PUBLISH/APPROVE/REJECT/ROLLBACK/GRAYSCALE/LOGIN |
| target_type | VARCHAR(32) | 目标类型 |
| target_id | VARCHAR(64) | 目标ID |
| target_version | INT | 目标版本 |
| before_snapshot | MEDIUMTEXT | 变更前快照(JSON) |
| after_snapshot | MEDIUMTEXT | 变更后快照(JSON) |
| details | TEXT | 操作详情 |
| ip_address | VARCHAR(45) | 来源IP(兼容IPv6) |
| operated_at | DATETIME | 操作时间 |

### approval_record — 审批记录表

| 字段 | 类型 | 说明 |
|------|------|------|
| record_id | VARCHAR(64) | 审批记录ID |
| target_type | VARCHAR(32) | 关联规则类型 |
| target_id | VARCHAR(64) | 关联规则ID |
| target_version | INT | 关联版本号 |
| action | VARCHAR(16) | 操作: SUBMIT/APPROVE/REJECT/WITHDRAW |
| operator | VARCHAR(64) | 操作人 |
| comment | TEXT | 审批意见 |
| operated_at | DATETIME | 操作时间 |

### grayscale_config — 灰度发布配置表

| 字段 | 类型 | 说明 |
|------|------|------|
| config_id | VARCHAR(64) | 灰度配置ID |
| target_type | VARCHAR(32) | 关联规则类型 |
| target_id | VARCHAR(64) | 关联规则ID |
| target_version | INT | 关联版本号 |
| percentage | INT | 当前灰度百分比(0-100) |
| previous_percentage | INT | 上一次百分比 |
| operator | VARCHAR(64) | 操作人 |
| started_at | DATETIME | 灰度开始时间 |
| updated_at | DATETIME | 最后调整时间 |
| grayscale_status | VARCHAR(16) | 状态: NOT_STARTED/IN_PROGRESS/FULL/PAUSED/ROLLED_BACK |

---

## Elasticsearch 索引

### decision-log-{yyyy.MM} — 决策执行日志 (按月)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | keyword | 决策ID |
| traceId | keyword | 追踪ID |
| strategyId | keyword | 策略ID |
| channel | keyword | 渠道 |
| decisionResult | keyword | 结果(PASS/REJECT/MANUAL) |
| score | integer | 评分 |
| riskLevel | keyword | 风险等级 |
| rejectReason | text | 拒绝原因 |
| rejectCode | keyword | 拒绝代码 |
| inputSnapshot | object | 输入快照(JSON) |
| outputSnapshot | object | 输出快照(JSON) |
| trace | object | 追踪详情(JSON) |
| durationMs | long | 耗时(毫秒) |
| timestamp | long | 时间戳 |
| tags | keyword[] | 标签 |

---

## 表间关系图

```
ODS(6表) → DataX/Canal同步 → DWD(4表) → Spark ETL → DWS(3表) → Spark ETL → ADS(3表)
                                                                     ↓
HBase(3表) ← Flink实时写入 ← Kafka ← 业务系统
MySQL(5表) ← decision-admin CRUD
ES(按月索引) ← decision-server 写入
```
