# Agent Squad 全链路日志追踪与 Agent 测评设计

> 2026-11-03；基于 main 的实际代码，分支 feat/20261103_log_optimize。此文是设计，不代表功能已开发完成。代码注释与日志如何说人话见 [术语说明](terminology-guide.md)。

## 1. 我们要解决什么

现在系统虽然有 requestId、任务和命令编号、AgentScope 自己记录的执行事件，但出问题时仍很难迅速回答：
1. 用户请求从网页到后台究竟走到了哪里，哪个步骤最慢？
2. Agent 调用了哪个模型、哪几个工具、几个 Worker，为什么出现重试或重复回答？
3. Agent 的最终结果对不对？选择的工具合不合理？有没有超预算、未经审批操作或多次调用？
4. 换模型、换提示词、调整 Agent 流程后，如何用相同题目比较改动前后的质量、时延和费用？

**分三层做：**

| 层次 | 能解决什么 | 保存在哪里 |
|---|---|---|
| 应用日志 | 搜索“这个任务出了什么问题” | 集中日志系统 |
| 执行追踪（Trace） | 展示调用顺序，每步时长和原因 | OpenTelemetry（简称 OTel，通用追踪标准）+ 可选可视化平台 |
| Agent 测评（Eval） | 用固定测试场景打分、对比不同版本 | 以后单独建数据集、评分和实验记录 |

用户消息、任务状态、审批记录、工具外部操作的结果，仍以当前 MySQL 业务记录为准。监控平台宕机不能阻断业务，日志和监控数据不能代替数据库业务事实。

## 2. 结合当前代码，真正会走的路径

~~~mermaid
flowchart TD
 A["用户在页面发消息"] --> B["Gateway：身份验证，转发到 Server"]
 B --> C["Server：校验输入，保存消息和待执行命令"]
 C --> D["后台执行器接手命令"]
 D --> E["AgentScope：启动 Master"]
 E --> F["模型调用、工具选择、生成计划"]
 F --> G["可能分派给多个 Worker"]
 G --> H["Worker 执行模型/工具/沙箱"]
 F --> I["AgentScope 保存执行事件"]
 H --> I
 I --> J["后台读取执行事件，更新任务状态"]
 J --> K["等待用户输入/审批 或 继续执行"]
 K --> I
 J --> L["检查最终答案归属，发布最终结果"]
 L --> M["通过 SSE 通知页面"]
 C -. "保存 taskId / executionId / commandId" .-> D
~~~

已核查的接入位置：

| 当前代码 | 需要记录的关键事实 |
|---|---|
| GatewayRequestIdFilter、GatewayAuthenticationFilter、GatewayRoutingConfiguration | 请求从哪里来、鉴权是否通过、网关转发耗时；继续保留当前 X-Request-Id |
| RequestCorrelationFilter | 服务收到请求后能带着相同追踪编号进入日志；异常时也要清理线程信息 |
| MessageAcceptanceService / Writer、GoalTaskService | 消息是否真正保存，生成的 messageId、executionId、taskId、commandId，是否重复提交 |
| CommandClaimService、CommandDeliveryService | 从入库到开始执行等待多久、接手结果、重试次数、具体失败阶段 |
| MasterAgent、WorkerFactory、ExecutionContextMiddleware | Master/Worker 运行开始结束、AgentScope Session/Run/Turn 编号 |
| ModelBudgetMiddleware、ProfiledModelBudgetMiddleware | 模型名、版本、耗时、真实 token、预算预留及实际结算 |
| PlanService、Worker 相关服务 | 计划版本、任务节点、Worker 并行/依赖关系、结果复用 |
| NativeProjectionPoller、NativeProjectionService | 处理到第几条 Agent 事件、事件延迟、是否重复、是否跳号、业务状态结果 |
| OperationExecutor、SandboxExecutionService、InteractionService | 工具名称、审批状态、外部写入是否确认成功、沙箱耗时 |
| ResponseExecutionService、TaskFinalizerService、ConversationEventWriter | 是否形成有效答案、是否取得最终发布资格、为何转入失败/取消/等待 |
| ConversationEventStreamer、TaskEventStreamer | 页面通知是否成功，断开连接不会改变后台执行结果 |

