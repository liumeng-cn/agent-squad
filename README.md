# Agent Squad

Agent Squad 是一个面向 Web 的多智能体运行底座。用户发送消息后，平台持久受理并连续回复，需要独立推进时由 Master 创建目标；Master 可以直接回答，也可以按计划委派具备独立能力的 Worker。Skill 的按需识别使用由后续 D4 实现，不与 Worker 一一对应。

项目采用 Java 21、Spring Boot、MyBatis、MySQL 和 AgentScope Harness。Java 服务与 Web 页面在本机运行，Docker 只提供 MySQL 等依赖；当前不需要 Python 服务。

## 当前进度

| 阶段 | 当前状态 | 已验证与主要缺口 |
| --- | --- | --- |
| D0 基础探针 | 已完成 | 固定依赖、持久受理与离线恢复探针通过 |
| D1 单 Master | 主链路已验证 | 本地登录、任务受理、直接回答、快照/SSE、取消与故障注入通过；普通回复与目标支持有界暂时故障恢复；生产故障演练属于 D6 |
| D2 受控工具 | 后端核心已验证 | 连接、操作账本、只读/模拟写入、追问/审批、核对及基础预算；D2 页面、预算超限场景和最新事务调整的真实崩溃复验待完成 |
| D3 多 Worker | 本地验收完成 | 新能力发现、计划准入、串行/并行派发、汇合、分支恢复、保守结果复用；任务详情、交互、显式授权核对与最小文本产物链路通过本地验证 |
| D4 Skill 与记忆 | 本地验收完成 | Skill 导入/版本固定/Grant 隔离、自动发现按需读取、显式记忆写入/纠正/删除屏障；证据见 openspec/changes/d4-skills-memory/evidence/ |
| D5 沙箱与交付 | 本地验收完成 | 真实容器隔离实验（资源/网络/挂载/超时/逃逸阻断）、stage/commit 受控交付、归属鉴权与清理、全局预算；证据见 openspec/changes/d5-sandbox-delivery/evidence/ |
| D6 集成发布 | 本地验收完成 | 多副本 SSE 跨实例水位、guard/租约不双主、备份恢复机制、预算/安全回归、Markdown 消毒；完整备份还原与真实压测属发布门槛，证据见 openspec/changes/d6-integration-release/evidence/ |

新版页面已接入连续会话：普通交流不创建 GoalTask，Master 按需建立后台工作；任务在途可继续提问或补充要求。追问、审批、分级故障、允许的重试与下载均留在对话中，停止回答与停止工作分别控制。侧栏支持安全标题、改名与历史分页，空态及附件布局按用户参考调整。

本轮功能、回归结果与验证边界见 [对话体验验收记录](openspec/changes/conversation-first-experience/evidence/ui-regression-2026-10-29.md)。当前仓库迁移包含 SQL V13 及 Java V14 标题回填；实际开发库版本本轮未连接核验。D3 原基线仍见 [D3 验收记录](openspec/changes/d3-multi-worker/evidence/progress.md)；D4–D6 尚未交付，不能将本轮对话优化计作这些阶段完成。原 SDK 故障窗口、确定性模型协议测试、真实 DeepSeek 检查分别记载；Java 21 运行时与生产恢复仍未验证。

## 本地运行

准备 JDK 21、Maven、Docker MySQL 8.4，以及 Node.js 20.19+ 或 22.12+。MySQL 需监听 `127.0.0.1:13306`，预建 `agent_squad` 数据库和可访问该库的账号；应用启动时由 Flyway 执行表结构迁移。已有同名容器和数据库时直接复用，不要为启动服务清空数据。

1. 在仓库根目录复制 [环境变量示例](.env.example) 为 `.env.local`，填写 MySQL 账号、本地登录账号及模型配置。`.env.local` 已被 Git 忽略。若希望提问得到模型回答，设置 `SQUAD_DELIVERY_ENABLED=true` 并填写 `SQUAD_MODEL_API_KEY`、`SQUAD_MODEL_BASE_URL`、`SQUAD_MODEL_NAME`；保持 `false` 时只会受理消息，不会投递执行。
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
页面 → Message / ResponseExecution / Command → 回复 Master
                                              └→ 按需建立 GoalTask → Task Master → Worker
原生持久事件 → 平台投影 → 会话消息/状态快照与 SSE → 当前对话
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
