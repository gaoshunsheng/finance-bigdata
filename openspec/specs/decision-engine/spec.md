# 决策引擎规格

> 版本: 1.0 | 状态: Draft | 创建: 2026-06-03 | 关联: [平台总览](../platform-overview/spec.md)

---

## 1. 概述

### 1.1 定位

自研决策引擎是企业征信大数据平台的核心模块，负责执行信贷全生命周期的自动化决策。引擎完全自主可控，不依赖第三方规则引擎商业授权。

### 1.2 设计原则

| # | 原则 | 说明 |
|---|------|------|
| 1 | 编译一次，执行多次 | 规则 JSON → AST → 缓存 → 直接执行编译产物 |
| 2 | 数据获取与规则执行分离 | 变量引擎负责取数据，规则引擎只负责判数据 |
| 3 | 决策流驱动一切 | 所有请求进入 DAG，无游离规则 |
| 4 | 追踪是第一等公民 | 架构层面内置可解释性，非事后打补丁 |
| 5 | 引擎与业务解耦 | 核心引擎不知道业务语义，业务在上层组件实现 |

### 1.3 性能目标

| 指标 | 目标 |
|------|------|
| 单笔决策 P99 延迟 | < 3 秒 |
| 引擎自身开销 | < 50ms |
| 单机 QPS | ≥ 500 |
| 规则容量 | ≥ 500 条在线规则 |
| 评分卡容量 | ≥ 20 个在线评分卡 |
| 决策流容量 | ≥ 50 个在线决策流 |

---

## 2. 引擎核心架构

### 2.1 编译执行模型

```
┌─────────────────────────────────────────────────────────────────────┐
│  编译期 (Compile Time)                                              │
│                                                                     │
│  规则JSON ──▶ JSON Parser ──▶ AST Builder ──▶ Optimizer ──▶ Cache │
│              (Jackson)       (构建抽象       (常量折叠    (Caffeine) │
│                               语法树)        短路标记               │
│                                              变量收集)              │
│                                                                     │
│  产出: CompiledRule / CompiledScorecard / CompiledDAG               │
│  特性: 不可变对象 (Immutable), 线程安全, 可序列化                     │
│                                                                     │
├─────────────────────────────────────────────────────────────────────┤
│  执行期 (Runtime)                                                   │
│                                                                     │
│  请求 ──▶ Cache命中 ──▶ 创建ExecutionContext ──▶ DAG执行 ──▶ 返回  │
│           编译产物     (每次请求隔离)            (拓扑排序      结果  │
│                                                     并行执行)       │
│                                                                     │
│  引擎不使用字节码生成 (ASM/Javassist), 使用 AST 解释器模式           │
│  理由: 瓶颈在数据获取(网络IO), 不在规则执行(CPU);                    │
│       AST 天然支持可解释性追踪; 开发复杂度远低于字节码生成            │
└─────────────────────────────────────────────────────────────────────┘
```

### 2.2 表达式引擎

基于 **Aviator** (阿里开源) 二次封装：

| 能力 | 规格 |
|------|------|
| 基础运算 | 比较(GT/LT/GTE/LTE/EQ/NEQ)、逻辑(AND/OR/NOT)、算术 |
| 编译缓存 | 表达式字符串 → AviatorExpression → Caffeine 缓存 |
| 自定义函数 | between(), in(), daysBetween(), isInProvince(), overdueCount(period) |
| 类型安全 | 编译期类型检查, 运行时自动类型转换 |
| 空值处理 | 统一 null 安全处理, null 参与比较返回 false |

---

## 3. 五种规则类型

### 3.1 条件规则 (Condition Rule)

**本质**: IF 条件 THEN 动作

```json
{
  "ruleId": "R001",
  "name": "年龄准入检查",
  "priority": 100,
  "conditions": {
    "operator": "AND",
    "operands": [
      {"field": "age", "op": "GTE", "value": 22},
      {"field": "age", "op": "LTE", "value": 60}
    ]
  },
  "actions": [
    {"type": "REJECT", "reason": "年龄不符准入要求", "code": "AGE_001"}
  ]
}
```

