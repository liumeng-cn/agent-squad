# C1.2 账号生命周期与会话证据

验证时间：2026-11-02；MySQL 8.4 隔离库 `as_test_*`，JVM 退出自动删除；未触碰开发库。

## 1. 账号管理与改密（C1.2.1/C1.2.3）

实现位置：

- `server/agent-squad-gateway/src/main/java/com/agentsquad/squad/gateway/admin/`
  （`AdminUserController`、`UserAccountService`、`AdminUserMapper` + XML）
- `GatewayRoutingConfiguration` 将 `/api/v1/admin/**` 保留在网关本地，不转发业务服务。
- `AuthController` 新增 `POST /api/v1/auth/password`。
- 统一错误出口 `GatewayExceptionHandler`：400/401/403/404/409/422/429，响应只含可理解原因。

真实 MySQL + 真实 HTTP 结果：

```text
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 -- AdminUserHttpTest
```

覆盖：管理员列表与 no-store、创建账号（201、临时密码 ≥20 位、不返回摘要）、
唯一用户名 409、用户名格式 422、CAS 更新与冲突 409、目标不存在 404、
临时密码账号业务请求与管理员接口 403、改密错误当前密码 400、
改密成功 204 且旧会话 401、改密后业务请求 200、重置密码使旧密码 401、
非管理员访问 403、缺少 CSRF 401、最后一个活动管理员不可停用 409、
角色变化撤销会话。另在 `ConversationApiSecurityTest` 增加 ADMIN 读取他人对话
返回 404 的断言，真实结果：

```text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- ConversationApiSecurityTest
```

## 2. 持久会话策略与跨实例（C1.2.2）

真实结果：

```text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- SessionPolicyHttpTest
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- CrossInstanceSessionTest
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0 -- GatewayProxyHttpTest
```

覆盖：`Secure; HttpOnly; SameSite=Lax; Path=/`、登录与 `/me` no-store、
CSRF 不匹配 401、绝对过期 401、账号停用 401；两个进程内真实 Netty 实例共享同一库，
实例 A 登录的会话在实例 B 可用，实例 B 退出后实例 A 立即 401。
CSRF 由会话令牌 HMAC 派生，`/login` 与 `/me` 返回同一值，避免刷新和多标签页互踢。

## 3. 初始管理员 CLI

`BootstrapAdminCommand` 打包为独立可执行分类 jar
（`agent-squad-gateway-0.1.0-SNAPSHOT-bootstrap-admin.jar`），仅从环境变量读取账号和密码，
要求用户表为空；应用启动不调用。对临时 schema 的真实执行结果：

```text
first_exit=0
初始管理员已创建，请使用临时密码登录并立即修改；userId=<uuid>
second_exit=1
初始管理员创建失败：已存在账号，拒绝重复初始化
sq_user: username=cli-admin role=ADMIN status=ACTIVE must_change_password=1 hash_prefix=$2a$
sq_auth_audit: BOOTSTRAP_ADMIN SUCCESS
```

临时 schema 已在验证后删除；命令输出与日志均未出现密码。

```text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- BootstrapAdminServiceTest
```

## 4. 前端账号管理与强制改密（C1.2.4）

真实结果：

```text
npm --prefix web/console run build
vue-tsc --noEmit + vite build 通过；新增 ChangePasswordView、AdminUsersView 产物
```

本地 `agent_squad` 原为 44 张业务表，经用户确认后按 baseline 增量补齐
`sq_user/sq_auth_session/sq_auth_rate_bucket/sq_auth_audit`（仅 CREATE TABLE IF NOT EXISTS，
未删改既有表和数据），当前 48 张表。使用 `BootstrapAdminCommand` 创建 `admin` 后，
以更新后的网关实例（18082，同一开发库）和 Vite 开发服务器（5173，代理到 18082）
完成浏览器验收：

```text
登录页：只有账号/密码/登录，无公开注册入口
admin 用初始临时密码登录 -> 自动进入 /change-password（显示"临时密码"提示）
改密成功 -> /login?passwordChanged=1 并提示使用新密码登录
admin 新密码登录 -> /app/conversations（工作台）
/app/admin/users -> 列出 admin，角色/状态/密码状态/创建时间正确
创建 user-b -> 列表出现"需修改临时密码"，临时密码仅在页面显示一次
退出登录 -> user-b 用临时密码登录 -> 自动进入 /change-password -> 改密成功 -> 重新登录
user-b 访问 /app/admin/users -> 被路由守卫重定向回 /app/conversations
管理员停用 user-b -> user-b 下一次业务请求返回 401 -> 前端跳转 /login?redirect=/app/skills
停用账号再次登录 -> 页面显示"登录已失效或凭据错误"
user-b 已重新启用，供 C1.4.2 端到端验收使用
```

401 处理链路：`http.ts` 清除 CSRF 并触发 `onUnauthorized`，认证仓库清空用户；
`main.ts` 监听 userId 变化调用 `conversations.resetAll()`，关闭 EventSource 并清空会话缓存。
本次业务服务未启动、C1.3 内部断言尚未实现，因此 SSE 只验证到关闭代码路径；
真实 SSE 与断网快照恢复留待 C1.4.2。

## 5. 待继续

- C1.4.2 使用两个真实部署实例与两个真实账号做端到端验收（含 SSE、断网恢复）。
