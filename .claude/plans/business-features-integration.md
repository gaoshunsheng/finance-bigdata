# 执行计划：补全业务功能 + 同步任务清单 + 端到端联调

## 目标
1. 补全业务功能 — 外部适配器框架、企业画像真实查询、模型持久化
2. 同步任务清单 — tasks.md 与实际代码对齐
3. 端到端联调 — 全栈 22 服务完整链路验证

---

## Phase 1: 同步任务清单 (预估 30min)

### Task 1.1: 同步 tasks.md
- 读取 tasks.md 全部 120 项
- 根据代码库实际状态，将已完成项标记为 `[x]`
- 保留真正未完成项为 `[ ]`
- 提交变更

## Phase 2: 补全业务功能 (预估 3-4h)

### Task 2.1: 外部 API 通用适配器框架
**文件**: `decision-server/src/main/java/com/credit/platform/server/adapter/`
- 创建 `ExternalApiAdapter` 接口：超时3s、重试1次、降级到缓存、Mock模式
- 创建 `AbstractExternalApiAdapter` 基类：OkHttpClient + 重试 + 降级 + 指标采集
- 创建 `ExternalApiConfig` 配置类
- 创建 `AdapterRegistry` 注册中心
- 创建 `ExternalApiProvider` 实现 VariableProvider (L1)，使用 CompletableFuture.allOf 并行调用

### Task 2.2: 实现 Mock 外部 API 适配器 (6个)
**文件**: `decision-server/src/main/java/com/credit/platform/server/adapter/impl/`
- `CreditBureauMockAdapter` — 征信报告 (信用分、逾期记录、查询记录)
- `BusinessRegistrationMockAdapter` — 工商数据 (企业注册、经营范围、法人)
- `JudicialMockAdapter` — 司法数据 (诉讼记录、执行信息)
- `TelecomMockAdapter` — 运营商数据 (手机实名、在网时长)
- `TaxSocialMockAdapter` — 税务/社保数据 (纳税记录、社保缴纳)
- `BlacklistMockAdapter` — 黑名单/舆情数据

### Task 2.3: 修复企业画像服务占位数据
**文件**: `data-platform/data-service/src/main/java/.../service/EnterpriseProfileService.java`
- 替换 `relatedPersons` 占位数据 → 从 HBase enterprise_profile 表查询关联人员
- 替换 `riskSignals` 占位数据 → 从 ES decision-log 聚合风险信号
- 替换 `dataSourceStatus` 占位数据 → 动态检测实际数据源可用性
- 保留 fallback 方法但标记为降级逻辑

### Task 2.4: 模型平台持久化存储
**文件**: `model-platform/app/`
- 创建 SQLAlchemy ORM 模型 (ModelRecord, DatasetRecord, EvaluationReport, MonitoringData)
- 实现 `ModelRepository` — MySQL 持久化层
- 修改 `ModelTrainer` — 训练完成后写入 DB
- 修改 `SampleManager` — 数据集元数据写入 DB
- 修改 `InferenceService` — 启动时从 DB 加载模型索引
- 添加 Alembic 迁移脚本

### Task 2.5: 补齐 Admin CRUD Service 缺失
**文件**: `decision-admin/src/main/java/.../service/`
- 创建 `ScorecardAdminService` (CRUD + 发布)
- 创建 `DecisionTableAdminService` (CRUD + 发布)
- 创建 `FlowAdminService` (CRUD + 发布)
- 创建 `VariableAdminService` (CRUD)
- 创建 `ExperimentAdminService` (CRUD)
- 创建 `DecisionLogController` (从 ES 查询)
- 创建 `AnalyticsController` (趋势聚合)

### Task 2.6: 补齐测试覆盖率缺口
- DAG 引擎测试: 8 → 20 (缺 12)
- DecisionTable 测试: 9 → 10 (缺 1)
- RuleExecutor 测试: 14 → 15 (缺 1)
- VariableEngine 测试: 14 → 15 (缺 1)
- 添加 3 个缺失的 Aviator 自定义函数 (isInProvince, overdueCount, creditQueryCount)

## Phase 3: 端到端联调 (预估 1-2h)

### Task 3.1: 启动全栈服务
- docker-compose up 全部 22 服务
- 验证每个服务健康检查通过

### Task 3.2: 数据管道联调
- DataX MySQL → HDFS ODS (已验证)
- Spark ETL ODS → DWD → DWS → ADS (已验证)
- Canal CDC → Kafka → Flink 实时计算

### Task 3.3: 决策引擎联调
- decision-admin: 创建规则 → 发布 → 审批
- decision-server: 调用决策 API → 验证返回
- 外部适配器 Mock 调用链路

### Task 3.4: 前端联调
- decision-ui: 访问管理后台
- 创建/编辑规则 → 可视化编辑器
- Dashboard 数据展示

---

## 执行顺序
Phase 1 → Phase 2 (2.1→2.2→2.5→2.3→2.4→2.6) → Phase 3
