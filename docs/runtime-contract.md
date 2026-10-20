# 运行对象、权威与恢复协议

2026-10-20，Java + AgentScope 设计 v0.2。固定 SDK 评估目标为 v2.1.0-BETA1（提交 5451c73abe47053340683a1cc6d67b290b056648）。这是依赖新会话能力的设计候选，不是已验证生产版本；不用 main，也不混用 v2.0.4 API。具体兼容门槛见[评审](review.md)。

## 1. 标识与权威

| 对象 | 稳定标识 | 唯一权威 | 语义 |
| --- | --- | --- | --- |
| 用户对话 | conversationId | 平台 Conversation | 用户可见历史与归属；一个对话最多一个未终结 GoalTask |
| 用户目标 | taskId | 平台 GoalTask | 一次用户目标，跨重规划、等待、重启保持不变；不命名为框架 Run |
| 协作计划 | taskId + planVersion | 平台 Plan | 不可变 DAG、汇合标准、权限/能力版本引用 |
| 委派节点 | jobId | 平台 WorkerJob | 一个局部目标及其恢复协调，不是一个 Agent 推理步骤 |
| Agent 会话 | bindingId → namespace/userId/agentId/sessionId | AgentScope SessionLog | 一组 Agent 消息、命令与检查点；框架原生记录不由业务表改写 |
| 框架逻辑输入 | frameworkTurnId | AgentScope | 一次 SDK submit；resume 保持 Turn，可产生新 Run |
| 框架执行 | frameworkRunId | AgentScope | 一次执行，不等于用户目标或 WorkerJob |
| 外部操作 | operationId | 平台 Operation | 已授权的稳定调用意图、外部结果及不确定性 |
| 用户可见快照 | taskId + eventSeq | 平台事件读模型 | 从框架持久事实与平台事实投影；不能据此重启模型 |

GoalTask 不强行与 Turn 一对一。Master 先规划、节点完成后再汇总，可以分成多个有界协调轮次，每轮对应一个 framework Turn，均属于原 taskId。每个 WorkerJob 使用独立 Session 和一个初始 Turn，崩溃时恢复原 Turn；原生输入等待用 respond 继续，不新建相同目标偷偷重跑。

Master Session 固定到 taskId + conversation generation；会话历史由授权 ContextSource 读取已经提交的前序目标/最终答案；Worker Session 固定到 jobId；agentId 固定到能力版本，namespace 固定到部署实例的逻辑应用标识。标识由平台建立，模型不能决定主体或写入任意 Session。

## 2. 确定性委派边界

Master/Worker 都使用 HarnessAgent。生产路径通过 publish_plan、request_input、propose_completion 等平台工具委派和收尾；这些是工具适配，不是第二套 ReAct 引擎。生产 Agent 禁用原生自由 agent_spawn/动态子 Agent 创建，避免绕过持久计划、预算与恢复协调。Worker 通过受控能力工厂构建叶子 HarnessAgent，不允许递归委派。

这与“完全使用默认 Subagent 后台工具”不同：当前默认后台本地任务不能保证跨节点接管。WorkerDefinition 仍复用声明/工厂概念，但平台 WorkerDispatcher 是跨 Worker 工作归属权威，框架 AgentSession 是每个 Agent 内部执行权威。不得同时让 WorkspaceTaskRepository 运行同一个生产 Job。未来框架补齐后可替换 dispatcher，保留产品语义。

publish_plan 输入见 PlanPublish；只有 Master 作用域能调用。校验无环、版本、节点数、依赖、Schema、权限与上下文后一次事务创建 Plan、Jobs、Command。它返回 planVersion/jobIds，不等待所有子节点。Master 本轮结束后，用户目标仍 WAITING_WORKERS，不把框架 Turn 完成当用户任务完成。

Job 结果持久完成时递增 task.signalVersion；汇合器只在依赖/等待条件满足、无未处理 Master 轮次时创建一个 MASTER_SIGNAL Command，唯一键为 taskId + signalVersion。输入固定为结果 ID/版本及摘要，不伪装用户新消息。中途新增结果合并到下一轮；避免每条日志都唤醒 Master。

