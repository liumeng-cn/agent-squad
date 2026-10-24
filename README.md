# Agent Squad

Agent Squad 是一个面向 Web 的多智能体运行底座。用户提交目标后，平台负责持久受理、运行、展示进度与恢复；Master 可以直接回答，也可以在后续版本中委派具备独立能力的 Worker。Skill 由执行中的 Agent 按需识别和使用，不与 Worker 一一对应。

项目采用 Java 21、Spring Boot、MyBatis、MySQL 和 AgentScope Harness。Java 服务与 Web 页面在本机运行，Docker 只提供 MySQL 等依赖；当前不需要 Python 服务。

## 当前进度

| 阶段 | 当前状态 | 已验证与主要缺口 |
| --- | --- | --- |
| D0 基础探针 | 已完成 | 固定依赖、持久受理与离线恢复探针通过 |
| D1 单 Master | 主链路已验证 | 本地登录、任务受理、直接回答、快照/SSE、取消与故障注入通过；异常回合自动恢复未完成 |
| D2 受控工具 | 后端核心已验证 | 连接、操作账本、只读/模拟写入、追问/审批、核对及基础预算；D2 页面、预算超限场景和最新事务调整的真实崩溃复验待完成 |
| D3 多 Worker | 开发中 | 能力定义发布、查询与状态管理已实现；计划、派发、汇合、重规划与恢复待完成 |
| D4 Skill 与记忆 | 未开发 | 自动发现使用 Skill、版本与长期记忆治理 |
| D5 沙箱与交付 | 未开发 | 隔离执行、产物与完整预算 |
| D6 集成发布 | 未开发 | 多副本、备份恢复、负载与安全回归 |

普通问答由 Master 直接处理，不委派 Worker；每次提问仍先作为任务受理，再由后台执行。D1 的真实模型回答、A02/A03 进程故障注入和页面人工验收，以及 D2 的 A07/A08/B05/B06/B09 端到端链路，均有本地 Java + Docker MySQL 证据。现有 Web 页面仍以 D1 聊天和任务详情为主，缺少连接设置、追问/审批卡及操作核对入口。当前版本是开发中的底座，不作为生产平台使用。

当前设计静态检查还指出两张 D2 表（`sq_secret_vault`、`sq_mock_ticket`）缺少统一审计字段；设计快照和已编号迁移均存在此问题。修正时需新增迁移并核对已有数据，不能直接改写历史迁移。

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

默认打开 [http://127.0.0.1:5173/login](http://127.0.0.1:5173/login)，后端监听 `127.0.0.1:18080`。使用 `.env.local` 中的 `SQUAD_LOCAL_USER` 和 `SQUAD_LOCAL_PASSWORD` 登录；本地单账号默认具有 `ADMIN` 角色，可用 `SQUAD_LOCAL_ROLE=USER` 改为普通角色。若后端改用 `SERVER_PORT=18081`，启动页面前设置 `VITE_API_TARGET=http://127.0.0.1:18081`；页面端口可用 `npm run dev -- --port 5174` 调整。

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
                            ├→ 受控工具 → 操作账本 / 交互
                            ↓
                 原生日志投影 → 任务事件 / 快照 / SSE
```

`server/` 是 Java 服务，`web/console/` 是 Vue 工作台，`api/` 放接口契约（OpenAPI 与配置 Schema），`openspec/` 放能力 spec、变更计划与稳定约束。数据库结构以 `server/src/main/resources/db/migration/` 的 Flyway 迁移为唯一权威。

### Java 服务包结构

```text
com.agentsquad.squad
├── identity/              认证、登录、会话主体
├── conversation/          会话、任务、命令、投递的受理与控制
├── runtime/               运行协调：完成提案、终结、投影映射
├── presentation/          读投影：任务快照、事件流、SSE
├── interaction/           追问与审批交互（投递启用时生效）
├── integration/agentscope/ AgentScope 框架适配与会话网关
├── tools/                 业务工具：连接管理、操作账本、预算、示例工具
├── worker/                Worker 能力定义与状态管理
├── governance/            配额与预算治理
├── config/                Spring 配置
└── common/                共享异常与工具类
```

每个能力包内部按 controller → service → repository 分层；domain 放持久行对象，dto 放请求/响应 record，与持久对象分离。注入统一使用 `@Resource` 字段注入，不使用构造方法注入。

继续阅读：[规划与设计](openspec/README.md) · [接口契约](api/openapi.json)。
