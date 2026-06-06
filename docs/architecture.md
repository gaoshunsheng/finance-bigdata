# 架构总览

## 1. 平台定位与核心能力

企业征信大数据平台是一个面向金融信贷场景的**实时风控决策系统**，支持从数据采集、特征计算、模型训练到在线决策的全链路闭环。

**核心能力：**
- ⚡ 毫秒级实时决策 — 规则引擎 + 评分卡 + 决策流 + 模型推理
- 🔄 编译缓存 — compile-once-execute-many，Caffeine L1 + 版本管理
- 📊 110+ API 端点 — 覆盖规则全生命周期（CRUD → 测试 → 审批 → 灰度 → 发布）
- 🌊 大数据实时计算 — Flink + Kafka 日处理 50M+ 条记录
- 🤖 模型训练部署一体化 — XGBoost/LightGBM 训练 → ONNX 导出 → gRPC 推理
- 🔐 企业级安全 — RBAC + JWT + AES-256 字段加密 + 审计日志
- 📈 灰度发布与审批工作流 — 多级审批 → 灰度 → 全量
- 🏛️ 数据治理体系 — 元数据管理、血缘追踪、数据质量监控

---

## 2. 系统架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                         前端层 (Frontend)                        │
│  decision-ui — Vue 3 + Ant Design Vue + AntV X6 + ECharts      │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐          │
│  │ 规则编辑器│ │ 评分卡编辑│ │ 流程设计器│ │ 分析看板  │          │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘          │
├─────────────────────────────────────────────────────────────────┤
│                       决策服务层 (Service)                       │
│  ┌─────────────────┐  ┌─────────────────┐  ┌───────────────┐  │
│  │ decision-server  │  │ decision-admin   │  │ model-platform│  │
│  │   :8080 决策执行  │  │   :8081 管理后台  │  │ :8082 模型平台 │  │
│  │ 认证→限流→审计    │  │ CRUD/发布/审批    │  │ 训练/推理/监控 │  │
│  │ →决策→日志       │  │ RBAC/JWT         │  │ FastAPI+gRPC  │  │
│  └─────────────────┘  └─────────────────┘  └───────────────┘  │
│  ┌─────────────────┐                                            │
│  │ data-service     │  ← 特征查询 / 企业画像 / BI 报表           │
│  │   :8083 数据服务  │  ← Redis + HBase + ES + Trino            │
│  └─────────────────┘                                            │
├─────────────────────────────────────────────────────────────────┤
│                      引擎核心层 (Engine Core)                    │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐            │
│  │ engine-core  │  │engine-common│  │decision-sdk │            │
│  │ 编译/执行     │  │ 模型/加密    │  │ 客户端 SDK   │            │
│  │ Aviator+Caffeine│ 脱敏/异常    │  │ HTTP+自动配置│            │
│  │ 纯Java无Spring │              │  │             │            │
│  └─────────────┘  └─────────────┘  └─────────────┘            │
├─────────────────────────────────────────────────────────────────┤
│                     大数据平台层 (Big Data)                      │
│  ┌───────┐ ┌───────┐ ┌───────┐ ┌───────┐ ┌───────┐ ┌───────┐ │
│  │ Flink │ │ Kafka │ │Hadoop │ │ Hive  │ │ HBase │ │ Trino │ │
│  │1.18.1 │ │3.6.1  │ │3.3.6  │ │3.1.3  │ │2.5.5  │ │ 435   │ │
│  └───────┘ └───────┘ └───────┘ └───────┘ └───────┘ └───────┘ │
│  ┌──────────────────┐  ┌──────────────────┐                     │
│  │ DolphinScheduler │  │ data-governance  │                     │
│  │    3.2.1 调度     │  │  元数据/血缘/质量  │                     │
│  └──────────────────┘  └──────────────────┘                     │
└─────────────────────────────────────────────────────────────────┘
```

---

## 3. 模块依赖关系图

```
                        ┌──────────────┐
                        │ engine-common │ (基础层: 模型/加密/脱敏/异常)
                        └──────┬───────┘
                               │
                    ┌──────────┼──────────┐
                    │          │          │
             ┌──────┴──────┐   │   ┌──────┴──────┐
             │ engine-core │   │   │decision-sdk │
             │ (引擎核心)   │   │   │ (客户端SDK)  │
             └──────┬──────┘   │   └──────┬──────┘
                    │          │          │
             ┌──────┴──────┐   │          │
             │decision-admin│  │          │
             │  (管理后台)   │  │          │
             └──────┬──────┘   │          │
                    │          │          │
             ┌──────┴──────────┴──────────┴──┐
             │       decision-server           │
             │        (决策执行服务)            │
             └────────────────────────────────┘

  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
  │  decision-ui     │  │ model-platform   │  │ data-platform    │
  │  (Vue 3 前端)    │  │ (Python 模型平台) │  │ (大数据平台)     │
  │  独立部署         │  │ 独立部署          │  │  ├ flink-jobs    │
  └─────────────────┘  └─────────────────┘  │  ├ data-service   │
                                            │  └ data-governance│
                                            └─────────────────┘
