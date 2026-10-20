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

当前本地认证会话仍在进程内，正式账号表、密码散列、会话持久化、授权版本和完整同源校验未实现。数据库迁移覆盖受理链路 5 张业务表、命令投递字段、投影水位、AgentScope JDBC 基础表和直接回答的完成候选字段。旧 `sq_native_session_object` 表仍留在已编号 V2 迁移中，但新代码不再读取或写入它；是否清理需先核对已有数据库数据及 Flyway 历史，不能直接改已发布迁移。原生 `AgentSession.submit`、领取租约、官方共享存储、运行生命周期投影以及直接回答的完成候选裁决代码已经接入但默认关闭；这些新增链路**未测试、未启动服务**。只有 Master 提交候选且同一原生 Turn 成功结束，事务才会发布最终答案和目标终态。无候选纠正、异常 Turn 的业务终结、恢复和页面/SSE 尚未实现，不能把 `DELIVERED` 或单次 Run 结束说成 Agent 已完成。
