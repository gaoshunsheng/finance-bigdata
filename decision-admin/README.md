# decision-admin

> 决策引擎管理后台，Spring Boot 应用，提供规则 CRUD、发布流程、灰度发布、审批、版本管理和安全认证。

## 功能概述

- **规则全生命周期管理** — 支持 RULE/SCORECARD/DECISION_TABLE/DECISION_TREE/FLOW/VARIABLE 六种资产类型
- **版本化编辑** — 每次修改创建新版本，支持版本对比（Diff）和回滚
- **完整发布流程** — DRAFT -> TESTING -> PENDING_REVIEW -> APPROVED -> GRAYSCALE -> RELEASED
- **灰度发布** — 按百分比逐步提升流量（5% -> 25% -> 50% -> 100%），支持暂停/恢复/回滚
- **审批流程** — 提交审批、审批通过、审批驳回、撤回，完整审批记录
- **RBAC 权限** — 四级角色（VIEWER/EDITOR/APPROVER/ADMIN），JWT 认证
- **审计日志** — 所有配置变更操作记录，包含变更前后快照
- **REST API** — 为 decision-ui 前端提供管理接口

## 包结构

```
src/main/java/com/credit/platform/admin/
├── controller/        — REST API 控制器
│   ├── RuleController.java          — 条件规则管理
│   ├── ScorecardController.java     — 评分卡管理
│   ├── DecisionTableController.java — 决策表管理
│   ├── DecisionTreeController.java  — 决策树管理
│   ├── FlowController.java          — 决策流管理
│   ├── VariableController.java      — 变量管理
│   ├── PublishController.java       — 发布管理
│   ├── ExperimentController.java    — 实验管理
│   ├── AuthController.java          — 认证接口
│   └── AuditLogController.java      — 审计日志查询
├── service/           — 业务服务
│   ├── RuleAdminService.java        — 规则 CRUD 服务
│   ├── RulePublishService.java      — 发布流程编排
│   ├── ApprovalService.java         — 审批服务
│   ├── GrayscalePublishService.java — 灰度发布服务
│   ├── VersionDiffService.java      — 版本对比服务
│   └── RuleRepository.java         — 规则持久化仓储
├── mapper/            — MyBatis-Plus Mapper
│   ├── RuleMapper.java
│   ├── AuditLogMapper.java
│   ├── GrayscaleConfigMapper.java
│   ├── UserMapper.java
│   └── ApprovalRecordMapper.java
├── model/             — 数据模型
│   ├── RuleEntity.java              — 规则实体
│   ├── PublishStatus.java           — 发布状态枚举
│   ├── ApprovalRecord.java          — 审批记录
│   ├── GrayscaleConfig.java         — 灰度配置
│   ├── VersionDiff.java             — 版本对比结果
│   └── ApiResponse.java            — 统一响应
└── security/          — 安全模块
    ├── SecurityConfig.java           — Spring Security 配置
    ├── JwtService.java               — JWT 令牌服务
    ├── JwtAuthenticationFilter.java  — JWT 认证过滤器
    ├── UserService.java              — 用户服务
    ├── UserRepository.java           — 用户仓储
    ├── AuditLog.java                 — 审计日志实体
    ├── AuditLogService.java          — 审计日志服务
    ├── AuditLogRepository.java       — 审计日志仓储
    ├── User.java                     — 用户实体
    ├── Role.java                     — 角色枚举
    └── Permission.java               — 权限定义
```

## 关键类说明

| 类名 | 说明 |
|------|------|
| `DecisionAdminApplication` | Spring Boot 启动类，端口 8081 |
| `RuleAdminService` | 规则 CRUD 统一入口，支持创建、更新、版本创建、删除、状态推进、回滚 |
| `RulePublishService` | 发布流程编排，协调审批、灰度、版本对比 |
| `ApprovalService` | 审批流程管理：提交、通过、驳回、撤回 |
| `GrayscalePublishService` | 灰度发布管理：启动、阶梯提升、调整、暂停、恢复、回滚 |
| `VersionDiffService` | 版本对比服务，比较两个版本的 JSON 内容差异 |
| `RuleEntity` | 规则实体，复合主键（type, id, version） |
| `PublishStatus` | 发布状态枚举：DRAFT/TESTING/PENDING_REVIEW/APPROVED/GRAYSCALE/RELEASED/ROLLED_BACK |
| `JwtService` | JWT 令牌签发与验证 |
| `SecurityConfig` | Spring Security 配置，RBAC 权限控制 |

## 配置项

`application.yml` 中的关键配置：

```yaml
server:
  port: 8081

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/credit_platform?useUnicode=true&characterEncoding=utf8mb4&useSSL=false
    username: root
    password: root123
  sql:
    init:
      mode: always
      schema-locations: classpath:db/schema.sql

mybatis-plus:
  mapper-locations: classpath:mapper/**/*.xml
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0

decision:
  security:
    enabled: true
```

## 数据库表结构

| 表名 | 说明 |
|------|------|
| `rule_entity` | 规则实体表，存储六种资产的版本化定义，复合主键 (type, id, version) |
| `sys_user` | 系统用户表，RBAC 四级角色，BCrypt 密码加密 |
| `audit_log` | 审计日志表，记录所有配置变更，含变更前后快照 |
| `approval_record` | 审批记录表，记录每次审批操作历史 |
| `grayscale_config` | 灰度发布配置表，按百分比逐步提升流量 |

## 构建与运行

```bash
# 构建
mvn clean package -pl decision-admin -am

# 运行（需先启动 MySQL）
java -jar decision-admin/target/decision-admin-1.0.0-SNAPSHOT.jar
```

## 测试

```bash
mvn test -pl decision-admin
```

## 依赖关系

- **依赖**: `engine-core`
- **被依赖**: `decision-server`
- **外部依赖**: Spring Boot 3.2.5、MyBatis-Plus 3.5.6、MySQL、Spring Security