**编译产物**: `ConditionRuleAST` — 递归的布尔表达式树

**命中策略**:
- `FIRST_HIT`: 首条命中即返回 (黑名单/准入)
- `ALL`: 全量执行, 收集命中结果 (全面检查)
- `PRIORITY`: 按优先级排序, 命中即停

### 3.2 评分卡 (Scorecard)

**本质**: 多特征分箱加权评分 + 阈值判定

```json
{
  "type": "SCORECARD",
  "scorecardId": "SC_CREDIT_A",
  "initialScore": 500,
  "characteristics": [
    {
      "name": "年龄",
      "field": "age",
      "bins": [
        {"range": [null, 22],  "score": -10, "reason": "年龄偏小"},
        {"range": [22, 30],   "score": 15},
        {"range": [30, 45],   "score": 25},
        {"range": [45, 60],   "score": 20},
        {"range": [60, null], "score": -5}
      ]
    }
  ],
  "cutoff": {
    "reject": 550,
    "review": 620,
    "pass": 620
  }
}
```

**编译产物**: `CompiledScorecard` — 预排序的 bin 数组 (支持二分查找)

**执行逻辑**:
1. 初始分 = initialScore
2. 遍历 characteristics, 对每个:
   - 获取 field 对应变量值
   - 二分查找命中的 bin
   - 累加 score
   - 记录得分明细 (可解释性)
3. 根据 cutoff 判定: reject / review / pass

### 3.3 决策表 (Decision Table)

**本质**: 二维表, 列是条件, 行是规则, 交叉格是结果

```json
{
  "type": "DECISION_TABLE",
  "columns": [
    {"name": "客户类型", "field": "customer_type"},
    {"name": "信用等级", "field": "credit_level"},
    {"name": "贷款用途", "field": "loan_purpose"}
  ],
  "rows": [
    {
      "conditions": ["NEW", "A", "CONSUMPTION"],
      "result": {"action": "APPROVE", "maxAmount": 500000}
    },
    {
      "conditions": ["NEW", "C", "*"],
      "result": {"action": "REJECT", "reason": "新客C级不可准入"}
    }
  ],
  "hitPolicy": "FIRST_MATCH"
}
```

**编译产物**: `CompiledDecisionTable` — 可选 hash index 加速查找

**前端展示**: Excel 风格表格 (基于 Handsontable), 支持 `*` 通配符

### 3.4 决策树 (Decision Tree)

**本质**: 嵌套的条件树, 叶子节点是动作

**编译产物**: `DecisionTreeNode` — 树形结构, 递归遍历执行

### 3.5 规则集 (Rule Set)

**本质**: N 条条件规则的容器, 统一命中策略

```json
{
  "type": "RULE_SET",
  "ruleSetId": "RS_BLACKLIST",
  "hitPolicy": "FIRST_HIT",
  "rules": [
    {"ruleId": "R001", ...},
    {"ruleId": "R002", ...}
  ]
}
```

---

## 4. 决策流引擎 (DAG)

### 4.1 节点类型

| NodeType | 说明 | 输入 | 输出 |
|----------|------|------|------|
| `DATA_PREP` | 数据准备节点 | 请求参数 | 变量填充到上下文 |
| `RULE_SET` | 规则集执行 | 变量值 | 命中结果 |
| `SCORECARD` | 评分卡计算 | 变量值 | 得分 + 分段结果 |
| `MODEL` | 模型推理调用 | 特征向量 | 模型分数/概率 |
| `DECISION` | 条件分支 | 上下文变量 | 分支选择 |
| `SUB_FLOW` | 子流程(嵌套DAG) | 上下文 | 子流程结果 |
| `AB_SPLIT` | AB实验分流 | 实验配置 | 分支选择 |
| `ACTION` | 动作(通过/拒绝/人工) | 上下文 | 最终决策 |
| `SCRIPT` | Aviator 脚本 | 变量值 | 脚本返回值 |

### 4.2 边条件

| 类型 | 说明 | 示例 |
|------|------|------|
| 无条件 | 无条件到达下一节点 | 数据准备 → 黑名单检查 |
| 表达式 | Aviator 表达式为 true 时到达 | `blacklist_hit == false` |
| 枚举 | 节点返回值匹配时到达 | scorecard.result == "PASS" |