## 3. 持久受理与跨存储桥接

平台 MyBatis 事务与框架 journal CAS 不属于一个事务，不宣称跨两者原子提交。

1. public submit 在平台事务内锁 Conversation，校验 owner/generation/配额、幂等键和输入摘要；创建 GoalTask、MasterBinding、SUBMIT Command、task.accepted 事件，提交后返回 202 taskId/commandId。
2. 202 表示平台持久接受，不表示模型已启动或 SDK inbox 已接受。命令轮询会再次发现未送达记录。
3. bridge 领取 Command，使用稳定 SDK submit key=commandId、固定输入和固定 Binding 调用 AgentSession.submit。SDK 收据持久后写回 frameworkTurnId。此处失败重送同 key/输入，由 SDK 去重。
4. 框架日志导出器按 bindingId/nativeEventId 去重；在平台事务里更新执行读投影、产生公开事件并标记已处理，再推进原生导出水位。回执丢失最多重复导出，不能重复业务处理。
5. 若 SDK 已接受但平台未写回，重送原 Command 或扫描 inbox/日志补映射。禁止换 Session、换 key 或改输入重试。

Command 既是平台命令回执又是持久投递意图，不再另建同含义 Outbox。它调度 SDK 输入，不调度 SDK 模型步骤。平台数据库故障时不接受新目标或副作用；SDK 故障保留命令，返回可见的阻塞原因。

## 4. 运行所有权与租约

RuntimeBinding 保存已知 Session 的发现目录和平台 guard：owner/fence/leaseUntil，数据库 UTC 时间，初值 60 秒，20 秒续租。领取用事务行锁/CAS 增加 fence。owner 是服务实例标识，不是模型字段。每个实例有启动注册与到期扫描，不依赖内存队列发现旧任务。

平台 guard 控制“谁有权调工具和提交业务变更”；框架 journal 的 writer lease/epoch 控制“谁能推进这个 Agent”。两者不同层级。新 owner 取得 guard 后还须等待框架旧 writer 释放/过期，不能越过框架锁，也不能把自己的 fence 写进原生 head 假装获得租约。

创建新 HarnessAgent/AgentSession 时绑定当前 guard、权限快照和不可变配置；接管后重建实例，避免缓存 RuntimeContext 保留旧 fence。旧实例在模型调用前、工具派发前、业务提交前检查 guard，失效立即停止；原生迟到写由 journal 自身 fence 拒绝。

模型在途请求可能重复计费，但不产生重复业务副作用。持久写操作还须通过 Operation 独立 gate，不能只靠 Agent guard。SDK 本地时钟租约需要校时、偏差与网络分区测试；不声称框架 lease 已改为数据库时间。

## 5. 恢复决策表

| 发现状态 | 处理 |
| --- | --- |
| 平台 Command 未送达 | 同 key/输入重送 SDK；业务不重新受理 |
| SDK 命令排队，未执行 | 在原 Session 调用 start，保留输入和原 Turn |
| SDK Run 活跃且 writer 未过期 | 等待；不并发 resume，不释放他人租约 |
| 已中断、有 pending HITL | 保持等待，先验证/应用原回复；不空输入续跑 |
| 已中断、有 uncertain tool | 查 Operation；已知结果由可信适配器 reconcileToolOutcomes；仍未知则 RECONCILING |
| 已中断、无 pending/unknown | 检查取消、授权、版本/文件存在；可恢复时调用 resume 原 Turn |
| 原 Turn 已完成、平台未收结果 | 导出持久事实并补投影，不再调用模型 |
| 检查点/配置不兼容 | RECOVERY_BLOCKED，记录原因；禁止清空会话冷启动冒充恢复 |
| 取消标记存在 | 只执行停止、核对与收尾，禁止恢复正常业务 |

