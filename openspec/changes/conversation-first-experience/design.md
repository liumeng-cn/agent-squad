# Design

## Context

动机与范围见 proposal.md。当前事实：

- `GoalTaskService.submit` 为每次发送原子写入 Task/Binding/Command，并用 conversation.active_task_id 拒绝第二条输入。
- 聊天 `runtime.send` 直接调用 createTask；历史由 TaskSnapshot 拼接，缺少独立消息账本；Workspace 无会话时不展示输入框，在途任务锁住输入。
- BudgetService、Operation、Interaction、Artifact 关联与 SDK 投影广泛依赖 taskId，不能只改消息入口或让 taskId 为空就认为完成。
- 当前 AgentScope 模型配置 stream(false)，因此本期先交付真实持久阶段反馈，不承诺逐 token 输出。后续接真实增量仍需先持久再公开。
- 主 spec 的“会话忙返回 409”“直接回答也完成任务”“任务详情独立路由”与用户验收目标冲突；本变更 delta 明确替换这些行为，既有 D3 证据保留为旧基线。

## Goals / Non-Goals

**Goals:**

- 将业务目标与推理执行分开，不削弱 D3 的持久受理、guard、预算、授权与外部写入核对。
- 使普通对话、后台工作与多轮补充能共处一个用户可理解的消息流；Worker 仍是局部委派执行者。
- 在 D4 前建立稳定的执行归属与上下文引用，后续接能力不再修改聊天入口。

**Non-Goals:**

- 不实现 D4 Skill/记忆与 D5 Sandbox 的业务能力，只同步它们的接入约定。
- 不将每条消息重命名为隐形 GoalTask，不依据关键词硬编码判断聊天/任务，不手写模型 JSON 决策循环。
- 不引入服务拆分、队列中间件、自动授权或自动安装；不以视觉完成替代真实执行验收。

## Decisions

### 1. 对象身份与归属

选择独立 Message 与 ResponseExecution，抽取共享运行归属 ExecutionScope 和 BudgetAccount。理由：避免给所有低层方法传可空 taskId，也避免复制一套没有预算/恢复的聊天执行器。替代方案“每句建隐藏任务”仍保留错误生命周期；“聊天直接调模型”丢失已有保障，均不采用。

| 对象 | 关键字段与规则 | 生命周期/所有权 |
| --- | --- | --- |
| Conversation | conversationId/userId/generation/version、activeTaskId、nextMessageSeq、nextEventSeq、title/titleSource/titleVersion、lastActivityAt | 用户所有；删除递增 generation，拒绝新受理、读取和派发 |
| Message | messageId/conversationId/seq/role/content/revision/sourceMessageId/executionId/taskId?、artifactRefs | user/assistant；内部工具与协调轮次不是用户消息；正文修改通过 revision 替换 |
| ResponseExecution | executionId/sourceMessageId/scopeId/bindingId/status/version、answerMessageId、failureId? | 每条用户消息有独立可靠回复执行；普通完成不结束任务 |
| ExecutionScope | scopeId/userId/conversationId/generation、kind RESPONSE/TASK、ownerId、budgetAccountId | Binding/Command/Operation/Interaction 的统一受权主体；ownerId 按 kind 校验，不充当业务任务 |
| GoalTask | taskId/originMessageId/originExecutionId/scopeId/version、D3 状态与 Plan/Jobs | 由 Master 创建；一个会话最多一个未终结目标，多个历史目标可查 |
| BudgetAccount | accountId/userId/limit/reserved/settled/unknown、pricingVersion | 首次回复受理时固定；任务沿用来源账户；任务终结前不能因来源回复完成而关闭 |
| MessageDirective | directiveId/sourceMessageId/taskId/logicalKey/payloadHash/expectedVersion/status、appliedVersion | Master 明确提交目标补充/取消/重新规划；ACCEPTED/APPLIED/REJECTED，未应用不能声称生效 |
| Failure | failureId/scopeId/category/code/actor/action/retryable/attempt/maxAttempts/safeMessage/diagnosticId | 持久事实；页面只接安全字段，不接堆栈或原始模型上下文 |

新增表按当前 V6 后递增迁移，遵守业务 UUID 与 BIGINT 主键分离、中文注释和公共审计字段。现有任务数据不删除，scope/account 由 taskId 稳定映射回填；唯一键包括 userId+conversationId+idempotencyKey、conversationId+messageSeq、bindingId+nativeEventId、scopeId+logicalKey。迁移不改变原 SDK sessionId、submit key 或原生 journal。