### 4.3 执行模型

```
1. 从缓存获取 CompiledDAG
2. 变量预取: 分析 DAG 需要的所有变量, 按 Layer 并行获取
   Layer 0 (输入): 直接从请求取
   Layer 1 (外部): 并行调用所有外部 API (总耗时 = max)
   Layer 2 (缓存): 并行查 Redis + HBase
   Layer 3 (衍生): 懒计算
3. 拓扑排序逐层执行:
   - 同层无依赖节点并行 (CompletableFuture)
   - 条件分支在 resolveNextNodes 中决定
   - 每个节点记录 TraceEntry
   - 单节点超时可配置
4. 汇总结果, 生成决策报告
5. 异步写入决策日志到 ES
```

### 4.4 决策流 JSON 示例

```json
{
  "flowId": "FLOW_CREDIT_001",
  "version": "1.0",
  "nodes": [
    {"id": "data_prep", "type": "DATA_PREP", "config": {"prefetchVars": true}},
    {"id": "blacklist", "type": "RULE_SET", "config": {"ruleSetId": "RS_BLACKLIST"}},
    {"id": "anti_fraud", "type": "MODEL", "config": {"modelId": "MOD_ANTI_FRAUD_V2"}},
    {"id": "scorecard", "type": "SCORECARD", "config": {"scorecardId": "SC_CREDIT_A"}},
    {"id": "reject_low", "type": "ACTION", "config": {"action": "REJECT"}},
    {"id": "manual_review", "type": "ACTION", "config": {"action": "MANUAL"}},
    {"id": "limit_model", "type": "MODEL", "config": {"modelId": "MOD_LIMIT_V3"}}
  ],
  "edges": [
    {"from": "data_prep", "to": "blacklist"},
    {"from": "blacklist", "to": "reject_low", "condition": "blacklist.hit == true"},
    {"from": "blacklist", "to": "anti_fraud", "condition": "blacklist.hit == false"},
    {"from": "anti_fraud", "to": "scorecard", "condition": "anti_fraud.score < 0.7"},
    {"from": "scorecard", "to": "reject_low", "condition": "scorecard.score < 550"},
    {"from": "scorecard", "to": "manual_review", "condition": "scorecard.score >= 550 && scorecard.score < 620"},
    {"from": "scorecard", "to": "limit_model", "condition": "scorecard.score >= 620"},
    {"from": "limit_model", "to": "END_PASS"}
  ]
}
```

---

## 5. 变量引擎

### 5.1 变量分层

| Layer | 名称 | 来源 | 数量 | 获取延迟 |
|-------|------|------|------|----------|
| 0 | 输入变量 | 业务请求 | ~50 | 0ms |
| 1 | 外部变量 | 三方API | ~200 | 200-800ms |
| 2 | 缓存变量 | Redis/HBase (Flink预计算) | ~1500 | 5-20ms |
| 3 | 衍生变量 | L0-L2 实时计算 (Aviator表达式) | ~300 | <1ms |

### 5.2 变量注册表

```sql
CREATE TABLE variable_registry (
    var_id          VARCHAR(50) PRIMARY KEY,
    var_name        VARCHAR(100) NOT NULL COMMENT '变量名称',
    var_category    VARCHAR(50) COMMENT '业务域(征信/工商/运营商/内部/衍生)',
    layer           TINYINT COMMENT '层级 0-3',
    source          VARCHAR(50) COMMENT '来源(INPUT/PBOC_API/REDIS/COMPUTED等)',
    data_type       VARCHAR(20) COMMENT '数据类型(STRING/INTEGER/DECIMAL/DATE)',
    expression      TEXT COMMENT '衍生变量的计算表达式',
    dependencies    JSON COMMENT '依赖的变量ID列表',
    description     TEXT COMMENT '变量说明',
    version         INT DEFAULT 1,
    status          VARCHAR(20) DEFAULT 'ACTIVE',
    created_at      DATETIME,
    updated_at      DATETIME
);
```

### 5.3 变量获取优化

