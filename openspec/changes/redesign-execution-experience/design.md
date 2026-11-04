# Design

## Context

动机、源码证据 R01-R36 及实验 P01-P06 见 proposal.md。四份 specs 定义外部行为；本文选择实现路线，不将未执行的 SDK 实验视为能力已验证。此变更跨回复、任务、调度、数据库、前端和追踪，必须有详细设计。

当前最关键约束：平台事务与 SDK 日志不原子；Scope 是共享授权/预算边界；Plan/WorkerJob 是现有真实计划；正式交付已有代际和所有者屏障。不能为了缩短链路删除这些事实。

## Goals / Non-Goals

**Goals:**

- 简单回复不需要完成声明工具和回执解释回合；复杂目标严格验收。
- 每条消息可查看真实步骤、并行活动、等待原因、产物及完整诊断关联。
- 提交后及时推进，崩溃后不丢事实、不重复外部副作用、不产生重复终稿。
- 状态权威唯一、协议类型明确，历史与旧计划可解释。

**Non-Goals:**

- 不引入另一套 DAG 调度器，不将所有对象合成万能 Execution 表。
- 不升级固定 SDK 或引入框架服务端；能力契约不通过时先修订设计，禁止私自依赖内部反射/私有接口。
- 不在本次规划执行生产发布、真实模型调用或真实库迁移；不建设测评平台。

## Decisions

### 1. 保留领域对象，统一身份引用与进度查询

采用 OwnerRef `{type: RESPONSE|TASK|JOB, id}`；Job 关联 taskId、planVersion、scopeId。保留现有 reply/task/job ID，Scope 继续负责账户、会话代际及技能/记忆快照。沙箱 ID 在公共协议称 sandboxExecutionId，避免与回复 executionId 混淆。

不物理合并 Scope/Reply：Master 与 Worker 共用 Scope，但各自有执行资格和结果。统一表会把授权、调度及结果混在一起。公共进度组装器按 sourceMessageId 连接回复、转交目标、Job 与活动，聊天及详情共享 DTO；原始业务状态仍由原服务裁决。

| 事实 | 唯一写入边界 | 查询侧用途 |
| --- | --- | --- |
| 回复状态/后继关系 | ResponseExecutionService、ResponseRetryService | 消息下面的回答状态与历史 |
| 目标/计划/依赖 | GoalTaskCreationService、PlanService、任务控制和汇合政策 | 目标进度、当前计划和旧版历史 |
| Job 结果/资格 | WorkerResultService、WorkerDispatchService | 节点步骤和尝试 |
| 授权/账户 | Scope、ExecutionAuthorizationService、BudgetService | 访问与消耗，不充当进度 |
| 外部效果 | Operation 服务及账本 | 核对和停止结果，活动仅引用 |
| 输出/交付 | 新 OutputService、既有 FinalDeliveryService | 草稿与唯一正式答案 |
| 活动 | 新 ActivityProjectionService | 真实步骤时间线，不反向裁决操作成功 |

### 2. 通过已提交事件驱动业务，采用持久收件确认

选择官方 SessionExportSink + 平台持久事件收件记录。sink.name 固定为 `squad-execution-v2`，不包含实例、用户或每次启动 UUID；SDK 自身按 Session 隔离其导出序号。

sink 绑定服务端 userId/bindingId/sessionId 与映射版本，不能信任模型自报归属。收到事件后用短事务插入唯一收件记录，确认落库后返回；记录主要保存原生日志定位、类型、摘要及必要的受控业务字段，不整包复制 request/prepared、模型消息和工具参数。未知必需类型先保留定位并阻塞该绑定消费，不能确认为处理完成。不可安全映射的载荷仍通过 SDK 公开接口读取。

选择持久收件而非在 SDK 导出线程直接执行完整业务事务，避免父任务锁、会话锁和慢消费占用导出工作。相比只发内存通知，多一次短写入换取明确确认与重启可调度；不是引入 Kafka 等新基础设施。

```text
SDK committed journal
        --> official export sink
        --> durable inbox commit --> ACK native export
                    |
                    v
             per-binding consumer
                    |
        business + activity + public events + cursor commit
                    |
                    v
               notify SSE
```

