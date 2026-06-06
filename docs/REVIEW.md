# 代码质量审查报告

> **审查日期**: 2026-06-06
> **审查范围**: 全平台 6 大模块，247+ 源文件
> **审查方法**: 按模块并行深度审查，逐文件阅读

---

## 统计摘要

| 严重程度 | 数量 | 说明 |
|---------|------|------|
| 🔴 Critical | 28 | 必须立即修复，存在安全漏洞或数据正确性风险 |
| 🟠 Major | 64 | 需要尽快修复，影响功能正确性或安全性 |
| 🟡 Minor | 50 | 建议修复，影响代码质量或可维护性 |
| 🔵 Info | 39 | 改善建议，不影响功能 |
| **合计** | **181** | |

### 按模块分布

| 模块 | 🔴 | 🟠 | 🟡 | 🔵 | 合计 |
|------|-----|-----|-----|-----|------|
| engine-core (74 files) | 7 | 13 | 11 | 7 | 38 |
| engine-common + decision-sdk (16 files) | 3 | 10 | 7 | 4 | 24 |
| decision-server (16 files) | 5 | 10 | 6 | 7 | 28 |
| decision-admin (39 files) | 6 | 12 | 8 | 8 | 34 |
| data-platform + model-platform (75+ files) | 4 | 9 | 7 | 4 | 24 |
| decision-ui (47 files) | 3 | 10 | 11 | 9 | 33 |

---

## 🔴 Critical 发现 (28 项)

### 1. 安全类 — 认证与授权

| # | 模块 | 文件 | 问题 |
|---|------|------|------|
| 1 | decision-server | `interceptor/AuthInterceptor.java:78` | `dev-` token 绕过认证，无 profile 保护，生产环境可被任何人利用 |
| 2 | decision-server | `interceptor/AuthInterceptor.java:68` | Token 验证用简单字符串比较，非 JWT，默认密钥 `change-me` 可猜 |
| 3 | decision-admin | `security/JwtService.java:41` | HMAC 密钥每次重启随机生成，所有已发 token 失效；多实例不共享密钥 |
| 4 | decision-admin | `security/JwtService.java:203` | 手写 JWT builder 不转义特殊字符，存在 JSON 注入风险 |
| 5 | decision-admin | `security/UserService.java:141` | 硬编码默认管理员密码 `admin123` |
| 6 | decision-admin | `security/SecurityConfig.java:33` | 安全层默认关闭 (`matchIfMissing=false`)，缺配置则无认证 |
| 7 | decision-admin | `controller/AuthController.java:148` | createUser 无服务层权限校验，仅靠 Spring Security URL 过滤 |
| 8 | decision-ui | `views/login/LoginView.vue:52` | 登录页明文显示默认管理员密码 `admin / admin123` |
| 9 | decision-ui | `utils/token.ts` | Refresh token 存 localStorage，XSS 可窃取 |

### 2. 安全类 — 输入与注入

| # | 模块 | 文件 | 问题 |
|---|------|------|------|
| 10 | decision-server | `interceptor/RateLimitInterceptor.java:73` | X-Forwarded-For 欺骗绕过限流 |
| 11 | decision-server | `interceptor/RateLimitInterceptor.java:28` | ConcurrentHashMap 无上限增长，配合 IP 欺骗可 OOM |
| 12 | decision-admin | `controller/AuthController.java:110` | Open redirect：`redirect` 参数未校验，可跳转到外部恶意站点 |
| 13 | data-platform | `service/ReportService.java:78` | Trino SQL 注入：`String.format` 拼接 date 参数 |
| 14 | model-platform | `main.py:18` | CORS `allow_origins=["*"]` + `allow_credentials=True` |
| 15 | model-platform | `inference_service.py:79` | pickle 反序列化可导致远程代码执行 |

### 3. 正确性类 — 并发与竞态

| # | 模块 | 文件 | 问题 |
|---|------|------|------|
| 16 | engine-core | `cache/VersionedRuleCache.java:109` | put() 竞态：saveToHistory 与 ref.set 不在同一锁内 |
| 17 | engine-core | `cache/VersionedRuleCache.java:194` | rollback() 无锁迭代 history，ConcurrentModificationException |
| 18 | engine-core | `cache/VersionedRuleCache.java:326` | invalidate() 移除 history 不获取锁 |
| 19 | engine-core | `cache/VersionedRuleCache.java:382` | versionLocks 无限增长，永不清理 |
| 20 | decision-server | `interceptor/RateLimitInterceptor.java:48` | 滑动窗口计数器竞态，允许突发流量超限 |

