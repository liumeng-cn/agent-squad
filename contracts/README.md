# HTTP 与应用 DTO 契约 v0.2

[openapi.json](openapi.json) 已替换原 57 接口草稿，当前为 Java + AgentScope 设计。只定义公共 HTTP 和内部工具使用的 DTO，不将同 JVM 应用端口包装成内部 HTTP。所有路径 planned，无运行服务；[manifest](design-manifest.json)记录可复核数量和模块表归属。

## 统一规则

- 用户入口用 HttpOnly Cookie；除登录外写请求需要 X-CSRF-Token；登录检查同源 Origin。AuthView 返回 CSRF 值，原始 Cookie 不进入 JSON。
- 修改请求携带 Idempotency-Key，按 userId + 动作/资源 scope 去重；同键同规范化输入返回原回执，不同内容 409。令牌、密码等敏感输入用服务端 HMAC 指纹比较，不把其原值写 Command/Audit；Connection secret 先安全存储，再只保存 secretRef。
- expectedVersion / If-Match 是产品资源版本；frameworkTurnId、frameworkRunId、原生日志 seq、guardFence 各有不同语义，不能互换。所有时间为 UTC ISO8601，业务 ID 为 UUID；原生框架 ID 按不透明字符串处理。
- TaskReceipt 的 202 表示平台持久受理，不表示 SDK 已启动。CommandReceipt 表示命令持久接受，不能被当作停止/审批生效的最终确认。
- SDK 对接通过[运行协议](../docs/runtime-contract.md)中的应用端口；执行身份只能由平台注入。OpenAPI 中 PlanPublish、RuntimeBinding、ToolIntent 等是内部 DTO，不意味着开放 HTTP 管理接口。
- Error 含 code/message/requestId/details；校验失败 400 或 422；未登录 401；缺角色 403；资源不存在或无归属权限 404；版本/幂等/任务忙 409；容量限制 429；存储或执行依赖不可用 503。恢复受阻和操作未知是可查询业务状态，不伪装空成功。SSE 过期游标 410。
- 列表 nextCursor 可缺省，绑定用户/过滤/排序；limit=1–100。游标有签名，不接收任意 SQL 排序。

## 条件校验

Schema 之外还必须检查：ContextItem 的 content/artifactId 至少其一；PlanNode 引用归属与 DAG；TaskCreate 附件 READY 且同 owner；JobResult 证据范围；APPROVAL 确认原参数 hash；INPUT answer.value 满足原 Interaction.answerSchema；PATCH 至少一个实际修改字段。

PlanPublish 的 node.inputSchema/outputSchema 使用 WorkerDefinition 已发布版本，不接受模型放宽约束。模型不能提供 RuntimeBinding、租约或身份对象冒充执行上下文。连接策略与 JSON Schema 深度/大小均须服务端验证。

通用 JSON 上限 2MiB；Skill 文本包 1MiB、单文件 256KiB；上下文 1MiB；文件 10MiB；正文最大 200000 字符且仍受整体字节/预算上限。Schema 的字符串长度不代替 UTF-8 字节限制。禁止任意 pickle、宿主路径、回调 URL。

## 交互与输出

Interaction.APPROVAL必须携ApprovalContext供用户核实脱敏影响；OperationDetail提供脱敏参数/结果证据，SkillDetail提供文件/Grant，任务CommandReceipt可按commandId授权查询。ConnectionUpdate.expectedVersion匹配Connection.revision；Skill首次Grant为enabled=false/version=0，创建授权后版本递增。

InteractionResponse.kind 决定 INPUT/APPROVAL 分支；APPROVAL 不允许修改工具参数或接受永久放行规则。respond 返回 CommandReceipt；原生交互若已消费，通过日志对账返回原结果而非再执行。

SSE 发送 TaskEvent，event名为message，事件 ID=taskId:seq；首次after参数取snapshot.cursor，自动重连的合法Last-Event-ID优先。snapshot 返回一致公开水位，读取与订阅不触发模型。payload 的字段见 [事件规范](../docs/event-catalog.md)，不能将任意原生记录直接透传。

[examples.json](examples.json) 为虚构正例；[invalid-examples.json](invalid-examples.json) 为必须拒绝的结构反例。静态检查只验证引用/示例/设计一致性，不代替完整 OpenAPI 工具、SQL 执行或 SDK 故障实验。