消费同 binding 串行、不同 binding 可并行，按 nativeSeq 连续处理。同序号不同 eventId/digest 是数据冲突，不覆盖；缺号从已提交日志补齐。单个绑定毒事件不停止其他绑定。消费失败不推进业务游标，按分类限速重试并显示消费受阻；不可恢复必需事件进入需核对状态。

原生 head、收件最大连续序号、业务消费序号分别记录。补偿扫描比较这些事实，不按业务终态过滤；已结束运行仍补齐尾部。即使 SDK 导出确认丢失，重复插入和业务 producerKey 均幂等。收件记录清理必须晚于业务消费、恢复窗口及诊断保留；日志引用仍待消费时不得清理 SDK 数据。

P03 是接入准入条件；失败则暂停该适配任务并修订设计，不悄悄切到内存通知。

### 3. 将读取、启动与上下文装配分开

2026-11-04存储核对修订：固定SDK的CompositeFilesystem.sessionStorage路由`agents/`时落到默认本地后端；仅配置JdbcDistributedStore和RemoteFilesystemSpec不能证明journal已入数据库。v2必须在Master/Worker构建时显式注入同一RuntimeJournalStore（官方WorkspaceSessionLogStore + RemoteFilesystem + JDBC BaseStore），使用固定平台/版本/用户namespace，再由SessionKey区分Agent与会话。禁止把新后端直接套到旧Binding而假定历史自动存在；旧本地journal保持原路径，Binding持久记录协议与日志后端，切换、排空或受控迁移由10.1/10.2负责，不按进程环境猜测。此约束属于3.2接入前置，当前旧入口未切换。

新 RuntimeSessionProvider 统一创建 Master/Worker 首发、resume、respond、恢复用上下文，注入 sink、OwnerRef、attempt、追踪关联与固定权限引用。业务身份只来自数据库。

readCommittedEvents/inspectReceipt 不调用 session.start、不领取 Job、不续租 guard；恢复协调器明确调用 startPending，并受当前计划、取消、授权和租约约束。WorkerFactory 拆出能力构建/运行资格与纯会话读取，避免页面/补偿查询间接启动推理。

原因：当前 readEvents 有恢复副作用；统一 Provider 优于在每个入口复制 sink/trace 注入。Java 回调对象只在进程中装配，不写命令 JSON。

### 4. 普通回复自然结束，目标工具终结

普通回复 v2 的 scoped tool surface 移除 propose_completion。保留实际需要的资料、记忆、交互、外部操作和创建目标工具；不增加专门意图分类模型。自然完成由 turn/output 与关联运行事实决定。

| 条件 | 处置 |
| --- | --- |
| 当前尝试、可信 turn/output、run completed、最后模型明确正常 stop、正文有效、无开放效果、仍有发布权 | 发布 SUCCEEDED |
| length 等明确截断且有可公开文本 | 发布 PARTIAL，明确中断原因，不自动再调用模型 |
| 停止原因缺失/无法映射、有文本 | 发布带 OUTPUT_COMPLETENESS_UNKNOWN 的 PARTIAL，不声称完整成功 |
| 只有工具请求/思考块或空正文 | 不发布空成功；按明确故障记录失败，禁止恢复强制候选纠正 |
| suspended/开放交互 | 等待交互，不发布终稿 |
| 外部 UNKNOWN/IN_FLIGHT | 核对/等待，不宣布成功或安全取消 |
| 取消已提交 | 保留中断输出，走取消收口 |
| 交付权已转给目标 | 回复结束可无正式答案，平台任务受理说明独立存在 |

技术 completed 不保证业务成功；GenerateReason 的默认 MODEL_STOP 不作为充分条件。最后模型与 turn/output 必须通过 modelCall/Run/Turn 关联，不能按“最新 assistant 文本”猜测。P01 未证实映射前不能启用自然发布。

2026-11-04实验修订：流式stop后附usage-only尾块时，实际model/end.finishReason为null，尽管已提交model/chunk中存在stop（见validation.md）。因此适配层必须按modelCallId对已提交chunk、model/end和turn/output做结束事实规范化：保留明确观察到的终结原因及其原生事件来源，分别提取可信usage；缺失或冲突为UNKNOWN，不能简单取最后一个响应。content_filter等受限结果不发布为完整成功，也不作为普通未知原因直接放行。规范化实现及用量/停止原因冲突测试通过前，P04仍失败，依赖发布不可启用。

