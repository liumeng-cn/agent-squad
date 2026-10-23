# Agent Squad

Agent Squad 是一个面向 Web 的多智能体运行底座。用户提交目标后，平台负责持久受理、运行、展示进度与恢复；Master 可以直接回答，也可以在后续版本中委派具备独立能力的 Worker。Skill 由执行中的 Agent 按需识别和使用，不与 Worker 一一对应。

项目采用 Java 21、Spring Boot、MyBatis、MySQL 和 AgentScope Harness。Java 服务与 Web 页面在本机运行，Docker 只提供 MySQL 等依赖；当前不需要 Python 服务。

## 当前进度

目前处于 **D1：单 Master 运行链路**。已实现本地登录、会话、目标持久受理、命令投递、Master 直接回答、任务快照与 SSE 事件、取消及异常状态收口。普通问答由 Master 处理，不委派 Worker。一次提问当前仍以任务形式受理：接口先返回任务 ID，后台执行，页面订阅结果。

尚未实现 Worker 协作、Skill 接入、受控业务工具、审批、长期记忆和沙箱。真实模型的正向回答、A02/A03 进程故障注入复验和浏览器端人工验收均已在本地 Java + Docker MySQL 上完成；异常回合的自动恢复仍未实现。不要把当前版本当作生产可用平台。[详细证据与限制](docs/d1-evidence.md)

## 本地运行

准备 JDK 21、Maven、Docker MySQL 8.4，以及 Node.js 20.19+ 或 22.12+。MySQL 需监听 `127.0.0.1:13306`，预建 `agent_squad` 数据库和可访问该库的账号；应用启动时由 Flyway 执行表结构迁移。已有同名容器和数据库时直接复用，不要为启动服务清空数据。

1. 在仓库根目录复制 [环境变量示例](.env.example) 为 `.env.local`，填写 MySQL 账号、本地登录账号及模型配置。`.env.local` 已被 Git 忽略。若希望提问得到模型回答，设置 `SQUAD_DELIVERY_ENABLED=true` 并填写 `SQUAD_MODEL_API_KEY`、`SQUAD_MODEL_BASE_URL`、`SQUAD_MODEL_NAME`；保持 `false` 时只会受理任务，不会投递执行。
2. 在一个终端启动 Java 服务：

   ```bash
   set -a
   source ./.env.local
   set +a
   mvn -f server/pom.xml -B -ntp spring-boot:run
   ```

3. 在另一个终端启动页面：

   ```bash
   cd web/console
   npm ci
   npm run dev
   ```

默认打开 [http://127.0.0.1:5173/login](http://127.0.0.1:5173/login)，后端监听 `127.0.0.1:18080`。使用 `.env.local` 中的 `SQUAD_LOCAL_USER` 和 `SQUAD_LOCAL_PASSWORD` 登录。若后端改用 `SERVER_PORT=18081`，启动页面前设置 `VITE_API_TARGET=http://127.0.0.1:18081`；页面端口可用 `npm run dev -- --port 5174` 调整。

运行测试时使用**未加载 `.env.local` 的独立终端**，避免测试任务进入正在使用的 MySQL：

```bash
mvn -f server/pom.xml -B -ntp test
npm --prefix web/console run build
```

常规后端测试使用临时 H2；专用 MySQL 集成测试需显式配置测试数据库，默认跳过。另可在装有 Python 3.10+ 和 `jsonschema` 的设计工具环境运行 `python3 tools/check_design.py`；Python 不参与服务运行。

## 运行链路与目录

```text
Web 页面 → Spring HTTP API → GoalTask / Command（MySQL）
                            ↓
                     AgentScope Master → 模型
                            ↓
                 原生日志投影 → 任务事件 / 快照 / SSE
```

`server/` 是 Java 服务，`web/console/` 是 Vue 工作台，`contracts/` 放公共 API 契约，`data/` 放数据设计，`docs/` 放架构、模块方案和验证记录。`data/schema.sql` 是设计快照；实际数据库结构以 `server/src/main/resources/db/migration/` 的 Flyway 迁移为准。

继续阅读：[范围与需求](docs/overview.md) · [架构与选型](docs/architecture.md) · [运行与恢复协议](docs/runtime-contract.md) · [模块设计](docs/modules/README.md) · [HTTP 契约](contracts/openapi.json) · [阶段验收](docs/acceptance-cases.md)。
