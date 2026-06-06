# engine-core

> 信贷风控决策引擎核心模块，提供规则编译、执行、评分卡、决策流、变量引擎和追踪等核心能力。

## 功能概述

- **规则编译** — 将 JSON 规则定义编译为不可变 AST 产物，支持条件树、逻辑组合、多种比较运算符
- **规则执行** — 无状态线程安全的规则执行器，支持规则集批量执行和单条规则求值
- **表达式引擎** — 基于 Aviator 的表达式求值引擎，内置 `between`、`in`、`daysBetween` 等自定义函数
- **评分卡** — 完整的评分卡编译与执行，支持特征分箱、初始分、截断阈值（PASS/REVIEW/REJECT）
- **决策树** — 二叉决策树编译与执行，支持嵌套条件组合
- **决策表** — 决策表编译与执行
- **DAG 决策流** — 有向无环图决策流编译，支持条件边和并行节点执行
- **变量引擎** — 四层变量解析架构（INPUT -> EXTERNAL -> CACHED -> DERIVED），自动依赖展开
- **缓存管理** — 基于 Caffeine 的多分区本地缓存（规则/流程/评分卡），支持版本化 CopyOnWrite 热加载
- **决策追踪** — 完整的决策过程追踪，支持 begin/end 模式和快捷记录，可插拔的 TracePublisher
- **沙箱测试** — 历史样本回放和版本对比（Diff），用于验证规则变更影响
- **实验引擎** — A/B 实验分流和指标收集

## 包结构

```
src/main/java/com/credit/platform/engine/core/
├── compiler/          — 规则编译器，JSON -> AST 编译
│   ├── model/         — 编译模型（ConditionNode、ComparisonOperator、HitPolicy、RuleAction 等）
│   ├── CompiledConditionRule.java
│   ├── CompiledRuleSet.java
│   ├── CompiledRule.java
│   └── RuleCompiler.java
├── expression/        — 表达式引擎（Aviator）
│   ├── ExpressionEngine.java
│   ├── BetweenFunction.java
│   ├── InFunction.java
│   └── DaysBetweenFunction.java
├── executor/          — 规则执行器
│   ├── RuleExecutor.java
│   ├── ExecutionContext.java
│   └── RuleExecutionResult.java
├── scorecard/         — 评分卡编译与执行
│   ├── ScorecardCompiler.java
│   ├── ScorecardExecutor.java
│   ├── CompiledScorecard.java
│   └── ScorecardResult.java
├── tree/              — 决策树编译与执行
│   ├── DecisionTreeCompiler.java
│   ├── CompiledDecisionTree.java
│   ├── BranchNode.java
│   ├── LeafNode.java
│   └── DecisionTreeNode.java
├── table/             — 决策表编译
│   ├── DecisionTableCompiler.java
│   └── CompiledDecisionTable.java
├── flow/              — DAG 决策流编译与执行
│   ├── DAGCompiler.java
│   ├── CompiledDAG.java
│   ├── FlowNode.java
│   ├── FlowEdge.java
│   └── FlowExecutionResult.java
├── variable/          — 变量引擎
│   ├── VariableEngine.java
│   ├── VariableRegistry.java
│   ├── VariableDefinition.java
│   ├── VariableLayer.java
│   ├── VariableProvider.java（接口）
│   └── VariableResolveContext.java
├── cache/             — 缓存管理
│   ├── RuleCacheManager.java
│   ├── VersionedRuleCache.java
│   ├── VersionedArtifact.java
│   ├── ArtifactCompiler.java（接口）
│   ├── HotReloadListener.java（接口）
│   └── CacheReloadEvent.java
├── trace/             — 决策追踪
│   ├── DecisionTracer.java
│   ├── DecisionTrace.java
│   ├── TraceEntry.java
│   ├── TracePublisher.java（接口）
│   ├── TraceReporter.java
│   ├── DecisionReport.java
│   ├── TraceLevel.java
│   └── NodeType.java
├── model/             — 模型服务客户端
│   ├── ModelServiceClient.java（接口）
│   ├── DefaultModelServiceClient.java
│   ├── ModelRequest.java
│   ├── ModelResponse.java
│   ├── ModelConfig.java
│   ├── ModelFeatureMapper.java
│   ├── FeatureMapping.java
│   └── SHAPExplainer.java
├── experiment/        — A/B 实验引擎
│   ├── ExperimentConfig.java
│   ├── ExperimentSplitter.java
│   └── ExperimentMetrics.java
└── sandbox/           — 沙箱测试
    ├── SandboxRunner.java
    ├── SandboxRequest.java
    ├── SandboxResult.java
    ├── HistorySample.java
    ├── DiffReport.java
    └── DiffEntry.java
```

## 关键类说明

| 类名 | 说明 |
|------|------|
| `RuleCompiler` | 规则编译器，将 JSON 规则定义编译为不可变 AST |
| `RuleExecutor` | 无状态规则执行器，线程安全，核心入口方法 `execute()` |
| `ExpressionEngine` | 基于 Aviator 的表达式引擎，编译一次执行多次，内置自定义函数 |
| `ScorecardCompiler` | 评分卡编译器，支持特征分箱和截断阈值 |
| `ScorecardExecutor` | 评分卡执行器，计算最终得分和决策结果 |
| `DecisionTreeCompiler` | 决策树编译器，支持嵌套条件和逻辑组合 |
| `DAGCompiler` | DAG 决策流编译器，编译节点和条件边 |
| `VariableEngine` | 变量引擎，四层变量解析（INPUT/EXTERNAL/CACHED/DERIVED） |
| `VariableRegistry` | 变量注册中心，管理变量定义和依赖关系 |
| `RuleCacheManager` | 基于 Caffeine 的三分区本地缓存管理器 |
| `VersionedRuleCache` | 版本化规则缓存，支持 CopyOnWrite 热加载 |
| `DecisionTracer` | 决策追踪器，记录每个节点的执行轨迹 |
| `TracePublisher` | 追踪日志发布接口（fire-and-forget 异步契约） |
| `SandboxRunner` | 沙箱测试运行器，支持历史样本回放 |
| `ExperimentSplitter` | A/B 实验分流器 |

## 构建与运行

```bash
# 构建
mvn clean package -pl engine-core -am

# 仅编译（不打包）
mvn compile -pl engine-core -am
```

本模块为纯 Java 库，不可独立运行，需由 decision-server 或 decision-admin 引用。

## 测试

```bash
mvn test -pl engine-core
```

## 依赖关系

- **依赖**: `engine-common`
- **被依赖**: `decision-server`、`decision-admin`
- **外部依赖**: Aviator 5.4.3、Caffeine 3.1.8、Jackson
