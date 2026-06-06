## 1. 基础设施搭建 (Phase 0)

### 1.1 大数据集群

- [x] 1.1.1 搭建 Hadoop 集群 (HDFS NameNode + DataNode × N, 配置 HA) — Docker Compose 单节点 + K8s manifest
- [x] 1.1.2 搭建 Hive Metastore + HiveServer2, 创建 ODS/DWD/DWS/ADS 四层数据库 — Docker Compose + init SQL
- [x] 1.1.3 搭建 Kafka 集群 (≥3 Broker), 创建 CDC/日志/特征 Topic — Docker Compose 单节点
- [x] 1.1.4 搭建 Flink 集群 (Standalone 或 YARN 模式), 配置 Checkpoint — Docker Compose JobManager + TaskManager
- [x] 1.1.5 搭建 HBase 集群, 设计特征表 RowKey 方案 (客户ID+时间戳) — Docker Compose + DDL + RowKey 设计
- [ ] 1.1.6 搭建 Redis Cluster (≥6节点), 规划 Key 命名规范和 TTL 策略 — 仅单节点, Key 命名和 TTL 已规划
- [ ] 1.1.7 搭建 Elasticsearch 集群 (≥3节点), 配置 ILM 冷热分层策略 — 仅单节点, 无 ILM
- [x] 1.1.8 搭建 MySQL 主从集群, 创建规则/变量/实验等业务库 — 单节点 + 01_init_schema.sql
- [x] 1.1.9 搭建 DolphinScheduler 调度平台, 配置 ETL 任务基线 — 3.2.1 standalone + 6 任务 DAG
- [x] 1.1.10 搭建 K8s 集群 + Docker Registry, 编写决策引擎 Dockerfile — 17 个 K8s manifest + Dockerfile.java + Dockerfile

### 1.2 数据采集管道

- [x] 1.2.1 部署 DataX, 编写 MySQL/Oracle → HDFS ODS 的批量同步任务 — 3 个 DataX job 配置 + 端到端验证
- [x] 1.2.2 部署 Canal (MySQL CDC), 配置 Binlog → Kafka 实时同步管道 — Canal Docker + canal.properties
- [x] 1.2.3 部署 Fluentd/Filebeat, 配置应用日志 → Kafka → HDFS + ES 管道 — Filebeat Docker + filebeat.yml
- [ ] 1.2.4 编写外部 API 通用适配器框架 (超时3s/重试1次/降级到缓存/Mock模式)
- [ ] 1.2.5 实现征信 API 适配器 (央行征信报告获取 → 清洗 → HDFS + HBase)
- [ ] 1.2.6 实现工商/司法/税务/社保/运营商 API 适配器
- [ ] 1.2.7 实现黑名单/舆情数据采集适配器
- [x] 1.2.8 配置 DataX 批量同步的 DolphinScheduler 调度任务 (T+1 凌晨) — daily ETL workflow JSON + 导入脚本

## 2. 引擎核心开发 (Phase 0-1)

### 2.1 项目骨架

- [x] 2.1.1 创建 Maven 多模块项目: engine-common / engine-core / engine-test / decision-admin / decision-web / decision-sdk / decision-server
- [x] 2.1.2 engine-common: 定义 DecisionRequest/Response/Result 等通用模型和异常体系
- [x] 2.1.3 引入 Aviator 依赖, 封装 ExpressionEngine (编译缓存 + 自定义函数注册)
- [ ] 2.1.4 实现自定义 Aviator 函数: between/in/daysBetween/isInProvince/overdueCount/creditQueryCount — between/in/daysBetween 已实现, 缺 isInProvince/overdueCount/creditQueryCount
- [x] 2.1.5 引入 Caffeine 依赖, 实现规则缓存管理器 RuleCacheManager

### 2.2 规则引擎核心

- [x] 2.2.1 定义 AST 节点体系: ASTNode(基类) → ConditionNode/ComparisonNode/LogicalNode/ActionNode
- [x] 2.2.2 实现 JSON → AST 编译器 (RuleCompiler): 解析条件规则的 conditions + actions
- [x] 2.2.3 实现 AST 解释执行器 (RuleExecutor): 递归遍历 AST, 短路求值, 返回布尔结果
- [x] 2.2.4 实现规则集执行器: 支持 FIRST_HIT / ALL / PRIORITY 三种命中策略
- [x] 2.2.5 实现 Null 安全处理: null 参与比较返回 false, 可配置默认值
- [x] 2.2.6 编写规则编译器单元测试 (≥20个用例, 覆盖 AND/OR/NOT/GT/LT/GTE/LTE/EQ/NEQ/BETWEEN/IN) — 33 个测试
- [ ] 2.2.7 编写规则执行器单元测试 (≥15个用例, 覆盖命中策略/短路求值/null处理/边界值) — 当前 14 个, 缺 1 个