Operation/Interaction、Reservation、ContextSnapshot、Binding、Command 统一关联 scopeId，taskId/jobId 为可选业务上下文而非普通回复的前置条件；不同作用域仍校验 userId、generation 和 guard。产物输入关联 Message，输出关联 ExecutionScope 并可关联 Task/Job；下载仍回用户和有效归属验证，不接受客户端自报 scope。

### 2. 请求、事务与恢复

选择消息受理与任务受理解耦，复用已有 Bridge/投影链路。理由：任务是 Master 的业务决策，不是发送按钮的副作用。替代方案前置一次无持久保障的“意图识别模型”多一次成本且有崩溃窗口，不采用。

规划契约（实现时统一写入 api/openapi.json；这里不是已上线接口）：

| 入口 | 输入 | 输出与边界 |
| --- | --- | --- |
| POST /api/v1/conversations/messages | Idempotency-Key；首次消息 text/artifactIds | 首发原子创建 Conversation/Message/Execution，202 MessageReceipt；同键重试返回同一会话 |
| POST /api/v1/conversations/{id}/messages | 同上，归属由登录态决定 | 202 {conversationId,messageId,executionId,commandId,status,cursor}；不直接返回新任务 |
| GET /api/v1/conversations/{id}/snapshot | beforeMessageSeq?、limit | {conversation,messages,nextBeforeSeq,activeExecutions,activeTask,interactions,deliverables,failures,cursor}；一致前缀，历史分页无省略或重跑 |
| GET /api/v1/conversations/{id}/events | after/Last-Event-ID | 会话级 SSE；generation、seq、messageId/revision、scopeId?，不含内部 prompt/推理 |
| PATCH /api/v1/conversations/{id}/title | expectedTitleVersion/title | 手动标题、CAS；409 刷新，不覆盖新值 |
| POST /api/v1/executions/{id}/cancel | expectedVersion、幂等键 | 202 持久停止回答；任务取消沿用独立 Task 控制入口，界面明确区分 |
| POST /api/v1/executions/{id}/retry | expectedVersion、幂等键 | 仅服务端 allowedActions 提供时可用，返回关联的后继执行/原恢复收据，不修改终态历史 |

共享运行锁序固定 Conversation → 已存在的 Task/ResponseExecution 所有者 → 用户预算汇总行 → BudgetAccount → Binding/Command/Event/Operation/Interaction；新对象受理在同一会话锁下创建。原生投影与核对也先锁会话和所有者，避免反向取锁。预算占用采用锁后当前读，不能依赖 MySQL 可重复读的旧快照。单个事务不调用 SDK 或外部模型；审批外部派发继续独立提交 IN_FLIGHT，调用方不得持有该操作行锁。

1. 校验消息 1–20000 字符、≤20 个 READY 附件与归属；计算规范化 payloadHash。
2. 查幂等记录，同键异输入 409；首发原子创建会话，避免失败产生孤立空会话。
3. 锁会话并取得单调 seq，创建 user Message、RESPONSE Scope、BudgetAccount、QUEUED Execution 和 SUBMIT Command；追加 message.accepted 与 execution.queued。
4. 提交后返回 202。Bridge 继续用稳定 commandId submit，收据窗口重投同 key；原生成功后才发布 assistant Message 与完成事件。
5. 页面重连仅查快照和事件。删除/取消竞争重验 generation/guard；未知写入进入核对，不因重试换 key 再发。

ResponseExecution 状态：QUEUED → RUNNING → WAITING_INPUT/WAITING_APPROVAL/RECOVERING/RECONCILING → RUNNING → SUCCEEDED/PARTIAL/FAILED；取消经 CANCELLING → CANCELLED。QUEUED 不是 RUNNING；等待用户不是失败；浏览器连接中断是本地连接状态，不修改执行。持久终态不可原位复活，受控重试建立关联后继；中断且原 Turn 可恢复才用 RESUME。

### 3. 会话消息与任务协调并行

选择每条回复固定独立 SDK Session/Binding，从已提交消息历史构建受权上下文；任务协调使用独立 Task Binding，继续 D3 signalVersion 机制。理由：同一 SDK Session 同时挂起任务与回复会产生 pending/单写者竞争；把聊天 Master 和任务协调混用一个长 Turn 也会重新锁住输入。每个 Binding 仍单 writer、同 key 恢复。