### 4. 正确性类 — 逻辑错误

| # | 模块 | 文件 | 问题 |
|---|------|------|------|
| 21 | engine-core | `flow/CompiledDAG.java:88` | DAG 无环检测，循环图导致无限循环 |
| 22 | engine-core | `model/DefaultModelServiceClient.java:220` | 手写 JSON 解析器在边界情况产生错误结果 |
| 23 | decision-sdk | `DecisionClient.java:212` | 手写 JSON 解析器 key 匹配可能错位 |
| 24 | decision-sdk | `DecisionClient.java:171` | 手写 JSON 序列化器未处理 \r \t \b \f 等控制字符 |
| 25 | decision-admin | `controller/AuthController.java:45` | `user.setLastLoginAt()` 未持久化到数据库 |
| 26 | decision-server | `controller/DecisionController.java` → `service/DecisionReportService.java:56` | getDailyStats 用 CalendarInterval.Month，返回月度数据 |
| 27 | model-platform | `config/TrinoConfig.java:48` | Trino 无连接池，每次查询新建 JDBC 连接 |
| 28 | decision-ui | `api/data.ts:3` | 双重 `/api/v1` 前缀导致所有数据服务请求 404 |

---

## 🟠 Major 发现摘要 (64 项)

### engine-core (13 项)

| # | 文件 | 问题 |
|---|------|------|
| 1 | `compiler/RuleCompiler.java:258` | compileActions 循环中 params 被覆盖，只保留最后一个 |
| 2 | `compiler/model/ComparisonConditionNode.java:78` | Comparable 强转可能 ClassCastException |
| 3 | `executor/ExecutionContext.java:86` | getVariable 泛型类型擦除，CCE catch 无效 |
| 4 | `experiment/ExperimentSplitter.java:26` | groups 为空时 IndexOutOfBoundsException |
| 5 | `experiment/ExperimentSplitter.java:45` | hashCode fallback 破坏实验一致性 |
| 6 | `expression/DaysBetweenFunction.java:83` | Date→LocalDate 用系统默认时区，分布式部署结果不一致 |
| 7 | `model/DefaultModelServiceClient.java:52` | 重试循环 off-by-one |
| 8 | `model/DefaultModelServiceClient.java:123` | 负超时值导致 IllegalArgumentException |
| 9 | `model/DefaultModelServiceClient.java:197` | escapeJson 不处理所有特殊字符 |
| 10 | `model/SHAPExplainer.java:127` | 魔法数字 100.0/2.0，特征范围非 0-100 时 SHAP 近似错误 |
| 11 | `scorecard/CompiledScorecard.java:113` | findBin 声称二分查找实际线性扫描 |
| 12 | `scorecard/CompiledScorecard.java:192` | Cutoff.decide() 忽略 pass 阈值 |
| 13 | `variable/VariableEngine.java:107` | 派生变量解析不按依赖顺序 |

### engine-common + decision-sdk (10 项)

| # | 文件 | 问题 |
|---|------|------|
| 1 | `sdk/DecisionClient.java:275` | HttpURLConnection 未 disconnect |
| 2 | `sdk/DecisionClient.java:285` | getErrorStream() 返回 null 导致 NPE |
| 3 | `sdk/DecisionClient.java:158` | 重试逻辑不区分可重试/不可重试错误 |
| 4 | `common/crypto/FieldEncryptor.java:54` | AES 密钥 bytes 未清零 |
| 5 | `common/crypto/FieldEncryptor.java:166` | isEncrypted() 误判任何长 Base64 字符串 |
| 6 | `common/crypto/FieldEncryptor.java:39` | 无密钥轮转支持 |
| 7 | `common/masking/SensitiveDataMasker.java:40` | 不覆盖 15 位旧身份证 |
| 8 | `common/masking/SensitiveDataMasker.java:219` | registerStrategy() 线程不安全 |
| 9 | `common/model/DecisionRequest.java:44` | 无防御性拷贝、无参数校验 |
| 10 | `common/model/DecisionResponse.java:53` | extra Map 可被外部修改 |

### decision-server (10 项)

