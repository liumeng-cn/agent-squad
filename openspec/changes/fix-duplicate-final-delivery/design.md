# Design：同源消息最终交付的持久裁决

## 1. 事实边界与定位

已观察到：一条用户写作请求对应两条完整且内容不同的助手回复。源代码中存在两条写 `sq_message` 的独立路径，但**尚无该次会话的线上执行事实**，不能断言必然由 RESPONSE + TASK 并行交付导致。

定位时按 `conversationId` 读取 `sq_message.message_seq/message_id/role/source_message_id/execution_id/task_id`，关联 `sq_goal_task.origin_execution_id/origin_message_id`、`sq_response_execution`、对应 `answer.completed` 和 `message.completed` 事件，并核对提交时间及 Native Binding/Command 身份。只采集必要字段和正文摘要，禁止记录完整 Prompt、敏感上下文及模型私有推理。

判断矩阵：

| 运行事实 | 定位方向 |
| --- | --- |
| 同一 sourceMessageId 一条有 executionId，另一条有 taskId | RESPONSE 与 TASK 各自发布完整结果 |
| 同一 sourceMessageId 两个不同 executionId | 重试、重复受理或执行代际裁决 |
| 相同 messageId 仅在页面重复 | SSE/snapshot 合并与渲染身份 |
| 同一 taskId 对应多次完成投影 | `answer.completed` 投影幂等与重放 |

## 2. 交付权模型

**选择：平台持久交付权 + 最终发布事务校验**，而非增加 Prompt 禁令或按正文相似度过滤。

以 `(userId, sourceMessageId, deliveryGeneration)` 标识逻辑最终交付位（具体是否引入新表或在现有执行实体扩字段，由 apply 前的事务/约束核查决定）。其最小事实包括：

- `ownerType`：`RESPONSE` 或 `TASK`；`ownerId` 指向 `executionId` 或 `taskId`；
- `state`：`OWNED`、`PUBLISHED` 或明确的放弃/终结状态；
- `publishedMessageId`：完成后不可随事件重放再分配不同消息身份；
- `version` / generation：用于 CAS 和明确的合法重试/重新发起边界。

初始化为 RESPONSE 拥有交付权；仅当 `create_goal_task` 成功持久受理时，在同一受控事务与校验边界将交付权转为 TASK。若 RESPONSE 已正式发布结果，则后续创建同源 Task 必须拒绝冲突，不能暗中再次交付。转移成功后 RESPONSE 仍可正常结束其原生 Turn，但不得作为 FINAL 发布模型全文。

不使用 `active_task_id` 等瞬时会话状态判定归属；以原始 `sourceMessageId`、`origin_execution_id` 和持久绑定追溯本次交付。多条独立用户消息各有独立交付位；同会话已有 Task 时的新问题仍能正常直接回复。

## 3. 两条发布路径的统一收口

- `MessageAcceptanceWriter`：用户消息、RESPONSE 执行与初始交付位原子受理；原有 Idempotency-Key 重送复用原身份。
- `GoalTaskCreationService`：保留任务创建幂等；在同事务里核对/转移对应交付权，处理与 RESPONSE 完成竞争。
- `ResponseExecutionService.finish`：持久完成 RESPONSE 执行；只有持有 FINAL 权限时才将 `finalAnswer` 写成正式回答。若已委派，仅发送平台确定的简短受理/进度通知（或依 UI 既有任务状态不额外发消息），不把模型提供的完整结果当作通知透出。
- `TaskFinalizerService` / `TaskEventPublishingService`：Task 终态与对话最终结果分开处理；只有任务仍持有该代际交付权才发布 FINAL，重复完成事件返回既有消息身份。
- 出错与恢复：发布事务失败后仍由持久交付位决定是否重试投影；取消、失效计划、迟到 Worker 结果不能夺回交付权。若确需用户显式重做，应建立清晰的新代际，不将自动重试当成第二次正式交付。

推荐由服务端统一标记或持久区分 `ACK/PROGRESS/FINAL`，公开 API 若新增类型须同步契约和前端；不能根据回复长度/正文字面内容推断类型。

## 4. 事务与替代方案

- 最终仲裁必须与 `sq_message` 正式发布处于同一事务，或使用同等可证明的唯一约束 + CAS；锁顺序需明确，避免 `GoalTaskCreationService` 与 `ResponseExecutionService` 相互死锁。
- 优先评估复用现有 `sq_response_execution` 与 Task 绑定关系，再决定最小落表方案；若新建交付位，按项目规范同步唯一 baseline DDL、中文字段注释、Mapper/DTO 与权限隔离。
- 不选“仅通过 Prompt 限制 Master”——模型不能保证遵守。
- 不选“前端按相似度去重”——两份不同作文仍会重复，且会掩盖后端错误。
- 不选“每次请求最多一条助手消息”——会误杀合法受理通知与追问。
- 不选“只在最后按 sourceMessageId 覆盖旧消息”——已显示结果与实际来源不透明，且会破坏可审计性。

## 5. 验证与交付

测试矩阵覆盖：普通问答、普通两篇作文、主动创建 GoalTask 的协作写作、转交后 RESPONSE 仍提交完整 `finalAnswer`、GoalTask 失败/取消、重复工具调用、重复事件、重复执行回执、响应与任务完成并发、SSE 断线/回放、用户明确重新生成、新消息与旧任务并行交流。

验收以数据库权威最终交付数量和 Web 可见 FINAL 数量同时为准；正常 ACK 不计入 FINAL。记录真实测试命令、通过/跳过项和脱敏证据，未经真实模型验证不宣称已解决历史案例。
