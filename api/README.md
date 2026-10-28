# HTTP 与应用 DTO 契约 v0.2

[openapi.json](openapi.json) 已替换原 57 接口草稿，当前为 Java + AgentScope 设计。只定义公共 HTTP 和内部工具使用的 DTO，不将同 JVM 应用端口包装成内部 HTTP。契约按阶段实现，历史 planned 标记不构成运行能力证明；具体实现与验证状态见对应 OpenSpec 变更证据。[manifest](design-manifest.json)记录可复核数量和模块表归属。

## 统一规则

- 用户入口用 HttpOnly Cookie；除登录外写请求需要 X-CSRF-Token；登录检查同源 Origin。AuthView 返回 CSRF 值，原始 Cookie 不进入 JSON。
- 修改请求携带 Idempotency-Key，按 userId + 动作/资源 scope 去重；同键同规范化输入返回原回执，不同内容 409。令牌、密码等敏感输入用服务端 HMAC 指纹比较，不把其原值写 Command/Audit；Connection secret 先安全存储，再只保存 secretRef。
- expectedVersion / If-Match 是产品资源版本；frameworkTurnId、frameworkRunId、原生日志 seq、guardFence 各有不同语义，不能互换。所有时间为 UTC ISO8601，业务 ID 为 UUID；原生框架 ID 按不透明字符串处理。
- TaskReceipt 的 202 表示平台持久受理，不表示 SDK 已启动。CommandReceipt 表示命令持久接受，不能被当作停止/审批生效的最终确认。
- SDK 对接通过[运行协议](../openspec/specs/runtime/spec.md)中的应用端口；执行身份只能由平台注入。OpenAPI 中 PlanPublish、RuntimeBinding、ToolIntent 等是内部 DTO，不意味着开放 HTTP 管理接口。
- Error 含 code/message/requestId/details；校验失败 400 或 422；未登录 401；缺角色 403；资源不存在或无归属权限 404；版本/幂等/任务忙 409；容量限制 429；存储或执行依赖不可用 503。恢复受阻和操作未知是可查询业务状态，不伪装空成功。SSE 过期游标 410。
- 列表 nextCursor 可缺省，绑定用户/过滤/排序；limit=1–100。游标有签名，不接收任意 SQL 排序。

## 条件校验

Schema 之外还必须检查：ContextItem 的 content/artifactId 至少其一；PlanNode 引用归属与 DAG；TaskCreate 附件 READY 且同 owner；JobResult 证据范围；APPROVAL 确认原参数 hash；INPUT answer.value 满足原 Interaction.answerSchema；PATCH 至少一个实际修改字段。

PlanPublish 的 node.inputSchema/outputSchema 使用 WorkerDefinition 已发布版本，不接受模型放宽约束。模型不能提供 RuntimeBinding、租约或身份对象冒充执行上下文。连接策略与 JSON Schema 深度/大小均须服务端验证。

通用 JSON 上限 2MiB；Skill 文本包 1MiB、单文件 256KiB；上下文 1MiB；文件 10MiB；正文最大 200000 字符且仍受整体字节/预算上限。Schema 的字符串长度不代替 UTF-8 字节限制。禁止任意 pickle、宿主路径、回调 URL。

## 交互与输出

Interaction.APPROVAL必须携ApprovalContext供用户核实脱敏影响；OperationDetail提供脱敏参数/结果证据，SkillDetail提供文件/Grant，任务CommandReceipt可按commandId授权查询。ConnectionUpdate.expectedVersion匹配Connection.revision；Skill首次Grant为enabled=false/version=0，创建授权后版本递增。

InteractionResponse.kind 决定 INPUT/APPROVAL 分支；APPROVAL 不允许修改工具参数或接受永久放行规则。respond 返回 CommandReceipt；原生交互若已消费，通过日志对账返回原结果而非再执行。

SSE 发送 TaskEvent，event名为message，事件 ID=taskId:seq；首次after参数取snapshot.cursor，自动重连的合法Last-Event-ID优先。snapshot 返回一致公开水位，读取与订阅不触发模型。payload 的字段见 [事件规范](../openspec/specs/events-artifacts/spec.md)，不能将任意原生记录直接透传。

