# 运行受理阶段验证记录

更新时间：2026-10-20

## 已实现

`GoalTaskService` 在一个 MyBatis 事务中锁定归属对话，并写入 `sq_goal_task`、Master `sq_runtime_binding`、`sq_command`、`sq_task_event` 与会话 `active_task_id`。同一用户、对话、幂等键和输入返回原收据；同键换输入返回冲突。任务、事件和命令查询都按登录主体过滤。

正式源码按职责命名为 `AuthService`、`RuntimeController`、`GoalTaskService` 及各 Mapper；阶段编号只用于开发计划与验证记录。旧 `X-D1-User` 入口、内存目标/事件、文件命令存储及写死的 `demo/demo` 已删除。本地账号密码由被 Git 忽略的 `.env.local` 提供，服务只监听本机；写请求需要 Cookie 和 `X-CSRF-Token`。

## 已验证

- H2 文件库：迁移通过；关闭并重新创建 Spring 应用后，目标、命令、事件仍可读取；幂等、不同用户不可读取原目标/命令均通过。V2/V3/V5 的多列 `ALTER TABLE` 已拆分为单条 `ADD COLUMN`，兼容 H2 文件测试库与 MySQL。
- MySQL 8.4：项目专用 `agent-squad-mysql` 容器使用独立数据卷和 `127.0.0.1:13306`；因历史迁移版本与当前文件不一致，本次验证前已重建 `agent_squad` 库，Flyway V1–V5 重新应用成功。
- HTTP 与持久化：服务已在本机 `18080` 启动，通过 `curl` 完成登录、创建对话、提交任务、查询任务与事件；杀掉进程并重启后，同一任务与事件仍可读取。
- 控制命令：取消（`POST /api/v1/tasks/{taskId}/cancel`）校验 `expectedVersion`，将目标置为 `CANCELLING`，撤销未送达业务命令并写入 `STOP` 命令；恢复（`POST /api/v1/tasks/{taskId}/resume`）仅接受 `WAITING_INPUT`、`WAITING_APPROVAL`、`RECOVERY_BLOCKED`、`RECONCILING` 状态并写入 `RESUME` 命令。两者均按 `Idempotency-Key` 幂等，同键同输入返回同一 `CommandReceipt`。
- SSE 事件流：`GET /api/v1/tasks/{taskId}/events` 以 `text/event-stream` 返回，事件名统一为 `message`，`id` 为 `taskId:seq`，`data` 为 `TaskEvent` JSON；支持 `after` 查询参数与 `Last-Event-ID` 重连，跨任务游标与未来水位返回 400，未认证返回 401。
- HTTP 安全：伪造 `X-D1-User` 不能访问；缺 Cookie 或 CSRF 的写请求返回 401；带合法会话和 CSRF 可创建对话。
- AgentScope 固定版本的 `SessionKey` 编译/标识长度探针通过。

验证命令：
- 单元/持久化测试：在 `server` 目录运行 `mvn -B -ntp test`。
- 服务启动测试：在仓库根目录加载 `.env.local`，于 `server` 目录执行 `java -jar target/agent-squad-server-0.1.0-SNAPSHOT.jar`，随后用 `curl` 走 `/api/v1/auth/login`、`/api/v1/conversations`、`/api/v1/conversations/{id}/tasks`、`/api/v1/tasks/{id}`、`/api/v1/tasks/{id}/cancel`、`/api/v1/tasks/{id}/resume` 与 `/api/v1/tasks/{id}/events`。

## 尚未完成

当前本地认证会话仍在进程内，正式账号表、密码散列、会话持久化、授权版本和完整同源校验未实现。数据库迁移覆盖受理链路 5 张业务表、命令投递字段、投影水位、AgentScope JDBC 基础表和直接回答的完成候选字段。旧 `sq_native_session_object` 表仍留在已编号 V2 迁移中，但新代码不再读取或写入它；是否清理需先核对已有数据库数据及 Flyway 历史，不能直接改已发布迁移。

原生 `AgentSession.submit`、领取租约、官方共享存储、运行生命周期投影以及直接回答的完成候选裁决已接入，但默认关闭（`agentsquad.delivery.enabled=false`）。2026-10-22 在全新隔离 H2 库启用投递，通过真实 DeepSeek 模型完成登录、创建对话、提交“2+2”、Master 调用完成工具、任务 `SUCCEEDED`、答案事件与快照读取，最终答案为“2+2 的结果是 4。”。现有 MySQL 库未启用真实投递验证，浏览器端页面仍待人工验收。异常 Turn 的自动恢复仍未实现。不能把 `DELIVERED` 或单次 Run 结束说成 Agent 已完成。

随后补充了 V9 `blocked_reason`：非成功原生回合不再把目标留在 `RUNNING`，而是事务性写入 `RECOVERY_BLOCKED`、公开事件并使旧完成候选失效；取消意图仍优先。恢复命令被 SDK 拒绝时，命令 `BLOCKED` 与目标阻塞状态同事务提交。隔离 H2 集成测试覆盖失败回合、重复投影、显式恢复受理、拒绝收口及取消竞争；这不等同于固定 SDK 的真实失败恢复实验。完整多 Worker 协作、权限/操作核对和生产级恢复仍未实现。

本阶段 `mvn -B -ntp test` 结果：15 项中 14 项通过、1 项跳过，Flyway V1–V9 在隔离 H2 文件库迁移通过。真实模型验证只覆盖成功路径；失败路径由持久库事件测试覆盖。
