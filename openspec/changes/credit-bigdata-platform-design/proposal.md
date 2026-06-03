## Why

集团信贷业务目前缺乏统一的智能化决策基础设施，业务审批依赖人工经验和分散的规则配置，无法支撑日益增长的信贷业务量和监管合规要求。需要建设一套覆盖数据接入、存储计算、分析服务和治理运维的全链路闭环体系，以实现信贷决策的自动化、智能化和可解释化，满足银保监会对决策可追溯、可审计的监管要求。

## What Changes

- **新建大数据平台**: 从业务系统(MySQL/Oracle)、日志文件、外部数据源(征信机构/工商/司法等)采集原始数据，构建 Hadoop 生态的数据湖仓(HDFS + Hive 分层)，通过 Flink 实现实时特征计算，支撑上层决策引擎和模型平台的数据需求
- **新建自研决策引擎**: 采用编译执行模型(JSON → AST → 缓存)，支持 5 种规则类型(条件规则/评分卡/决策表/决策树/规则集)、DAG 决策流编排、变量引擎(4层分层，数千变量管理)、AB 实验分流、可解释性追踪。提供 Vue 可视化管理后台，业务人员可自助配置规则和决策流
- **新建模型平台**: 管理 LR/XGBoost/LightGBM 等模型的全生命周期，包括样本管理、特征工程、自动化训练、PMML/ONNX 模型部署、PSI/AUC 稳定性监控
- **新建运维平台**: 覆盖基础设施监控、ETL 任务调度(DolphinScheduler)、数据质量监控、成本优化
- **外部数据源全量接入**: 央行征信、百行/朴道征信、工商、司法、税务、社保、运营商、电商/支付、黑名单、舆情等 10 类外部数据源

## Capabilities

### New Capabilities

- `data-ingestion`: 数据采集能力 — 批量采集(DataX)、实时CDC(Canal/Debezium)、外部API采集、日志采集(Fluentd)
- `data-storage`: 数据存储能力 — HDFS数据湖、Hive分层数仓(ODS/DWD/DWS/ADS)、Kafka实时流、HBase特征KV、ES搜索、Redis缓存
- `data-compute`: 数据计算能力 — Spark离线计算、Flink实时特征计算(5+任务)、Trino即席查询
- `data-governance`: 数据治理能力 — 元数据管理、数据质量5维监控、数据安全(脱敏/加密/RBAC/审计)、生命周期管理(冷热分层)
- `data-service`: 数据服务能力 — 数据API、BI报表、数据产品(企业画像/信用报告)
- `rule-engine`: 规则引擎能力 — 5种规则类型的编译执行、AST解释器、Aviator表达式引擎、规则热加载(Caffeine+MQ广播)
- `scorecard-engine`: 评分卡引擎能力 — 多特征分箱评分、阈值判定、得分明细追踪
- `decision-flow`: 决策流能力 — DAG编排(9种节点类型)、拓扑排序并行执行、条件分支、子流程嵌套
- `variable-engine`: 变量引擎能力 — 4层变量分层(输入/外部/缓存/衍生)、注册表、懒加载、预取优化、数千变量管理
- `ab-experiment`: AB实验能力 — 流量分流(consistent hashing)、指标收集、统计显著性分析、自动回滚
- `explainability`: 可解释性能力 — 4级可解释(规则/流程/模型/审计)、TraceEntry全链路追踪、SHAP模型解释、决策报告生成
- `decision-admin`: 决策管理后台能力 — Vue可视化编辑器(规则/评分卡/决策表/DAG)、策略版本管理、审批发布流程、沙箱回测、决策分析看板
- `model-lifecycle`: 模型全生命周期能力 — 样本管理、WOE特征工程、模型训练(LR/XGBoost/LightGBM)、评估(KS/AUC/PSI)、PMML/ONNX部署、PSI监控告警
- `ops-monitoring`: 运维监控能力 — 基础设施/任务/业务三级监控、DolphinScheduler任务调度、成本优化

### Modified Capabilities

无修改的能力（全部为新建）。

## Impact

- **技术栈**: Java 17+ / Spring Boot 3.x / Vue 3 / Hadoop / Flink / Spark / Redis / HBase / ES / MySQL / Kafka / Aviator / AntV X6
- **基础设施**: 需要搭建 Hadoop 集群(≥5节点)、Kafka 集群(≥3节点)、Redis Cluster(≥6节点)、ES 集群(≥3节点)、K8s 集群
- **外部依赖**: 10 类外部数据源 API 对接（征信/工商/司法/税务/社保/运营商/电商/黑名单/舆情）
- **团队**: 10人团队(1架构师+4后端+2大数据+2前端+1测试)，12个月分4阶段交付
- **API**: 新增决策执行API、规则管理API、模型服务API、数据查询API
- **数据**: 日增 >5000万条，总存储 >50TB，需规划存储容量和集群规模
