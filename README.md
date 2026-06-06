# 信贷风控决策引擎平台

> Credit Risk Decision Engine Platform

## 项目简介

信贷风控决策引擎平台是一套面向企业级信贷业务的全链路风控决策系统，涵盖规则引擎、评分卡模型、决策表、DAG 决策流等核心能力。平台整合大数据基础设施与实时计算框架，支持从数据采集、特征工程、模型训练到决策执行的端到端风控闭环，助力金融机构实现智能化、自动化的信贷审批与风险管控。

---

## 技术栈

### 后端

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 17 | 核心开发语言 |
| Spring Boot | 3.2 | 应用框架 |
| MyBatis-Plus | 3.5 | ORM 框架 |
| Elasticsearch | 8.13 | 决策日志持久化与检索 |
| Redis | 7.x | 缓存与会话管理 |
| MySQL | 8.x | 业务数据存储 |

### 大数据

| 技术 | 版本 | 说明 |
|------|------|------|
| Apache Flink | 1.18 | 实时流计算 |
| Apache Spark | 3.x | 批处理与 ETL |
| Apache Hive | 3.x | 数仓查询引擎 |
| Apache HBase | 2.x | 海量特征存储 |
| Apache Kafka | 3.x | 消息队列 |
| Trino | 435 | 联邦查询引擎 |

### 前端

| 技术 | 说明 |
|------|------|
| Vue 3 | 前端框架 |
| TypeScript | 类型安全 |
| Ant Design Vue | UI 组件库 |
| ECharts | 数据可视化 |

### 模型平台

| 技术 | 版本 | 说明 |
|------|------|------|
| Python | 3.11 | 模型开发语言 |
| FastAPI | 0.100+ | 模型服务框架 |
| scikit-learn | 1.x | 机器学习库 |

### 部署与调度

| 技术 | 版本 | 说明 |
|------|------|------|
| Docker Compose | - | 本地开发编排 |
| Kubernetes | 1.28+ | 生产容器编排 |
| DolphinScheduler | 3.2 | 工作流调度 |

### 构建工具

- Maven 3.9（后端）
- npm / Vite（前端）

---

## 模块说明

| 模块 | 说明 |
|------|------|
| `engine-common` | 通用工具库（加密、脱敏、异常处理、通用常量） |
| `engine-core` | 引擎核心模块（规则 / 评分卡 / 决策表 / DAG 决策流 / 变量引擎 / 执行追踪 / AB 实验） |
| `engine-test` | 集成测试与性能基准（694+ 测试用例） |
| `decision-admin` | 管理后台后端（CRUD / 发布管理 / RBAC 权限 / JWT 认证 / 审批流 / 灰度发布） |
| `decision-sdk` | 客户端 SDK（供业务系统快速接入决策引擎） |
| `decision-server` | 决策执行服务（ES 持久化 / 报表统计 / 审计日志） |
| `data-platform` | 大数据平台（Flink 作业 / 数据服务 / 数据治理 / 实时特征计算） |
| `data-warehouse` | 数仓建设（Hive DDL + Spark ETL 脚本） |
| `data-pipeline` | 数据采集管道（DataX / Canal / Fluentd） |
| `decision-ui` | 管理后台前端（Vue 3 + 可视化规则编辑器 + 决策流设计器） |
| `model-platform` | 模型平台（模型训练 / 评估 / 在线推理 / 性能监控） |
| `docs` | 部署文档、K8s 配置、API 规范、培训资料 |

---

## 架构图

