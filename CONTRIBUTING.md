# 贡献指南

感谢你对 **信贷风控决策引擎平台** 的关注！本文档将帮助你快速了解如何在本地搭建开发环境、规范代码风格并参与项目贡献。

---

## 目录

1. [开发环境要求](#1-开发环境要求)
2. [项目克隆与首次构建](#2-项目克隆与首次构建)
3. [分支管理策略](#3-分支管理策略)
4. [代码规范](#4-代码规范)
5. [提交信息格式](#5-提交信息格式)
6. [PR 流程与 Review 要求](#6-pr-流程与-review-要求)
7. [测试要求](#7-测试要求)
8. [IDE 配置建议](#8-ide-配置建议)

---

## 1. 开发环境要求

开始开发前，请确保本地已安装以下工具并达到版本要求：

| 工具 | 版本要求 | 用途 |
|------|---------|------|
| JDK | 17+ | 后端 Java 开发与编译 |
| Maven | 3.8+ | 后端构建与依赖管理（推荐 3.9+） |
| Node.js | 20+ | 前端开发与构建 |
| npm | 随 Node.js 安装 | 前端依赖管理 |
| Python | 3.11+ | 模型平台开发 |
| Docker | 20.10+ | 容器化部署 |
| Docker Compose | v2+ | 本地基础设施编排 |
| Git | 2.30+ | 版本控制 |
| MySQL | 8.0 | 业务数据存储 |
| Redis | 7.x | 缓存与会话管理 |
| Elasticsearch | 8.13 | 决策日志持久化与检索 |

### 推荐 IDE

- **后端 (Java)**：IntelliJ IDEA（Ultimate 或 Community 版均可）
- **前端 (Vue)**：VS Code
- **模型平台 (Python)**：VS Code 或 PyCharm

---

## 2. 项目克隆与首次构建

### 2.1 克隆仓库

```bash
git clone <repository-url>
cd finance-bigdata
```

### 2.2 配置环境变量

```bash
cp docs/deployment/.env.example .env
# 根据本地环境修改 .env 中的配置项
```

### 2.3 构建后端（Maven 多模块）

```bash
mvn clean install -DskipTests
```

该命令会依次编译以下模块：

| 模块 | 说明 |
|------|------|
| `engine-common` | 通用工具库（加密、脱敏、异常处理） |
| `engine-core` | 引擎核心（规则/评分卡/决策表/DAG 决策流/变量引擎） |
| `engine-test` | 集成测试与性能基准 |
| `decision-admin` | 管理后台后端 |
| `decision-sdk` | 客户端 SDK |
| `decision-server` | 决策执行服务 |
| `data-platform` | 大数据平台（data-service、data-governance、flink-jobs） |

### 2.4 构建前端

```bash
cd decision-ui
npm install
npm run build
```

`npm run build` 会先执行 `vue-tsc` 类型检查，再通过 Vite 打包。若仅做开发调试，使用 `npm run dev` 启动开发服务器即可。

### 2.5 构建模型平台

```bash
cd model-platform
pip install -r requirements.txt
```

模型平台依赖 FastAPI、scikit-learn、XGBoost、LightGBM 等 ML 库，建议使用虚拟环境隔离：

```bash
python -m venv .venv
source .venv/bin/activate  # macOS/Linux
# .venv\Scripts\activate   # Windows
pip install -r requirements.txt
```

### 2.6 启动基础设施

```bash
docker compose -f docs/deployment/docker-compose.yml up -d
```

该命令会启动 MySQL、Redis、Elasticsearch、Kafka 等基础服务。启动后请确认各服务健康状态：

```bash
docker compose -f docs/deployment/docker-compose.yml ps
```

---

## 3. 分支管理策略

项目采用功能分支开发模式，所有变更通过 Pull Request 合入主分支。

### 分支命名规范

| 分支类型 | 命名格式 | 示例 | 说明 |
|---------|---------|------|------|
| 主分支 | `main` | `main` | 稳定可发布代码，受保护 |
| 功能分支 | `feat/<模块>-<简述>` | `feat/engine-scorecard-config` | 新功能开发 |
| 修复分支 | `fix/<模块>-<简述>` | `fix/admin-login-redirect` | Bug 修复 |
| 重构分支 | `refactor/<模块>-<简述>` | `refactor/core-rule-cache` | 代码重构 |
| 文档分支 | `docs/<简述>` | `docs/api-reference` | 文档更新 |
| 发布分支 | `release/<版本号>` | `release/1.1.0` | 版本发布准备 |

### 开发流程

1. 从 `main` 分支创建功能分支：
   ```bash
   git checkout main
   git pull origin main
   git checkout -b feat/engine-scorecard-config
   ```

2. 在功能分支上进行开发，定期提交。

3. 开发完成后，推送分支并创建 Pull Request。

4. PR 通过 Code Review 并通过所有 CI 检查后，由 Maintainer 合并。

### 合并策略

- 合并 PR 时使用 **`--no-ff`**（非快进合并），保留分支历史和合并节点。
- 禁止直接向 `main` 分支 push。
- 合并后删除远端功能分支。

---

## 4. 代码规范

### 4.1 Java 代码规范

- 遵循 [阿里巴巴 Java 开发手册](https://github.com/alibaba/p3c)（华山版）。
- 类名使用 UpperCamelCase，方法名和变量名使用 lowerCamelCase。
- 常量使用全大写下划线分隔（UPPER_SNAKE_CASE）。
- 每个类和公开方法必须有 Javadoc 注释。
- 单个方法长度不超过 80 行。
- 项目使用 Lombok 简化 POJO，避免手写 getter/setter。

### 4.2 Vue / TypeScript 代码规范

- 遵循 [Vue.js 官方风格指南](https://cn.vuejs.org/style-guide/)（优先级 A 和 B 规则为强制）。
- 组件文件名使用 PascalCase（如 `RuleEditor.vue`）。
- 使用 `<script setup>` 语法。
- Props 必须定义类型。
- 使用 TypeScript 严格模式，禁止使用 `any`（特殊情况需注释说明）。

### 4.3 Python 代码规范

- 遵循 [PEP 8](https://peps.python.org/pep-0008/) 编码规范。
- 使用 4 个空格缩进。
- 函数和类必须有 docstring。
- 使用 type hints 标注函数签名。
- 行长度不超过 120 字符。

### 4.4 通用规范

- 所有源文件使用 **UTF-8** 编码。
- 换行符统一使用 **LF**（Unix 风格），禁止 CRLF。
- 行尾禁止多余空格。
- 文件末尾保留一个空行。

### 4.5 静态检查工具

| 语言 | 工具 | 运行方式 |
|------|------|---------|
| Java | Checkstyle | `mvn checkstyle:check` |
| Vue / TypeScript | ESLint | `npm run lint`（decision-ui 目录下） |
| Python | pylint / flake8 | `pylint model-platform/` |

提交前请确保本地检查通过：

```bash
# Java
mvn checkstyle:check

# 前端
cd decision-ui && npx eslint . --fix

# Python
cd model-platform && pylint app/
```

---

## 5. 提交信息格式

项目采用 [Conventional Commits](https://www.conventionalcommits.org/zh-hans/) 规范。每条提交信息由以下部分组成：

```
<type>(<scope>): <subject>

[optional body]

[optional footer]
```

### Type（必填）

| 类型 | 说明 | 示例 |
|------|------|------|
| `feat` | 新功能 | `feat(engine): 添加评分卡版本回滚功能` |
| `fix` | Bug 修复 | `fix(admin): 修复登录重定向循环` |
| `docs` | 文档更新 | `docs: 更新 API 接口文档` |
| `refactor` | 重构（不改变外部行为） | `refactor(core): 优化规则引擎缓存策略` |
| `test` | 测试相关 | `test(engine): 补充决策表边界条件测试` |
| `chore` | 构建/工具变更 | `chore: 升级 Spring Boot 至 3.2.5` |
| `style` | 代码格式（不影响逻辑） | `style: 统一缩进为 4 空格` |
| `perf` | 性能优化 | `perf(data): 优化 Flink 作业窗口聚合性能` |

### Scope（推荐填写）

常用的 scope 包括：

- `engine` — 引擎核心（engine-core、engine-common）
- `admin` — 管理后台（decision-admin）
- `server` — 决策服务（decision-server）
- `sdk` — 客户端 SDK（decision-sdk）
- `ui` — 前端（decision-ui）
- `data` — 数据平台（data-platform）
- `model` — 模型平台（model-platform）
- `deploy` — 部署相关

### 示例

```
feat(engine): 实现规则热加载与灰度发布

- 支持规则版本管理与灰度流量分配
- 添加 AB 实验配置 API
- 新增规则发布审批流程

Closes #123
```

---

## 6. PR 流程与 Review 要求

### 6.1 创建 PR

1. **Fork 或分支开发**：项目成员直接创建分支，外部贡献者请先 Fork 仓库。

2. **确保本地构建通过**：
   ```bash
   mvn clean install              # 后端编译
   cd decision-ui && npm run build # 前端类型检查与构建
   cd model-platform && pytest     # Python 测试
   ```

3. **推送分支并创建 PR**，目标分支为 `main`。

### 6.2 PR 标题格式

PR 标题遵循与提交信息相同的格式：

```
<type>(<scope>): <简短描述>
```

示例：
- `feat(engine): 添加评分卡配置管理接口`
- `fix(ui): 修复决策流设计器节点拖拽异常`
- `refactor(data): 重构 Flink 特征计算作业`

### 6.3 PR 描述模板

请在 PR 描述中包含以下内容：

```markdown
## 变更说明
简要描述本次变更的目的和内容。

## 变更类型
- [ ] 新功能（feat）
- [ ] Bug 修复（fix）
- [ ] 重构（refactor）
- [ ] 文档更新（docs）
- [ ] 测试补充（test）

## 关联 Issue
Closes #<issue-number>

## 测试情况
描述已完成的测试（单元测试/手动测试）。

## 检查清单
- [ ] 代码符合项目编码规范
- [ ] 已添加必要的单元测试
- [ ] 所有测试通过
- [ ] 已更新相关文档（如有必要）
```

### 6.4 Code Review 要求

- 每个 PR 至少需要 **1 位 Reviewer** 批准才能合并。
- Reviewer 关注以下方面：
  - 代码逻辑正确性
  - 是否符合编码规范
  - 是否有潜在的性能或安全问题
  - 测试覆盖是否充分
  - 是否需要更新文档
- 作者需在 3 个工作日内处理 Review 意见。
- 使用 Conventional Comments 格式进行评论：
  - `praise:` 赞扬
  - `nitpick:` 小问题（非阻塞）
  - `suggestion:` 建议改进
  - `issue:` 必须修改的问题
  - `question:` 疑问

### 6.5 CI 检查

PR 创建后会自动运行 CI 流水线，包含以下检查：

- Maven 编译（`mvn clean compile`）
- 单元测试（`mvn test`）
- 前端类型检查（`vue-tsc --noEmit`）
- Checkstyle 静态检查
- ESLint 检查

所有 CI 检查通过后方可合并。若 CI 失败，请在 PR 中说明原因并修复。

---

## 7. 测试要求

### 7.1 覆盖率要求

- 单元测试覆盖率不低于 **70%**（核心模块 engine-core 建议达到 80%+）。
- 新增功能必须附带对应的单元测试。
- Bug 修复必须包含回归测试。

### 7.2 运行测试

**Java 测试：**

```bash
# 运行全部测试
mvn test

# 运行指定模块测试
mvn test -pl engine-core

# 运行指定测试类
mvn test -pl engine-core -Dtest=RuleEngineTest

# 查看测试覆盖率报告
mvn jacoco:report
```

**前端测试：**

```bash
cd decision-ui

# 构建时包含类型检查
npm run build

# 若项目配置了 vitest，运行单元测试
npm run test
```

**Python 测试：**

```bash
cd model-platform

# 运行全部测试
pytest

# 运行指定测试文件
pytest tests/test_model_service.py

# 查看覆盖率
pytest --cov=app --cov-report=html
```

### 7.3 测试命名规范

- Java 测试方法名使用 `should_<期望行为>_when_<条件>` 格式。
  ```java
  @Test
  void should_throwException_when_ruleConfigIsNull() { ... }
  ```
- Python 测试函数名使用 `test_<被测功能>_<场景>_<期望结果>` 格式。
  ```python
  def test_train_model_with_valid_data_returns_success():
  ```

### 7.4 集成测试

集成测试位于 `engine-test` 模块，需要启动基础服务后运行：

```bash
# 先启动基础设施
docker compose -f docs/deployment/docker-compose.yml up -d

# 运行集成测试
mvn verify -pl engine-test
```

---

## 8. IDE 配置建议

### 8.1 IntelliJ IDEA（后端 Java 开发）

**必装插件：**

- **Lombok** — 支持 `@Data`、`@Builder` 等注解的编译与代码提示。
- **Maven Helper** — 便捷的依赖分析与冲突解决工具。
- **CheckStyle-IDEA** — 实时代码规范检查。

**配置步骤：**

1. **导入项目**：File → Open → 选择项目根目录的 `pom.xml`，选择 "Open as Project"。
2. **配置 JDK**：File → Project Structure → Project SDK → 选择 JDK 17。
3. **配置 Maven**：Settings → Build, Execution, Deployment → Build Tools → Maven → 设置 Maven home 路径和 `settings.xml`。
4. **启用 Annotation Processing**：Settings → Build, Execution, Deployment → Compiler → Annotation Processors → 勾选 "Enable annotation processing"。
5. **配置编码**：Settings → Editor → File Encodings → 全部设为 UTF-8。
6. **配置 Checkstyle**：Settings → Tools → Checkstyle → 添加阿里巴巴 Java 开发手册规则文件。

### 8.2 VS Code（前端与 Python 开发）

**必装插件：**

- **Vue - Official (Volar)** — Vue 3 语言支持与类型检查（禁用旧版 Vetur）。
- **ESLint** — JavaScript/TypeScript 代码检查。
- **Prettier** — 代码格式化。
- **Python** — Python 语言支持。
- **Pylance** — Python 类型检查与智能提示。

**推荐工作区配置（`.vscode/settings.json`）：**

```json
{
  "editor.formatOnSave": true,
  "editor.defaultFormatter": "esbenp.prettier-vscode",
  "editor.codeActionsOnSave": {
    "source.fixAll.eslint": "explicit"
  },
  "typescript.tsdk": "node_modules/typescript/lib",
  "python.defaultInterpreterPath": "model-platform/.venv/bin/python",
  "python.linting.pylintEnabled": true,
  "files.encoding": "utf8",
  "files.eol": "\n",
  "files.trimTrailingWhitespace": true,
  "files.insertFinalNewline": true
}
```

**推荐工作区扩展（`.vscode/extensions.json`）：**

```json
{
  "recommendations": [
    "vue.volar",
    "dbaeumer.vscode-eslint",
    "esbenp.prettier-vscode",
    "ms-python.python",
    "ms-python.vscode-pylance"
  ]
}
```

---

## 常见问题

### Q: Maven 构建报依赖下载失败？

检查 Maven `settings.xml` 是否配置了国内镜像源（如阿里云 Maven 镜像）。编辑 `~/.m2/settings.xml`：

```xml
<mirror>
  <id>aliyunmaven</id>
  <mirrorOf>central</mirrorOf>
  <name>阿里云公共仓库</name>
  <url>https://maven.aliyun.com/repository/public</url>
</mirror>
```

### Q: npm install 速度慢？

切换至国内镜像源：

```bash
npm config set registry https://registry.npmmirror.com
```

### Q: Docker 容器启动后服务连接失败？

1. 确认容器健康状态：`docker compose -f docs/deployment/docker-compose.yml ps`
2. 检查 `.env` 配置是否与 `docs/deployment/.env.example` 一致
3. 查看 MySQL 等服务的初始化日志：`docker compose -f docs/deployment/docker-compose.yml logs mysql`

### Q: engine-test 集成测试报数据库连接错误？

确保基础设施已启动且健康：

```bash
docker compose -f docs/deployment/docker-compose.yml up -d
# 等待所有服务 healthy 后再运行测试
mvn verify -pl engine-test
```

---

感谢你的贡献！如有疑问，请提交 Issue 或联系项目 Maintainer。