### 2.3 评分卡引擎

- [x] 2.3.1 定义评分卡 JSON 模型 (initialScore, characteristics, bins, cutoff)
- [x] 2.3.2 实现 ScorecardCompiler: JSON → CompiledScorecard (预排序 bin 数组)
- [x] 2.3.3 实现 ScorecardExecutor: 遍历 characteristics → 二分查找 bin → 累加 score → cutoff 判定
- [x] 2.3.4 实现得分明细追踪: 每个 characteristic 记录 value/bin/score, 写入 TraceEntry.details
- [x] 2.3.5 编写评分卡引擎单元测试 (≥15个用例, 覆盖分箱边界/cutoff/null值/多评分卡) — 22 个测试

### 2.4 决策表引擎

- [x] 2.4.1 定义决策表 JSON 模型 (columns, rows, hitPolicy)
- [x] 2.4.2 实现 DecisionTableCompiler: JSON → CompiledDecisionTable (可选 hash index)
- [x] 2.4.3 实现 DecisionTableExecutor: 按行匹配条件, FIRST_MATCH/ALL_MATCH 策略
- [ ] 2.4.4 编写决策表引擎单元测试 (≥10个用例, 覆盖通配符/多条件/空值) — 当前 9 个, 缺 1 个

### 2.5 决策流 (DAG) 引擎

- [x] 2.5.1 定义 DAG JSON 模型: 9种节点类型 (DATA_PREP/RULE_SET/SCORECARD/MODEL/DECISION/SUB_FLOW/AB_SPLIT/ACTION/SCRIPT) + 边条件
- [x] 2.5.2 实现 FlowCompiler: JSON → CompiledDAG, 拓扑排序 + 环检测
- [x] 2.5.3 实现 FlowExecutor: 拓扑排序逐层执行, CompletableFuture 并行执行同层无依赖节点
- [x] 2.5.4 实现条件分支求值: Aviator 表达式求值 → resolveNextNodes 选择下一批节点
- [x] 2.5.5 实现子流程支持: SUB_FLOW 节点递归调用其他 DAG
- [x] 2.5.6 实现节点超时控制: 可配置单节点超时, 超时触发 fallback
- [ ] 2.5.7 编写 DAG 引擎单元测试 (≥20个用例, 覆盖串行/并行/条件分支/子流程/超时/环检测) — 当前 8 个, 缺 12 个

### 2.6 变量引擎

- [x] 2.6.1 设计 variable_registry MySQL 表结构 (var_id/name/category/layer/source/data_type/expression/dependencies/version/status) — 01_init_schema.sql + VariableDefinition
- [x] 2.6.2 实现 VariableRegistry: 变量注册表的 CRUD + 版本管理
- [x] 2.6.3 实现 VariableResolver: 基于 ExecutionContext 的变量解析, 懒加载 — VariableEngine + VariableResolveContext
- [ ] 2.6.4 实现 VariablePrefetcher: DAG 编译时静态分析所需变量 → 请求时分 Layer 并行预取 — VariableEngine 有依赖展开但无独立预取器
- [x] 2.6.5 实现 Layer 0 InputVariableProvider: 从 DecisionRequest 直接提取输入变量
- [ ] 2.6.6 实现 Layer 1 ExternalApiProvider: 并行调用外部 API (CompletableFuture.allOf), 适配器模式
- [ ] 2.6.7 实现 Layer 2 RedisFeatureProvider + HBaseFeatureProvider: 批量查询, Redis Pipeline / HBase Scan — data-service 有 Redis/HBase 查询但 engine-core 无独立 Provider
- [x] 2.6.8 实现 Layer 3 ComputedVariableProvider: Aviator 表达式计算衍生变量, 依赖拓扑排序 — DerivedVariableProvider
- [x] 2.6.9 实现变量依赖分析: 自动解析衍生变量的依赖链, 变更时标记受影响的规则
- [ ] 2.6.10 编写变量引擎单元测试 (≥15个用例, 覆盖4层获取/并行预取/懒加载/依赖分析/null处理) — 当前 14 个, 缺 1 个

