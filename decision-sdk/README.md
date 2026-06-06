# decision-sdk

> 决策引擎客户端 SDK，提供零依赖的 Java HTTP 客户端和 Spring Boot 自动装配能力。

## 功能概述

- **同步决策调用** — 封装与 decision-server 的 HTTP 通信，支持同步执行决策
- **决策报告查询** — 查询指定决策 ID 的执行报告
- **超时与重试** — 可配置连接超时、读取超时和重试次数
- **API Key 鉴权** — 通过 `X-API-Key` 请求头传递鉴权信息
- **健康检查** — 检测决策引擎服务可用性
- **Spring Boot 自动装配** — 引入依赖后自动创建 `DecisionClient` Bean
- **零外部依赖** — 仅依赖 `engine-common`，使用 JDK 原生 `HttpURLConnection`

## 包结构

```
src/main/java/com/credit/platform/engine/sdk/
├── DecisionClient.java             — 决策引擎客户端（核心）
├── DecisionClientConfig.java       — 客户端配置
├── DecisionClientException.java    — 客户端异常
└── autoconfigure/                  — Spring Boot 自动装配
    ├── DecisionAutoConfiguration.java  — 自动装配类
    └── DecisionClientProperties.java   — 配置属性绑定
```

## 关键类说明

| 类名 | 说明 |
|------|------|
| `DecisionClient` | 决策引擎客户端，核心方法：`execute()`、`getReport()`、`isHealthy()` |
| `DecisionClientConfig` | 客户端配置，包含 endpoint、apiKey、超时时间、重试次数 |
| `DecisionClientException` | 客户端异常，封装 HTTP 调用失败信息 |
| `DecisionAutoConfiguration` | Spring Boot 自动装配，根据配置创建 `DecisionClient` Bean |
| `DecisionClientProperties` | `decision.client.*` 配置属性绑定类 |

## 使用方式

### 1. Spring Boot 自动装配

在 `pom.xml` 中引入依赖：

```xml
<dependency>
    <groupId>com.credit.platform</groupId>
    <artifactId>decision-sdk</artifactId>
</dependency>
```

在 `application.yml` 中配置：

```yaml
decision:
  client:
    enabled: true
    endpoint: http://localhost:8080
    api-key: your-api-key
    connect-timeout-ms: 3000
    read-timeout-ms: 5000
    max-retries: 2
```

在代码中直接注入使用：

```java
@Autowired
private DecisionClient decisionClient;

public void makeDecision() {
    DecisionRequest request = new DecisionRequest();
    request.setStrategyId("STR_CREDIT_V3");
    request.setChannel("APP");
    request.setApplicant(Map.of("age", 28, "income", 50000));

    DecisionResponse response = decisionClient.execute(request);
    System.out.println(response.getResult());
}
```

### 2. 手动创建客户端

```java
DecisionClient client = DecisionClient.create(
    DecisionClientConfig.builder()
        .endpoint("http://localhost:8080")
        .apiKey("your-api-key")
        .build()
);

DecisionResponse response = client.execute(request);
```

## 构建与运行

```bash
# 构建
mvn clean package -pl decision-sdk -am
```

本模块为 SDK 库，不可独立运行，需由业务应用引入。

## 测试

```bash
mvn test -pl decision-sdk
```

## 依赖关系

- **依赖**: `engine-common`
- **被依赖**: `decision-server`（编译时）、业务应用（运行时）
- **可选依赖**: Spring Boot（自动装配，optional）