会话受理顺序确定；回复执行按 messageSeq 串行领取，前一消息进入等待用户时允许后续消息处理，但待交互只由匹配 Interaction 响应解决，普通发言不擅自等同审批。上下文固定已提交前缀及最新任务事实，不读取未来消息；材料超限使用有预算且可审计的压缩，缺能力时明确边界，不静默丢历史。

Master 工具 `create_goal_task(logicalKey, title, objective, sourceMessageId)`：sourceMessageId 必须等于本次受权消息，平台从执行上下文取 user/conversation/account；事务创建任务 Scope、Task Binding、初始任务命令及 task.created，不在原回复 Binding 上偷偷更换身份。创建任务仅授予推进目标的能力，不自动授予外部写权限。创建完成后回复用户已开始处理的真实状态，后续交付由任务 Master 发布到同一会话。

Master 工具 `submit_task_directive(logicalKey, taskId, expectedVersion, kind, content)`：kind 为补充或目标替换建议；平台先校验该对话 activeTask，持久 ACCEPTED，再由 Task Master 在安全协调点消费，应用后 APPLIED 与目标版本事件同事务。查询进度只读，不必发修订。原 Task Master 是唯一计划发布者，聊天 Master 不直接改 Plan。取消依然走确定性受控端口。目标变化影响不可逆操作或意思不明时追问；不采用简单关键词匹配。

### 4. 统一预算、授权与故障恢复

选择按执行作用域取账本，任务继承原 BudgetAccount 引用而不是复制余额；补充消息自己的回复花自己的账户，原任务持续花原账户。理由：来源回复结束后任务仍可能并行花费；直接挪移已预留金额或按 taskId 新开额度会双算/漏算。

所有预留同事务校验 account 和 user 总并发/总占用上限，冻结定价版本；UNKNOWN 继续占用。模型、工具、标题生成、摘要、纠正和重试均纳入，不因创建任务或切供应商重新发额度。旧 Task account 回填保留已有 Reservation 结果，迁移验证总额守恒。

| category | actor/action | 自动处理边界 |
| --- | --- | --- |
| INPUT_REQUIRED | USER/PROVIDE_INPUT | 展示受限表单，不替用户补造重要事实 |
| AUTH_REQUIRED | USER/CONNECT 或 APPROVE | 只提供本人可处理的连接或审批；平台权限不足由 SYSTEM 处理 |
| ENVIRONMENT | SYSTEM/FALLBACK 或 CONTACT_SUPPORT | 能力缺失按已允许能力降级，不装依赖、不让用户修服务器 |
| SERVICE_TRANSIENT | SYSTEM/RETRY | 默认最多 2 次，指数退避，计入原预算；持久 nextAttemptAt 与次数 |
| LOGIC_INVALID | SYSTEM/CORRECT | 计划最多 2 次；完成候选最多 1 次；之后说明失败/缺口 |
| EXTERNAL_UNKNOWN | OPERATOR/RECONCILE | 优先自动查询已发生结果，不能盲目重发；有权限才显示人工核对 |
| INTERNAL | SYSTEM/STOP | 保留内容和诊断号，安全提示，不无依据重试 |

异常分类依据结构化错误与执行事实，不能用异常 message 字符串猜测权限。自动降级只改变已授权的实现方式，不能扩大用户目标、外部写范围或声称替代能力等价。向用户展示 safeMessage/actor/allowedActions，保留内部 code/stack 供服务端诊断；失败范围区分本条回复与独立任务。

### 5. 会话事件和统一呈现

选择会话级事件流，事务内分配递增会话 eventSeq；原 task 流可保留诊断用途，不能混用 cursor。理由：多条消息、普通回复、任务交付与交互无法由一个 activeTask snapshot 准确表示。替代方案页面轮询多个 Task 拼历史会漏掉普通消息且易串流。

事件至少包含 message.accepted、execution.status_changed、message.updated/completed、task.created/status_changed、directive.status_changed、interaction.requested/resolved、artifact.ready、failure.updated、conversation.title_updated。采用 {conversationId,generation,sequence,type,occurredAt,payload}。answer 依 messageId/revision 替换；任务多次内部协调不新增用户消息。快照 repeatable-read 返回一致 cursor；切会话先关闭旧流，再按新 generation 读取，晚到请求按 conversationId/request 序号丢弃。

聊天流按用户消息/助手消息真实顺序排列，任务状态使用紧凑工作条关联原消息，任务结果作为后续助手交付；追问、审批、产物和必要核对置于相关消息卡片中。普通用户无详情跳转、Worker 节点、内部日志或 requestHash 文案；必要的真实写入影响保留。复用 InteractionCard、下载与 OperationPanel 的后端鉴权和核对逻辑，不能因移入聊天而削弱访问校验。

