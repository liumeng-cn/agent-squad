# Design：C3 对话增量、上下文与 Agent 任务

## 1. 核心原则

ChatGPT 式用户体验 ≠ 公开 Agent 的每次思考。`MasterAgent` 同时承担普通回复与目标协调，公开给用户的仅为 **RESPONSE scope 中的允许展示正文**、服务端发布的真实状态、审批卡和最终产物。Worker 原始日志、工具参数、系统指令、内部规划、模型推理内容不得被原样透出。

保持原来的双层权威：AgentScope 原生 Journal = 执行事实；MySQL 业务消息与事件 = 对用户的权威投影。SSE 断连不触发重新提交模型任务。

## 2. 模型真实增量与安全出口

**先做 SDK 技术探针**：检查当前固定 `agentscope 2.1.0-BETA1` 的 `OpenAIChatModel.stream(true)`、`AgentEvent` 类型、ToolCall/ModelCall 与最终消息对应关系，使用真实协议桩和真实供应商分别核验。不得仅改 `stream(true)` 就声称具备最终可安全展示的 token 流。重点验证：SDK 事件是 user-facing answer 还是内部 ReAct 文本、回合重试/中断后的文本覆盖语义、模型输出的 `reasoning_content` 是否被隔离。

**分阶段落地**：

1. Phase S1：事件增量呈现（不改变模型）。前端只在具体 `message.completed`/状态事件上局部更新，减少所有事件均拉快照；事件 seq 缺口或代际不匹配时严格回退 snapshot，防止错序。
2. Phase S2：若 SDK 可确认安全、可归属的用户可见增量，则业务新增 `response.delta` 公共事件并保留 `message.completed` 最终事实。对于 Task Scope 不提供文本增量，只显示真实任务状态；Master 回合中未明确标记用户可见的内部片段不发布。
3. Phase S3：降低数据库写放大。单个 execution 的增量以 150–300ms 或 512–2048 字符作为**可调批次初值**（具体按压测），每批归属+序列+offset 幂等；最终消息以用户可见最终文本一次事务确认为权威，不把每个 token 持久插入一行。建议新增持久 `sq_response_draft`（`user_id/execution_id/message_id/revision/text/next_offset/status`）作为中断恢复的部分显示权威，同时事件表仅投影有界批次；如果 SDK 不支持可靠增量，用现有完成消息方式不创建该表。

### 建议事件载荷

```jsonc
{
  "conversationId": "uuid",          // 归属会话
  "generation": 1,                    // 代际
  "sequence": 139,                    // 公开事件序号
  "type": "response.delta",          // EventSource 的 event 名仍是 message
  "payload": {
    "executionId": "uuid",           // 对应本轮回复
    "messageId": "uuid",             // 正文身份；重试需要新 revision 或明确替换
    "revision": 2,                   // 修订号
    "offset": 4096,                  // Unicode 文本位置需明确采用 UTF-16 code units 或 UTF-8 bytes 并固化契约
    "text": "新增可见文本",              // 仅允许公开的内容
    "isFinal": false
  }
}
```

