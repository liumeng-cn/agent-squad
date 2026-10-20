# 运行受理阶段验证记录

更新时间：2026-10-20

## 已实现

`GoalTaskService` 在一个 MyBatis 事务中锁定归属对话，并写入 `sq_goal_task`、Master `sq_runtime_binding`、`sq_command`、`sq_task_event` 与会话 `active_task_id`。同一用户、对话、幂等键和输入返回原收据；同键换输入返回冲突。任务、事件和命令查询都按登录主体过滤。

正式源码按职责命名为 `AuthService`、`RuntimeController`、`GoalTaskService` 及各 Mapper；阶段编号只用于开发计划与验证记录。旧 `X-D1-User` 入口、内存目标/事件、文件命令存储及写死的 `demo/demo` 已删除。本地账号密码由被 Git 忽略的 `.env.local` 提供，服务只监听本机；写请求需要 Cookie 和 `X-CSRF-Token`。

## 已验证

- H2 文件库：迁移通过；关闭并重新创建 Spring 应用后，目标、命令、事件仍可读取；幂等、不同用户不可读取原目标/命令均通过。
- MySQL 8.4：项目专用 `agent-squad-mysql` 容器使用独立数据卷和 `127.0.0.1:13306`；Flyway V1 迁移、MyBatis 受理与读回通过。
- HTTP：伪造 `X-D1-User` 不能访问；缺 Cookie 或 CSRF 的写请求返回 401；带合法会话和 CSRF 可创建对话。
- AgentScope 固定版本的 `SessionKey` 编译/标识长度探针通过。

验证命令：在 `server` 目录运行 `mvn -B -ntp test`；MySQL 联调需先在仓库根目录加载 `.env.local`。测试使用随机业务 ID，写入项目专用库的少量测试记录。

## 尚未完成

当前本地认证会话仍在进程内，正式账号表、密码散列、会话持久化、授权版本和完整同源校验未实现。数据库迁移目前只覆盖受理链路 5 张表，后续需按数据字典补齐字段及其余模块。命令状态保持 `PENDING`：尚无 AgentScope 原生 `AgentSession.submit`、命令领取/租约与原生事件投影；不能把平台受理说成 Agent 已运行。页面与 SSE 也未接入。