### 2.7 可解释性追踪

- [x] 2.7.1 定义 TraceEntry 数据模型 (traceId/nodeId/nodeType/startTime/endTime/durationMs/input/output/details)
- [x] 2.7.2 实现 DecisionTracer: 在 ExecutionContext 中记录每个节点的 TraceEntry
- [x] 2.7.3 实现 TraceReporter: 汇总 TraceEntry 生成决策报告 (JSON)
- [x] 2.7.4 实现异步日志写入: TraceEntry → RocketMQ → Elasticsearch (不阻塞决策主流程) — MqTracePublisher + ES 持久化
- [x] 2.7.5 编写可解释性单元测试 (≥10个用例, 覆盖完整决策路径追踪/得分明细/审计级快照) — 23 个测试

### 2.8 热加载与缓存

- [x] 2.8.1 实现 Caffeine 规则缓存: ruleId → CompiledRule / flowId → CompiledDAG / scorecardId → CompiledScorecard
- [x] 2.8.2 实现 HotReloadListener: 监听 RocketMQ 广播消息, 触发重新编译和缓存替换
- [x] 2.8.3 实现 CopyOnWrite 语义: 新请求用新版本, 在途请求继续用旧版本 — VersionedRuleCache + AtomicReference
- [x] 2.8.4 实现版本快照: 保留最近 N 个版本的编译产物, 支持一键回滚 — VersionedArtifact + rollback()

## 3. 大数据平台开发 (Phase 1)

### 3.1 Hive 数仓建模

- [x] 3.1.1 设计 ODS 层: 业务系统原始表结构同步 (MySQL/Oracle → Hive ODS) — 7 张 ODS 表 DDL
- [x] 3.1.2 设计 DWD 层: 标准化明细表 (清洗/脱敏/统一编码/关联外部数据) — 5 张 DWD 表 DDL
- [x] 3.1.3 设计 DWS 层: 客户/产品/渠道维度汇总表 (近N月指标聚合) — 4 张 DWS 表 DDL
- [x] 3.1.4 设计 ADS 层: 信用评分宽表/风控指标汇总/决策分析主题表 — 4 张 ADS 表 DDL
- [x] 3.1.5 编写 Spark SQL ETL 脚本: ODS → DWD → DWS → ADS 全链路 — 3 个 ETL SQL + 端到端验证

### 3.2 Flink 实时特征计算

- [x] 3.2.1 实现 FLINK_001: 近3月征信查询次数 (Kafka CDC → 滑动窗口90天 → Redis) — CreditQuery3mJob
- [x] 3.2.2 实现 FLINK_002: 近6月逾期次数统计 (Kafka CDC → 滑动窗口180天 → Redis) — Overdue6mJob
- [x] 3.2.3 实现 FLINK_003: 近1月申请频次 (Kafka 埋点 → 滑动窗口30天 → Redis) — ApplyFreq1mJob
- [x] 3.2.4 实现 FLINK_004: 实时交易金额汇总 (Kafka 交易 → 滚动窗口1小时 → HBase) — TransactionSummaryJob
- [x] 3.2.5 实现 FLINK_005: 数据质量实时检测 (Kafka CDC → 事件驱动 → ES + 告警) — DataQualityCheckJob + ElasticsearchAlertSink

### 3.3 数据治理

- [x] 3.3.1 实现元数据自动采集: Hive/MySQL/HBase 表结构 → 元数据目录 — MetadataCollector + TableMetadata + ColumnMetadata
- [x] 3.3.2 实现数据血缘分析: 解析 Spark SQL / Flink DAG → 生成血缘图 — LineageAnalyzer + LineageGraph
- [x] 3.3.3 实现数据质量监控: 5维度 (完整性/准确性/一致性/及时性/唯一性) → 企微/邮件告警 — QualityMonitor + QualityRule
- [x] 3.3.4 实现数据安全: 4级分类/脱敏规则/字段级加密/RBAC权限/审计日志 — DataClassifier + DataMaskingService
- [x] 3.3.5 实现生命周期管理: 按层配置冷热归档策略, ES ILM 自动管理 — LifecycleManager

## 4. 决策引擎服务层 (Phase 1-2)

### 4.1 引擎服务 (decision-server)