明确规定 offset 单位建议统一 **UTF-16 code units**（前端 JS string.length 可直接操作）；Java 后端需专门实现一致的 UTF-16 offset 校验，避免 emoji/代理项切开；更推荐序号 `chunkIndex` + `revision` 而非以字节 offset 为唯一核对口径。最终实现以 **`chunkIndex` 连续 + 文本 hash + final message`** 为强一致验收，设计初期可选其中一种，在 API/schema 中只能保留一种正式权威形式。不得混用多种 offset 口径。

- 客户端：按 (conversationId, generation, executionId, messageId, revision, chunkIndex) 去重，序号不连续时先停止拼接、调用 snapshot 或 draft 回放；`message.completed` 以 DB 最终消息全文替换，不将 delta 和最终全文重复追加；断线重连按 `after` 水位续传。切会话 epoch 防回调污染。
- 失败或模型部分输出：若合法且对用户可见，保留 partial 草稿并以真实状态标注；拒绝“失败却展示为已完成”，不把 TOOL payload 当可见内容。
- 旧客户端：忽略未知事件仍可通过 snapshot 看到完整最终回复，接口保持兼容。
- 性能：前端 `mergeMessages()` 当前每次复制+全量排序，增量时避免每 token 对全部历史排序；只更新对应 message 的局部状态。

## 3. Markdown 渲染

`SafeMarkdown.vue` 当前自写语法子集，建议使用经过维护的 Markdown AST/解析库，禁止原生 HTML，URL scheme 只放行 `https/http`（图片先默认不启用远程加载，避免追踪像素/隐私泄露；启用时走图片代理或用户明确授权）。支持表格、代码围栏、引用、列表、链接、代码复制、代码语言提示、软换行；语法高亮可二期引入 lazy-load 库。

- 不得将未知 HTML 直接交给 `v-html`；若使用 HTML 输出必须经过可靠 sanitizer 并测试 SVG/属性/URL 攻击；更偏好 AST→受限 Vue VNode。
- 自动化覆盖：`<script>`、`javascript:`、`data:`、`onerror`、恶意 Markdown 链接、深层嵌套、超长代码块及表格。

## 4. 会话草稿/附件与交互

当前 `WorkspaceView.vue` 通过 `:key=conversationId` 重建 `ChatComposer`，且 watch `conversationId` 直接清空草稿/上传。改为按会话 ID（新建草稿使用独立 draft key）保留 **文本草稿**；使用 per-conversation Pinia 状态，持久化策略明确：首选 sessionStorage 仅存文本（避免持久化敏感内容）；附件仅在同一前端会话内保留已上传 artifactId 与可安全引用的信息，刷新后如果归属/时效不明则要求用户重新选择，不能自动重传旧 File。

- 发送成功仅清空当前会话对应草稿；发送不确定继续复用同一 Idempotency-Key，与当前 `ChatComposer.send` 行为一致。
- 多用户登出时清除所有对话草稿及临时附件引用；用户 A 登录后不得看到 B 的草稿。
- 长会话：使用消息数量阈值/性能回归决定是否虚拟滚动；保留“查看更早消息”位置与自动滚动 pinned 行为。
- 前端测试：引入 Vitest + Vue Test Utils（或 Node runner 等等效方案），覆盖 Pinia `epoch`、SSE seq 缺口、重连、分批增量、草稿与登录切换；CI 必须调用 `npm test`。

## 5. 上下文窗口与摘要（保证事实/记忆约束）

当前 `ResponseContextService.freeze()` 对消息字符 + 附件字符总量 >200000 直接拒绝；预算用字符而非真实 model token。新设计分三段：

1. **确定性选材**：当前用户消息必选、最近 N 轮原文、当前 activeTask/Directive 的权威状态事实、用户明示的附件引用、有效记忆引用；总量先根据模型 Profile 最大 token 和安全余量计算，不能只按 200k 字符判定。
2. **历史摘要**：对更早完成的对话生成摘要（独立低风险模型调用或本地确定性摘要），存 `sq_conversation_summary`（`user_id, conversation_id, generation, through_message_seq, version, summary_text, source_hash, memory_revision, created_at`）并计入用户模型预算。摘要仅由原始已完成消息产生，必须标注是摘要、不可替代用户精确指令；对引用失效或记忆删除后不再复用过期摘要。
3. **冻结与重投**：先生成/更新摘要，再在单一快照事务固定 summary 版本 + 近期原文 + 授权引用与内容摘要；同一个 `executionId` 的重投必须复用原冻结上下文并校验 hash；历史已被撤销时应拒绝旧冻结上下文，不得通过摘要复活被删记忆。无法安全摘要或模型预算不足时，优先保留当前消息与最近 N 轮、显式告知较早材料未纳入；绝不丢最新用户指令并装作成功。涉及用户原文完整性时不静默省略关键约束。

- 摘要必须通过包含指令注入/敏感记忆撤销的负面测试；不要让摘要重新成为“系统指令”。
- `ResponseContextService` 内的 200000 限制可保留作为最终防护，但不应该成为长会话唯一策略。

## 6. 修复“首次规划期间早到 Directive”

历史 E2E 曾记录以下案例（归档材料已从工作区移除，可在 Git 历史查阅）：Master 首次计划发布前收到用户补充，`PlanService.publish` 因 `pending` 拒绝；模型错误使用 sourceMessageId 调用 `apply_task_directive`，导致 MAX_ITERATIONS。解决的方向不是加一句提示词，而是让任务协调有确定性 ID 与安全状态：

- `TaskDirectiveMapper.pending(userId, taskId)` 已能返回 ACСEPTED 指令；提供受控只读工具/执行上下文 `list_pending_task_directives`（或者在 `publish_plan` 的业务错误结果中返回**当前归属任务的**指令 `directiveId/expectedVersion/kind/content`，不得暴露他人），以便 Master 使用真实 UUID；只读查询仍验证 Task Scope。
- `publish_plan` 发现 pending 时返回结构化 `PENDING_DIRECTIVE`（包含仅属于当前 Task 的待处理 ID），不允许造成无限模型循环；平台也可以在首次规划运行结束后创建 `TASK_DIRECTIVE` 的协调命令。**任何决定由真实 ID 和 CAS 版本驱动，不让模型凭 sourceMessageId 猜。**
- 如果规划已经提交不可变计划且新指令变更目标，按既有 plan version / goalRevision fencing 按安全点替换/失效旧计划，旧 Worker 结果不得自动作为新目标交付。
- 如果协调失败，任务应保持可诊断的待处理/恢复受限状态，并可重新协调；不能因 MAX_ITERATIONS 简单宣布完成；不能绕过未知 WRITE 核对。
- 必测：补充于首轮 publish 前、publish 事务提交前后的并发、重复补充、同键重放、多个指令、审批挂起、取消、旧计划结果迟到；真实模型场景复跑记录。

## 7. 交付验证

- 对一个普通回复监控：真实首段可见时间、最终消息一致性、恢复后重复字符数（应为 0）、序列缺口与回放；不能凭“每 500ms poll”估算固定首字延迟。
- 实际运行的 100/1000 token 长回答记录：SSE 事件批次数、DB 写次数、前端重渲次数和 p95 延迟；只展示测量，不宣称任何模型提供商 SLA。
- 新 UI 必须适配登录态与权限变化，禁止跨用户草稿和临时 delta 缓存串数据。