**预取机制**: DAG 编译时静态分析所有需要的变量, 请求进入时按 Layer 批量并行获取。

**懒加载**: 未被预取的变量, 在规则执行时按需获取。

**并行获取策略**:
- Layer 1 外部变量: CompletableFuture.allOf() 并行调用, 总耗时 = max(单个API)
- Layer 2 缓存变量: Redis Pipeline 批量获取 + HBase 批量 Scan
- Layer 3 衍生变量: 依赖拓扑排序后逐层计算

---

## 6. AB 实验引擎

### 6.1 设计思路

AB 实验不作为独立平台, 而是作为 DAG 中的 `AB_SPLIT` 节点。

### 6.2 实验配置

```json
{
  "experimentId": "EXP_20260601_001",
  "name": "评分卡V3灰度测试",
  "trafficKey": "applicationId",
  "buckets": [
    {"name": "champion", "ratio": 0.8, "strategyVersion": "STR_V2"},
    {"name": "challenger", "ratio": 0.2, "strategyVersion": "STR_V3"}
  ],
  "startDate": "2026-06-01",
  "endDate": "2026-06-15",
  "stopConditions": [
    {"metric": "pass_rate_diff", "threshold": 0.05, "significance": 0.95}
  ]
}
```

### 6.3 分流算法

```
hash(trafficKey) % 10000 → bucket 分配
例: 0-7999 → champion, 8000-9999 → challenger
保证同一 trafficKey 每次分流结果一致
```

### 6.4 指标收集

| 指标 | 说明 | 收集方式 |
|------|------|----------|
| 通过率 | 各分支的审批通过率 | 异步写入 ES |
| 平均耗时 | 各分支的决策耗时 | 异步写入 ES |
| 命中规则数 | 各分支的规则命中分布 | 异步写入 ES |
| 不良率 | 放款后的不良率 (需延迟观测) | T+1 离线计算 |

### 6.5 自动决策

- 实验组优于对照组 + 统计显著 → 自动提升流量比例 (需人工确认)
- 实验组劣于对照组 → 告警 + 自动回滚到 100% 对照组
- 无显著差异 → 延长实验周期

---

## 7. 可解释性引擎

### 7.1 TraceEntry 结构

每次决策记录完整执行轨迹:

```json
{
  "traceId": "DEC_20260603150000_12345",
  "applicationId": "APP_20260603_12345",
  "strategyId": "STR_CREDIT_V3",
  "strategyVersion": "3.2",
  "startTime": "2026-06-03 15:00:00",
  "totalDurationMs": 1520,
  "result": "PASS",
  "nodes": [
    {
      "nodeId": "blacklist",
      "nodeType": "RULE_SET",
      "durationMs": 12,
      "input": {"id_card": "3301***"},
      "output": {"hit": false, "rulesChecked": 45, "rulesHit": 0}
    },
    {
      "nodeId": "scorecard",
      "nodeType": "SCORECARD",
      "durationMs": 15,
      "input": {"age": 28, "overdue_count_6m": 0},
      "output": {"score": 78, "result": "PASS"},
      "details": {
        "breakdown": [
          {"characteristic": "年龄", "value": 28, "bin": "22-30", "score": 15},
          {"characteristic": "近6月逾期", "value": 0, "bin": "0次", "score": 30}
        ]
      }
    }
  ],
  "decisionPath": ["data_prep", "blacklist:pass", "anti_fraud:pass", "scorecard:78", "limit_model:50000"],
  "experimentGroup": "champion",
  "rejectReason": null,
  "rejectCode": null,
  "topFactors": [
    {"variable": "overdue_count_6m", "value": 0, "contribution": "+30"},
    {"variable": "credit_history_years", "value": 5, "contribution": "+15"}
  ]
}
```

### 7.2 可解释性分级

| 级别 | 对象 | 内容 |
|------|------|------|
| L1 规则可解释 | 业务人员 | 命中的规则、变量值 vs 阈值、评分卡得分明细 |
| L2 流程可解释 | 策略师 | 决策路径可视化 (DAG 高亮命中的节点) |
| L3 模型可解释 | 模型工程师 | SHAP 值、特征重要性、PDP 图 |
| L4 审计级 | 合规/监管 | 完整输入快照 + 每步输出 + 时间戳 + 操作者 |