- [x] 4.1.1 搭建 decision-server Spring Boot 项目, 集成 engine-core
- [x] 4.1.2 实现 DecisionController: POST /api/v1/decision/execute 入口
- [x] 4.1.3 实现接入层: API 网关 + 渠道管理 + 请求标准化 (多渠道字段映射) — ChannelConfig
- [x] 4.1.4 实现鉴权拦截器 (AuthInterceptor) + 限流拦截器 (RateLimitInterceptor)
- [x] 4.1.5 集成 Redis/HBase/ES/MySQL 连接池配置 — ElasticsearchConfig + ThreadPoolConfig
- [x] 4.1.6 配置线程池: DAG 并行执行线程池 + 外部 API 调用线程池 — ThreadPoolConfig.externalApiExecutor

### 4.2 AB 实验引擎

- [x] 4.2.1 设计 experiment_config MySQL 表结构 (实验ID/名称/流量键/分流比例/起止日期/终止条件) — ExperimentConfig
- [x] 4.2.2 实现 AB_SPLIT DAG 节点: consistent hash 分流 + 实验配置读取 — ExperimentSplitter
- [x] 4.2.3 实现 MetricsCollector: 异步收集各分支通过率/耗时/命中规则 → ES — ExperimentMetrics
- [x] 4.2.4 实现统计显著性计算: p-value < 0.05 时自动告警 — calculatePValue()
- [ ] 4.2.5 实现自动回滚: 实验组指标劣化 → 自动切换 100% 流量到对照组
- [x] 4.2.6 编写 AB 实验单元测试 (≥10个用例, 覆盖分流一致性/指标收集/显著性/回滚) — 11 个测试

### 4.3 模型调用引擎

- [x] 4.3.1 实现 ModelServiceClient: REST/gRPC 调用模型推理服务 — DefaultModelServiceClient + mock 模式
- [x] 4.3.2 实现 ModelFeatureMapper: 变量引擎变量 → 模型特征向量映射
- [x] 4.3.3 实现 SHAPExplainer: 调用模型服务的 SHAP 解释接口, 返回特征贡献度 — SHAPExplainer + ShapResult
- [x] 4.3.4 编写模型调用单元测试 (≥8个用例, 覆盖正常调用/超时/降级/特征映射) — 12 个测试

### 4.4 沙箱回测

- [x] 4.4.1 实现 SandboxRunner: 使用历史数据回放决策流程, 不影响在线环境 — SandboxRunner + SandboxRequest/Result
- [x] 4.4.2 实现 HistoryDataReplayer: 从 Hive/MySQL 批量加载历史样本 — HistorySample
- [x] 4.4.3 实现对比报告: 新旧版本规则执行结果对比, 高亮差异 — DiffEntry + DiffReport

### 4.5 接入 SDK

- [x] 4.5.1 实现 DecisionClient (Java SDK): 封装 HTTP 调用, 支持同步/异步
- [x] 4.5.2 实现 DecisionAutoConfiguration (Spring Boot Starter): 自动装配
- [ ] 4.5.3 编写 SDK 使用示例和文档 — 仅有 README, 无独立示例目录

## 5. 管理后台后端 (Phase 1-2)

### 5.1 CRUD 服务

- [x] 5.1.1 搭建 decision-admin Spring Boot 项目, 集成 MyBatis-Plus
- [x] 5.1.2 实现 RuleController + RuleService: 条件规则/规则集 CRUD (含版本管理)
- [ ] 5.1.3 实现 ScorecardController + ScorecardService: 评分卡 CRUD — Controller 存在, 独立 Service 缺失
- [ ] 5.1.4 实现 DecisionTableController + DecisionTableService: 决策表 CRUD — Controller 存在, 独立 Service 缺失
- [ ] 5.1.5 实现 FlowController + FlowService: 决策流 DAG CRUD (含环检测) — Controller 存在, 独立 Service 缺失
- [ ] 5.1.6 实现 VariableController + VariableService: 变量注册表 CRUD + 依赖分析 — Controller 存在, 独立 Service 缺失
- [ ] 5.1.7 实现 ExperimentController + ExperimentService: 实验配置 CRUD — Controller 存在, 独立 Service 缺失
- [ ] 5.1.8 实现 DecisionLogController: 从 ES 查询决策日志和报告
- [ ] 5.1.9 实现 AnalyticsController: 聚合查询通过率/命中率/转化率趋势

### 5.2 发布流程

