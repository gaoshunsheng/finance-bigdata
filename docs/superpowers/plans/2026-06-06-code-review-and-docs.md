# Code Quality Review + Documentation Improvement

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 全面审查代码质量（找出 bug、安全漏洞、设计缺陷、代码异味），然后补全缺失的文档（模块 README、架构指南、开发者手册等）。

**Architecture:** 分两大阶段执行。阶段一（Task 1-6）为代码质量审查，按模块并行派发审查 agent，汇总为 `docs/REVIEW.md`。阶段二（Task 7-14）为文档补全，根据审查结果和代码分析编写缺失文档。

**Tech Stack:** Java 17 / Spring Boot 3.2.5 / MyBatis-Plus 3.5.6 / Vue 3 + TypeScript / Python FastAPI / Elasticsearch 8.13.4 / Flink 1.18.1

---

## Phase 1: Code Quality Review (代码质量审查)

### Task 1: Engine Core 代码审查

**Files (74 source + 14 test):**
- `engine-core/src/main/java/com/credit/platform/engine/core/` — 全部 14 个包
- 重点关注: `cache/` (398 行 VersionedRuleCache), `compiler/`, `executor/`, `flow/`, `trace/` (311 行 DecisionTracer)

- [ ] **Step 1: 审查 engine-core 所有模块**
  - 按包逐个审查: cache, compiler, compiler/model, executor, experiment, expression, flow, model, sandbox, scorecard, table, trace, tree, variable
  - 检查项: 正确性 bug、边界条件、并发安全、资源泄漏、异常处理、代码重复、命名规范
  - 特别关注 VersionedRuleCache (398行) 的线程安全和缓存一致性
  - 特别关注 FlowExecutionResult/DAGCompiler 的边界条件

- [ ] **Step 2: 记录发现到临时文件**
  - 按严重程度分类: 🔴 Critical / 🟠 Major / 🟡 Minor / 🔵 Info
  - 每条发现包含: 文件路径、行号、问题描述、修复建议

### Task 2: Engine Common + Decision SDK 审查

**Files:**
- `engine-common/src/main/java/com/credit/platform/engine/common/` — 11 files
- `decision-sdk/src/main/java/com/credit/platform/engine/sdk/` — 5 files

- [ ] **Step 1: 审查 engine-common**
  - crypto/FieldEncryptor — AES-256-GCM 实现，检查密钥管理、IV 处理
  - masking/SensitiveDataMasker — 脱敏规则完整性
  - exception/ — 异常层次是否合理
  - model/ — DTO 字段完整性

- [ ] **Step 2: 审查 decision-sdk**
  - DecisionClient — HTTP 客户端配置、超时、重试
  - autoconfigure — Spring Boot 自动配置条件
  - 错误处理和降级策略

### Task 3: Decision Server 审查

**Files:**
- `decision-server/src/main/java/com/credit/platform/server/` — 16 files
- 重点关注: service/DecisionService (284行), interceptor/ (3个拦截器), trace/

- [ ] **Step 1: 审查 decision-server 全部模块**
  - controller/DecisionController — API 参数校验、错误码
  - service/DecisionService — 决策执行编排逻辑、异常传播
  - interceptor/ — 认证、限流、审计拦截器正确性和顺序
  - config/ — ES 配置、线程池配置合理性
  - trace/ — RocketMQ 消息发布可靠性
  - channel/ — 渠道配置验证

- [ ] **Step 2: 记录发现**

### Task 4: Decision Admin 审查

**Files:**
- `decision-admin/src/main/java/com/credit/platform/admin/` — 39 files
- 重点关注: security/JwtService (284行), service/VersionDiffService (294行), service/GrayscalePublishService (217行), controller/PublishController (244行)

- [ ] **Step 1: 审查 decision-admin 全部模块**
  - controller/ (10个) — 统一响应格式、参数校验、权限检查
  - service/ (6个) — 业务逻辑正确性、事务管理、并发控制
  - security/ (11个) — JWT 密钥管理、密码存储、RBAC 实现、认证流程
  - mapper/ (5个) — SQL 注入风险、N+1 查询
  - model/ — 实体映射、字段验证

- [ ] **Step 2: 记录发现**

### Task 5: Data Platform + Model Platform 审查

**Files:**
- `data-platform/flink-jobs/` — 11 files
- `data-platform/data-service/` — 14 files
- `data-platform/data-governance/` — 14 files
- `model-platform/app/` — 46 Python files

- [ ] **Step 1: 审查 Flink 作业**
  - 5 个 Flink Job — checkpoint 配置、状态管理、窗口策略
  - KafkaSourceFactory/EventDeserializer — 反序列化容错
  - Sink 函数 (Redis/HBase/ES) — 连接池管理、异常处理、幂等性

- [ ] **Step 2: 审查 data-service 和 data-governance**
  - data-service controller/service — Trino 查询注入、连接池
  - data-governance — 数据分类规则、脱敏策略

- [ ] **Step 3: 审查 model-platform (Python)**
  - FastAPI endpoints — 输入验证、认证
  - 训练/评估/推理 — 模型版本管理、并发安全
  - gRPC 服务 — protobuf 定义、错误处理

### Task 6: Frontend 代码审查 + 汇总审查报告

**Files:**
- `decision-ui/src/` — 26 Vue + 21 TypeScript files

- [ ] **Step 1: 审查前端代码**
  - api/ — HTTP 客户端配置、token 刷新、错误拦截
  - stores/ — 状态管理、权限校验
  - router/ — 路由守卫、权限路由
  - components/ — 组件设计、props 验证、事件处理
  - views/ — 页面逻辑、表单验证、错误处理
  - types/ — TypeScript 类型定义完整性