```

**依赖说明：**
- `engine-common` 是基础层，无内部依赖
- `engine-core` 仅依赖 `engine-common`，不依赖 Spring
- `decision-admin` 依赖 `engine-core`
- `decision-server` 依赖 `engine-core` + `decision-admin` + `decision-sdk`
- `decision-ui`、`model-platform`、`data-platform` 独立部署，通过 HTTP/gRPC 通信

---

## 4. 核心数据流 — 决策执行全链路

```
客户端 (SDK/API)
     │
     ▼
┌─────────────────────────────────────────────────────┐
│  Decision Server (:8080)                             │
│                                                      │
│  ① AuthInterceptor — API Key / JWT Token 验证        │
│  ② RateLimitInterceptor — 令牌桶限流 500 req/s       │
│  ③ DecisionAuditInterceptor — 审计日志记录            │
│                                                      │
│  ④ DecisionService.execute()                         │
│     │                                                │
│     ├─→ VersionedRuleCache.get(strategyId)            │
│     │     Caffeine L1 缓存 → 编译一次执行多次          │
│     │     最大 10000 条, 5 版本, 24h 过期              │
│     │                                                │
│     ├─→ VariableEngine.resolve()  变量解析 3 层       │
│     │     INPUT(请求参数) → DERIVED(派生计算)          │
│     │     → EXTERNAL(外部数据源)                       │
│     │                                                │
│     ├─→ CompiledDAG.execute()  DAG 编排               │
│     │     规则集 → 评分卡 → 决策表 → 决策树            │
│     │     → 模型推理 → 汇总                            │
│     │                                                │
│     ├─→ DecisionTracer  全链路追踪                    │
│     │     每个节点记录输入/输出/耗时                     │
│     │                                                │
│     └─→ DecisionResponse                              │
│           result: PASS/REJECT/MANUAL                  │
│           score + rejectReason + traceId              │
│                                                      │
│  ⑤ saveDecisionLog() → Elasticsearch                 │
│     索引: decision-log-{yyyy.MM} 按月                  │
│                                                      │
│  ⑥ MqTracePublisher → RocketMQ 广播追踪事件           │
└─────────────────────────────────────────────────────┘
     │
     ▼
  返回决策结果 (P99 < 3s, 500 QPS/节点)