| # | 文件 | 问题 |
|---|------|------|
| 1 | `controller/DecisionController.java:54` | DecisionRequestDto 无任何输入校验 |
| 2 | `controller/DecisionController.java:60` | X-Channel header 未校验合法性 |
| 3 | `service/DecisionService.java:269` | Decision ID 跨重启/实例不唯一 |
| 4 | `service/DecisionService.java:118` | 异常吞没：返回 MANUAL 而非错误 |
| 5 | `service/DecisionService.java:193` | 申请人 PII 明文存 ES |
| 6 | `config/WebMvcConfig.java:26` | **DecisionAuditInterceptor 未注册** — 审计功能完全无效 |
| 7 | `service/DecisionService.java:214` | getReport 按 traceId 查但 API 传 decisionId |
| 8 | `interceptor/RateLimitInterceptor.java:59` | 限流 per-key 而非全局 |
| 9 | `channel/ChannelConfig.java` | 死代码 — 从未注册为 Bean |
| 10 | `config/ElasticsearchConfig.java:32` | RestClient 未关闭，资源泄漏 |

### decision-admin (12 项)

| # | 文件 | 问题 |
|---|------|------|
| 1 | `service/ApprovalService.java:166` | ID 生成 currentTimeMillis+AtomicLong，重启/多实例碰撞 |
| 2 | `service/RuleRepository.java:32` | 同上，rule ID 碰撞风险 |
| 3 | `service/GrayscalePublishService.java:73` | 无 @Transactional，并发竞态 |
| 4 | `service/ApprovalService.java:39` | 无 @Transactional，部分状态不一致 |
| 5 | `service/RuleAdminService.java:153` | RBAC 用 enum ordinal 比较，脆弱 |
| 6 | `controller/AuthController.java:152` | Role.valueOf() 异常未处理 |
| 7 | `service/VersionDiffService.java:161` | 手写 JSON 解析器不处理转义引号 |
| 8 | `security/JwtService.java:236` | parseSimpleJson 按逗号分割，值含逗号时解析错误 |
| 9 | `security/JwtService.java:39` | Refresh token 无过期清理，内存泄漏 |
| 10 | `controller/RuleController.java:56` | CreateRequest 无校验注解 |
| 11 | `controller/AuthController.java:169` | changePassword 无身份匹配校验 |
| 12 | `controller/PublishController.java:38` | operator 来自请求体，未验证与认证用户一致 |

### data-platform + model-platform (9 项)

| # | 文件 | 问题 |
|---|------|------|
| 1 | `flink/TransactionSummaryJob.java:174` | AggregateFunction.getResult 丢失 customerId |
| 2 | `flink/RedisFeatureSink.java:52` | Redis 连接失败后 sink 永久禁用 |
| 3 | `flink/HBaseFeatureSink.java:68` | RowKey 用时间戳，非幂等，重放产生重复 |
| 4 | `service/ReportController.java:37` | date 参数无校验，SQL 注入 |
| 5 | `service/EnterpriseProfileService.java:80` | 硬编码假数据 |
| 6 | `service/FeatureController.java:49` | featureKeys 无数量限制，DoS 向量 |
| 7 | `model-platform/training.py:11` | 同步训练阻塞 async 事件循环 |
| 8 | `model-platform/trainer.py:437` | 模型纯内存存储，重启丢失 |
| 9 | `model-platform/config.py:22` | 硬编码数据库密码 |

### decision-ui (10 项)

| # | 文件 | 问题 |
|---|------|------|
| 1 | `api/request.ts:66` | Token 刷新竞态：pending 请求可能用过期 config |
| 2 | `router/index.ts:159` | catch-all 路由静默重定向，无 404 页面 |
| 3 | `router/index.ts:170` | 路由守卫只检查 token 存在性，不验证有效性 |
| 4 | `views/login/LoginView.vue:84` | Open redirect：redirect 参数未过滤 `//` 开头 |
| 5 | `components/flow/FlowDesigner.vue:296` | **无法创建连线**：onMouseUp 不检测目标节点 |
| 6 | `components/scorecard/ScorecardEditor.vue:203` | __total 魔法属性代替 computed |
| 7 | `components/table/TableEditor.vue:70` | row-key='_idx' 引用不存在的属性 |
| 8 | `api/admin.ts:5` | 20+ API 函数全用 `any` 类型 |
| 9 | `views/sandbox/SandboxView.vue:152` | 沙箱用 mock 数据，从未调用 API |
| 10 | `views/rule/RuleEditView.vue:102` | 保存显示成功但不实际调 API |

---

## 🟡 Minor 发现 (50 项)

<details>
<summary>点击展开完整列表</summary>