```
┌─────────────────────────────────────────────────────────┐
│                     前端层 (Frontend)                     │
│              decision-ui (Vue 3 + ECharts)               │
└────────────────────────┬────────────────────────────────┘
                         │ HTTP / WebSocket
┌────────────────────────▼────────────────────────────────┐
│                   决策服务层 (Decision)                    │
│  ┌──────────────┐  ┌───────────────┐  ┌──────────────┐  │
│  │decision-admin│  │decision-server│  │ decision-sdk │  │
│  │  管理后台API  │  │  决策执行服务   │  │   客户端SDK   │  │
│  └──────┬───────┘  └───────┬───────┘  └──────┬───────┘  │
└─────────┼──────────────────┼──────────────────┼──────────┘
          │                  │                  │
┌─────────▼──────────────────▼──────────────────▼──────────┐
│                   引擎核心层 (Engine)                      │
│  ┌────────────┐ ┌──────┐ ┌──────┐ ┌───┐ ┌────┐ ┌─────┐  │
│  │ engine-core│ │ 规则 │ │评分卡│ │决策│ │DAG │ │变量  │  │
│  │            │ │ 引擎 │ │      │ │ 表 │ │流  │ │引擎  │  │
│  └─────┬──────┘ └──────┘ └──────┘ └───┘ └────┘ └─────┘  │
│        │  engine-common（加密 / 脱敏 / 异常）              │
└────────┼──────────────────────────────────────────────────┘
         │
┌────────▼──────────────────────────────────────────────────┐
│                  数据平台层 (Data Platform)                 │
│  ┌──────────────┐  ┌───────────────┐  ┌───────────────┐  │
│  │ data-platform│  │ data-warehouse│  │ data-pipeline │  │
│  │  Flink 实时   │  │ Hive + Spark  │  │ DataX/Canal   │  │
│  │  特征计算     │  │ 数仓 ETL      │  │ 数据采集管道   │  │
│  └──────┬───────┘  └───────┬───────┘  └───────┬───────┘  │
└─────────┼──────────────────┼──────────────────┼───────────┘
          │                  │                  │
┌─────────▼──────────────────▼──────────────────▼───────────┐
│                大数据基础设施 (Infrastructure)               │
│  Kafka │ Elasticsearch │ MySQL │ Redis │ HBase │ Trino    │
└───────────────────────────────────────────────────────────┘
```

---

## 快速启动

### 环境准备

- JDK 17+
- Maven 3.9+
- Docker & Docker Compose
- Node.js 18+（前端开发）

### 启动步骤

```bash
# 1. 克隆项目
git clone <repository-url>
cd finance-bigdata

# 2. 配置环境变量
cp docs/deployment/.env.example .env

# 3. 编译打包
mvn clean package -DskipTests

# 4. 启动基础服务（MySQL、Redis、ES、Kafka 等）
docker compose -f docs/deployment/docker-compose.yml up -d

# 5. 启动后端服务
java -jar decision-admin/target/decision-admin-*.jar
java -jar decision-server/target/decision-server-*.jar

# 6. 启动前端（可选）
cd decision-ui && npm install && npm run dev
```

---

## API 文档

完整的 API 规范请参考 OpenAPI 文档：

- 本地文件：[docs/api/openapi.yaml](docs/api/openapi.yaml)
- 在线预览：启动服务后访问 `http://localhost:8080/swagger-ui.html`

---

## 测试

项目包含 **694+** 测试用例，覆盖引擎核心、管理后台、SDK 等模块。

```bash
# 运行全部测试
mvn test

# 运行指定模块测试
mvn test -pl engine-core
mvn test -pl engine-test

# 跳过测试打包
mvn clean package -DskipTests
```

---

## 目录结构

```
finance-bigdata/
├── engine-common/          # 通用工具库
├── engine-core/            # 引擎核心
├── engine-test/            # 集成测试
├── decision-admin/         # 管理后台后端
├── decision-sdk/           # 客户端 SDK
├── decision-server/        # 决策执行服务
├── decision-ui/            # 管理后台前端
├── data-platform/          # 大数据平台
├── data-warehouse/         # 数仓建设
├── data-pipeline/          # 数据采集管道
├── model-platform/         # 模型平台
├── docs/                   # 文档与配置
│   ├── api/                # API 规范
│   ├── deployment/         # 部署配置（Docker/K8s）
│   ├── ops/                # 运维文档
│   └── training/           # 培训资料
└── pom.xml                 # Maven 父 POM
```

---

## 许可证

本项目基于 [MIT License](https://opensource.org/licenses/MIT) 开源。