```

---

## 5. 关键设计决策

| 决策 | 选择 | 理由 |
|------|------|------|
| **编译缓存策略** | compile-once-execute-many (Caffeine) | 避免每次请求重复编译 JSON → CompiledRule，提升 10x 吞吐量 |
| **engine-core 纯 Java** | 无 Spring 依赖 | 可在任意 JVM 环境使用，单元测试无需启动 Spring 容器 |
| **表达式引擎** | Aviator 5.4.3 | 轻量（无 ASM 依赖）、高性能、可扩展自定义函数 |
| **ORM 框架** | MyBatis-Plus 3.5.6 | 复合主键灵活、SQL 可控、相比 JPA 性能更优 |
| **集成测试数据库** | H2 MODE=MySQL | 免安装、@Transactional + @Rollback 自动回滚 |
| **决策日志存储** | ES 按月索引 decision-log-{yyyy.MM} | 高吞吐写入、时间范围查询、Kibana 可视化 |
| **实时特征存储** | Redis + HBase 二级查询 | Redis L1 缓存 P99<20ms，HBase 持久化兜底 |
| **数仓分层** | ODS → DWD → DWS → ADS | 标准分层解耦：ODS 原始、DWD 脱敏、DWS 聚合、ADS 面向应用 |
| **容器编排** | Docker Compose + K8s | 开发环境用 Compose 一键启动，生产用 K8s 弹性伸缩 |
| **任务调度** | DolphinScheduler 3.2.1 | 原生中文 UI、可视化 DAG、支持 daily ETL 工作流 |

---

## 6. 技术选型理由表

| 类别 | 选型 | 候选方案 | 选择理由 |
|------|------|---------|---------|
| **后端框架** | Spring Boot 3.2.5 | Quarkus, Micronaut | 生态成熟、社区活跃、团队熟悉 |
| **规则引擎** | 自研 + Aviator | Drools, Easy Rules | 轻量可控、无 Rete-OPT 编译开销、支持自定义函数 |
| **本地缓存** | Caffeine 3.1.8 | Guava Cache | 异步刷新、统计监控、高性能 |
| **关系数据库** | MySQL 8.0 | PostgreSQL | 团队熟悉、运维成熟、MyBatis-Plus 兼容性好 |
| **搜索引擎** | Elasticsearch 8.13.4 | Solr | JSON API、Kibana 可视化生态、Spring Data 集成 |
| **消息队列** | Kafka + RocketMQ | RabbitMQ, Pulsar | Kafka 实时数据管道；RocketMQ 事务消息和灰度通知 |
| **实时计算** | Flink 1.18.1 | Spark Streaming | 事件时间处理、精准一次语义、状态管理 |
| **批处理** | Spark 3.x | MapReduce | DAG 执行模型、内存计算、Hive 集成 |
| **联邦查询** | Trino 435 | Presto, Drill | 更活跃的社区、更丰富的连接器 |
| **任务调度** | DolphinScheduler 3.2.1 | Airflow, Azkaban | 原生中文、可视化 DAG、开箱即用 |
| **前端框架** | Vue 3 + Ant Design Vue | React + Ant Design | 渐进式框架、学习曲线低、Composition API |
| **图编辑器** | AntV X6 | mxGraph, jsPlumb | 蚂蚁出品、Vue 3 集成好、DAG 场景成熟 |
| **ML 框架** | XGBoost + LightGBM | TensorFlow, PyTorch | 表格数据表现更优、训练快、可解释性 |
| **模型导出** | ONNX + PMML | 原生 pickle | 跨平台、标准化、安全（无 RCE 风险） |
| **ML 服务** | FastAPI + gRPC | Flask, Tornado | 异步高性能、自动 OpenAPI 文档、gRPC 低延迟 |
| **数据同步** | DataX + Canal | Sqoop, Debezium | DataX 批量全量、Canal MySQL CDC 增量 |
| **容器运行时** | Docker + K8s | Docker Swarm | K8s 生态完善、Helm Charts、自动扩缩容 |

---

## 7. 性能指标

| 指标 | 目标值 | 说明 |
|------|--------|------|
| **日处理量** | 50M+ 条 | Flink 实时 + Spark 批处理 |
| **数据存储** | 50TB+ | HDFS + HBase + ES + MySQL |
| **决策延迟** | P99 < 3s | 端到端决策执行 |
| **吞吐量** | 500 QPS/节点 | 水平扩展 |
| **特征查询** | P99 < 20ms | Redis L1 缓存 |
| **企业画像** | P99 < 500ms | 多源聚合（Redis + HBase + ES） |
| **测试覆盖** | 694+ 测试用例 | 单元测试 + 集成测试 + 性能测试 |
| **API 端点** | 110+ | 4 个服务完整覆盖 |
| **模块数** | 11 个 | Maven 多模块 + Python + Vue |
| **代码行数** | 30,000+ | Java 22K + Vue 8K + Python |

---

## 8. 部署架构

### Docker Compose（开发/测试）
```yaml
# 18 个服务一键启动
services:
  mysql, redis, elasticsearch          # 基础设施
  decision-server, decision-admin       # Java 应用
  model-platform                        # Python 应用
  decision-ui                           # Vue 前端 (nginx)
  data-service                          # 数据服务
  zookeeper, kafka                      # 消息队列
  hadoop-namenode, hadoop-datanode      # HDFS
  hive-metastore, hive-server           # Hive
  hbase-master                          # HBase
  flink-jobmanager, flink-taskmanager   # Flink
  trino                                 # 联邦查询
  dolphinscheduler-standalone           # 调度
  filebeat                              # 日志采集
```

### Kubernetes（生产）
- Namespace: `finance-platform`
- Ingress: Nginx，按路径路由到各服务
- HPA: decision-server 2 副本，按 CPU 自动扩缩
- ConfigMap + Secret: 配置与密钥分离
- PVC: 模型存储、Hadoop 数据持久化

---

## 9. 安全架构

```
客户端请求
     │
     ├─→ HTTPS (TLS 1.3)
     │
     ├─→ Nginx Ingress — 安全 Headers (X-Frame-Options, X-Content-Type-Options)
     │
     ├─→ AuthInterceptor — JWT / API Key 验证
     │
     ├─→ RateLimitInterceptor — 令牌桶限流
     │
     ├─→ DecisionAuditInterceptor — 审计日志
     │
     ├─→ Spring Security — RBAC 4 级角色 (VIEWER / EDITOR / APPROVER / ADMIN)
     │
     ├─→ FieldEncryptor — AES-256-GCM 字段级加密
     │
     └─→ SensitiveDataMasker — PII 脱敏 (身份证/手机/姓名/银行卡/邮箱)
```

**密钥管理：**
- JWT HMAC 密钥: 环境变量注入
- AES 密钥: 环境变量注入
- 数据库密码: K8s Secret / .env 文件
- 未来: 迁移到 Vault 或 K8s External Secrets

---

## 10. 监控与运维

| 层级 | 工具 | 用途 |
|------|------|------|
| **应用监控** | Spring Actuator + Prometheus | 健康检查、指标采集 |
| **日志收集** | Filebeat → Elasticsearch → Kibana | 集中式日志检索 |
| **决策追踪** | DecisionTracer → ES | 全链路决策追踪 |
| **模型监控** | PSI / 特征漂移检测 | 模型性能退化预警 |
| **数据质量** | QualityMonitor 5 维度 | 完整性/准确性/一致性/时效性/唯一性 |
| **任务调度** | DolphinScheduler Dashboard | ETL 任务状态监控 |