### engine-core (11 项)
- `cache/VersionedRuleCache.java:113` — put() 可能重复创建 AtomicReference
- `compiler/RuleCompiler.java:39` — ObjectMapper 每次新建，应共享
- `experiment/ExperimentMetrics.java:54` — calculatePValue 不验证 group 归属
- `experiment/ExperimentConfig.java:20` — 流量比例不验证总和为 1
- `flow/CompiledDAG.java:216` — buildResult boolean 参数含义模糊
- `sandbox/SandboxRunner.java:170` — diff 检测逻辑嵌套过深难理解
- `model/DefaultModelServiceClient.java:68` — isHealthy() 不 disconnect
- `flow/FlowNode.java:35` — getConfig 泛型擦除 CCE catch 无效
- `scorecard/ScorecardCompiler.java:129` — extractNumber 对非数字节点返回 0.0
- `table/CompiledDecisionTable.java:111` — TableRow 未防御性拷贝 result Map
- `variable/VariableResolveContext.java:21` — 不处理 null requestParams

### engine-common + decision-sdk (7 项)
- `sdk/DecisionClient.java:126` — 使用 HttpURLConnection 而非 HttpClient
- `crypto/FieldEncryptor.java:88` — encrypt(null) 返回 null 不一致
- `exception/DecisionEngineException.java` — 无 errorCode 字段
- `exception/` — 缺少常见异常子类
- `masking/SensitiveDataMasker.java:53` — Email 正则过严
- `masking/SensitiveDataMasker.java:132` — 单字名不脱敏
- `sdk/DecisionClientConfig.java:106` — Builder.build() 不验证必填字段

### decision-server (6 项)
- `interceptor/DecisionAuditInterceptor.java:85` — 序列化失败静默吞没
- `interceptor/DecisionAuditInterceptor.java:34` — 静态 ObjectMapper 不共享 Spring 配置
- `service/DecisionService.java:61` — requestCounter 不重置
- `repository/DecisionLogRepository.java:35` — 静态 ObjectMapper 与 Spring 配置不一致
- `controller/DecisionController.java:97` — Health 端点受限流影响
- `service/DecisionService.java:246` — rejectReason 存入 riskLevel 字段语义错误

### decision-admin (8 项)
- `service/RulePublishService.java:51` — fullPublish 无安全守卫
- `model/GrayscaleConfig.java:87` — configId 用 currentTimeMillis 可能碰撞
- `service/AuditLogService.java:26` — 审计日志从未被调用
- `repository/AuditLogRepository.java:64` — 分页 offset 可溢出
- `controller/` × 7 — 代码高度重复，应抽象为通用 Controller
- `service/RuleAdminService.java:36` — createdBy 硬编码 'system'
- `controller/VariableController.java` — 缺少 newVersion 端点
- `service/RuleAdminService.java:119` — promote() 跳过审批步骤

### data-platform + model-platform (7 项)
- `flink/ApplyFreq1mJob.java:137` — System.err.println 代替 logger
- `flink/TransactionSummaryJob.java:145` — 静默吞没解析错误
- `service/EnterpriseProfileService.java:220` — 原始 Map 类型不安全
- `flink/ElasticsearchAlertSink.java:78` — 同步 HTTP 导致背压
- `model-platform/main.py:33` — 弃用 on_event 装饰器
- `model-platform/data_prep.py:59` — positive_count 对非数字标签错误
- `model-platform/grpc_service.py:245` — gRPC 用不安全端口

### decision-ui (11 项)
- `api/request.ts:40` — 响应拦截器解包 data，破坏类型一致性
- `views/publish/PublishCenterView.vue:135` — 灰度操作仅修改本地状态
- `components/flow/FlowDesigner.vue:57` — selectedNode 非空断言
- `components/flow/FlowDesigner.vue:346` — onNodePropChange 空函数
- `views/analytics/AnalyticsView.vue:13` — 仪表盘全部硬编码静态值
- `views/analytics/AnalyticsView.vue:223` — markLine 配置位置错误
- `views/DashboardView.vue:112` — `as any` 绕过类型检查
- `router/index.ts:194` — title 可能为 undefined
- `components/flow/FlowDesigner.vue:245` — 模块级 idSeq 跨实例共享
- `api/request.ts:16` — pendingRequests 刷新失败时未清理
- `views/rule/RuleListView.vue:9` — 所有列表视图用空数组，从未调 API

</details>

---

## 🔵 Info 建议 (39 项)

<details>
<summary>点击展开完整列表</summary>

### engine-core (7 项)
- 5 个 Compiler 类重复 requiredText/optionalText/requiredNode 方法 → 提取基类
- VersionedRuleCache 398 行 → 拆分 VersionHistoryManager
- DecisionTracer 311 行 → Builder 模式或注解处理器
- DefaultModelServiceClient 用 HttpURLConnection → 考虑 HttpClient
- ExperimentSplitter 用 MD5 → 审计关注，考虑 SHA-256
- InFunction 每次解析 CSV → 缓存解析结果
- VariableRegistry getByLayer 线性扫描 → 索引 Map

