# Proposal

## Why

当前简单回复经过多层扫描、完成候选及可能的额外模型回合；页面只能感知粗粒度执行状态，日志追踪在命令线程、模型及事件同步之间断开。需要从真实业务流程重整回复、目标任务、工具活动、输出和恢复协议，使用户进度与持久事实一致，并能解释完整执行耗时。

本变更已完成研究记录、能力规格、详细设计与任务编排，并按用户要求开始实验验证。最新证据见validation.md：首批20项SDK实验19通过、1失败；业务实现未开始，P05/P06及MySQL部分未执行。下文“未执行”“候选”等保留为调研当时的历史，当前选择以design.md和specs为准，实验进度以validation.md为准。

## What Changes

- **BREAKING（拟议）**：重新定义普通回复的完成协议、受理说明、过程输出、流式答案和正式交付，评估取消普通回复强制 `propose_completion`；目标任务仍需平台验收与唯一交付。
- **BREAKING（拟议）**：梳理 Scope、回复、目标及 Worker 的身份与数据归属，统一关联引用和进度读模型；保留共享授权范围、计划依赖、预算归属、外部操作账本和发布屏障，不物理合并成万能执行表，也不另建重复调度状态机。
- 用真实计划节点组织用户步骤，用模型、工具、交互、汇合、交付事实形成活动与尝试；区分排队、执行、等待、失败、取消及结果未知，支持并行、重试和计划替换。
- 优先评估 SDK 官方已提交事件导出，正常路径及时推进，持久命令、游标和扫描承担补偿；前端采用一致快照与增量事件，不逐活动刷新完整快照。
- 打通入口、命令、运行、模型、工具和交付的追踪；业务进度不依赖日志或 Trace 采样。同步、响应式与持久异步调用分别管理 Span 生命周期。
- 统一业务枚举、数据库映射、OpenAPI 与前端类型，并用转换规则约束状态推进。
- 本次不实施代码、数据库、发布或真实模型调用；不新增测评平台，不先承诺性能数字。调研记录不代表实验通过。

## Capabilities

### New Capabilities

- `execution-lifecycle`：回复、目标与委派的执行身份、控制、完成及故障恢复。
- `execution-progress`：业务活动、尝试、并行聚合及可恢复的页面进度。
- `conversation-delivery`：受理说明、过程输出、流式答案与唯一正式交付。

### Modified Capabilities

- `api-contract-enums`：将现有账号和 Worker 定义枚举约束扩展到业务执行、活动、等待原因和输出用途等有限值；具体字段待设计确认。

## Impact

- 后端：conversation、runtime、integration/agentscope、agent/tool、presentation、observability；涉及公共类型、Mapper/XML、最终 baseline 及部署结构对比。
- 前端：chatApi、conversations store、MessageThread 及任务详情展示；对应 OpenAPI 传输结构随方案更新。
- 与在途 `add-agent-observability` 存在执行关联和日志协议交集；最终设计需明确归属和验收，不能把其已勾选事项当成端到端交付证据。`verify-mybatis-only-access` 与本次研究独立推进。
- 迁移、历史执行处理和回退需要单独细化；不删除远程 Git 关联，不修改实际库。

## Research Log

### 2026-11-04：项目与固定 SDK 源码核对

证据等级：A 为当前项目源码，B 为本地固定 SDK sources.jar，C 为已有测试源码；本轮未运行新实验。SDK 为 `2.1.0-BETA1`，对应项目固定 commit `5451c73abe47053340683a1cc6d67b290b056648`。