---

## 8. 规则热加载

### 8.1 机制

1. 规则 JSON 存储在 MySQL (版本化管理)
2. 引擎启动时全量加载到 Caffeine 本地缓存
3. 规则变更时: MySQL 更新 → MQ 广播 → 各节点收到消息 → 重新编译 → 替换缓存
4. 旧请求继续使用旧版本编译产物 (CopyOnWrite 语义)
5. 全程无需重启服务

### 8.2 版本生命周期

```
草稿(Draft) → 测试(Test) → 审批(Review) → 灰度发布(Grayscale) → 全量发布(Release)
                 ↑                           ↓
                 └──── 驳回(Reject) ◀────────┘
```

### 8.3 回滚

支持一键回滚到任意历史版本 (保留最近 N 个版本的编译产物)。

---

## 9. 项目模块结构

```
decision-engine/
├── engine-common/              # 通用模型 (无 Spring 依赖)
├── engine-core/                # 核心引擎 (纯 Java, 无 Spring 依赖)
│   ├── compiler/               # 编译器 (JSON → AST)
│   ├── executor/               # 执行器 (AST → 结果)
│   ├── variable/               # 变量引擎
│   ├── expression/             # 表达式引擎 (Aviator 封装)
│   ├── experiment/             # AB 实验引擎
│   ├── trace/                  # 可解释性追踪
│   ├── cache/                  # 规则缓存 & 热加载
│   └── model/                  # 模型调用客户端
├── engine-test/                # 沙箱测试
├── decision-admin/             # 管理后台后端 (Spring Boot)
├── decision-web/               # 管理后台前端 (Vue 3)
│   ├── rule-editor/            # 规则编辑器
│   ├── scorecard-editor/       # 评分卡编辑器
│   ├── decision-table-editor/  # 决策表编辑器
│   ├── flow-designer/          # DAG 设计器 (AntV X6)
│   ├── experiment-panel/       # 实验管理
│   ├── decision-analytics/     # 决策分析看板
│   ├── variable-management/    # 变量管理
│   └── publish-center/         # 发布中心
├── decision-sdk/               # 接入 SDK
└── decision-server/            # 引擎服务 (独立微服务)
```

**关键设计**: `engine-core` 是纯 Java 模块, 不依赖 Spring Framework, 确保引擎核心的可移植性和可测试性。

---

## 10. API 接口

### 10.1 决策执行 API

```
POST /api/v1/decision/execute
```

请求:
```json
{
  "strategyId": "STR_CREDIT_V3",
  "channel": "APP",
  "applicant": {
    "name": "张三",
    "idCard": "330102199001011234",
    "phone": "13800138000",
    "applyAmount": 100000,
    "loanTerm": 12,
    "loanPurpose": "CONSUMPTION"
  },
  "metadata": {
    "deviceFingerprint": "...",
    "ipAddress": "...",
    "channelVersion": "2.5.0"
  }
}
```

响应:
```json
{
  "decisionId": "DEC_20260603150000_12345",
  "result": "PASS",
  "score": 78,
  "creditLimit": 50000,
  "interestRate": 0.085,
  "rejectReason": null,
  "rejectCode": null,
  "traceId": "DEC_20260603150000_12345",
  "durationMs": 1520
}
```

### 10.2 决策报告查询 API

```
GET /api/v1/decision/report/{decisionId}
```

返回完整的 TraceEntry 和可解释性报告。

### 10.3 策略管理 API

| API | Method | 说明 |
|-----|--------|------|
| /api/v1/rules | CRUD | 规则管理 |
| /api/v1/scorecards | CRUD | 评分卡管理 |
| /api/v1/decision-tables | CRUD | 决策表管理 |
| /api/v1/flows | CRUD | 决策流管理 |
| /api/v1/variables | CRUD | 变量管理 |
| /api/v1/experiments | CRUD | 实验管理 |
| /api/v1/publish | POST | 发布管理 (草稿→测试→审批→发布) |