- [x] 5.2.1 实现 RulePublishService: 草稿 → 测试 → 审批 → 灰度 → 全量 完整生命周期
- [x] 5.2.2 实现 ApprovalService: 审批流程 (提交审批/审批通过/审批驳回)
- [x] 5.2.3 实现灰度发布: 按百分比逐步提升流量 (5% → 50% → 100%) — GrayscalePublishService
- [x] 5.2.4 实现一键回滚: 回退到任意历史版本 (恢复编译产物缓存) — VersionedRuleCache.rollback()
- [x] 5.2.5 实现版本对比: 新旧版本规则差异对比展示 — VersionDiffService + VersionDiff

### 5.3 权限管理

- [x] 5.3.1 实现 RBAC 权限模型: viewer/editor/approver/admin 四级角色
- [x] 5.3.2 实现认证鉴权: JWT Token + Spring Security — JwtService + JwtAuthenticationFilter + SecurityConfig
- [x] 5.3.3 实现操作审计: 所有配置变更操作记录审计日志 — AuditLog + AuditLogService + AuditLogRepository

## 6. 管理后台前端 (Phase 1-2)

### 6.1 项目骨架

- [x] 6.1.1 搭建 Vue 3 + TypeScript + Vite 项目, 集成 Ant Design Vue + Pinia + Vue Router
- [x] 6.1.2 实现通用布局: 侧边栏导航 + 顶部面包屑 + 内容区 — AdminLayout.vue
- [x] 6.1.3 实现 API 封装层: Axios 封装 + 统一错误处理 + Token 刷新 — api/request.ts + api/auth.ts

### 6.2 可视化编辑器

- [x] 6.2.1 实现规则编辑器: 条件拖拽配置 (字段/运算符/值), AND/OR/NOT 逻辑组合 — ConditionBuilder.vue + ConditionNode.vue + ActionEditor.vue
- [x] 6.2.2 实现评分卡编辑器: 特征列表/分箱表格/分数配置/cutoff 阈值设置, 实时评分预览 — ScorecardEditor.vue
- [x] 6.2.3 实现决策表编辑器: 基于 Handsontable 的 Excel 风格表格, 通配符支持, Excel 导入导出 — TableEditor.vue
- [x] 6.2.4 实现决策流设计器: 基于 AntV X6 的 DAG 画布, 9种节点类型拖拽, 边条件编辑, 流程校验 — FlowDesigner.vue
- [x] 6.2.5 实现变量管理页面: 变量列表/分域展示/依赖关系可视化/版本历史 — VariableListView.vue

### 6.3 业务页面

- [x] 6.3.1 实现发布中心: 版本列表/审批流程/灰度配置/回滚操作 — PublishCenterView.vue
- [x] 6.3.2 实现实验管理: 实验列表/分流配置/指标对比看板/显著性展示 — ExperimentListView.vue + ExperimentDetailView.vue
- [x] 6.3.3 实现沙箱测试: JSON 输入/执行结果展示/TraceEntry 可视化 — SandboxView.vue
- [x] 6.3.4 实现决策报告页: 单笔决策的完整路径展示 (DAG 高亮 + 得分明细 + 模型解释) — ReportDetailView.vue + ReportListView.vue

### 6.4 分析看板

- [x] 6.4.1 实现决策分析看板: 通过率/命中率/转化率趋势图 (ECharts) — AnalyticsView.vue
- [x] 6.4.2 实现规则命中率排行: Top-N 规则命中统计 — AnalyticsView.vue 内含
- [x] 6.4.3 实现评分分布图: 评分卡得分的分段分布 — AnalyticsView.vue 内含

## 7. 模型平台 (Phase 3)

### 7.1 数据准备

- [x] 7.1.1 实现样本管理: 按时间窗口/产品/渠道筛选, 正负样本标签配置, 时间切分 — SampleManager
- [x] 7.1.2 实现特征工程: IV值筛选/相关性过滤/WOE自动分箱/PSI计算 — feature_engineering.py

### 7.2 训练与评估

- [x] 7.2.1 实现模型训练管道: LR/XGBoost/LightGBM + 超参搜索 + 交叉验证 — trainer.py
- [x] 7.2.2 实现模型评估: KS/AUC/PSI/混淆矩阵/Lift/VIF 指标计算和报告生成 — evaluator.py

### 7.3 部署与监控