### 6. 布局、标题与默认页

选择用户截图的交互骨架，保留项目品牌：约 240px 浅色侧栏、时间分组的会话标题、底部账户入口；主区白底，消息与输入宽度约 760px；空态问候与输入居中，首发后内容向上、输入贴底；窄屏侧栏抽屉。发送按钮圆形，附件图标为有 accessible name 的按钮，原生 input 隐藏；textarea 自动增长并处理中文 IME、Enter/Shift+Enter。未实现的思考/搜索按钮不出现。附件卡片包含名称/状态/移除，失败可重试，进行中禁无效提交；切会话丢弃迟到上传结果。

首屏无 conversationId 即本地 draft，不依赖创建成功才给输入框。每个草稿固定首发 key，返回收据后 replace 路由；失败保留文本/附件/key。历史按游标分页而非默认仅取一页；切换加载期间不展示上一会话内容。

标题先以首条有效用户消息去控制字符、折叠空白、限制 32 字生成纯文本 fallback，立即持久；可选模型标题作为低优先命令，预算不足/失败保留 fallback，不阻塞回复。用户手动 PATCH 后 titleSource=MANUAL，自动标题必须 CAS 初始 titleVersion/AUTO_PENDING 才能写入。历史默认标题按首条消息或旧任务输入生成 fallback；无内容保留“新对话”。侧栏 recency 和日期字段来自后端 lastActivityAt，不解析 UUID 或客户端猜时间。

### 7. 后续阶段影响

| 计划 | 冲突 | 调整 |
| --- | --- | --- |
| D3 | 原验收按每消息 Task 组织 | 完成证据保留；本变更替换入口与普通对话语义，重跑 D3 委派/恢复回归 |
| D4 | 仅 GoalTask/Job 固定 Skill；记忆屏障只阻塞任务 | Response Scope 同样固定版本/授权代际，来源引用 Message；普通回复和任务双路径验收 |
| D5 | 父 Task 预算；独立上传/下载和诊断页 | 接入 BudgetAccount 与 Scope，交付卡/诊断在对话内；沙箱隔离范围不变 |
| D6 | 只验 Task SSE、备份缺少消息事实 | 增加消息/执行/标题/指令/账户及会话水位；并发聊天+后台工作、窄屏/IME/断线验收 |

实施顺序：本变更主链路 → D4 → D5 → D6。可以先交付视觉切片，但未完成消息/任务分离前不得宣称这轮体验改造已验收。

## Risks / Trade-offs

- [涉及运行主键与归属迁移] → 分两步 expand/backfill 与入口切换；以迁移回归、总额守恒、原 submit key 与用户隔离验证为门槛。
- [新旧执行同时处理导致双 writer/双派发] → 升级时停领取、等在途安全点；不能将同一 Task 同时交两种协调链路。
- [多会话回复带来预算与并发放大] → 用户级汇总锁与配额，account 预留同事务；标题低优先级。
- [任务修订与在途写入冲突] → 指令有受理/应用状态，沿用计划替换屏障与 UNKNOWN 核对，不承诺即时撤回已发生写入。
- [消息历史扩大上下文] → 固定前缀、明确压缩预算及证据；Worker 不继承整段会话，记忆仍显式治理。
- [本机 JDK 与基线不一致] → 编译目标 21 与实际运行版本分别记录，D6 补真实 Java 21 运行证据。

## Migration Plan

1. 先补契约与新增迁移、回填旧 Task 的 scope/account 与用户/助手消息；用稳定唯一键幂等回填，不清库、不改 V1–V6。
2. 扩展共享运行层支持 Scope，并用旧任务链路验证预算、授权、交互、取消、journal 恢复不退化；运行中的任务保持原 Session/Command 身份。
3. 停止新领取并确认在途安全点后启用消息入口和新会话流；新前端一次切换，旧 createTask 不再作为用户聊天入口。
4. 将原详情组件的用户动作迁入消息流；旧地址定向关联会话并定位消息，诊断 API 可继续内部保留，不维护两套产品交互。
5. 同步主 specs、OpenAPI、ui-manifest、D4–D6；执行 MySQL 迁移、定向/全量测试与真实页面验收，记录固定模型/SDK/JDK与已知限制。
6. 失败时停新派发并保留事实；仅在未接受任何新模式写入时可退旧应用。已产生新消息/执行后采用前向修复，禁止回退删除表或让旧版本忽略新操作。
