# decision-server

> 决策引擎服务端，Spring Boot 应用，提供决策执行、报告查询、渠道接入和追踪发布能力。

## 功能概述

- **决策执行** — 接收决策请求，加载策略 DAG/规则集/评分卡，执行完整决策流程并返回结果
- **决策报告查询** — 基于 Elasticsearch 查询决策日志和执行报告
- **多渠道接入** — 支持 APP/WEB/API/PARTNER 四种渠道，自动字段映射和默认值注入
- **追踪发布** — 通过 RocketMQ 异步发布决策追踪日志到 Elasticsearch
- **缓存热加载** — 监听 RocketMQ 规则变更消息，触发版本化缓存热加载
- **审计拦截** — 请求审计日志记录
- **限流控制** — 基于令牌桶的 API 限流
- **API 鉴权** — JWT / API Key 鉴权机制

## 包结构

```
src/main/java/com/credit/platform/server/
├── config/            — Spring 配置
│   ├── EngineCoreConfig.java     — 引擎核心 Bean 装配
│   ├── ElasticsearchConfig.java  — Elasticsearch 客户端配置
│   ├── ThreadPoolConfig.java     — 线程池配置（DAG 执行、外部 API）
│   └── WebMvcConfig.java         — Web MVC 拦截器注册
├── controller/        — REST API 控制器
│   └── DecisionController.java   — 决策执行和报告查询接口
├── service/           — 业务服务
│   ├── DecisionService.java      — 决策编排服务（核心）
│   └── DecisionReportService.java — 决策报告服务
├── model/             — 数据模型
│   └── DecisionLogDocument.java  — ES 决策日志文档
├── repository/        — 数据访问
│   └── DecisionLogRepository.java — ES 决策日志仓储
├── trace/             — 追踪与缓存热加载
│   ├── MqTracePublisher.java         — MQ 追踪日志发布
│   └── RocketMQReloadListener.java  — RocketMQ 缓存重载监听
├── channel/           — 渠道配置
│   └── ChannelConfig.java        — 多渠道字段映射与默认值
└── interceptor/       — 拦截器
    ├── AuthInterceptor.java         — 鉴权拦截器
    ├── RateLimitInterceptor.java    — 限流拦截器
    └── DecisionAuditInterceptor.java — 审计拦截器
```

## 关键类说明

| 类名 | 说明 |
|------|------|
| `DecisionServerApplication` | Spring Boot 启动类，端口 8080 |
| `DecisionController` | REST API 入口：`POST /api/v1/decision/execute`、`GET /api/v1/decision/report/{id}` |
| `DecisionService` | 决策编排核心服务，协调缓存加载、DAG 执行、追踪发布、日志持久化 |
| `DecisionLogDocument` | Elasticsearch 决策日志文档模型 |
| `DecisionLogRepository` | ES 决策日志数据访问层 |
| `ChannelConfig` | 渠道配置，APP/WEB/API 字段映射标准化 |
| `MqTracePublisher` | RocketMQ 追踪日志异步发布 |
| `RocketMQReloadListener` | RocketMQ 缓存热加载监听器（条件激活） |
| `EngineCoreConfig` | 引擎核心 Bean 装配（VersionedRuleCache、ScorecardExecutor 等） |

## 配置项

`application.yml` 中的关键配置：

```yaml
server:
  port: 8080

decision:
  engine:
    thread-pool:
      dag-execution:
        core-size: 8
        max-size: 32
        queue-capacity: 200
      external-api:
        core-size: 4
        max-size: 16
    cache:
      max-size: 10000
      max-versions: 5
      expire-hours: 24
    rate-limit:
      enabled: true
      max-requests-per-second: 500
    auth:
      enabled: true
      secret-key: "change-me-in-production"

elasticsearch:
  host: ${ES_HOST:localhost}
  port: ${ES_PORT:9200}
  scheme: ${ES_SCHEME:http}
```

## 构建与运行

```bash
# 构建
mvn clean package -pl decision-server -am

# 运行
java -jar decision-server/target/decision-server-1.0.0-SNAPSHOT.jar
```

## 测试

```bash
mvn test -pl decision-server
```

## 依赖关系

- **依赖**: `engine-core`、`engine-common`、`decision-admin`、`decision-sdk`
- **被依赖**: 无（终端服务）
- **外部依赖**: Spring Boot 3.2.5、Elasticsearch 8.13.4