目标保留 propose_completion、Worker 保留 report_job_result 的结构化验收。终结工具仅在候选确实保存且结果为成功时直接返回可对外答案或安全结构化结果，不返回“已保存回执”作为正文。混合工具批次不依赖 returnDirect 保证终结：使用现有模型 schema 表达终结调用与其他副作用不能同批完成，服务端仍核对全部待处理效果；不满足时不发布。P02 必须覆盖 SDK 实际批次和新输入竞争。

受理目标后由平台同事务公开任务 ACK，最终交付权仍转给目标；ACK 不占用最终交付位。

### 5. 任务汇合、验收与停止规则集中定义

新 PlanJoinPolicyService（纯政策计算，不访问网络）输入当前 Plan 定义、Job 事实、开放效果和验收结论；由应用服务在持锁事务内应用。代替 MasterSignalService 和 CompletionProposalService 各自的字符串判断。

选择“所有已接受分支先明确收口，再最终汇总”，不在必要节点完成后遗弃可选在途工作。

| 汇合策略 | 汇合条件 | 成功与部分完成 |
| --- | --- | --- |
| ALL_REQUIRED | 所有节点已成功、不可恢复失败、依赖失败跳过、取消或替代；无未知效果 | 必要节点成功且必要验收满足才能成功；可选失败仅在影响验收时导致 PARTIAL |
| BEST_EFFORT | 同上，收集所有可用结果；允许失败分支进入汇总 | 验收仍决定成功；不足但有可交付结果为 PARTIAL，没有可用结果且不可恢复为 FAILED |

至少一个 required 节点；optional 是工作必要性，不是依赖可忽略标记。所有 dependsOn 默认硬依赖，只接受 SUCCEEDED；上游不可恢复失败或被跳过，则递归将当前版未执行下游标为 SKIPPED，reason=DEPENDENCY_FAILED。恢复受限先明确协调决策（恢复/重规划/放弃），不能直接跳过仍可恢复节点。

acceptanceCriteria 从字符串扩为 `{criterionId,text,required,verificationType}`；verificationType=DETERMINISTIC|MODEL_REVIEW|USER_REVIEW。结论 PASS|FAIL|UNKNOWN，记录 verifier、evidenceRefs、planVersion、goalRevision。MODEL_REVIEW 明确为模型判断，不伪装程序验证；缺少必要证据为 UNKNOWN。需要用户判断则创建具体交互，不凭模型输出通过。旧字符串迁移为 required MODEL_REVIEW，标为原始未结构化条件，历史结果不倒推验证通过。

修改目标继续区分 ACCEPTED/APPLIED/REJECTED；应用和旧计划屏障、新修订同事务。用户查询进度不创建 directive。

取消先阻止新派发并向所有活跃 Binding 发稳定停止命令，再检查运行静止、Job 收口及 Operation/Sandbox 未知效果。未知则 RECONCILING，仍可核对；静止且效果明确后 CANCELLED。已成功发布先赢则取消返回冲突；取消先提交则完成不能发布。取消不撤销外部成功事实。

审批接口改为短事务保存决定及审批处理命令，不在 HTTP 答复事务等待外部 I/O；执行器独立 CAS 后调用连接器，再持久结果及 RESPOND。用户决定 RECEIVED/APPLIED 分开；固定 +5s 替换为原请求已挂起、无活跃冲突、答复齐备的投递条件，不满足则明确延后和原因。外部成功后崩溃重投复用 operationId/businessIntent，禁止重复写。

### 6. 状态、活动分类与页面聚合

保留回复现有状态；GoalTask 增加 RECOVERING 表达自动恢复，阶段单独派生，不增加 PLANNING_THINKING 等组合状态。Job 定义 BLOCKED、READY、RUNNING、WAITING_INPUT、WAITING_APPROVAL、RECOVERING、RECOVERY_BLOCKED、RECONCILING、CANCELLING、SUCCEEDED、PARTIAL、FAILED、CANCELLED、SKIPPED、SUPERSEDED。

ActivityStatus=QUEUED|RUNNING|WAITING|SUCCEEDED|FAILED|CANCELLED|UNKNOWN。重试不是把旧状态改回 RUNNING，而是同 activity 下 attemptNo+1，新尝试记录；活动当前摘要 version 推进。WaitReason=CAPACITY|DEPENDENCY|INPUT|APPROVAL|EXTERNAL_RESULT|RESUME_READY|RETRY_BACKOFF|EVENT_RECOVERY。