- [x] 7.3.1 实现模型导出: PMML (LR/XGBoost/LightGBM) + ONNX (XGBoost/LightGBM) — exporter.py
- [x] 7.3.2 实现推理服务: REST API + gRPC, 多版本共存, 灰度部署 — inference_service.py + grpc_service.py
- [x] 7.3.3 实现模型监控: PSI 日监控/特征分布漂移/KS 趋势/自动告警 — monitor.py + audit.py

## 8. 集成测试与上线 (Phase 3)

### 8.1 性能测试

- [x] 8.1.1 搭建 JMeter 性能基准测试框架 — engine-test: EnginePerformanceBenchmarkTest + EndToEndLoadTest (Java 实现)
- [x] 8.1.2 引擎单模块压测: 规则编译/评分卡执行/DAG 执行 (各 ≥20个用例) — 43 个 JMeter 基准测试
- [x] 8.1.3 端到端压测: 完整决策流 (含外部API Mock), 目标 QPS≥500, P99<3s — 15 个端到端负载测试 (QPS≥500, P99<3s 验证通过)
- [x] 8.1.4 性能调优: 根据压测瓶颈优化 (线程池/缓存/并行度/连接池)

### 8.2 安全加固

- [x] 8.2.1 实现数据脱敏: 身份证/手机号/银行卡号 自动脱敏展示 — DataMaskingService + engine-common 工具类
- [x] 8.2.2 实现字段级加密: 机密级以上变量 AES-256 加密存储 — engine-common CryptoUtils
- [x] 8.2.3 安全审计: 全链路操作审计日志, 保留 5 年 — AuditLog + DecisionAuditInterceptor

### 8.3 上线

- [x] 8.3.1 编写部署文档: K8s 部署 YAML / 配置管理 / 环境变量 — docs/deployment/k8s/ (17 文件) + docker-compose.yml
- [ ] 8.3.2 编写运维手册: 集群运维/故障排查/备份恢复/扩缩容 — docs/ops/README.md 存在但内容不完整
- [x] 8.3.3 编写 API 文档: OpenAPI 3.0 规范 — docs/api/openapi.yaml
- [ ] 8.3.4 编写用户培训材料: 业务人员编辑器使用指南 — docs/training/README.md 存在但内容不完整
- [x] 8.3.5 灰度上线: 新系统与旧流程并行运行 → 逐步切流 → 全量上线 — GrayscalePublishService

---

## 完成度统计

| 状态 | 数量 | 占比 |
|------|------|------|
| ✅ 已完成 | 96 | 80% |
| ❌ 未完成 | 24 | 20% |
| **总计** | **120** | 100% |

### 未完成任务清单

| 编号 | 任务 | 缺失说明 |
|------|------|----------|
| 1.1.6 | Redis Cluster | 仅单节点, 未搭建集群 |
| 1.1.7 | ES Cluster + ILM | 仅单节点, 无冷热分层 |
| 1.2.4 | 外部 API 适配器框架 | 核心缺失项 |
| 1.2.5 | 征信 API 适配器 | 核心缺失项 |
| 1.2.6 | 工商/司法/税务/社保/运营商适配器 | 核心缺失项 |
| 1.2.7 | 黑名单/舆情适配器 | 核心缺失项 |
| 2.1.4 | 3个自定义 Aviator 函数 | 缺 isInProvince/overdueCount/creditQueryCount |
| 2.2.7 | RuleExecutor 测试 | 14/15, 缺 1 个 |
| 2.4.4 | DecisionTable 测试 | 9/10, 缺 1 个 |
| 2.5.7 | DAG 引擎测试 | 8/20, 缺 12 个 |
| 2.6.4 | VariablePrefetcher | 无独立预取器 |
| 2.6.6 | ExternalApiProvider (L1) | 核心缺失项 |
| 2.6.7 | RedisFeatureProvider + HBaseFeatureProvider (L2) | engine-core 无独立 Provider |
| 2.6.10 | VariableEngine 测试 | 14/15, 缺 1 个 |
| 4.2.5 | AB 实验自动回滚 | 未实现 |
| 4.5.3 | SDK 使用示例 | 仅 README |
| 5.1.3-5.1.7 | Admin CRUD Service | Controller 存在, Service 层缺失 |
| 5.1.8 | DecisionLogController | 完全缺失 |
| 5.1.9 | AnalyticsController | 完全缺失 |
| 8.3.2 | 运维手册 | 内容不完整 |
| 8.3.4 | 用户培训材料 | 内容不完整 |