| 编号 | 已确认事实与证据 | 对方案的约束 |
| --- | --- | --- |
| R01 | A：`MessageAcceptanceWriter.accept` 创建消息、回复、Scope、账户、Binding、Command 和最终交付位；`GoalTaskCreationService.create` 创建目标 Scope，复用来源账户并转交最终交付权 | 不能机械删除预算或交付屏障；先梳理身份归并与转交语义 |
| R02 | A：`ResponseExecutionService.accept/validProposal/correctOrFail` 主要根据运行起止及完成候选发布，缺候选时可再发一次纠正命令；`ProposeCompletionTool` 未覆盖 `isReturnDirect` | 普通回复自然输出与任务验收应分开设计，避免把协议纠正变成固定聊天成本 |
| R03 | B：`AgentTool.isReturnDirect` 默认 false；`ReActAgent` 满足批次条件且工具结果为 SUCCESS 时可直接结束，否则继续推理；ERROR/DENIED/INTERRUPTED 不直接作为答案 | 不能仅添加开关；要验证混合工具批次、失败和恢复；当前工具返回的是回执，直接展示会错误 |
| R04 | B：`SessionRecorder.finish` 写入 `turn/output`，非成功结果标记 incomplete，并写回合和运行结束事实 | 普通回复可考虑从可信输出完成；必须绑定 Turn、Run、当前尝试、发布资格与停止原因，不能发布任意 assistant 消息 |
| R05 | A/B：平台派发默认 1s；SDK `AgentSession.start` 内部 100ms 调度，`await` 也轮询；平台事件扫描默认 1s、无事件后延后 5s；每 SSE 连接 500ms 查询，前端事件后再刷新快照 | 不能将配置间隔简单求和当作实测耗时；目标是移除正常路径的多层等待，保留恢复核对 |
| R06 | B：`SessionExportSink.CONTEXT_KEY` 可从 RuntimeContext 提供；`SessionRecorder.flushNow` 提交后异步调用 `SessionLogExporter.drain`，逐事件 accept 成功后推进持久导出序号。A：项目未使用该接口 | 官方导出优先于自定义 SessionLog 提交包装；仍需核实运行上下文传递、提交频率、多导出者和尾部恢复 |
| R07 | A：`NativeProjectionService` 处理原生事件时会锁会话和所有者；`ConversationEventWriter` 使用会话序号；`ConversationQueryService` 用重复读形成快照 | 活动细化不能直接变成逐 token 的会话锁事务；必须设计分区、批量和快照事件边界 |
| R08 | A：`ObservationSupport.observe` 是同步 Supplier 包装，标签统一 lowCardinality；模型预算链路是 Flux 且切 boundedElastic | 需要专用响应式生命周期；业务 ID 不进入低基数指标，不能把创建 Publisher 的耗时当执行耗时 |
| R09 | C：`ModelStreamContractTest` 仅验证本地模型桩文本解析；`ResponseNativeExecutionIntegrationTest` 包含旧代际候选拒绝、UNKNOWN 阻止完成/过早取消等场景 | 已有测试保护有价值，但不足以证明流式交付、取消、重启、唯一终稿已端到端成立 |
| R10 | B：`SessionLogExporter.drain` 按 sink.name 读取持久导出序号，accept 返回后才推进；synchronized 只锁当前 exporter 对象 | sink 若只入内存队列就返回，会使导出确认早于业务落库；多个 Recorder/导出器仍需平台幂等及按绑定串行消费，不能依赖这个对象锁保证跨实例唯一 |
| R11 | B：`SessionRecorder` 每 1s flush 并尝试导出；finish 写结束事实并 flush 后关闭 heartbeat；导出失败只告警。`ReActAgent` 直接返回会把成功工具结果合成为 assistant 正文并产生文本事件 | 运行结束后的末次导出失败不能靠下一次 heartbeat 保证恢复；不能把当前“候选已保存”回执直接变成正式答案；已提交事件也不等于逐 token 低延迟 |
| R12 | A：Master 的 submit/resume/respond 经 sessionFor，而 readEvents/inspectTask 另建 RuntimeContext；WorkerFactory.sessionFor 也创建上下文并查材料、续租 guard | sink/trace 注入必须统一会话获取入口，不能只覆盖首发；查询与启动恢复目前混合，需拆分无副作用读取和显式恢复，避免观测读取影响执行 |

项目源码入口相对仓库根：`server/agent-squad-server/src/main/java/com/agentsquad/squad/`；涉及文件以上表类名定位。SDK 证据来自 `~/.m2/repository/io/agentscope/agentscope-{core,harness}/2.1.0-BETA1/*-sources.jar`，主要为 `AgentSession`、`AgentTool`、`ReActAgent`、`SessionRecorder`、`SessionExportSink`、`SessionLogExporter`。

### 方案取舍记录

- D01（建议，未定稿）：保留 GoalTask、Plan/WorkerJob 的业务含义，不在其上再复制一套通用 Step 调度引擎；活动用于事实与展示。
- D02（建议，未定稿）：普通聊天使用正常回合输出；任务完成保留严格验收。受理说明由平台已提交事实生成，不需要额外模型回合。
- D03（修正）：前一轮建议的 SessionLogStore/commit 包装降为备选；发现官方 SessionExportSink 后，优先验证官方公开机制。
- D04（建议，未定稿）：运行流文本与正式交付分别处理；候选保存、原生回合结束、正式发布是不同事实。
- D05（建议，未定稿）：官方导出 sink 选择“事务消费完成才确认”，或“写入持久平台 inbox 才确认”；不能仅提交内存队列就依赖 SDK 导出序号保证不丢。最终取舍需比较事务耦合、吞吐和独立恢复成本。
- D06（建议，未定稿）：为所有运行路径统一装配上下文；纯查询只读取已提交日志，启动 Session/续租/重建执行资源放到明确的恢复流程。

### 前期公开实现对比索引

以下是同一轮探索早先读取的官方源码，用于比较建模方式，不代表项目固定 SDK 的行为；在线分支会变化，后续实验需固定版本。