[examples.json](examples.json) 为虚构正例；[invalid-examples.json](invalid-examples.json) 为必须拒绝的结构反例。静态检查只验证引用/示例/设计一致性，不代替完整 OpenAPI 工具、SQL 执行或 SDK 故障实验。

## 对话执行基础迁移

`conversation-first-experience` 的 1.1 先增加 MessageReceipt、ConversationSnapshot、MessageView 和 ResponseExecutionView 等 DTO；它们目前是数据与传输基础，消息受理接口、SDK 普通回复运行与页面切换由后续任务接入。新增 schema 的 foundation-dto 标记不代表接口已经开放。

- V7 只扩展消息、回复执行、ExecutionScope、BudgetAccount、会话事件和消息附件关联，以及旧表的归属引用。
- V8 在事务中回填旧 Task 的共享账户/作用域、原始用户消息和最新公开答案；稳定来源键避免重复。旧任务仍保留，旧 Session/Command/submit key 与 Reservation 金额不变，不创建模型命令，也不伪造历史 ResponseExecution。
- 账户只保存固定额度，实际占用继续由 Reservation 的 SETTLED/RESERVED/UNKNOWN 事实计算，避免复制汇总余额导致漂移。
- 新 Scope 查询同时验证用户、会话代际、逻辑删除和真实 owner；跨用户、删除资源或失效代际不可读取。
- 迁移新增表与列保持旧链路可运行；在运行层切换前，新建旧式 Task 尚不走新消息链路。实际开发库升级与页面验收应在主链路接入后进行，不能据此宣称聊天体验已完成。

## 共享执行边界

`conversation-first-experience` 的 1.2 将运行归属扩展为 ExecutionScope：任务绑定、普通回复绑定、命令、工具操作、交互与附件可引用同一用户范围内的执行，taskId 只是可选业务上下文。旧任务入口已经创建固定 Scope/Account，Worker 局部快照也记录 Scope；普通回复的正式消息入口与 SDK 收尾仍由 1.3/1.4 接入。

预算在同一会话/实际所有者、用户总账和账户锁下预留；MySQL 的占用查询使用当前读，不能沿用等待锁前的旧快照。默认同用户累计上限为 50000000 微单位，可通过 `SQUAD_BUDGET_MAX_MICROUNITS_PER_USER` 配置，不随新会话或转任务重置；未知收费继续占用。

工具计费键固定在 Operation：V10 保留旧操作原稳定键，新 READ 请求按原生会话/Turn/工具调用身份摘要分开预留，WRITE 仍按业务意图去重。连接器写入异常不证明未执行，记录 UNKNOWN；核清前同会话既不准备新外部操作，也不派发先前 PREPARED 的操作。派发校验与 IN_FLIGHT 在独立事务中提交，外部 I/O 在事务外执行。

用户答复在最后一个开放交互解决后把相应执行置 QUEUED，避免 SDK 立即继续时仍被等待态误拦截；这不代表模型已经运行。停止/删除/代际变化拦截新派发，已发生的结算和核对仍保留事实。Operation/Interaction 的 taskId 可省略，CommandReceipt 的 taskId 可为 null。

## 消息持久受理

- `POST /api/v1/conversations/messages` 首发原子创建会话和消息；`POST /api/v1/conversations/{conversationId}/messages` 在已有会话中追加消息，在途 Task 不构成“会话忙”。请求为 `{text, artifactIds?}`，Cookie、CSRF 与 Idempotency-Key 均须合法；用户、Task、Scope 等未知字段拒绝。
- 原子事务写入 Conversation/Message/ResponseExecution/Scope/Account/Binding/Command 和两个受理事件，附件必须同用户且就绪有效。无效、越权或过期附件使整批回滚，已上传文件不随消息受理失败被删除。
- 首发并发同键只保留一套事实；唯一键失败先完整回滚，再从新读视图补查胜出收据。同键同正文/附件返回原身份，同键换输入 409；附件 UUID 大小写规范化，顺序保留。
- 首发重送须使用原首发端口和原 key；获得收据后才把后续消息发送到会话追加端口。接口返回 MessageReceipt，不返回新 Task。cursor 为会话受理前缀，不能当任务事件游标使用。
- `MESSAGE_SUBMIT` 目前仅持久排队，普通回复 Bridge/SDK 生命周期由 1.4 接入；新增接口的 202 不代表模型已启动。旧聊天页面尚未切换到这些入口。
