# engine-common

> 决策引擎公共模块，定义跨模块共享的模型、异常和工具类。

## 功能概述

- **公共模型** — 决策请求/响应模型、决策结果枚举、动作类型枚举
- **异常体系** — 决策引擎统一异常层级，涵盖编译、执行、变量解析等错误场景
- **字段加密** — AES 对称加密，用于敏感字段加解密
- **数据脱敏** — 敏感数据脱敏处理，支持手机号、身份证号、银行卡号、姓名等类型

## 包结构

```
src/main/java/com/credit/platform/engine/common/
├── model/             — 公共数据模型
│   ├── DecisionRequest.java
│   ├── DecisionResponse.java
│   ├── DecisionResult.java（枚举：PASS/REJECT/REVIEW/MANUAL）
│   └── ActionType.java（枚举：PASS/REJECT/REVIEW/MANUAL/SCORE/ASSIGN）
├── exception/         — 统一异常体系
│   ├── DecisionEngineException.java（基类）
│   ├── RuleCompileException.java
│   ├── RuleExecuteException.java
│   └── VariableResolveException.java
├── crypto/            — 字段加密
│   └── FieldEncryptor.java
└── masking/           — 数据脱敏
    ├── SensitiveDataMasker.java
    └── SensitiveType.java（枚举：PHONE/ID_CARD/BANK_CARD/NAME/EMAIL）
```

## 关键类说明

| 类名 | 说明 |
|------|------|
| `DecisionRequest` | 决策请求模型，包含策略 ID、渠道、申请人信息 |
| `DecisionResponse` | 决策响应模型，包含决策 ID、结果、评分、拒绝原因 |
| `DecisionResult` | 决策结果枚举：PASS（通过）、REJECT（拒绝）、REVIEW（人工审核）、MANUAL（人工） |
| `ActionType` | 规则动作类型枚举，定义规则命中后的动作 |
| `DecisionEngineException` | 引擎基础异常，所有业务异常的父类 |
| `RuleCompileException` | 规则编译异常，JSON 解析或条件树构建失败时抛出 |
| `RuleExecuteException` | 规则执行异常 |
| `VariableResolveException` | 变量解析异常 |
| `FieldEncryptor` | AES 字段加密器，支持加密/解密/判断是否已加密 |
| `SensitiveDataMasker` | 敏感数据脱敏器，支持手机号、身份证、银行卡号、姓名、邮箱等类型 |

## 构建与运行

```bash
# 构建
mvn clean package -pl engine-common -am
```

本模块为纯 Java 库，不可独立运行，被所有其他模块引用。

## 测试

```bash
mvn test -pl engine-common
```

## 依赖关系

- **依赖**: 无（零外部依赖）
- **被依赖**: `engine-core`、`decision-server`、`decision-admin`、`decision-sdk`、`data-platform`