### engine-common + decision-sdk (4 项)
- FieldEncryptor byte[] 构造函数设计说明
- registerStrategy() 可覆盖内置策略
- DecisionClient 无 Closeable 接口
- DecisionAutoConfiguration 可加防御性检查

### decision-server (7 项)
- DecisionRequestDto 应提取到独立文件
- MqTracePublisher 反射调用 RocketMQ — 已知权衡
- RocketMQReloadListener 传 null loader — 需确认是否故意
- EngineCoreConfig TracePublisher bean 冲突
- DecisionReportService Arrays.asList 不必要
- AuthInterceptor 错误响应手动拼接 JSON
- DecisionService 构造函数部分参数无 null 检查

### decision-admin (8 项)
- User.hasPermission 从未使用
- ApprovalService.getPendingApprovals 忽略 approver 参数
- GrayscalePublishService DEFAULT_RAMP_STEPS 无 75%
- RuleEntity.setContent() 副作用更新 updatedAt
- application.yml 默认密码 root123
- RulePublishService 传 'all' 字符串给被忽略的参数
- VersionDiffService parseJson 失败返回空 map
- RuleMapper 全部使用 #{} 参数绑定（正面发现）

### data-platform + model-platform (4 项)
- 5 个 Flink Job 留有 debug print
- FeatureQueryService 硬编码 feature 类型列表
- model-platform audit router 重复 tags
- model-platform sanitize_dataset 仍含 DataFrame

### decision-ui (9 项)
- api/admin.ts 命名和组织问题
- publish API 空 operator
- app store sidebar 状态不持久
- NProgress 缺类型声明
- report search TODO stub
- FlowDesignView 无保存功能
- ConditionBuilder 导出接口应独立文件
- analytics dateRange 类型为 any
- VariableListView 仅客户端创建变量

</details>

---

## 优先修复建议

### P0 — 立即修复（安全风险）

1. **AuthInterceptor dev-token 绕过** — 生产环境认证形同虚设
2. **JwtService 密钥管理** — 重启失效、多实例不共享、JSON 注入
3. **SecurityConfig 默认关闭** — 缺配置即无安全
4. **默认管理员密码** — `admin123` 硬编码
5. **Trino SQL 注入** — date 参数拼接
6. **pickle 反序列化 RCE** — 模型平台
7. **RateLimiter X-Forwarded-For 欺骗** — 限流可绕过
8. **前端登录页密码泄露** — 明文显示默认密码
9. **前端 Refresh token XSS** — localStorage 存储

### P1 — 尽快修复（功能正确性）

1. **VersionedRuleCache 竞态条件** — 4 处并发 bug
2. **DAG 无环检测** — 可无限循环
3. **DecisionAuditInterceptor 未注册** — 审计功能完全无效
4. **getDailyStats 返回月度数据** — CalendarInterval.Month 错误
5. **手写 JSON 解析/序列化** — 3 处（SDK、DecisionClient、ModelServiceClient）
6. **DecisionService 异常吞没** — 返回 MANUAL 而非错误
7. **前端 API 双重前缀** — data.ts 所有请求 404
8. **前端列表/编辑视图未联调 API** — 多个页面数据不持久

### P2 — 计划修复（代码质量）

1. **缺少 @Transactional** — GrayscalePublishService、ApprovalService
2. **Operator 字段伪造** — PublishController
3. **ID 生成碰撞风险** — 多处 currentTimeMillis+AtomicLong
4. **派生变量依赖顺序** — VariableEngine
5. **FieldEncryptor 无密钥轮转** — 合规风险
6. **前端 TypeScript 类型缺失** — api/admin.ts 全 any

---

## 架构级建议

1. **手写 JSON 全部替换为 Jackson** — DecisionClient、ModelServiceClient、VersionDiffService、JwtService 中的手写 JSON 解析/序列化应统一使用 Jackson
2. **SDK 迁移到 java.net.http.HttpClient** — 替换 HttpURLConnection，获得连接池、HTTP/2、async 支持
3. **前端 UI 联调** — 多个视图组件已构建但未接入后端 API，需要逐个对接
4. **审计日志全链路** — AuditLogService、DecisionAuditInterceptor 均未实际生效
5. **密钥管理统一** — JWT、AES、数据库密码均需从配置中心/Secret 管理加载