- [ ] **Step 2: 汇总所有审查结果为 `docs/REVIEW.md`**
  - 按模块组织
  - 按严重程度排序
  - 包含统计摘要
  - 提供优先修复建议

---

## Phase 2: Documentation Improvement (文档完善)

### Task 7: 架构总览文档

**Files:**
- Create: `docs/architecture.md`

- [ ] **Step 1: 编写架构总览文档**
  内容包括:
  - 平台定位与核心能力
  - 系统架构图 (4 层: 前端层 → 决策服务层 → 引擎核心层 → 大数据平台层)
  - 模块依赖关系图
  - 数据流说明 (请求从 SDK → Server → Engine Core 的完整路径)
  - 关键设计决策 (compile-once-execute-many, 引擎无 Spring 依赖等)
  - 技术选型理由表

### Task 8: 各模块 README

**Files:**
- Create: `engine-core/README.md`
- Create: `engine-common/README.md`
- Create: `decision-server/README.md`
- Create: `decision-admin/README.md`
- Create: `decision-sdk/README.md`
- Create: `decision-ui/README.md` (替换 Vite 占位内容)
- Create: `model-platform/README.md`
- Create: `data-platform/README.md`
- Create: `data-platform/flink-jobs/README.md`
- Create: `data-platform/data-service/README.md`
- Create: `data-platform/data-governance/README.md`

- [ ] **Step 1: 编写每个模块的 README**
  每个 README 统一模板:
  - 模块简介 (一句话)
  - 功能列表
  - 包结构说明
  - 关键类说明
  - 配置项
  - 构建与运行命令
  - 测试说明

### Task 9: 开发者手册 (CONTRIBUTING.md)

**Files:**
- Create: `CONTRIBUTING.md`

- [ ] **Step 1: 编写开发者贡献指南**
  内容包括:
  - 开发环境要求 (Java 17, Node 20, Python 3.11, Maven, Docker)
  - 项目克隆与首次构建步骤
  - 分支管理策略 (main, feat/*, fix/*)
  - 代码规范 (Java: Alibaba Java Coding Guidelines, Vue: Vue Style Guide)
  - 提交信息格式 (Conventional Commits)
  - PR 流程与 Review 要求
  - 测试要求 (单元测试覆盖率、集成测试)
  - IDE 配置建议 (IntelliJ IDEA, VS Code)

### Task 10: API 使用指南

**Files:**
- Create: `docs/api-guide.md`

- [ ] **Step 1: 编写 API 叙述性文档**
  内容包括:
  - 快速开始: 获取 JWT Token
  - 决策执行 API (核心): 请求/响应示例、错误码说明
  - 规则管理 CRUD API: 创建/查询/更新/删除
  - 评分卡管理 API
  - 决策表管理 API
  - 决策流管理 API
  - 发布管理 API (含灰度发布)
  - 模型平台 API: 训练/评估/推理/监控
  - 数据服务 API: 特征查询/企业画像/报表
  - 每个端点附带 curl 示例

### Task 11: 数据字典文档

**Files:**
- Create: `docs/data-dictionary.md`

- [ ] **Step 1: 编写数据仓库数据字典**
  内容包括:
  - 数仓分层说明 (ODS → DWD → DWS → ADS)
  - ODS 层 7 张表结构 (loan_application, customer_info, credit_report 等)
  - DWD 层 5 张表结构
  - DWS 层 4 张汇总表结构
  - ADS 层 4 张应用表结构
  - HBase 3 张表结构
  - MySQL 核心业务表 (rule_entity 等)
  - ES 索引结构 (decision-log-{yyyy.MM})
  - 每张表: 字段名、类型、说明、来源

### Task 12: 变更日志

**Files:**
- Create: `CHANGELOG.md`

- [ ] **Step 1: 编写 CHANGELOG.md**
  基于 git log 回溯所有重要变更:
  - 从 git log 提取所有 merge commit
  - 按版本组织 (Unreleased / 1.0.0)
  - 分类: Added / Changed / Fixed / Security
  - 引用 commit hash

### Task 13: Frontend 开发者指南

**Files:**
- Create: `decision-ui/DEVELOPMENT.md`

- [ ] **Step 1: 编写前端开发指南**
  内容包括:
  - 技术栈说明 (Vue 3 + TypeScript + Ant Design Vue + ECharts + AntV X6)
  - 目录结构详解
  - 开发服务器启动 (`npm run dev`)
  - 组件开发规范 (Composition API + TypeScript)
  - 状态管理规范 (Pinia stores)
  - API 调用规范 (api/ 模块用法)
  - 路由和权限守卫
  - 构建与部署

### Task 14: 最终验证

- [ ] **Step 1: 验证所有文档链接有效**
  检查所有文档内部交叉引用、文件路径引用是否正确

- [ ] **Step 2: 验证 README.md 包含新文档索引**
  更新根 README.md 添加文档索引章节

- [ ] **Step 3: 提交所有变更**
  ```bash
  git add docs/ *.md engine-core/README.md engine-common/README.md \
    decision-server/README.md decision-admin/README.md decision-sdk/README.md \
    decision-ui/README.md decision-ui/DEVELOPMENT.md model-platform/README.md \
    data-platform/README.md data-platform/flink-jobs/README.md \
    data-platform/data-service/README.md data-platform/data-governance/README.md
  git commit -m "docs: code review + comprehensive documentation suite"
  ```
