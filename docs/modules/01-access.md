# M01 接入、身份与会话

## 入口与数据

POST /api/v1/auth/login → LoginRequest / AuthView；POST /auth/logout → 204；GET /auth/me → AuthView。POST /conversations → ConversationCreate / Conversation；GET /conversations → ConversationPage；GET/DELETE /conversations/{conversationId}。完整路径及错误见 OpenAPI。

Account 保存用户名、密码散列、roles、status、authVersion；AuthSession 保存随机会话令牌哈希、有效期及登录时 authVersion，不存明文 token。Cookie HttpOnly/Secure/SameSite=Lax；GET me 返回会话绑定 CSRF 值（由服务端密钥与随机登录Session ID确定性派生，数据库保存校验摘要；不靠从哈希反解原值），写请求须同时携带 X-CSRF-Token；登录校验同源 Origin，登录成功旋转会话和 CSRF。开发 HTTP 仅明确的 localhost 配置可关闭 Secure。

Conversation 保存 ownerUserId、generation、activeTaskId、title、status/version。RuntimeContext 从已认证主体和服务端 Binding 构建，客户端/模型不得指定 org/user/session 命名空间。管理员权限不隐式等于可读所有用户数据；敏感运维核对单独授权并审计。

## 归属与删除

所有 ID 查找先验证 userId 复合归属；不存在与无权统一返回 NOT_FOUND。列表游标绑定 owner、过滤条件和稳定排序，不能跨用户复用。Worker 内部工具使用 ExecutionScope，不能接受普通请求体中的身份覆盖。

DELETE conversation 校验 If-Match，事务内 generation+1、status=DELETING、撤销在途 GoalTask 正常派发、写取消与清理 Command，返回 202。立即禁止读取/继续；只有停止和核对可执行。异步清理框架命名空间、记忆派生引用和产物，保留最小审计与未决外部操作，不立即删除 UNKNOWN 证据。

并发受理必须锁 Conversation 行检查 generation/status，保证删除后不能创建新任务。清理幂等且记录进度；逻辑删除不意味着文件和备份已经物理删除。

## 用例与验收

登录 → 创建会话 → 提交目标 → 断开页面 → 再登录查询；账号禁用或 authVersion 变化使旧 Cookie 失效。两个用户使用相同显示标题、伪造 conversationId 或 frameworkSessionId 均不能串数据。删除与提交并发仅一方成功，旧 guard 不能绕过删除屏障。

初始账号与角色由受控初始化流程建立，不开放公共注册/升权接口；角色变化递增authVersion使旧会话失效。测试fixtures显式包含两个用户和一个受限运维角色，管理员不默认具备所有资源内容权限。