**现状限制：**当前 Server 已有 Actuator 和 Prometheus 依赖，但没有核实到统一打通这些步骤的 Trace/Span 组件。现有 application.yml 特意关闭 AgentScope AgentTraceMiddleware 的日志，不直接打开这个可能输出完整上下文的开关。AgentScope Harness 固定在 2.1.0-BETA1，实现时需要针对该版本验证事件生命周期和 token 是否真实可取。

## 3. 怎样把一次执行串起来

### 3.1 不要把三种编号混为一谈

| 编号 | 是什么 | 会持续多久 |
|---|---|---|
| requestId | 现有接口请求编号，方便页面报错和历史日志查找 | 一次 HTTP 请求 |
| traceId | 通用追踪平台的调用编号 | 一次具体的技术调用 |
| spanId | 一次调用中的某个步骤编号 | 从步骤开始到结束 |
| scopeId | 一次回复或一个目标任务的统一执行范围 | 从任务保存到结束，包括恢复 |
| executionId / taskId | 具体的普通回复 / 目标任务编号 | 业务生命周期 |
| commandId | 后台处理的一条命令编号 | 同一命令失败重试时不变 |
| jobId | 一个 Worker 执行的工作节点 | 节点生命周期 |
| bindingId / runId / turnId | Agent 会话关联、单次运行、单次回合的编号 | 与 AgentScope 实际事实一致 |

**特别注意：**一个 Agent 任务可能等待几个小时、重启后继续。不能让网页的 HTTP Trace 一直“运行几小时”。

方案是：网页请求和后台处理分别记录真实耗时，后台处理通过已经保存的 scopeId、commandId 和 Trace Link（关联之前那次调用）连接起来。即使重启，也能从业务编号追溯最初消息、历次投递和当前运行；无法关联的旧历史命令允许查询但不伪造 Trace。

### 3.2 传递追踪信息

