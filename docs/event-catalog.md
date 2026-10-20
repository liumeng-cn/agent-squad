# 公开事件载荷与快照

TaskEvent 外壳见 OpenAPI：taskId/seq/type/answerRevision/occurredAt/payload。payload 按以下固定类型校验，未列字段默认拒绝。所有 ID 为业务 UUID，原生 ID 和 Secret 不进入公开事件。

| type | payload 必填字段 | 可选字段 |
| --- | --- | --- |
| task.accepted | commandId | 无 |
| task.state_changed | status、version | reasonCode |
| plan.published | planVersion、jobIds | 无 |
| job.progress | jobId、summary | progressPercent（0–100，仅确实可测时） |
| job.completed | jobId、status、resultVersion | evidenceRefs |
| interaction.requested | interactionId、kind | 无（问题详情从授权视图读取） |
| interaction.resolved | interactionId、status | 无 |
| operation.unknown | operationId | reasonCode |
| operation.reconciled | operationId、disposition | 无 |
| answer.started | producerBindingId | 无 |
| answer.delta | text、messageKey | 无 |
| answer.replaced | replacesRevision | reasonCode |
| answer.completed | text、evidenceRefs | 无 |
| task.completed | status、version | 无 |

answer.started 只能由 Master 已授权回答轮次产生。producerBindingId 是平台绑定ID而非框架原生Session；不向客户端开放执行操作。messageKey 为本次答案内的稳定块键，最多160字符，不是业务数据授权凭证。状态枚举与 GoalTask/WorkerJob 一致，text/summary 长度按契约总量约束。

快照读取一个确定公开 watermark，在同一一致视图构造 task/jobs/pending/answer，返回 cursor=taskId:watermark；UI 先显示快照再读后续事件。原生EventSource首次通过after查询参数传游标；自动重连合法Last-Event-ID优先，event名统一message，data.type区分业务事件。删除和权限变化优先于回放；任何历史游标都不能突破当前读取权限。

原生片段丢失或需重新生成时先 answer.replaced，revision 单调增加。不能在原 revision 追加一次全新生成。最末完整答案以 Finalizer 提交的 answer.completed 为准。

事件驱动 UI 更新不意味着开放工具可视化生成页面；本阶段只定义运行结果的协议。
