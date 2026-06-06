# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/), and this project adheres to [Semantic Versioning](https://semver.org/).

## [1.0.0] - 2026-06-06

### Added

#### 决策引擎核心 (engine-core)

- 初始化 Maven 多模块骨架 + 引擎基础层 (`93053d0`)
- 规则引擎核心 — AST 编译器 + 执行器 (`866ffda`)
- 评分卡引擎 — 多特征分箱加权评分 + 阈值判定 (`3099e87`)
- 决策表引擎 — 二维表条件匹配 + 通配符 (`7ae60ae`)
- 决策树引擎 — 嵌套条件树深度优先遍历 (`9018040`)
- DAG 决策流引擎 — 拓扑排序 + 条件边路由 (`6d25f28`)
- 变量引擎 — 4层分层 + 注册表 + 依赖解析 + Provider 抽象 (`afd03c1`)
- 可解释性追踪引擎 — TraceEntry + DecisionTracer + TraceReporter 四级报告 (`65ba401`)
- 热加载与版本化缓存 — CopyOnWrite + 版本快照 + 一键回滚 (`61f8503`)
- AB 实验引擎 — 一致性哈希分流 + 指标收集 + p-value 统计 (`6d46ce3`)

#### 决策服务层 (decision-server / decision-admin / decision-sdk)

- Spring Boot 决策服务层 — REST API + 鉴权限流 + 线程池 (`ad6588a`)
- 管理后台 — 规则/评分卡/决策流/变量 CRUD + 发布流程 (`ecf0d7f`)
- 模型服务客户端 + SHAP 解释器 + 决策 SDK 及 Spring Boot Starter (`6d8489a`)
- 沙箱回测引擎 (`f5a669f`)
- 发布工作流 — 审批、灰度发布、版本对比、回滚 (`b661020`)
- 数据库持久化 — MyBatis-Plus + MySQL (`d965b18`)
- RocketMQ 追踪发布器 — 基于反射实现，无编译时依赖 (`6ee1915`)
- RocketMQ 配置热重载监听器 — JSON 消息解析 (`6ee1915`)

#### 安全与权限

- RBAC + JWT + Spring Security + 审计日志 (`ac3cab5`)
- 敏感数据脱敏工具 (`0c39f57`)
- AES-256-GCM 字段级加密 (`4b560c6`)
- 安全审计增强 (`f32e233`)

#### 管理前端 (decision-ui)

- Vue 3 管理后台项目脚手架 (`f2abad6`)
- 可视化编辑器 — 规则、评分卡、决策表、决策流 DAG、变量 (`836312a`)
- 业务页面与数据分析看板 (`3cd2940`)

#### 模型平台 (model-platform)

- 模型平台 — 样本管理、模型训练、评估、部署与监控 (`356ec25`)

#### 大数据平台

- 大数据平台 — Flink 作业、Hive 数仓、数据治理、管道配置 (`2a79e72`)
- Trino 联邦查询 + DolphinScheduler 编排调度 (`60f380c`)
- ES 持久化、前端对齐、实时数据 Sink (`e0d0145`)

#### 性能测试与质量保障

- JMeter 基准测试框架 + 43 项引擎性能测试 (`94df9d2`)
- 15 项端到端负载测试 — QPS>=500, P99<3s 验证通过 (`ff1900c`)
- 集成测试 (15 项端到端) + 性能基准测试 (5 项) — 全部通过 (`762284d`)
- 替换 Mock 为真实数据查询 + 694 项测试全覆盖 (`d0a5649`)
  - 企业画像服务: Redis -> HBase 降级链 + ES 聚合
  - 报表服务: Trino JDBC 查询 ADS 层 + ES 聚合
  - 数据质量监控: 实现唯一性/一致性/时效性维度检查
  - 覆盖 data-service (34)、decision-admin (52)、data-governance (100)、decision-server (25)、model-platform (41) 测试

#### 部署与运维

- 部署文档 — Dockerfile、docker-compose、K8s YAML (`76ef113`)
- 运维手册、OpenAPI 3.0 文档、培训材料、灰度发布方案 (`7013b54`)
- decision-ui Dockerfile (Node 构建 -> Nginx 服务, 端口 3000) (`6ee1915`)
- Filebeat 容器 + 配置 (容器日志 -> Kafka -> ES) (`6ee1915`)
- K8s Ingress 路由 /data 至 data-service (`6ee1915`)
- 项目根目录 README.md 完整文档 (`6ee1915`)
- `.env.example` 环境变量示例文件 (`6e4519e`)
- MySQL 自动初始化脚本 `init-sql/01_init_schema.sql` — 覆盖 8 个数据库/表 (`6e4519e`)

### Changed

- Flink JobManager 端口从 8081 调整为 8088，避免与 decision-admin 冲突 (`6e4519e`)
- Dockerfile.java 增加 data-platform 子模块 pom.xml (flink-jobs, data-service, data-governance) (`6ee1915`)
- elasticsearch-java 版本管理迁移至根 POM dependencyManagement (`6ee1915`)
- docker-compose 增加 decision-ui、Filebeat 服务配置 (`6ee1915`)
- model-platform 配置支持 MODEL_PLATFORM_* 和 APP_*/DATABASE_URL 双命名规范 (`6e4519e`)
- model-platform Dockerfile 改用 APP_HOST/APP_PORT/APP_WORKERS 环境变量 (`6e4519e`)
- Aviator WARN 日志修复 (`f5a669f`)

### Fixed

- Flink JobManager 与 decision-admin 端口冲突 (`6e4519e`)
- MySQL 初始化 SQL 缺失问题 (`6e4519e`)
- model-platform 环境变量命名不匹配 (`6e4519e`)
- model-platform API 路由导入兼容 FastAPI >= 0.133 (`d0a5649`)
- model-platform data_prep filter_params 类型不匹配 (`d0a5649`)
- model-platform 训练模型记录序列化错误 (`d0a5649`)
- model-platform 批量预测结果提取错误 (`d0a5649`)
- decision-server 测试修复 (`f5a669f`)

## [0.1.0] - 2026-06-03

### Added

- 项目初始化 — OpenSpec 设计文档和规格 (`dbf4d7c`)
- `.worktrees` 加入 `.gitignore` (`d15fa38`)