| 分组 | 活动类型 | 当前落点 |
| --- | --- | --- |
| PREPARATION | CONTEXT_PREPARE、FILE_READ、SKILL_LOAD、DATA_QUERY | ResponseContextService、read_attachment、prepare_context、load_skill_through_path、mock_lookup |
| PLANNING | TASK_CREATE、CAPABILITY_LOOKUP、PLAN_PUBLISH、WORKER_DISPATCH、TASK_CHANGE | create_goal_task、list_worker_capabilities、publish_plan、派发/指令服务 |
| EXECUTION | MODEL_CALL、SCRIPT_EXECUTE、EXTERNAL_ACTION、MEMORY_WRITE | 模型中间件、sandbox_python、mock_ticket_create、write_explicit_memory |
| VERIFICATION | RESULT_VERIFY、OPERATION_RECONCILE | 验收政策、操作核对 |
| DELIVERY | RESULT_AGGREGATE、RESULT_SUBMIT、ARTIFACT_COMMIT、ANSWER_PUBLISH | MASTER_SIGNAL 接手、report_job_result、propose_completion、commit_artifact、最终发布 |

MODEL_CALL 的显示阶段取可信命令 purpose：首次回复理解、资料后处理、MASTER_SIGNAL 汇总；没有事实则“正在思考”。tool/requested 是 QUEUED，tool/dispatch 才 RUNNING；工具错误结果必须 FAILED，即使 Java 调用正常返回。业务拒绝/审批等待不记成运行错误。

工具元数据注册独立分类，不改每个工具的业务实现来拼文案。记录 resourceId，再经归属校验获得安全显示名；允许字段模板生成中文，不记录任意模型自报进度文字。

默认展示当前摘要，展开显示当前计划 Job，再展开 Activity/Attempt。必要用户交互置顶，但保留并行运行节点；统计当前 planVersion 的成功/失败/运行/等待/跳过，复用独立徽标。终态历史分页，不在首页拉取全部活动。sourceMessageId 将目标放回原请求下，ACK 与最终答案用途不同。

### 7. 数据结构变更清单与约束

本节是建模契约，DDL 在 apply 时生成到最终 baseline 与部署迁移文件，必须带中文注释及统一审计字段；本次不修改 schema。时间统一 UTC datetime(3)，业务 ID 与自增存储键分开。

| 表/改动 | 关键字段与约束 | 所有权/事务 |
| --- | --- | --- |
| 新 sq_runtime_event_inbox | user_id、binding_id、session_id、native_seq、native_event_id、native_type、payload_digest、mapping_version、必要脱敏载荷/引用、status、attempts、next_attempt_at、error_code；唯一(user_id,binding_id,native_seq) 与 (user_id,binding_id,native_event_id)，相同键异摘要报冲突；索引(status,next_attempt_at,id) | sink 短事务确认；不保存完整 Prompt |
| 新 sq_execution_attempt | attempt_id、owner_type/id、scope_id、binding_id、command_id、turn_id、run_id、attempt_no、protocol_version、traceparent/tracestate、root_trace_id、status、started_at/ended_at；唯一(user_id,binding_id,run_id)，运行未分配时按command+pending身份再绑定 | 运行事实及投递服务；业务恢复与新回答分开 |
| 新 sq_execution_activity | activity_id、owner_type/id、source_message_id、task_id/job_id、plan_version、parent_activity_id、activity_type/group、status、wait_reason、version、source_key、resource_id、safe_summary；唯一(user_id,source_key)，索引(owner_type,owner_id,id) | 投影短事务写；权威业务结果仅引用 |
| 新 sq_activity_attempt | activity_id、attempt_no、execution_attempt_id、status、start/end、error_code、tool_call_id/model_call_id、operation_id/sandbox_execution_id；唯一(user_id,activity_id,attempt_no) | 运行映射及投影；缺少起止值为 NULL |
| 新 sq_output | output_id、owner_type/id、source_message_id、execution_attempt_id、purpose、status、revision、committed_text、committed_offset、sha256、message_id；purpose=ACK|COMMENTARY|ANSWER，status=DRAFT|COMPLETED|INTERRUPTED|PUBLISHED|SUPERSEDED | 已提交文本可恢复；正式消息与交付位同事务 |
| 新 sq_delivery_artifact | output_id、artifact_id、role=INTERMEDIATE|FINAL、job_id、plan_version；唯一(user_id,output_id,artifact_id) | 发布事务验证文件归属及版本 |
| 新 sq_acceptance_result | task_id、goal_revision、plan_version、criterion_id、verification_type、result、verifier_ref、evidence_refs、reason_code；唯一(user_id,task_id,goal_revision,plan_version,criterion_id) | 验收更新与完成锁协调 |
| 新 sq_interaction_decision | decision_id、interaction_id、expected_version、request_hash、decision_hash、safe_payload、status、command_id；唯一(user_id,interaction_id)，同幂等键异内容拒绝 | 决定受理与处理命令原子提交 |
| 修改回复/目标/命令 | protocol_version；命令增加追踪上下文、ownerRef、等待原因；回复v2不再写候选/纠正字段 | v1保留至耗尽，v2单写 |
| 修改 runtime_binding | received_cursor、native_event_cursor、last_known_head、terminal_applied_seq、mapping_version；next_projection_at 用于补偿；索引补偿到期和差异状态 | 收件与业务确认分离，不用业务终态排除尾部 |
| 修改 sq_message、sq_artifact | 消息 output_id/purpose；产物来源 scope/binding/job/plan/output，用途 | 上传附件与生成产物分开 |