| 项目与来源 | 调研结论 |
| --- | --- |
| [OpenHands SDK state.py](https://github.com/OpenHands/software-agent-sdk/blob/main/openhands-sdk/openhands/sdk/conversation/state.py) | execution_status 区分 idle/running/paused/waiting_for_confirmation/finished/error/stuck/deleting；idle 不代表终态成功 |
| [OpenCode v1.19.4 types.gen.ts](https://github.com/anomalyco/opencode/blob/v1.19.4/packages/sdk/js/src/gen/types.gen.ts) | Session idle/busy/retry；工具 pending/running/completed/error，消息部件和权限事件单独表达 |
| [Gemini CLI scheduler/types.ts](https://github.com/google-gemini/gemini-cli/blob/main/packages/core/src/scheduler/types.ts) | 工具 validating/scheduled/awaiting_approval/executing/success/error/cancelled；支持工具展示信息及进度 |
| [Gemini CLI turn.ts](https://github.com/google-gemini/gemini-cli/blob/main/packages/core/src/core/turn.ts) | 模型内容、工具请求/确认/响应、重试、压缩、结束、取消和循环检测分别表达 |

共性仅支持“生命周期、操作活动、交互事件分离”这一建模判断；文件读取、任务安排、结果汇总等业务分组仍由本项目真实工具与目标流程定义。

### 2026-11-04：导出、流式及计划语义补查

本轮仍为 A/B/C 静态证据，未执行 SDK 行为实验或真实数据库操作。

| 编号 | 证据与发现 | 设计含义 |
| --- | --- | --- |
| R13 | B：`RuntimeContext.Builder.from` 复制 stringAttributes 和 typedAttributes；`AgentSession` 复制来源上下文，launch 再复制；sink 是对象引用，不是持久对象 | 正常复制链能保留 sink；进程重启必须按固定 Session 身份重新装配，不能把 Java 回调对象存入命令 JSON |
| R14 | B：`SessionModels.stream` 将 ChatResponse 追加为 model/chunk；`SessionModels.end` 保存 status、finishReason、usage 并调用 record/flush；`ToolExecutor` 的 tool/chunk 也是先 append | 持久增量来自模型/工具混合事件，需白名单提取可见文本；不能将 chunk 原载荷发送页面，不能将流正常完成自动判为答案完整 |
| R15 | A：`ProjectionMapper.findDueTargets` 按父目标/回复及 Worker 业务状态筛选，排除终态和部分恢复阻塞状态，并要求命令已投递 | 新的尾部补偿不能复用这个状态过滤当全部依据；业务已结束仍可能有未导出/未处理原生尾部，需按独立消费进度和终结屏障核对 |
| R16 | A：`PlanPublish` 含 joinPolicy、acceptanceCriteria；`PlanNode` 含 required。当前 joinPolicy 校验后保存在 definitionJson，汇合与完成路径未按 joinPolicy/required 分支；MasterSignalService 主要使用 allSucceeded、failedBranch、activeBranch | 协议已宣称的汇合标准与实际裁决存在缺口，不能只给页面新增状态。需明确 ALL_REQUIRED/BEST_EFFORT 的业务规则并实现同一套汇合判定 |
| R17 | A：`WorkerDependencyService.completedDependencies` 只接受全部依赖 SUCCEEDED，其他情况保持 BLOCKED；MasterSignalService 的 activeBranch 不包含 BLOCKED | 依赖失败后的下游尚缺明确“不能再执行”结果，需区分等待依赖与依赖失败跳过；聚合不能把 BLOCKED 全算进行中，也不能误报全部成功 |
| R18 | A：`PlanService` 新版本创建新 Job，旧未启动节点 SUPERSEDED，在途节点设置屏障；`WorkerResultReuseService` 保守复用并保存 reusedFromJobId | 页面必须按当前 planVersion 计数，旧版折叠保留；复用显示“沿用已核实结果”，不能伪造本轮执行耗时或再次调用工具 |
| R19 | A：`WorkerResultService` 先保存候选，原生回合结束后才 finalizeSuccess 并激活依赖；恢复尝试上限读取固定计划节点及运行 profile | “已提交子任务结果”与“子任务确认成功”要分开；恢复次数受计划和 profile 双重约束，页面不能独立推算重试上限 |

补充证据入口：`runtime/service/{PlanService,MasterSignalService,WorkerDependencyService,WorkerResultService,WorkerResultReuseService,WorkerRecoveryService}.java`、`runtime/dto/{PlanPublish,PlanNode}.java`、`mapper/runtime/{ProjectionMapper,PlanMapper}.xml`；SDK `SessionModels`、`RuntimeContext.Builder.from`、`ToolExecutor`。

### 导出确认方案比较与故障窗口

| 候选 | 确认边界 | 优点 | 代价与前提 |
| --- | --- | --- | --- |
| A：sink 直接事务消费 | 业务状态、活动、公开事件和平台消费序号提交后返回 | 少一层平台队列，事务完成语义明确 | 业务锁延迟占用 SDK 异步导出线程；需限制并发、批量及毒事件处理 |
| B：sink 写持久收件记录 | 已提交唯一事件收件记录后返回，业务处理用独立序号 | 隔离 SDK 导出与业务消费；崩溃后能独立调度 | 新增存储与一次持久化；不能原样复制包含 Prompt、工具参数的完整 SDK 事件 |
| C：sink 只通知内存队列 | 通知入队就返回 | 通知轻量 | 仅在独立平台消费游标、日志保留和补偿均可靠时可用，SDK 导出游标不能代表业务已处理 |

当前倾向先比较 A/B 的吞吐与故障行为，不因为“不怕大改”默认增加队列。B 若只保存日志引用，须保证 SDK 日志在消费、重放及核对完成前不可删除；若保存载荷，必须定义必要字段、访问和保留边界。

| 崩溃位置 | 必须得到的行为 |
| --- | --- |
| SDK 提交成功、sink 未执行 | 独立补偿按已提交日志与消费进度发现事件 |
| 平台事务失败、sink 抛错 | 导出序号不确认，阻止越过失败事件 |
| 平台已提交、SDK 导出序号未推进 | 重投事件命中幂等，既不重复业务副作用也不新增重复步骤 |
| 持久收件后、业务消费前 | 从收件记录恢复；不能因 SDK 已确认而丢失工作 |
| 末次导出失败、运行已结束 | 由独立尾部核对恢复，不依赖运行中 heartbeat |
| 原生事件无法解析 | 记录受影响绑定和消费故障；可选扩展事件与影响业务的必需事件分别处理，不能全部静默跳过 |

### 普通回复和流式输出的收口条件

- 当前 SDK 的 model/end 同时携带技术 status 与模型 finishReason；必须定义 stop、工具调用、长度截断、内容受限、最大迭代、异常、取消的映射，不能只检查 run/end=completed。
- 自然输出只取当前有效绑定/回合/尝试对应的 turn/output；先确认完整性、公开用途、发布归属及待处理交互/外部结果，再发布正式答案。
- model/chunk 包含模型响应，不等于可对外的纯文本。模型内部推理、工具参数（包括 finalAnswer 参数）、子任务局部内容和用户秘密不能因流式接入绕过输出筛选。
- 单纯将 stream(false) 改为 true 只能打开模型增量，无法提供安全、可恢复的答案协议；1s 持久 flush 也不能保证字符级即时展示。需选择已提交批量流，或可被权威快照替换的临时流并明确断线行为。
- 增量标识建议包含 execution/attempt/output 身份、块序号及偏移；重复忽略，缺口补齐，不同尝试禁止追加到同一缓冲区。最终文本以正式发布记录校验和替换。
- 预算中间件对缺少可信 usage 的调用会报错并核对预算；流式桩必须覆盖 usage-only 尾块、连接提前关闭和取消，不能为流式体验放松用量真实性要求。

### 计划与页面步骤的候选规则（尚未确认）

1. ALL_REQUIRED：必要节点成功且外部副作用已核对后才允许成功交付；可选节点失败如何影响 PARTIAL、尚在运行节点是否等待或取消，必须作为明确策略，不隐式遗弃。
2. BEST_EFFORT：节点成功、失败、依赖失败跳过均保存事实，所有应结束分支得到明确结论后进入汇总；不能用策略名自动把任务判为成功，仍需验收条件。
3. 依赖上游尚在执行，显示等待依赖；上游不可恢复失败且无替代路径，下游显示因依赖失败未执行，保留上游引用。自动重规划必须产生新计划版本。
4. 当前计划按成功、失败、运行、等待、跳过分别计数；旧版与复用明确标识，不以“终态节点数/总节点数”作为成功百分比。
5. acceptanceCriteria 当前是字符串列表，尚无逐项证据裁决；最终设计需区分机器可验证条件和人工/模型判断，不能以模型声明全部完成冒充验收证据。

### 2026-11-04：补充要求、交互、取消与重试分支

仍为静态源码证据，以下风险未通过并发/崩溃实验复现，不作为已经发生的线上故障结论。

| 编号 | 当前代码事实 | 需要解决的规则 |
| --- | --- | --- |
| R20 | A：`TaskDirectiveService.submit` 保存 ACCEPTED；目标协调会话 decide 才能 APPLIED/REJECTED。应用推进 goalRevision、使旧计划失效并拒绝基于旧目标代际的其他并发指令；`directive.status_changed` 已含原因与版本 | 用户补充不是立即生效，也不是简单注入当前 Worker；页面要显示“已收到、待协调、已应用/未应用”及关联源消息，不能仅依赖回复模型口头说明 |
| R21 | A：`TaskDirectiveService.coordinate` 要求安全点和无开放副作用；不满足时延后再次检查；当前聊天组件未针对 directive 状态形成明确进度展示 | 等待应用要说明阻塞原因，应用时展示旧步骤失效和新计划准备；普通进度查询不得生成修改指令 |
| R22 | A：`InteractionService.respond` 校验内容摘要、版本与有效期，审批允许时会调用外部执行器；`OperationExecutor.execute` 用 NOT_SUPPORTED 暂停调用者事务，外部状态独立持久；之后再保存交互答复和 RESPOND | 点击批准不等于操作成功，外部成功也可能早于交互答复事务提交。页面应分别展示批准受理、外部执行/核对、答复投递、运行恢复；重试复用账本结果，不能重复外部写入 |
| R23 | A：交互 RESPOND 命令 dueAt 固定 now+5s；`queueTaskAfterInteraction/queueResponseAfterInteraction` 仅在没有 OPEN 交互且未取消时转 QUEUED | 固定 5s 是额外等待的明确来源，真实意图需再核实；应评估用“原运行挂起并可接收答复”等事实作为投递条件，不直接删除延时造成恢复竞争；多个交互不能第一条答复就显示全部恢复 |
| R24 | A：回复取消在 `ResponseExecutionService.settleCancelled` 遇 uncertain 转 RECONCILING；目标 `TaskFinalizerService.finalizeCancelled` 与 `CompletionMapper.cancelTask` 未显式要求无 UNKNOWN/IN_FLIGHT、无在途 Worker，再置 CANCELLED 并清 activeTask | 两条路径的取消收口语义不同。需验证目标停止时未知操作、仍在执行的子节点及迟到结果，定义停止请求/执行已静止/外部影响已核实，不能假设取消会撤销已发生副作用 |
| R25 | A：`ResponseRetryService` 只允许指定暂时故障且无开放效果的显式重试，新建回复/Scope/Binding 和交付代际，沿用源消息和预算；SDK RESUME 则复用原 Turn | “重新回答”与“恢复执行”必须使用不同身份、按钮与审计语义；不能在一个活动上覆盖全部尝试，不能把显式重试变成重复用户消息 |
| R26 | A：`OperationReconcileService` 支持确证成功、确证未执行、仍不确定，要求版本与证据；仍不确定继续保留 UNKNOWN，确证未执行后重试仍需预检与审批 | 核对是独立业务活动，不能用通用重试按钮代替；页面显示核对证据摘要和下一步，不输出原始操作参数 |

源码证据：`conversation/service/{TaskDirectiveService,TaskControlService,ResponseControlService,ResponseRetryService,ResponseExecutionService}.java`、`interaction/service/InteractionService.java`、`tools/operation/{OperationExecutor,OperationReconcileService}.java`、`runtime/service/TaskFinalizerService.java`、`mapper/runtime/CompletionMapper.xml`、`mapper/conversation/ExecutionScopeMapper.xml`。

### 控制分支的候选用户流程

| 用户动作 | 页面即时反馈 | 后续事实驱动进展 | 收口条件 |
| --- | --- | --- | --- |
| 补充/替换目标 | 要求已收到，尚未应用 | 等待安全点、协调决定、目标版本更新、旧计划停用、新计划发布 | APPLIED 或带原因的 REJECTED；不得先显示已生效 |
| 批准操作 | 确认正在提交 | 决定受理、操作执行或复用结果、核对未知结果、交互答复排队、原运行恢复 | 操作结果和运行恢复分别确认；审批成功不是整个任务成功 |
| 拒绝操作 | 拒绝决定正在提交 | 未执行操作取消、答复原运行、协调其他方案 | 拒绝当前操作不自动取消整个目标 |
| 回答追问 | 答复正在提交 | 内容校验、答复持久化、等待其他必要交互、恢复原 Turn | 服务端确认剩余阻塞，不能靠按钮点击直接切 RUNNING |
| 停止工作 | 已请求停止 | 禁止新派发、向在途执行发停止、等待静止、核对未知外部影响 | 明确取消结果及仍需处理的外部影响，保留已发生事实 |
| 自动恢复 | 正在恢复第 N 次 | 同一业务执行/Turn 的后继 Run，保留前次尝试 | 恢复成功、达到上限、需要用户处理，三者明确区分 |
| 重新回答 | 新的一次回答已排队 | 新执行与输出身份，保留旧失败历史，沿用受控预算 | 新代际唯一终稿；旧尝试迟到不得发布 |

### 故障验证清单：本轮新增（尚未执行）

- V01：批准后的外部操作成功，交互事务提交前崩溃；重试答复不得重复写入，结果、预算和 RESPOND 最终可补齐。
- V02：交互答复已提交，但旧 Run 尚未进入可恢复状态；及时触发重试应基于可恢复事实，不能用任意睡眠维持正确性。
- V03：同 Scope 两个 OPEN 交互，只回答一个；页面仍显示剩余交互，不误报整项恢复。
- V04：目标取消与外部写入 IN_FLIGHT/UNKNOWN 并发；页面、任务终态、操作核对入口及迟到结果一致，不能失去核对能力。
- V05：目标取消与多个 Worker 同时运行；未启动命令取消、在途执行停止与交付屏障分别验收，不将取消受理等同所有线程已停止。
- V06：两个基于同 goalRevision 的补充并发；记录实际应用与拒绝结果，用户可理解哪条要求生效，旧计划不得继续提交当前结果。
- V07：显式重试后旧 Run 的完成/增量迟到；新输出不拼接旧文本、预算不重置、无第二份终稿。
- V08：用户拒绝审批与连接器已开始执行并发；不能显示“未产生外部变更”除非已由状态和执行栅栏证明。

### 2026-11-04：产物、会话事件与研究进度

| 编号 | 当前代码事实 | 设计含义 |
| --- | --- | --- |
| R27 | A：`ArtifactDeliveryService.stage` 暂存内容；commit 再验运行成功、绑定、Scope、guard 与摘要，同事务生成公开产物、关联并发布 artifact.ready | 暂存、文件就绪、目标交付是不同事实；产物可以先于目标完成，失败后不能撤回已经发生的下载事实 |
| R28 | A：`TaskEventPublishingService.insertProjected` 对答案与交互特殊处理，其余事件统一变成 task.status_changed，仅携带 taskId/status/version；计划发布和结果复用的载荷没有原样到达会话端 | 仅增加前端组件不够；需设计明确的计划、步骤、活动及产物公开事件，保留经筛选的业务字段，不能转发全部内部 payload |
| R29 | A：`ConversationQueryService.validateCursor` 检查会话代际、序号上界及最早保留事件；Controller 支持 Last-Event-ID。前端手动关闭 EventSource，重连先读快照，再从 snapshot.cursor 建新连接 | 已有可恢复订阅基础；下一版要明确快照与事件切换、过期游标重置及已展开历史处理，不能把服务器支持 Last-Event-ID 视为前端已增量续传 |
| R30 | A：前端 applyEventPayload 仅处理已知 messageId 的 message.completed，其他事件均触发 queueRefresh；每次消息完成还刷新会话列表 | 需要按事件联合类型实现纯 reducer，状态、步骤、交互和产物直接合并；缺序号/缺身份才补查，不能活动越细完整查询越多 |
| R31 | A：MessageThread 将消息附件与会话 deliverables 合并显示；产物 commit 关联 Scope/Task 并未在该步骤关联最终答案消息 | 文件应有 sourceExecution/job/planVersion/output 用途与结果引用；区分用户上传、过程产物、正式交付、已被替代版本，不能只按会话收集成一个文件列表 |

证据：`artifact/service/ArtifactDeliveryService.java`、`agent/tool/CommitArtifactTool.java`、`presentation/service/{TaskEventPublishingService,ConversationQueryService}.java`、`presentation/controller/ConversationReadController.java`、`web/workbench/src/features/chat/{stores/conversations.ts,components/MessageThread.vue}`。

### 前端恢复与产物的候选验收

- V09：读取快照期间产生新事件，按快照游标接续后不漏步骤；旧快照不能覆盖已应用的更新版本。
- V10：事件重复、缺号、游标过期、会话代际改变分别有确定处理；新建 EventSource 不依赖已销毁连接的 Last-Event-ID。
- V11：慢客户端积压时有界缓存、批量或强制快照恢复，不阻塞模型和其他用户；心跳不是业务进度。
- V12：文件已公开后目标失败/取消，页面仍可解释其来源及未完成部分；不声称文件已撤销，也不把过程文件当最终验收通过。
- V13：新计划替换旧计划时，旧产物保留来源和版本标识；最终交付显式引用实际采用的产物。
- V14：终稿正文及正式产物引用在发布事务内一致；下载权限、失效文件、重复发布分别验证，不能只核对文本答案唯一。

### 研究进度账（范围估计，非测试通过率）

以主链路、SDK 能力、计划协作、控制恢复、输出产物、页面协议、统一数据与状态设计、验证及迁移八个领域估计，当前约完成 65%。这个比例只表示证据梳理与方案收敛，未表示完成实现或运行验证。

| 领域 | 本轮状态 | 仍需完成 |
| --- | --- | --- |
| 回复/目标/工具主链路 | 关键事实已梳理 | 完成协议的最终选择与时序收敛 |
| 固定 SDK 能力 | 公开接口及限制已核对 | 导出、自然输出、直接返回和流式的隔离行为实验 |
| Plan/Worker 协作 | 依赖、版本、复用与汇合缺口已定位 | 必需/可选节点和验收规则统一 |
| 指令/交互/取消/重试 | 关键事务和风险已定位 | 分支状态机、并发及崩溃验证 |
| 输出/文件产物 | 发布边界与关联缺口已梳理 | 输出用途、正式交付引用与保留规则 |
| 快照/SSE/前端状态 | 当前协议及载荷损失已定位 | 增量协议、合并规则、背压及过期恢复 |
| 统一身份/状态/数据归属 | 有候选，未定稿 | 逐表逐字段保留/合并清单、权威与事务边界 |
| 性能/故障验证/迁移 | 已有 V01-V14 场景清单 | 可执行验证方案、历史在途处理与回退策略 |

运行实验目前未执行；已有测试源码只能证明存在测试意图，不能作为当前通过证据。只写研究记录，没有修改实现或操作真实库。

### 最终细化方案交付目录（拟议）

1. 业务用例与页面交互：正常路径、并行、等待、异常、控制及交付。
2. 执行对象归属：现有对象/表/字段的保留、合并、替换与唯一权威。
3. 状态与转换：前置条件、触发者、事务、副作用、幂等、并发和恢复。
4. SDK 接入与调度：上下文装配、官方导出、确认、补偿、模型与工具终结。
5. 输出与产物：流式身份、内容筛选、版本、正式发布及结果引用。
6. 接口和事件：请求响应、枚举、快照、游标、增量、背压及权限。
7. 数据与代码落点：DDL、Mapper、服务职责、事务锁序及删除旧路径。
8. 观测与验证：trace/span、日志、性能测量、V01-V14 及正常场景。
9. 迁移与实施：在途执行、历史数据、分阶段切换、回退和任务依赖。

### 2026-11-04：数据权威、候选协议与切换边界

| 编号 | 当前事实 | 结论或修正 |
| --- | --- | --- |
| R32 | A：`sq_execution_scope` 保存会话代际、owner、共享账户和恢复计数；`ExecutionAuthorizationService` 在 Scope 冻结技能/记忆授权。`BindingMapper.insertMaster/insertWorker` 将同一目标的 Master 与 Worker 关联到相同 Scope | Scope 是共享授权/资源范围，不是一条独立执行树节点。前期“合并 Scope 与回复”的建议尚不成立，不能简单给每个 Worker 新建 Scope 或预算 |
| R33 | A：回复、目标、Worker、Command、Operation、Sandbox 的 status 分别由各自表和服务推进；沙箱 execution_id 与回复 execution_id 属不同命名空间。Binding 的原生序号与会话公开序号也不同 | 统一关联不等于统一状态所有权；API 和日志须限定 ID 含义，禁止仅凭 executionId 无类型地拼接沙箱与回复 |
| R34 | A：已有 `TaskSnapshot` 提供任务、Job、交互及答案；聊天快照另行装配，公开会话事件与任务事件是不同序号 | 公共进度 DTO 和组装规则可以共享，但不能混用两个游标；统一 reducer 的输入应是同一公开事件协议，不能让聊天和详情自行解释内部状态 |

补充证据：最终 baseline 中的 `sq_execution_scope/sq_response_execution/sq_goal_task/sq_worker_job/sq_runtime_binding/sq_sandbox_execution`；`mapper/conversation/BindingMapper.xml`、`runtime/service/ExecutionAuthorizationService.java`、`presentation/dto/TaskSnapshot.java`。

### 对象与唯一权威的候选分工

| 对象 | 拥有什么 | 不负责什么 | 改造取舍 |
| --- | --- | --- | --- |
| Message | 用户原文、受理顺序、对外消息与版本 | 不据连接状态判断后台执行 | 保留源消息身份，增补输出用途与执行关联 |
| ResponseExecution | 一次回复及后继关系、取消意图、正式输出 | 不强制承担目标计划和子节点调度 | 去除普通回复的强制完成候选/纠正协议，条件待行为验证 |
| GoalTask | 用户目标修订、计划头、验收结论、控制与最终结果 | 不以一个 SDK Run 的技术结束直接决定成功 | 保留；集中汇合和完成政策，补齐必要节点与验收语义 |
| WorkerJob | 固定计划版本的节点、依赖、候选/确认结果、执行资格 | 不决定父目标成功，不覆盖新计划 | 保留为真实步骤权威，新增依赖失败等明确处置 |
| Scope | 会话代际、预算账户与授权资料范围 | 不代表每个模型/工具/Worker 的独立生命周期 | 保留并收紧职责；分散的恢复计数归属需另审 |
| RuntimeBinding/Run | 业务与 SDK 会话/回合/运行的映射及消费进度 | 不充当用户产品状态 | 统一装配与读取入口，区分绑定生命周期与具体运行 |
| Activity/Attempt | 实际模型/工具/调度/核对活动、尝试和耗时 | 不重复拥有外部写入结果、审批和任务状态 | 持久投影引用权威业务事实；需要活动类型、来源身份和幂等键 |
| Interaction/Operation | 用户决定及外部操作结果与核对证据 | 不因页面关闭或用户停止自动抹掉影响 | 保留独立账本；补足取消和恢复的共同收口规则 |
| Output/Delivery | 输出内容块、正式版本、产物引用、发布资格 | 不将全部模型文本视为最终答复 | 统一输出协议，保留唯一正式发布与已发生下载事实 |

D07（修正，候选）：暂不采用“将所有业务执行合并为一张万能 Execution 表”的路线。优先清理事实归属、关联和流程，在统一读模型中呈现执行树；如后续证明存在真正的公共生命周期，再讨论公共父表，不为减少编号牺牲授权隔离。

### 候选公开事件协议（研究草案，尚非 OpenAPI 契约）

统一信封包含 schemaVersion、conversationId、generation、sequence、eventId、occurredAt、type、payload。消费序号只属于会话公开事件，原生日志序号只作为内部来源，不能替代公开 sequence。

| 事件族 | 最小业务字段及处理 |
| --- | --- |
| execution.updated | ownerType/ownerId、sourceMessageId、version、status、allowedActions；明确回复或目标身份，不复用沙箱 executionId |
| plan.published / plan.superseded | taskId、goalRevision、planVersion、planId、节点摘要和依赖；新版本原子替换当前计划，旧版保留历史 |
| job.updated | taskId、planVersion、jobId、nodeKey、version、status、reason、reusedFromJobId；迟到旧计划事件仅更新旧版历史 |
| activity.updated | activityId、parentActivityId、ownerRef、binding/run/modelCall/toolCall 等内部映射所需身份的安全子集、version、type、status、attempt、时间及资源摘要；不包含原始参数/结果 |
| interaction.updated / directive.updated | 稳定业务 ID、所属目标/活动、version、状态、原因、允许操作；审批正文按明确权限读取，不随每条进度重复推送 |
| output.started / output.delta / output.completed / output.interrupted | outputId、ownerRef、attemptId、blockId、offset、用途；delta 必须说明偏移单位，不能混用字节/UTF-16/字符；complete 携带正式版本或可补查引用 |
| artifact.ready / delivery.published | artifactId、来源任务/Job/计划版本、输出用途、正式交付引用、内容摘要和允许的展示信息；不推送文件内容和下载凭据 |

统一合并规则：信封 sequence 先去重并检查连续性，实体 version 再防止旧状态覆盖；同一序号不同内容记协议故障。忽略未知可选事件时仍推进序号，遇不兼容必需事件执行快照/版本升级策略。实体身份缺失时补查，不将控制按钮点击视为权威状态。

临时输出流若独立于持久会话序号，必须使用独立 stream/offset 并声明可丢失，通过已提交输出快照纠正；不得给未持久内容分配假装可重放的会话序号。正式状态与交付只来自提交成功的公开事件。

### 状态转换设计约束（候选）

- Reply：排队、执行、等待、恢复、取消、终态仍分开；技术输出正常结束只是完成输入条件，必须通过发布资格、停止原因与外部效果校验。
- Task：计划等待/执行/汇总是流程事实，最终成功/部分完成由验收裁决；等待具体原因可从必要阻塞项聚合，不以最后一条 Worker 事件覆盖全局状态。
- Job：等待依赖、可领取、执行、等待交互、恢复受限、成功/失败/依赖失败未执行/旧计划替代分别有明确结果；不以 SUPERSEDED 假装用户主动取消。
- Activity：模型技术完成、文件读取成功、外部结果未知、审批等待分别保留，不把 Operation.UNKNOWN 转成 Activity.SUCCEEDED；活动结果不回写权威操作账本。
- Output：生成、被中断、完成、正式发布与被新版本替代各有事实；输出完成不必然等于目标完成，正式发布版本不可被旧增量修改。

### 切换与回退边界（候选）

1. 不使用同时写旧/新正式答案的“双执行”；影子验证只消费同一已提交事件计算差异，不再次调用模型或工具。
2. 受理时固定 executionProtocolVersion 和事件映射版本；已有运行使用原协议完成，新运行使用新协议。恢复不能因部署切换而重新解释已发生的副作用。
3. 新投影从已提交日志构建，但只回填可证明的活动；历史缺少开始时间、模型用量或输出用途时标未知，不伪造完整时间线。
4. 回退只切换新受理入口和可读协议；新协议已受理的运行必须由兼容消费者完成或安全暂停，不能把旧二进制直接连上不兼容新表。
5. 删除旧代码在在途耗尽、历史可读、输出/预算/账本对账通过后执行；不以“开发期”默认清库。正式发布、外部写入和下载不通过回退撤销。
6. 迁移文件先生成并校验，真实数据库按明确授权执行；SDK 自有存储仍只通过公开接口读取/恢复，不自行编写 JDBC 绕过其写入协议。

### 2026-11-04：静态调研收敛与行为验证计划

R35（B）：`Msg.getGenerateReason` 在 metadata 缺失、字段不存在或无法识别时回退 MODEL_STOP；`SessionRecorder.outcome` 对非显式中断/挂起/失败原因通常映射 completed。因此不能单凭 GenerateReason 或 run/end 将自然输出判为完整成功。应关联 model/end.finishReason、可信 turn/output、当前运行及发布屏障；停止原因缺失的兼容模型需要明确“无法确认完整性”的策略，而非静默当作 stop。

R36（C）：现有 `MySqlTestDatabase` 使用独立 as_test_ schema、按临时目录保留重启身份，并在 JVM 退出时清理本次创建的库；需要显式测试环境变量，不读取应用私有配置。后续实验必须限定专用测试实例/隔离库，不能直接运行未知 fixture 后宣称只读。本轮只读取 fixture，未创建或删除库。

### 架构方向收敛（仍为建议，不代表用户逐项确认）

- 保留 GoalTask/Plan/WorkerJob 与 Scope 的业务和授权边界；统一进度读模型，不引入万能执行表或第二套 DAG 调度器。
- 普通回复采用可信自然输出；目标保留结构化完成验收，并优先验证终结工具直接返回，减少回执解释回合。
- 优先官方 SessionExportSink；平台消费确认选择可靠事务边界，保留独立补偿，不依赖末次导出一定成功。
- 已提交状态、输出版本、正式交付持久可靠；低延迟临时流若引入，明确独立偏移和快照纠正语义。
- 页面步骤直接对应当前计划节点，活动对应实际模型/工具/交互；等待、依赖失败、旧计划替代、结果复用分别展示。
- 业务状态与公开事件提交后及时通知；每连接查库轮询和每事件完整快照刷新退出正常路径。
- trace 生命周期分别适配 Servlet/Reactor/持久异步，活动与尝试关联；业务正确性不依赖采样和观测平台。

### 六个决策实验（计划，未执行）

| 实验 | 输入与边界 | 必须断言 | 决定什么 |
| --- | --- | --- | --- |
| P01 自然回复终结 | 本地模型桩返回 stop、length、缺少原因、工具调用、异常关闭、最大迭代；读取 SDK 持久事件 | 每种情况的 model/end、turn/output、run/end 与正文身份；正常回复只一次必要模型调用；不完整结果不被默认为完整答案 | 自然回复可用条件、缺失原因和截断策略 |
| P02 终结工具直接返回 | 成功、错误、拒绝、混合批次、挂起后恢复、终结同时收到补充输入 | 模型调用次数、原生停止原因、正文来源、候选与发布状态；不能把回执变成答案；新输入不丢失 | isReturnDirect 可直接采用还是需要更严格终结协议 |
| P03 官方导出确认 | sink 失败、事务提交后 ACK 前中断、两个 exporter、Run 结束后尾部导出失败、进程重启 | 已提交事件不漏、重复不产生副作用、消费序号连续、故障不终止模型、尾部能补齐 | 直接事务消费或持久收件模式，补偿触发与并发边界 |
| P04 流式内容与用量 | 文本/工具参数/思考块混合、usage-only 尾块、截断、重复块、取消、新尝试迟到 | 只输出允许内容；偏移与最终正文一致；预算只结算可信用量；重试不拼接旧文本；记录提交与页面延迟 | 已提交批量流能否满足体验，是否需要临时输出通道 |
| P05 计划协作与控制 | 必要/可选失败、依赖失败、改计划、结果复用、审批答复与挂起竞争、取消与未知操作竞争 | 当前计划计数正确；失败依赖有明确结果；旧计划不能发布；审批不重复写；未知影响保留核对能力 | 汇合、取消和计划版本最终状态机 |
| P06 页面恢复与性能 | 快照并发新事件、重复/缺号/过期游标、慢消费者、多 Worker、观测不可用 | 页面最终与权威快照一致；缓存有界；不逐活动完整刷新；延迟分解可解释；无跨用户串号 | 最终事件字段、批量、背压与性能目标 |

P01-P04 可优先通过固定 SDK、本地 HTTP 桩与隔离日志存储确认 SDK 契约；不能将内存/文件探针结果冒充 MySQL 事务验证。P05 及持久恢复使用真实 MySQL 隔离实例，P06 包含前后端端到端验证。所有失败注入仅限实验环境，不调用真实模型或外部写系统。

每个实验输出固定版本、环境、输入、事件顺序/安全摘要、断言通过与失败、耗时分解和对设计的影响。不保存完整 Prompt、凭据或工具原始结果。性能先测受理、排队、模型首块、工具、事件落库、推送、页面应用和终稿发布，不先承诺未经测量的 SLA。

### 研究阶段收口说明

主链路与关键分支的静态调查已足以提出完整设计；尚未解决的主要是上述行为契约与实证。停止用重复源码浏览替代实验，也不把文档记录数量当作质量或通过率。此前约 65% 是跨静态调查、设计收敛和验证准备的范围估计；目前静态调查基本收敛，P01-P06 仍全部未执行，最终方案尚未定稿。

接续顺序：先形成对应能力 specs 与 design，明确上述实验作为设计准入条件，再生成实施任务；探索阶段不编写生产实现或新增实验代码。若关键实验否定 SDK 候选路线，更新设计并记录取舍，不用不成立的假设推动实现。

### 尚待完成的研究清单

1. 官方导出：RuntimeContext 复制是否保留 sink；Master、Worker、resume/respond 与重启是否均注入；sink.name 的稳定性；同一 Session 并发 drain 的行为；最后一次导出失败后无新事件如何补偿。
2. 导出确认：sink 返回代表已持久处理还是仅内存通知；若只通知后推进导出游标，平台独立游标与补偿必须确保不会漏事实。
3. 自然输出：模型停止、工具直接返回、截断、最大迭代、权限等待和取消分别如何完成；禁止把技术 completed 自动等同业务 SUCCEEDED。
4. 流式恢复：增量来源与提交频率、attempt/message 身份、偏移去重、重连和最终文本校验；暂停和重试不能拼接不同尝试。
5. 状态及数据：对象保留/合并清单、字段与归属、活动状态转换、事务/锁序、计划版本、预算继承、历史数据与回退。
6. 完整场景：普通聊天、附件问答、目标创建、并行/依赖、用户指令修改、审批、UNKNOWN、取消完成竞争、进程崩溃、SSE 重连及观测故障。
7. 最终方案需给出请求响应、事件字段、DDL、类/模块职责、转换与恢复步骤、性能验证方法和实现任务；这些内容尚未定稿。
