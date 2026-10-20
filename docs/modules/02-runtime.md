# M02 用户目标与运行集成

以[运行协议](../runtime-contract.md)为状态权威及故障窗口依据。使用 AgentScope BETA1 的持久会话能力，不自己存每个模型步骤和检查点。

## 公共接口

POST /api/v1/conversations/{conversationId}/tasks：TaskCreate → TaskReceipt，202。GET /tasks/{taskId} → GoalTask；POST /tasks/{taskId}/cancel → CommandReceipt，202；POST /tasks/{taskId}/resume → CommandReceipt，202。写请求携带 Idempotency-Key；控制命令还需 expectedVersion。

TaskCreate 为 message、artifactIds、profileKey；权限、能力、Skill、模型版本及预算限制由平台冻结。TaskReceipt 包含 taskId/commandId/status；frameworkRunId 可以尚不存在，不能伪造一个“运行中”收据。

## 产品状态

| 状态 | 条件 / 后继 |
| --- | --- |
| QUEUED | 平台持久受理，尚未推进；可 RUNNING/CANCELLING |
| RUNNING | Master/Worker 有可推进执行；可等待、恢复受阻、核对、取消或终态 |
| WAITING_WORKERS | 有未终结 Job；结果汇合后 RUNNING |
| WAITING_INPUT / WAITING_APPROVAL | 全部工作等待交互；有效答复后 QUEUED |
| RECOVERY_BLOCKED | 检查点、凭据、配置或兼容性缺失；修复并授权恢复后 QUEUED |
| RECONCILING | 未知外部结果阻止业务推进；核对后回到可推进态或取消收尾 |
| CANCELLING | 禁止新业务；可 RECONCILING / CANCELLED |
| SUCCEEDED / PARTIAL / FAILED / CANCELLED | 产品终态；不可 resume；用户重做创建新任务 |

cancelRequestedAt 独立保存，进入 RECONCILING 不清除。状态由平台业务事实决定，framework Run 状态仅是其中一项证据。框架 Turn 失败允许进入恢复而不是立即把 GoalTask 失败。

## 事务、命令与生命周期

受理锁 Conversation，按 userId/scope/idempotencyKey 查 Command 摘要；相同请求返回原 receipt，不同内容 409。创建 Task、Binding、Command 和事件，设置 activeTaskId 后提交。一个会话最多一个未终结目标，忙时 409，不偷偷排第二个目标混上下文。

Command 包含 action、targetBindingId、payload、payloadHash、expectedRunId、deliveryStatus、receipt、dueAt 和发送租约；本身就是持久桥接意图。SUBMIT 用稳定 Command ID 作为 SDK 幂等键；RESUME、RESPOND、STOP 先查日志/原请求状态再决定补投递，详见运行协议。

Binding 存固定 framework identity/configDigest、guard、上次观察 NativeSeq、最新 Turn/Run 引用；后两者是可重建读投影。启动扫描发现未终结 Binding，按任务取消/交互/未知结果优先级恢复。owner 管理 Agent 实例，不能在 HTTP 返回时关闭后台 Agent；应用关闭停止领取、合作中断并释放自身资源，不更改他人任务。

resume 前验证 Task version、配置、授权、记忆/会话 generation、原生 pending/uncertain；已排队返回已有回执，不建立新用户目标。反复自动失败超过 profile.maxRecoveryAttempts 后进入 RECOVERY_BLOCKED，不能无限计费重跑。

完成只由 Finalizer 校验候选及持久框架完成事实后提交；最终状态、公开答案事件和 activeTaskId 清除同一个平台事务。取消流程与失败核对不执行补偿；补偿必须另建授权 Operation。

验收：平台已受理而 SDK 未接受、SDK 已接受而平台未收据、Turn 完成但导出丢回执三个窗口均不丢任务或重复执行；两 owner 竞争及旧实例迟到受双层 guard/journal 约束。