既有 sq_command 是平台 outbox，不再复制一张命令 outbox。收件确认和业务消费独立的代价是两次持久写；收益是 SDK 导出不被父任务锁拖住。活动规模通过只记录边界与合并进度控制，不逐 token 建 Activity。

数据库有限值均 VARCHAR+枚举映射，不引入 MySQL ENUM。跨模块枚举在 common，内部状态留所属模块；SDK 原始字符串仅在适配层解析。生成 TypeScript/OpenAPI 或做一致性校验，禁止继续 status:string。

### 8. 事务、锁序与幂等

遵守现有会话→所有者→交付位顺序；新增场景明确如下：

| 操作 | 锁/事务 | 崩溃恢复 |
| --- | --- | --- |
| 消息受理 | 会话、源消息/Reply、Scope与账户、Command、ACK/公开事件一次提交 | 同幂等键返回原身份，提交后通知可丢 |
| 原生收件 | 仅 inbox 唯一插入和连续 received_cursor | 已落库未 ACK 可重复；序号缺口不推进连续游标 |
| 业务事件应用 | conversation→owner(Task→Plan→Job适用时)→binding cursor→activity/output→公开序号；一个连续批次全部提交 | 失败整批回滚，重投无副作用 |
| 交互决定 | 归属与版本校验、决定 CAS、处理命令与公开事件一次提交 | 执行外部 I/O 不持有该事务 |
| 外部派发 | 固定 operationId，短事务取得资格→无事务外部I/O→短事务CAS结果 | 结果未知保留账本，重投先查权威结果 |
| 正式发布 | conversation→owner→final_delivery→output/message→artifact引用→公开事件 | 发布资格、正文、产物引用与事件原子，不因通知失败重做模型 |

高频模型增量先按 binding/输出有界聚合，已提交原生事件按批次推进，不逐 token 锁会话。每次处理有事件数/字节数/耗时预算，到达边界提交后继续。统一锁序用于实现审查及死锁测试，不能依赖注释使不同路径自动安全。

### 9. 及时唤醒与低延迟输出

2026-11-04按用户方向纳入Redis：允许用于调度协调、限流、幂等与事件流，不限于缓存。当前优先以Streams承载跨实例的命令/绑定唤醒引用，以Pub/Sub广播会话有更新的即时通知；不将原始Prompt、工具参数或模型内部思考送入通知。Streams消息包含环境、userId、bindingId、commandId或已提交事件水位，消费仍经过已有授权与业务幂等检查；确认与失败转移、积压及保留窗口在3.5实现时验证。MySQL提交后发布Redis存在跨存储窗口，命令/收件记录保留为补偿依据，扫描补发可重复，不靠双写同时成功假装原子。Pub/Sub通知可丢，断线以已提交水位补读。用户将自行安装Redis，本轮未安装、连接或声称该路径已通过联调；缓存、执行容量和其他权威状态是否放Redis按各自恢复约束决定，不强制同一种存储。

