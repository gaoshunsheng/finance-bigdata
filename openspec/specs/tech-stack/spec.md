# 技术选型规格

> 版本: 1.0 | 状态: Draft | 创建: 2026-06-03 | 关联: [平台总览](../platform-overview/spec.md)

---

## 1. 总览

| # | 决策项 | 选型 | 备选 | 选型理由 |
|---|--------|------|------|----------|
| 1 | 后端语言 | Java 17+ | — | 团队技术栈, 金融行业标准 |
| 2 | 后端框架 | Spring Boot 3.x | — | 生态成熟, 企业级首选 |
| 3 | ORM | MyBatis-Plus | JPA | 灵活的 SQL 控制, 金融团队熟悉 |
| 4 | 前端框架 | Vue 3 + TypeScript | React | 团队技术栈 |
| 5 | UI 组件库 | Ant Design Vue | Element Plus | 金融场景组件丰富 |
| 6 | DAG 画布 | AntV X6 | LogicFlow | 蚂蚁开源, 金融场景验证 |
| 7 | 表格编辑 | Handsontable | AG Grid | Excel 风格, 开箱即用 |
| 8 | 表达式引擎 | Aviator | MVEL | 阿里开源, 金融验证, 编译缓存, 安全 |
| 9 | 本地缓存 | Caffeine | Guava Cache | 性能最优, Java 标准缓存 |
| 10 | 规则存储 | MySQL 8.0 | PostgreSQL | 团队熟悉, 运维成熟 |
| 11 | 决策日志 | Elasticsearch 8.x | — | 全文检索, JSON 友好, ILM 管理 |
| 12 | 实时特征 | Redis Cluster 7.x | — | 亚毫秒延迟, 集群高可用 |
| 13 | 历史特征 | HBase 2.x | — | 海量 KV, 高并发随机读写 |
| 14 | 消息队列 | RocketMQ / Kafka | — | RocketMQ (热加载广播); Kafka (数据管道) |
| 15 | 数据湖 | HDFS | — | Hadoop 生态标准 |
| 16 | 数据仓库 | Hive 3.x | — | SQL on HDFS 标准 |
| 17 | 实时计算 | Flink 1.18+ | — | 金融行业标准, 流批一体 |
| 18 | 离线计算 | Spark 3.x | — | 大规模批处理标准 |
| 19 | 任务调度 | DolphinScheduler | Airflow | 国产开源, 中文友好, 金融验证 |
| 20 | 即席查询 | Trino (Presto) | — | 多数据源联邦查询 |
| 21 | 模型推理 | PMML4S / ONNX Runtime | — | 标准模型格式, 高性能推理 |
| 22 | 容器化 | Docker + Kubernetes | — | 标准化部署运维 |
| 23 | JSON 处理 | Jackson | — | Spring Boot 默认, 性能最优 |
| 24 | 接口规范 | RESTful + OpenAPI 3.0 | gRPC | 通用性, 前端友好 |

---

## 2. 关键选型决策记录

### 2.1 为什么自研规则引擎（不选 LiteFlow / Drools / URule）

| 方案 | 否决原因 |
|------|----------|
| LiteFlow | 组件编排引擎, 非规则引擎。无评分卡/决策表/规则匹配, 管理后台完全自研, 开发量未显著减少 |
| Drools | 学习曲线极陡 (DRL/RETE), 国内人才稀缺, Spring Boot 3 兼容问题, 最好工具需 Red Hat PAM 商业授权 |
| URule Pro | 开源版 2018 停更, Pro 版商业授权, 供应商锁定风险, 社区极小 |
| **自研** | **完全可控, 无授权风险, 可按需定制, 但开发量最大** |

### 2.2 为什么用 AST 解释器而非字节码生成

- 决策引擎瓶颈在数据获取 (网络 IO 800ms), 不在规则执行 (CPU <50ms)
- AST 解释器性能足够 (200 条规则 ≈ 5-10ms), 且天然支持可解释性追踪
- 字节码生成 (ASM/Javassist) 开发复杂度高, 调试困难, 安全性受限

### 2.3 为什么选 Aviator 而非 MVEL / Groovy

| 维度 | Aviator | MVEL | Groovy |
|------|---------|------|--------|
| 安全性 | 高 (沙箱模式) | 中 | 低 (历史漏洞多) |
| 性能 | 编译缓存后接近原生 | 略快 | 较慢 |
| 金融验证 | ✅ 阿里金融体系验证 | 一般 | 一般 |
| 自定义函数 | 支持 | 支持 | 支持 |
| 体积 | 轻量 | 轻量 | 重量 |

### 2.4 为什么选 AntV X6 做 DAG 画布

| 维度 | AntV X6 | LogicFlow | jsPlumb |
|------|---------|-----------|---------|
| 开源方 | 蚂蚁集团 | 百度 | 商业+开源 |
| 金融场景验证 | ✅ 众多金融项目 | ✅ 有案例 | 一般 |
| 自定义节点 | 强 | 强 | 中 |
| 社区活跃度 | 高 | 中 | 中 |
| MIT 协议 | ✅ | ✅ | ⚠️ 部分 |

---

## 3. 版本基线

| 技术 | 版本 | JDK 要求 |
|------|------|----------|
| Java | 17 LTS | — |
| Spring Boot | 3.2.x | JDK 17+ |
| Vue | 3.4+ | — |
| Flink | 1.18+ | JDK 11+ |
| Spark | 3.5.x | JDK 17+ |
| Hadoop | 3.3.x | JDK 8+/11+ |
| Hive | 3.1.x | JDK 8+ |
| HBase | 2.5.x | JDK 8+ |
| Redis | 7.x | — |
| Elasticsearch | 8.x | JDK 17+ |
| MySQL | 8.0 | — |
| Kafka | 3.6+ | JDK 17+ |
| Aviator | 5.4+ | JDK 8+ |