resume 没有应用指定幂等键，不能与 submit 一样盲重送。Command 固定预期 previousRunId；桥接前后检查 SDK inbox/Turn/Run，若已经产生新执行则补收据，若仍有 writer 则等待，不能再排一个 resume。无法辨明进入人工核对，不以新 Turn 规避。

## 6. 副作用与框架日志不一致窗口

| 故障窗口 | 恢复 |
| --- | --- |
| Operation PREPARED、尚未 IN_FLIGHT | 校验审批/撤权/取消后允许发送 |
| IN_FLIGHT 持久后、发送前崩溃 | 也视为 UNKNOWN；不根据推测重发 |
| 外部成功，平台已保存结果，SDK 未保存 tool result | 按原 framework toolCallId 读取结果，reconcile 原调用，再恢复 |
| 外部成功但平台未保存 | 连接器按外部幂等键/关联号查证；不支持查询则人工核对 |
| 平台 Plan 已创建、SDK 未记录 publish_plan 结果 | 以 binding/Turn/toolCallId 或稳定 logicalKey 找原 Plan，返回相同回执 |
| propose_completion 已保存、SDK 未完成该 Turn | 仅保存候选，不发布终态；等待持久 Turn 成功与候选匹配 |

## 7. 完成、取消与正文

propose_completion 保存结构化 CompletionProposal（finalAnswer、outcome、evidenceRefs、planVersion、answerRevision、sourceBinding/Turn）。只有该 Turn 持久成功、所需节点满足、无 UNKNOWN、无待审批且未取消后，Finalizer 才事务写 GoalTask 终态、最终正文事件与 Conversation activeTask 清除。单纯文本回复或 SDK Turn 完成均不能直接判用户目标成功。

取消 API 同事务写 cancelRequestedAt、递增 cancelGeneration、取消未送达业务命令并创建 STOP Command。正在发送的工具不能保证撤回；IN_FLIGHT 转核对。SDK interrupt 是合作请求，不是已停确认。只有所有业务 dispatch 被封闭、执行停止或已失效、未知操作已处理，才置 CANCELLED。无新产品任务可复用旧任务的取消权限。

正文只来自 Master 已授权回答轮次。Worker 原始输出仅进入局部日志与结构化结果，用户页面最多显示脱敏进度。半答案恢复增加 answerRevision 并发 answer.replaced，不拼接两次生成。最终候选校验失败不能沿用旧成功提示。

## 8. 内部应用端口（不是新增 HTTP）

| 端口 | 参数 → 返回 | 调用边界 |
| --- | --- | --- |
| GoalTaskService.submit | Actor, ConversationId, TaskCreate, IdempotencyKey → TaskReceipt | 公共入口，平台事务 |
| SessionBridge.deliver | CommandId → DeliveryReceipt | bridge guard；固定绑定和输入 |
| RuntimeOwner.acquire/renew | BindingId, InstanceId, ExpectedFence → Guard | 数据库时间条件写 |
| PlanService.publish | ExecutionScope, PlanPublish → PlanReceipt | 仅 Master 工具，事务去重 |
| WorkerDispatcher.dispatch | JobId, Guard → FrameworkReceipt | 能力工厂+AgentSession，无手写模型循环 |
| SessionRecovery.inspect/continue | BindingId, PreviousRunId, Guard → RecoveryDecision | 先查 pending/uncertain，再使用 SDK API |
| OperationService.prepare/dispatch/reconcile | ExecutionScope, ToolCall / OperationId / Evidence → OperationView | 写操作独立账本与授权 |
| SessionProjector.accept | BindingId, NativeEvent → ProjectionReceipt | 去重/投影/唤醒同一平台事务 |
| TaskFinalizer.tryComplete | TaskId, ProposalId, ExpectedVersion → CompletionDecision | 检查持久框架事实与业务状态 |

ExecutionScope 为平台构造的 userId/taskId/jobId/bindingId/fence/policyRevision，不能从模型 JSON 反序列化取得。下一阶段实现接口时用具体 Java 类型，不建立无边界的 Map 工具总线。
