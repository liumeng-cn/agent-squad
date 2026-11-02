# C1.4.2 多实例端到端验收证据

验证时间：2026-11-02 17:25（Asia/Shanghai）。

## 1. 环境

- 临时隔离库：`c1_accept_20261102172507`，由 `V1__baseline.sql` 建立 48 张表；
  未触碰开发库 `agent_squad`。
- 同一业务服务：`127.0.0.1:18080`。
- 两个独立网关进程，连接同一 MySQL 库和同一业务服务：
  - 实例 A `127.0.0.1:18091`
  - 实例 B `127.0.0.1:18092`
- 账号：`c1acceptadmin`（ADMIN）与 `c1acceptuser`（USER）；临时密码由运行时生成，
  验收结束后账号随临时库删除，未写入证据或日志。
- 断言密钥来自 `.env.local`，只在进程环境变量中传递，未输出。

启动就绪检查（无断言直接访问业务）：

```text
business_no_assertion=401 gatewayA_unauth=401 gatewayB_unauth=401
```

网关日志中每个实例各 1 条 `ERROR`，内容为 macOS 缺少 Netty DNS 原生库后回退系统
解析器，不是请求失败；业务服务 0 条 `ERROR`。

## 2. 两个真实账号的生命周期

```text
bootstrap_admin=created
admin_temp_login=200 must_change=True
admin_change_password=204
admin_login=200 must_change=False
create_user_b=201
user_b_temp_change=204 relogin=200 must_change=False
```

- 初始管理员由一次性 CLI 创建，首次登录被强制改密。
- 管理员通过受限接口创建 user-b，响应中的临时密码只出现一次且未记录。
- user-b 首次登录被强制改密，改密后旧会话失效并重新登录成功。

## 3. 跨实例会话

在实例 B 登录的 user-b 会话，在实例 A 立即可用：

```text
cross_instance_session: login_on_B visible_on_A=200 username=c1acceptuser
```

随后使用该会话在实例 A 受理消息，同会话在实例 B 读取快照，均成功：

```text
send_message_cross_instance=202
snapshot_after_disconnect=200 title=验收会话-B
```

## 4. 伪造内部头与越权

浏览器携带 `X-User-Id`、`X-User-Role`、`X-Squad-User-Id` 试图冒充管理员，
网关剥离后按真实会话签发断言；新建对话的数据库归属仍为 user-b：

```text
forged_headers: conversation owner=user_b (not admin)
```

user-b 读取管理员会话：

```text
cross_user_read_admin_conversation=404
```

直接访问业务端口 18080（绕过网关）：

```text
direct_18080: forged_headers=401 unsigned=401
```

## 5. SSE、发送消息与断网后快照恢复

user-b 的会话先写入一条消息（202），再手动修改标题产生公开事件。

第一次 SSE（实例 A，使用实例 B 登录的 Cookie）收到连续事件：

```text
sse_first_stream_events=3 last_cursor_present=yes
event ids（截断显示事件序号）: <conversationId>:0:1, :0:2, :0:3
```

断开后再次修改标题并读取快照，快照已反映最新标题：

```text
snapshot_after_disconnect=200 title=验收会话-B
```

携带第一次连接的 `Last-Event-ID` 在实例 A 重连，只收到水位之后的新事件：

```text
sse_reconnect=200 new_events=1
event id: <conversationId>:0:4
```

事件类型由数据库核对：

```text
event_seq 1 message.accepted
event_seq 2 execution.queued
event_seq 3 conversation.title_updated
event_seq 4 conversation.title_updated
```

说明：本次 `agentsquad.delivery.enabled=false`，没有真实模型调用；消息只持久受理，
执行保持 `QUEUED`，命令保持 `PENDING`。本证据不声称模型执行或真实回复已验证。

## 6. 退出与管理员停用

user-b 在实例 B 退出，实例 A 的同一会话立即失效：

```text
logout_on_B=204 cross_instance_me=401
```

管理员在实例 A 停用 user-b：

```text
admin_disable=200 disabled_me=401 disabled_message=401 message_count_unchanged=1
```

停用后的既有会话返回 401，新消息不再受理，会话消息数保持 1，没有新增写入。

## 7. 验收后的数据库状态（临时库）

账号：

```text
c1acceptadmin  ADMIN  ACTIVE    must_change_password=0  auth_version=1
c1acceptuser   USER   DISABLED  must_change_password=0  auth_version=2
```

会话（`active` 为未撤销计数）：

```text
c1acceptadmin  active=1  revoked=1
c1acceptuser   active=0  revoked=3
```

user-b 的 3 个撤销会话分别来自改密撤销、主动退出和管理员停用。

会话与消息：

```text
conversation  c1acceptadmin  管理员会话     status=ACTIVE  title_version=0  event_seq=0
conversation  c1acceptuser   验收会话-B     status=ACTIVE  title_version=2  event_seq=4
message       user-b 消息 1 条  role=USER  completed=1  execution_id 已关联
execution     QUEUED（delivery 关闭）
command       PENDING，未领取（delivery 关闭）
```

认证审计（不含令牌、密码和完整 IP）：

```text
BOOTSTRAP_ADMIN  SUCCESS  1
LOGIN            SUCCESS  5
LOGOUT           SUCCESS  1
PASSWORD_CHANGE  SUCCESS  2
USER_CREATE      SUCCESS  1
USER_UPDATE      SUCCESS  1
```

## 8. 清理

验收进程已全部退出，18080/18091/18092 无监听残留。
临时库 `c1_accept_20261102172507` 在取证后删除；开发库 `agent_squad` 未修改。
