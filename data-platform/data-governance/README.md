# data-governance

> 数据治理框架，提供元数据管理、数据血缘分析、五维数据质量监控、数据安全和生命周期管理。

## 功能概述

- **元数据管理** — 表级和列级元数据采集与存储
- **数据血缘分析** — 解析 SQL/ETL 脚本，自动构建表级血缘关系图
- **五维数据质量** — 完整性、准确性、一致性、及时性、唯一性五维质量检测
- **数据安全** — 数据分级分类和脱敏处理
- **生命周期管理** — 数据表的生命周期和过期清理策略

## 包结构

```
src/main/java/com/credit/platform/data/governance/
├── metadata/               — 元数据管理
│   ├── MetadataCollector.java    — 元数据采集器
│   ├── TableMetadata.java        — 表级元数据
│   └── ColumnMetadata.java       — 列级元数据
├── lineage/                — 数据血缘分析
│   ├── LineageAnalyzer.java      — 血缘分析器（解析 SQL 依赖）
│   ├── LineageGraph.java         — 血缘关系图
│   ├── LineageNode.java          — 血缘节点（表/字段）
│   └── LineageEdge.java          — 血缘边（源 -> 目标）
├── quality/                — 五维数据质量
│   ├── QualityMonitor.java       — 质量监控器（核心）
│   ├── QualityRule.java          — 质量规则定义
│   └── QualityViolation.java     — 质量违规记录
├── security/               — 数据安全
│   ├── DataClassifier.java       — 数据分级分类器
│   └── DataMaskingService.java   — 数据脱敏服务
└── lifecycle/              — 生命周期管理
    └── LifecycleManager.java     — 生命周期管理器
```

## 关键类说明

| 类名 | 说明 |
|------|------|
| `MetadataCollector` | 元数据采集器，采集表级和列级元数据 |
| `TableMetadata` | 表级元数据模型，包含表名、列信息、分区信息等 |
| `ColumnMetadata` | 列级元数据模型，包含列名、数据类型、注释等 |
| `LineageAnalyzer` | 数据血缘分析器，解析 INSERT/FROM/JOIN SQL 提取血缘关系，支持 ODS/DWD/DWS/ADS 层级推断 |
| `LineageGraph` | 血缘关系图，由节点和边构成的有向图 |
| `QualityMonitor` | 五维数据质量监控器，支持规则注册、实时检查和违规记录 |
| `QualityRule` | 质量规则定义，包含维度、表达式、严重级别 |
| `QualityViolation` | 质量违规记录 |
| `DataClassifier` | 数据分级分类器，识别敏感数据级别 |
| `DataMaskingService` | 数据脱敏服务 |
| `LifecycleManager` | 数据表生命周期管理 |

## 五维数据质量

| 维度 | 英文 | 检查内容 | 规则表达式示例 |
|------|------|---------|--------------|
| 完整性 | COMPLETENESS | 字段空值率 | `NOT NULL` |
| 准确性 | ACCURACY | 枚举值、范围、正则校验 | `>=0`、`REGEX:\\d+`、`ENUM:A,B,C` |
| 一致性 | CONSISTENCY | 跨表关联一致性 | `REF:dim_product.product_id` |
| 及时性 | TIMELINESS | 数据产出 SLA | `SLA:08:00`、`SLA:2H` |
| 唯一性 | UNIQUENESS | 主键重复检测 | 自动检测 |

## 数据血缘层级推断

| 表名前缀 | 推断层级 |
|---------|---------|
| `ods.*` | ODS（操作数据层） |
| `dwd.*` | DWD（明细数据层） |
| `dws.*` | DWS（汇总数据层） |
| `ads.*` | ADS（应用数据层） |

## 构建与运行

```bash
# 构建
mvn clean package -pl data-platform/data-governance -am
```

本模块为 Java 库，不可独立运行，由 data-service 或其他模块引用。

## 测试

```bash
mvn test -pl data-platform/data-governance
```

## 依赖关系

- **依赖**: `engine-common`、Jackson
- **被依赖**: `data-service`（集成使用）