平台 Command 事务提交后通知有界 dispatcher，保留公平领取、独立控制容量和稳定提交键；扫描仅补偿遗漏。SDK 内部100ms调度仍保留，不能绕开其持久 inbox。

模型公开文本通过生命周期中间件/公开回调生成可丢的 preview；只对回复/指定最终输出通道打开，Worker 内部文本、reasoning及工具参数全部不推送。任务协调内部回合不开放预览。P04 需证明区块身份及渠道可判定，否则保持活动即时、正文仅用可靠已提交批次，禁止降级为转发原始块。

preview 使用 `event: output.preview`，无 SSE id，不占公开 sequence。包含 streamId、outputId、attemptId、blockId、offset、text、preview=true。offset 采用 UTF-16 code unit（Java/JS length 一致），禁止拆开代理对；hash 对完整 UTF-8 文本计算。临时流失败可以丢失，重连后由 committed_text 重置；不得据 preview 宣布完成。

已提交的 output.updated/正式完成有公开序号；预览与权威内容不一致时按 revision 替换，不能只追加。最终 turn/output 校验正文，系统回执不进入用户答案。

### 10. HTTP、快照和公开事件契约

保留现有路由，业务 JSON 继续 ApiResult。以下定义的是 v2数据，不在规划阶段修改 api/openapi.json；实现必须同步。

| 接口 | 请求 | 响应 data关键字段 |
| --- | --- | --- |
| POST /api/v1/conversations[/id]/messages | 原text/artifactIds、Idempotency-Key | conversationId/messageId/executionId/commandId/status/cursor/protocolVersion |
| GET /api/v1/conversations/{id}/snapshot | limit、beforeMessageSeq | 原会话消息、executions/taskStates/interactions等，加protocolVersion、progressGroups、outputs、artifactRefs、cursor |
| GET /api/v1/conversations/{id}/events | after或Last-Event-ID | 公开v2事件与独立preview；持久事件才有id |
| GET /api/v1/execution-progress | ownerType、ownerId、cursor、limit | activities、attempt摘要、nextCursor；校验归属，limit默认50最大100 |
| GET /api/v1/outputs/{id} | 无 | purpose/status/revision/committedText/offset/hash/artifactRefs |
| 既有交互答复接口 | expectedVersion、幂等键、决定/内容摘要 | decisionId/status=RECEIVED/commandId；前端不解释为执行成功 |
| 既有取消/重试接口 | expectedVersion、幂等键 | 原控制回执，加明确ownerRef；后续状态等事件确认 |

ProgressGroup={sourceMessageId,ownerRef,phase,summary,counts,allowedActions,planVersion,version,activeActivities,recentActivities,nextCursor}；counts分别提供succeeded/failed/running/waiting/skipped/reused/total，不返回伪百分比。快照只含当前活动和有限最近历史，详情分页。

持久事件信封：schemaVersion=2、eventId、conversationId、generation、sequence、occurredAt、type、required、payload。事件族 execution.updated、plan.published/plan.superseded、job.updated、activity.updated、interaction.updated、directive.updated、output.updated、delivery.published、artifact.ready。每类 payload 为独立 DTO 与 TS 判别联合，禁止裸 Map 承担业务契约。

sequence 先检连续，实体 version/revision再防回退。未知可选事件推进sequence，必需事件不兼容则断流重取受支持快照。过期游标使用既有 EXPIRED，前端重建快照。打开连接期间禁止 snapshot旧响应覆盖后来事件：以快照cursor作为基线缓冲再重放，或丢弃早于已应用水位的同代际快照。翻页只合并历史，不修改实时水位。

SSE通知改为每会话共享 hub+提交后唤醒读取，避免每连接500ms查库；单实例进程通知足够，多实例由平台事件补偿读取保证正确性，短周期共享尾读按活跃会话而非按连接。队列字节/条数有界，慢连接发 resync_required 后关闭；重连按快照恢复。每次发送批次重验用户/会话权限，并对禁用/删除事件主动关闭订阅。

### 11. 追踪与日志

入口 W3C 上下文持久到 Command/Attempt；普通自动调用延续 trace，模型/工具按实际订阅开始和完成创建子span。HTTP span正常结束；不为等待用户把一个span保持数小时。人工答复新trace以span link和root_trace_id关联原执行。