1. 网页 → Gateway → Server：传递 W3C \`traceparent\`/\`tracestate\`，这是业界标准的追踪格式；同时继续保留 \`X-Request-Id\`。
2. Gateway 的 WebFlux/Reactor 异步线程要传播上下文；Server 的 Servlet 线程使用 MDC 保存当前 traceId/requestId，结束时清理；不能假设换线程后 MDC 会自动正确。
3. 消息与命令保存时，**建议增加一个只存关联编号的小记录**：userId、scopeId、commandId、上一次 traceId/spanId、保存时间。建议名称 \`sq_observability_link\`，具体表结构和迁移另行审查后实现。
4. 后台接手命令时开启新的 Trace 片段，关联上一次编号，按 commandId 标记第几次尝试。重复读取 Agent 事件不能再次创建业务结果。
5. Master 和 Worker 都要记录自己的 run/turn，不能把某个 Worker 或 SDK 回合成功当作整个用户任务成功。

### 3.3 需要记录的步骤

| 追踪步骤（英文为标准/程序名） | 页面上怎么解释 | 记录什么 |
|---|---|---|
| http.request | 网页请求 | 接口、状态、耗时 |
| message.accept | 收到并保存消息 | 是否保存、是否重复、消息/执行编号 |
| command.deliver | 后台开始处理 | 等待时间、尝试次数、结果 |
| invoke_agent | Agent 开始工作 | Master/Worker、运行编号、完成情况 |
| model.call | 调用大模型 | 提供商、模型、耗时、真实 token、成本 |
| tool.execute | 执行工具 | 工具名、执行时间、是否需审批、结果类型 |
| plan.publish / worker.dispatch | 生成计划 / 分配 Worker | 计划版本、节点编号、依赖 |
| event.apply | 处理 Agent 执行事件 | 已处理序号、延迟、是否重复/缺失 |
| interaction.wait | 等用户输入或批准 | 等待原因、等待时长 |
| answer.publish | 发布最终答案 | 结果状态、是否已由其他流程发布 |
| event.notify | 通知网页 | 事件类别、投递结果 |

RAG 的 search/retrieve/rerank、Guardrail、Embedding 和 Evaluator 等类型作为以后扩展，**当前没有运行的步骤绝不记录虚假的耗时**。

### 3.4 一条日志长什么样

~~~json
{
  "time": "2026-11-03T20:30:00+08:00",
  "level": "INFO",
  "service": "agent-squad-server",
  "event": "command.delivered",
  "message": "后台命令已提交给 AgentScope",
  "traceId": "0123456789abcdef0123456789abcdef",
  "spanId": "1234567890abcdef",
  "requestId": "11111111-1111-4111-8111-111111111111",
  "scopeId": "scope-example",
  "commandId": "command-example",
  "attempt": 2,
  "durationMs": 43,
  "status": "SUCCEEDED",
  "errorCode": null
}
~~~

样例中的 ID 是为了说明字段，不代表真实业务值。**只传允许字段，不对 request/response、异常、整个对象先 JSON 序列化后截断。** 建议初始单条结构化日志总量限制在 4 KiB 左右（上线前压测调整），避免大日志、长异常和大量对象引发内存暴涨。

## 4. 采用哪些业界技术

| 组件/标准 | 建议怎么用 | 为什么 |
|---|---|---|
| OpenTelemetry + W3C Trace Context | 统一 Trace/Span 和跨服务传递方式 | 不依赖具体可视化平台 |
| OTel GenAI 语义约定 | 标注 Agent、模型、工具、Token 信息 | 用行业通用字段，未来便于接入分析平台 |
| OpenInference | 作为第二套可转换的 Agent/LLM/TOOL/EVALUATOR 标注 | 兼容 Phoenix 等分析工具 |
| Micrometer Tracing + OTel bridge + OTLP | 集成 Spring Boot 3.5.6，使用 OTel API 补 Agent 特殊步骤 | 能与已有 Actuator/Prometheus 配合 |
| Collector（追踪数据接收器） | 批量接收、过滤、限流、导出 Trace | 让慢/坏的监控平台不阻塞业务 |
| Phoenix / Langfuse | 后续可对比，先考虑自托管 PoC | 两者都支持 Agent 跟踪、数据集/测评相关流程 |

**不在生产中直接引入某平台 SDK 并把业务代码绑死。** OTel GenAI 约定正在发展，固定实际使用的语义规范版本，并保留字段转换层。避免 Java Agent、Spring Starter、手工埋点重复记录同一次调用。

参考资料（与实现时具体版本核对）：
- OTel GenAI：https://opentelemetry.io/docs/specs/semconv/gen-ai/
- OTel Spring Boot tracing：https://docs.spring.io/spring-boot/3.5/reference/actuator/tracing.html
- OpenInference：https://arize-ai.github.io/openinference/spec/
- Phoenix：https://arize.com/docs/phoenix/
- Langfuse 测评：https://langfuse.com/docs/evaluation/overview

## 5. 为以后 Agent 测评保留什么

不要只有“答案相似度”。面向 Agent 要检查**结果和过程**：

| 测评方向 | 典型问题 | 依据 |
|---|---|---|
| 答案质量 | 是否完整、准确回答，是否遗漏约束 | 最终答案 + 经授权的参考答案 |
| 计划与协作 | 任务拆分是否合理、Worker 是否重复、是否按依赖顺序执行 | 计划版本、jobId、执行事件 |
| 工具选择 | 工具该不该调用，是否重复调用，结果是否正确利用 | 工具步骤、状态、业务证据 |
| 安全 | 未经批准是否执行写入、是否越权、外部结果不确定时是否停止重试 | 操作账本、权限、审批、UNKNOWN |
| 稳定性 | 超时能否恢复、重试是否重复产生副作用、同一消息是否只发一个最终答案 | 命令、失败、最终发布记录 |
| 效率/成本 | 完成耗时、模型调用数、token、每个成功任务的成本 | Trace、模型真实计费和预算记录 |

### 5.1 评测独立于日志采样

生产 Trace 通常会采样（只留部分请求），所以**不能直接拿 Trace 数量计算任务成功率**，也不能把所有用户对话自动当成评测数据集。未来从已有业务事实读取“结果是否成功、是否重复写、是否违反审批、耗时”等可靠信息，再经授权/脱敏选入评测集。

### 5.2 最小可扩展的测评记录

~~~json
{
  "dataset": {"id": "agent-regression", "version": 3, "caseId": "approval-check"},
  "experiment": {"id": "experiment-001", "mode": "EVAL_SANDBOX"},
  "target": {"scopeId": "scope-example", "scopeKind": "TASK", "taskId": "task-example", "traceId": "0123456789abcdef0123456789abcdef"},
  "versions": {"code": "git-sha", "model": "model-version", "prompt": "master-v1", "toolSchema": "tools-v1", "skill": "skill-digest"},
  "evaluation": {"rule": "approval-compliance", "ruleVersion": "1.0", "method": "CODE", "scoreType": "BOOLEAN", "score": true, "evidenceIds": ["operation-example"]},
  "privacy": {"mode": "METADATA_ONLY"}
}
~~~

未来数据对象各有明确用途：
- **测试题库 Dataset / Case：**固定输入、预期行为、禁止的工具操作、版本。
- **实验 Experiment / Run：**用某一版模型/提示词/代码批量跑题，不同版本可以公平比较。
- **评分 Score：**记录评分标准、规则版本、分数/类别、证据和失败原因。
- **反馈 Feedback：**人工/用户/程序/模型评审的纠错意见；与自动打分分开。
- **必要内容存储 Artifact Ref：**只存经授权、脱敏后的内容引用和摘要，追踪本身不保存大段正文。

以后可能使用规则代码检查、LLM-as-a-Judge（让另一个模型按固定标准评审）、人工标注等方式组合。先做能确定对错的规则（不重复交付、未经批准不得写入、UNKNOWN 不重复执行），再做主观质量判断。

### 5.3 安全回放和数据版本

历史 Trace 只说明已经发生的步骤，不会神奇地复现模型的全部推理。未来要重新跑 Agent，必须使用隔离环境，外部写操作换成 Mock，不能拿线上操作权限重新执行。记录代码、模型、提示词、工具、Skill、价格、评测数据集版本；缺失的版本直接标 UNKNOWN，不猜测。

## 6. 实施顺序

**第一步：先查问题。** 两个服务统一追踪编号、结构化日志、HTTP 耗时，完成跨线程传递、日志安全限额，不把对象整包打出来。

**第二步：打通 Agent。** 记录命令等待与重试、Master/Worker、模型和工具调用、计划和执行事件、最终答案。必要时增加最小关联表，不能影响现有持久处理机制。

**第三步：看趋势。** 按模型、工具、步骤、异常类别统计慢在哪里、花多少钱、失败/恢复比例。Prometheus 指标标签不放 userId、taskId、commandId 等会无限增长的编号。

**第四步：做测评。** 先从线上故障整理经过授权的测试题，构建数据集、评分标准、实验对比；逐步增加质量判断和 CI 回归。

按这四步做，第一阶段就能排障，第二阶段能分析 Agent 过程，后两阶段再做系统化提升，不会为追求监控而一次性修改所有业务代码。

## 7. 必须通过的检查

1. 从浏览器到 Gateway、Server 的 traceId 关联正确；保留 requestId；错误或跨线程情况下不串号。
2. 从“消息已保存”到“后台第一次执行、失败重试、进程重启后继续执行”，都能用 executionId/taskId/commandId 查明白。
3. 一个目标由 Master + 多个 Worker 处理时能看出运行顺序、模型调用数、耗时、结果；不把 Worker 成功错算成整个目标成功。
4. 对等待输入、等待审批、取消、失败、外部操作结果未知、部分完成区分统计，不一律标 ERROR。
5. 页面 SSE 断开不等于 Agent 结束；重复事件不会导致重复终稿。
6. 大集合（如 6 万条数据）、长日志、巨型工具结果不进行完整日志 JSON 序列化；不泄露 Prompt、Cookie、密钥和用户隐私。
7. Trace 平台不可用/队列满时允许丢观测数据，但不能丢消息、命令、审计或影响工具安全性。
8. 评测数据收集与用户授权一致；不同模型/提示词版本可对比质量、安全、耗时与成本。

### 暂时不做

- 不直接修改真实数据库、不自动回放线上写操作。
- 不把原生 AgentScope 日志、用户公开事件和 OTel Trace 混在同一个表里。
- 不建立全量 Prompt/模型结果日志库，也不只靠最终答案相似度给 Agent 打分。
- 本文不宣称代码、集成测试或实际测评平台已经上线；具体开发拆分见 [任务清单](agent-observability-tasks.md)。