ObservationSupport分同步和响应式API；响应式在defer/订阅创建span，complete/error/cancel关闭，ThreadLocal不能越过线程复用泄漏。业务ID为高基数追踪属性或日志字段，低基数指标仅类型、阶段、结果和错误分类。日志用Activity/Attempt标识，与页面进度可互查。

INFO保留入口完成、命令接手、活动起止摘要、重试决定、发布结果；等待/取消不是ERROR。无事件扫描和续租不刷INFO。异常在负责处理的边界记录必要堆栈，禁止各层重复。预算预留/结算可保留结构化事实但不成为主时间线噪声；SDK工具注册等诊断降为DEBUG。Collector故障不阻塞业务，采样不决定页面事实。

与 add-agent-observability 的关系：本变更拥有活动、尝试和生命周期上下文接入；原变更保留OTel基础与导出故障隔离。apply前逐条对齐任务，不能同时由两套组件建立重复根span或重复消费；原变更勾选项须按当前端到端证据复核。

### 12. 验证准入和实现顺序

P01/P02/P03先于v2运行接入，P04先于预览上线；P05先于控制/汇合切换，P06先于去掉旧前端刷新路径。实验具体输入和断言见proposal，结果保存为独立验证记录，不以文档存在代替通过。

准入失败即停止依赖任务并修订本设计/specs/tasks，不要求实现者临时自选另一架构。缺字段保持UNKNOWN，不能伪造token、耗时和完成原因。现有测试的保留点与V01-V14全部纳入回归；SDK探针用本地桩，事务和恢复验证用隔离MySQL，真实模型和外部系统另按任务授权。

性能输出分解受理、队列、模型首块、工具、原生提交、收件、业务消费、SSE和浏览器应用的p50/p95/p99及查询量。相同桩与并发比较旧/新路径；简单无工具正常问答目标是一轮必要模型调用，活动事件不触发完整快照。首次反馈与正文延迟先建立基线再设量化阈值，不把1s/500ms配置求和当实测。

## Risks / Trade-offs

- [固定BETA接口与推断不符] → P01-P04先验证公开契约，失败修订，不调用私有接口补洞。
- [收件写入与高频事件放大] → 只存必要字段/引用、批量消费与保留策略；记录实际写放大和延迟。
- [预览泄露或错归属] → 公开渠道白名单、绑定尝试校验、无可靠渠道禁用preview，不转发原载荷。
- [会话锁导致并行串行化] → 有界批量、短事务、锁序测试；不逐token写公开事件。
- [取消后外部效果仍未知] → 核对状态与入口保留，发布屏障阻止假成功，不承诺副作用撤销。
- [协议切换造成双运行] → 受理固定版本，仅影子消费事实，不双调用模型工具。
- [模型验收被误当确定性证据] → 标明verifier类型、证据和UNKNOWN，必要人工条件走交互。

## Migration Plan

1. 先完成P01-P04及契约评审，生成baseline、部署迁移与schema parity验证；不自动执行真实库DDL。
2. 增量建表加字段，既有记录protocolVersion=1，新路径先关闭；保留旧消费者处理在途运行。
3. 从同一已提交日志影子构建活动和输出摘要，对比业务结果，不触发模型/工具。历史缺字段为NULL/UNKNOWN；禁用“重放=重新执行”。
4. 新受理固定v2；原v1按旧规则耗尽。显式重新回答是新受理并固定新版本；自动恢复沿用原版本、账户、业务键。
5. 上线v2快照/reducer，兼容读取v1历史。导出、控制、输出按准入依赖开启，去掉固定RESPOND延时前验证恢复条件。
6. 对账终稿、预算、操作账本、事件消费与产物引用；确认无v1在途后删除普通回复强制候选/纠正及旧刷新/正常轮询路径，不永久保留无价值兼容层。
7. 回退关闭新v2受理，兼容消费者继续完成已有v2或安全暂停；不得用旧二进制处理不兼容新数据。已发生外部操作、交付和下载不能靠回退撤销。

## Open Questions

仅部署参数可后续定量：批量大小、活动/收件/输出保留天数、hub容量与补偿周期由P03/P06测量决定，不改变上述确认边界和协议。默认沿用现有产物保留策略，任何延长或缩短需说明影响；SDK行为不确定性已列为准入实验，不作为可跳过的开放问题。
