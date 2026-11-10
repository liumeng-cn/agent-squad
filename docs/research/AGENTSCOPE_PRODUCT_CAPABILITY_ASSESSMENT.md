# AgentScope Java 产品能力审计与架构选型研究

> 研究状态：第一轮源码审计已完成，架构结论为阶段性结论，尚未批准重构或推翻当前实现。  
> 审计目标分支：`feat/20261103_log_optimize`  
> 项目固定候选：AgentScope Java `2.1.0-BETA1`，上游源码基准 commit：`5451c73abe47053340683a1cc6d67b290b056648`  
> 研究原则：先核对固定版本源码和当前项目真实实现；上游 main 的新能力单独标记，不得直接算作当前依赖已有能力。

## 1. 结论先行

**当前不建议推翻 AgentSquad，也不建议把现有 Worker 调度整体替换成 AgentScope 原生 SubAgent。更合理的候选方向是：保留产品级任务控制面，重新审视并收敛 Agent 运行层，尽可能让 AgentScope Harness 承担 Agent 执行、会话、事件和检查点职责。**

原因：

1. AgentScope Harness 已有相当完整的 Agent 执行底座：ReAct、工具、中间件、模型调用、会话日志、checkpoint、AgentSession、上下文压缩、记忆、Skill、MCP、SubAgent 等。
2. AgentScope 原生 SubAgent 解决的是“把一个可独立处理的工作委派给另一个 Agent”，并不等同于产品需要的 GoalTask、不可变 DAG 计划、Worker 能力版本、依赖调度、业务授权、预算、结果验收和最终交付权。
3. AgentScope 的 AgentSession 与 Session Log 能处理持久提交、排队、运行中引导、中断、待办答复、恢复和历史读取。它们比自行维护 Agent 执行状态更值得优先使用。
4. 当前 AgentSquad 已经把 Master/Worker 的执行建立在 HarnessAgent + AgentSession + 原生 Session Log 上，并且有平台侧 Worker 任务治理、跨进程恢复协调和集成测试。它不是简单的“在 AgentScope 外重新造一套 Agent 引擎”。
5. 当前项目显式禁用了 Master 和 Worker 的原生 SubAgent，并且 Master/Worker 的 OpenAI 模型都配置了 `.stream(false)`。前者是架构选择，后者与产品实时反馈目标存在明显冲突，必须进一步验证和处理。
6. 固定依赖是 BETA 版本。上游 main 文档已有的能力、修复和 API 变化不能未经兼容性核查就直接纳入本项目设计。

因此，当前主要问题不是“AgentScope 能不能做”，而是：**哪些属于 AgentScope 执行内核，哪些必须由 AgentSquad 作为产品控制面负责，以及现有接入是否充分使用了原生能力。**

## 2. 产品目标基线

本次评估围绕以下产品目标，不把实现现状反过来当成产品约束。

### 2.1 两种产品入口，共用 Agent 运行底座

- **客服 / 日常聊天**：连续对话、RAG 与业务知识、记忆、自主决策、必要时组织多个 Agent 并行排查；用户可以持续交流、插话、调整要求、停止执行；页面断线不应导致后台任务无故丢失。
- **复杂任务工作台**：承接长流程调研、分析、文档/表格/演示文稿/多媒体产物等任务；展示阶段进度、执行结果、失败与恢复状态，支持人工介入和产物交付。
- 两个入口的交互方式不同，但不应因此产生两套互不兼容的 Agent 运行体系。

### 2.2 Master / SubAgent / Worker 的目标

- Master 是用户持续交流的主要对象，负责理解、决策、计划、协调、验收和最终答复。
- SubAgent 是 Master 为解决问题自主组织的协作者，可以并行调查，也可以按需创建。
- Worker 是面向复杂任务的受控执行单元，能力、权限、输入输出和运行预算可以由平台管理。
- 预设协作者与 Master 按任务动态组织协作者都需要评估。
- 执行过程需要可观测、可持久化、可恢复、可停止；不能把“任务记录还在”误认为“任务已经恢复执行”。

### 2.3 平台级扩展

未来允许不同业务平台通过平台级配置接入：Master 提示词、平台知识、RAG、Tools/API、Skill、权限规则等。它是平台/部署级能力，不等于当前阶段开放普通用户随意创建 Agent 或修改系统提示词。

## 3. 评估范围与证据等级

本报告区分三类证据：

- **固定版本源码/文档确认**：AgentScope Java commit `5451c73abe47053340683a1cc6d67b290b056648`，与项目固定依赖 `2.1.0-BETA1` 对应。
- **当前项目代码确认**：目标分支 `feat/20261103_log_optimize` 上实际读取的 POM、MasterAgent、WorkerFactory、NativeSessionGateway、WorkerRecoveryService、WorkerCrashRecoveryIntegrationTest、README 和 OpenSpec 配置。
- **待验证推断**：需要用固定依赖编译运行或故障注入验证，不能只靠文档下结论。

本轮未把 AgentScope Service 作为默认方案。项目 OpenSpec 明确选择嵌入 Harness SDK，不整套引入 AgentScope Service。

## 4. AgentScope 原生能力审计

### 4.1 Agent 执行与 Harness

HarnessAgent 基于 ReAct 执行循环，整合模型、工具、权限、中间件、工作区、Skill、MCP、记忆、上下文压缩、SubAgent 和会话能力。HarnessAgent 文档将其定位为面向持续工作的 Agent 封装；核心 ReActAgent 也可以单独使用，但若自行组合，需要自行承担更多工程集成。

对本产品的意义：

- 日常聊天和单次请求可以用 `call` / `streamEvents`。
- 需要后台排队、持续执行、补充要求、中断恢复时，优先评估 `agent.session(ctx)`。
- 不应为了“统一执行入口”把普通聊天也强行变成长任务；也不应为了 SSE 把后台任务绑定到 HTTP 请求订阅生命周期。

结论：**HarnessAgent 适合作为 Agent 执行内核，AgentSquad 不需要重新实现 ReAct、模型/工具调用循环和基础 Agent 生命周期。**

### 4.2 AgentSession：产品长任务的关键原生能力

固定版本 `AgentSession` 源码与文档确认了以下操作：

| 原生能力 | SDK 提供的行为 | 对产品的价值 | 仍需平台负责的部分 |
|---|---|---|---|
| `submit` | 持久接收任务，使用幂等键，进入后台队列 | 消息提交与执行解耦 | 用户消息、目标任务、业务命令的归属与权限 |
| `tasks / await` | 查询任务状态、观察执行结果 | 后台任务状态读取 | 页面业务视图与最终任务状态 |
| `steer` | 向指定运行中的任务补充/调整要求，在后续推理步骤生效 | 用户执行中插话 | 判断这条用户消息究竟是新任务、补充要求还是闲聊 |
| `inject` | 保存额外材料供后续推理读取，不单独启动任务 | 补充背景材料 | 资料授权、来源、版本与有效期 |
| `interrupt` | 请求执行在协作检查点停止 | 用户停止任务 | 取消外部副作用、工具超时和停止结果确认 |
| `resume` | 从保存的 checkpoint 继续最新未完成任务 | 中断后续做 | 恢复前的业务核对、权限复查、恢复次数与策略 |
| `pending / respond` | 查看待答交互并把用户答案交回原任务 | 追问、审批、工具确认 | 产品审批页面、用户身份和业务审批规则 |
| `log / transcript / inspect` | 读取已提交执行历史、消息、待办与不确定工具调用 | 页面恢复、排障与恢复判断 | 将原生事件投影成产品事件和聊天界面模型 |

重要边界：

1. `AgentSession` 持有后台执行订阅，观察 HTTP 请求或 SSE 连接结束，不会自动取消后台任务。
2. `submit` 返回的是接收回执，不是模型答案。
3. 页面重连应该读取快照和事件，不应再次 `submit` 或重新调用 `streamEvents`。
4. `interrupt` 是协作式停止请求，不是强制杀掉正在执行的 Java 线程或外部工具。
5. checkpoint 能恢复 Agent 的工作状态，但不会恢复原 Java 线程、网络请求或工具内部执行进度，也不会回滚已经产生的外部副作用。
6. 如果工具调用发生后、结果提交前进程退出，SDK 会暴露不确定工具调用；应用必须查询真实业务系统，再通过结果核对接口补交结果，不能盲目重试。
7. 重启后使用相同 `agentId`、`userId`、`sessionId` 和日志后端重建 Agent，再调用 `session.start()` 恢复持久队列调度。start 不会自动替业务决定恢复所有 interrupted turn。

**结论：AgentSession 已覆盖我们产品中相当一部分“后台会话运行管理”需求。平台不应再重复实现提交队列、会话级 steer/interrupt/respond/resume 和 Agent 执行订阅管理；但平台仍必须负责业务状态机、权限、恢复策略和副作用核对。**

### 4.3 Session Log 与 checkpoint

固定版本文档明确区分：

- `AgentEvent`：实时执行时产生的事件通知。
- `SessionEvent`：已经持久保存、可用于历史读取和恢复的记录。
- `AgentState` checkpoint：继续执行所需的工作状态。
- `AgentSession`：任务提交、排队、引导、答复、中断和恢复入口。

Session Log 具备顺序号、事件 ID、运行/回合标识、历史扫描和恢复检查能力。默认 Harness 使用 EVENT_LOG 作为原生会话状态恢复依据；配置的 SessionLogStore 必须满足相应的并发写入与 CAS 要求。仅配置旧 AgentStateStore 不等于已经配置好 EVENT_LOG 的日志恢复。

页面恢复仍需要应用层实现：

1. 选定已提交事件位置，生成截至该位置的快照。
2. 返回快照和 cursor。
3. 从 cursor 续读事件，按消息 ID / 工具调用 ID 更新已有视图。
4. 页面刷新丢失内存状态时重新取完整快照；短暂断线可从最后处理位置续读。

**结论：原生日志与 checkpoint 可以作为执行事实和恢复基础；SSE 快照、事件游标、前端消息卡片和业务状态投影仍然属于 AgentSquad。**

### 4.4 原生 SubAgent：有用，但不等于我们的 Worker 平台

固定版本 Harness 提供：

- `agent_spawn`：按声明创建子 Agent，可同步等待或后台运行。
- `agent_send`：向已有子 Agent 继续发送消息。
- `agent_list`：列出当前子 Agent。
- `task_output`、`wait_async_results`、`task_cancel`、`task_list`：管理后台任务结果。
- 支持工作区 Markdown spec、编程式声明、远程 Agent Protocol 子 Agent。
- 可通过 `persistSession` 让同一子 Agent 在指定身份组合下复用会话。
- 支持子 Agent 事件流转发、来源标记、权限继承与递归保护。
- Toolkit 默认支持同一轮多个工具并行调用，可实现简单 fan-out / fan-in。

但原生 SubAgent 的语义与产品 Worker 有明显差别：

| 比较维度 | AgentScope 原生 SubAgent | AgentSquad 产品 Worker |
|---|---|---|
| 核心目的 | Agent 内部委派独立工作 | 受平台治理的业务执行节点 |
| 创建来源 | 预设声明、工作区 spec、动态 spec 机制 | 已发布 Worker Definition 与 Master 计划 |
| 并行关系 | 同一轮并行工具调用，或后台任务 | 持久计划节点、dependsOn 依赖关系、调度器控制 |
| 任务状态 | SubAgent 实例与后台 task 状态 | GoalTask / WorkerJob / Plan / Command 等业务状态 |
| 输入输出 | Prompt 与 Agent 消息/工具结果 | 冻结上下文、输入约束、输出 Schema、候选结果 |
| 权限 | Agent 工具配置、权限继承等 | 用户/任务/Worker Definition/工具级平台授权 |
| 预算 | 模型与执行配置可覆盖 | 模型 Profile、任务预算与平台侧结算 |
| 故障恢复 | 子任务存储与执行恢复有自身边界，见下文 | 平台识别 interrupted turn，受控写入 RESUME 命令 |
| 交付 | 返回父 Agent 的结果或后台通知 | Worker 结果候选、平台验收、最终答案交付 |

#### 特别重要：后台 task 持久化不等于本地子 Agent 可跨重启续跑

固定版本 `WorkspaceTaskRepository` 源码写明：

- 任务记录以 Workspace 为权威状态来源，本地 `CompletableFuture` 只是当前节点执行句柄。
- 执行任务粘在创建它的节点。
- 其他节点可以读取持久任务状态，但不能接管本地 Future。
- 本地进程退出后，后台任务不会自动恢复执行；孤儿清理会在心跳超时后把未完成任务标为 FAILED。
- 远程 Agent Protocol 任务通过远程传输/轮询有不同的恢复路径。

这意味着：**原生 SubAgent 的“后台任务记录持久化”和“SubAgent AgentSession checkpoint 恢复”不是一回事。** 对本产品不能只看 TaskRepository 的任务状态文件就认定具备可靠跨进程恢复。

#### 动态创建能力的边界

Harness 有动态 SubAgent spec 重新加载机制；另有默认关闭的 `agent_generate` 工具，可以让模型生成新的 SubAgent spec。它生成的是可写入工作区、供后续推理加载的声明，不等同于不受约束地在当前任务里临时创建任意权限的执行进程。生产使用还需要审查 spec 写入权限、工具白名单、模型配置、版本和人工审核策略。

固定版本文档还写明递归保护：子 Agent 被限制为叶子角色，并有最大嵌套层数限制。因此不能假设原生 SubAgent 可以无限递归形成任意层级 Agent 树。

**结论：原生 SubAgent 适合 Master 的轻量专家委派、并行调查和短期协作；不应未经验证就取代产品级 Worker DAG 与业务治理。**

### 4.5 实时事件与模型流式

AgentScope 的 `streamEvents` 可以返回细粒度 `AgentEvent`；AgentSession 负责持久后台运行和会话事件读取。两者不是同一个功能：

- `streamEvents` 是当前执行的实时通知流。
- Session Log 是已提交事件的持久事实。
- 页面断线后应恢复历史/游标，而不是重启一次模型执行。
- 如果模型客户端关闭流式输出，就不能期待稳定的逐 token 文本增量。

当前 AgentSquad 的 MasterAgent 与 WorkerFactory 都使用：

```java
OpenAIChatModel.builder()
    ...
    .stream(false)
    .build()
```

这项配置是源码确认的。它与产品要求的“像聊天一样持续有回应、实时反馈执行进度”不匹配，至少会限制模型 token 增量。当前平台仍可以通过已提交 Session Log 事件投影阶段状态，但这不能等价于模型 token 流式。

下一步必须在固定 BETA1 版本、当前模型服务和当前 SSE 页面上分别验证：

1. `stream(true)` 时是否产生预期文本 chunk / tool event。
2. AgentSession 后台执行时，如何订阅实时事件且不让观察者生命周期控制后台任务。
3. 断开 SSE 后重新连接，如何从 Session Log cursor 补齐事件。
4. stop / steer 与模型请求、工具执行中的竞态行为。
5. 事件提交延迟、投影轮询延迟和页面展示延迟分别是多少。

### 4.6 上下文、记忆、RAG、Skill 与 MCP

固定版本 Harness 提供记忆和上下文能力：

- 对话压缩与上下文溢出恢复。
- 工具大结果卸载，避免超大工具结果长期占用上下文。
- 双层长期记忆：每日事实流水与长期记忆文件，带 LLM 提炼和合并。
- 记忆查询、读取、保存、会话搜索工具。
- Workspace 知识、Skill Repository、MCP 工具接入。
- 工具权限和工具过滤。

但它不等于完整的企业知识平台：

- 业务 RAG 的数据接入、租户权限、文档生命周期、切片/向量检索/重排、引用证据和知识更新策略，仍要由产品侧提供或集成。
- Harness Memory 是 Agent 长期记忆机制，不应直接当作企业知识库。
- 业务资料在模型调用前程序式加载，与模型在执行中通过 Tools 主动查询，是两种互补方式。
- 记忆提炼与上下文压缩会调用模型，需要单独评估成本、触发频率、数据脱敏和失败补偿。
- 当前 AgentSquad 的 Master/Worker 显式关闭了 Harness 默认 Memory Tools / Hooks，并使用平台自有记忆、Skill 与上下文工具。这是可以成立的架构选择，但要避免重复建设 SDK 已经能稳定承担的通用能力。

### 4.7 模型选择与权限

Harness 支持 Agent/子 Agent 分别配置模型、生成参数、工具集和权限上下文。它可以承载“Master 用强模型、简单 Worker 用便宜模型”的运行方式。

平台仍需负责：

- 模型 Profile 的凭据管理、启停和用户/平台授权。
- 按任务复杂度选模的策略、成本预算与实际 token 结算。
- 模型切换是否影响同一会话恢复兼容性。
- Worker 能力版本和工具白名单不可由模型自行扩大。
- 子 Agent 不得因为继承或动态加载绕过父级 DENY 权限和租户隔离。

## 5. 当前 AgentSquad 分支实现审计

### 5.1 当前真实架构不是“完全自研 Agent 引擎”

目标分支 `feat/20261103_log_optimize` 当前 README 将职责划分为：

- GoalTask 是用户目标。
- WorkerJob 是有界委派节点。
- AgentScope Session / Turn / Run 是执行事实。
- 平台命令与 SDK journal 不原子，通过持久 Command、稳定 SDK submit key、原生日志幂等导出和未知结果核对跨越崩溃窗口。

当前代码也确实直接使用 HarnessAgent 与 AgentSession，而不是自己实现模型推理循环：

- MasterAgent 构建长生命周期 `HarnessAgent`。
- MasterAgent 的 `submit` 使用命令 ID 作为 AgentSession submit 幂等键。
- `stop` 调用原生 `AgentSession.interrupt`。
- `resume` 调用原生 `AgentSession.resume(turnId)`。
- `respond` 调用原生 `AgentSession.respond(requestId, answer)`。
- MasterAgent 通过 Session Log 读取已提交原生事件。
- WorkerFactory 为 Worker 构建独立 HarnessAgent，并返回对应 AgentSession。
- WorkerRecoveryService 根据原生收据检查 interrupted turn，在业务 guard 有效、计划仍有效且恢复次数允许时，创建原 turn 的 RESUME 意图。

### 5.2 当前显式关闭的原生能力

MasterAgent：

- `.disableSubagents()`
- `.disableMemoryTools()`
- `.disableMemoryHooks()`
- 同时关闭默认文件系统、Shell、Web 等工具，并通过平台自有 Toolkit 注册工具。

WorkerFactory：

- `.disableSubagents()`
- `.disableMemoryTools()`
- `.disableMemoryHooks()`
- Worker 使用受控工具集、模型 Profile、输入快照和输出 Schema。

这不是“忘记开启”这么简单：当前 Master 的系统提示词明确要求复杂任务通过 `create_goal_task` 建立产品目标，再通过 `publish_plan` 发布计划；Worker 明确禁止继续委派和结束父目标。说明当前架构刻意把多 Worker 调度放在产品控制面，而不是让 SDK 原生 SubAgent 自主调度。

这一取舍对复杂任务治理是合理的，但对客服聊天里的自主并行调查是否过于受限，还需要做专项决策。

### 5.3 当前 Worker 恢复已经使用原生 AgentSession

当前 WorkerFactory 构建 HarnessAgent 后，通过固定 Binding 对应的 `session(context)` 执行。WorkerRecoveryService 的职责说明及代码显示：

- 只对原命令的 SDK 收据进行检查。
- 只对 SDK 明确返回 interrupted 且仍满足业务条件的 turn 建立恢复命令。
- 重新锁定 Task / Plan / Job，核对计划版本、计划状态、取消标记、业务 guard token 和租约。
- 使用原 turnId 和 guard token 形成恢复幂等依据。
- 恢复次数受计划节点和 Worker Runtime Profile 双重限制。
- SDK 拒绝恢复时，平台把节点标记为恢复阻塞，而不是伪装成新 SUBMIT。
- 原生回合已经结束时，丢弃多余恢复命令，最终状态仍由原生日志投影裁决。

这说明平台并没有替代 AgentScope 的 checkpoint；平台负责决定何时、由谁、在什么业务条件下调用原生 resume。

### 5.4 已有恢复集成测试的意义与边界

分支存在 `WorkerCrashRecoveryIntegrationTest`。该测试用本地 HTTP 模型桩、独立数据库和两个 Spring 应用实例，模拟：

1. Worker 已提交候选结果，但原生回合尚未结束。
2. 封存旧 Session writer，并让业务 guard 租约过期。
3. 第二个 Spring 实例接管固定 Binding。
4. 平台核对原生收据，并为原 turn 写入 RESUME 命令。
5. 验证同一个 Worker 最终成功、SUBMIT 只有一条、RESUME 只有一条。

这是一项有价值的故障恢复集成测试，但不能外推为所有崩溃窗口、多用户隔离、真实模型流式、生产多副本和高并发已经验收。README 也明确说明当前仍为本地验证阶段，真实 SDK 跨进程接管、完整安全矩阵、真实模型流式、容量压测和灾备演练仍未验收。

### 5.5 当前架构的主要问题与待验证项

| 项目 | 当前观察 | 判断 |
|---|---|---|
| Agent 执行循环 | 使用 HarnessAgent | 正确，建议保留 |
| 长任务提交/恢复 | 使用 AgentSession + 原生 Session Log | 方向正确，继续验证故障边界 |
| Worker DAG | 平台自有 GoalTask / Plan / WorkerJob | 属于产品治理，不建议直接替换为 SubAgent |
| Master 聊天自主委派 | 禁用了原生 SubAgent，走平台目标/计划工具 | 可能限制聊天场景的轻量自主协作，需专项设计 |
| Worker 自主委派 | 禁用 SubAgent | 与 Worker 叶子执行约束一致 |
| Token 实时输出 | Master / Worker 模型均 `stream(false)` | 与产品体验目标冲突，优先 PoC |
| SSE 重连 | 平台从已提交日志投影并恢复 | 方向正确，需端到端验证游标与延迟 |
| Memory | Harness Hooks / Tools 关闭 | 平台自建能力需与 Harness Memory 做成本/能力对比 |
| RAG | 需要平台业务知识接入 | Harness 不是开箱即用的企业 RAG 平台 |
| 多副本 | 当前发布边界仍是单业务执行实例 | 不可将 SDK 支持分布式存储等同于项目已具备多副本生产能力 |

## 6. 架构方案比较

### A. 保留当前架构，逐步改造

保留 Master + 产品 Worker DAG + 平台命令/业务状态机；继续使用 HarnessAgent / AgentSession / Session Log。针对聊天场景研究是否增加一个受限的原生 SubAgent 能力入口。

优点：
- 现有业务治理、恢复测试和持久化基础可复用。
- 不需要把 GoalTask / WorkerJob / Plan / Binding 等业务语义塞回 AgentScope。
- 可以分阶段验证原生能力，风险最低。

风险：
- 目前 Master 的原生 SubAgent 禁用，需要解决聊天协作与复杂任务调度的边界。
- 当前自有工具与 SDK Memory/Skill/Workspace 能力可能存在重复。
- SSE 模型流式配置需要改进。

### B. 保留业务控制面，重建 Agent 运行接入层

保留用户、会话、任务、计划、Worker Definition、权限、预算、产物和事件投影；重新设计 AgentScope 接入，使 Master / Worker 统一通过明确的 AgentRuntime、SessionFactory 和事件适配入口使用 SDK 原生能力。

优点：
- 可减少历史接入方式和适配代码的复杂度。
- 更容易把 AgentScope API 与产品业务模型隔离。
- 可以统一流式、持久化、恢复和事件来源。

风险：
- 当前已有的 AgentSession 接入已较深入，重建需要明确收益。
- 如果没有先验证 SDK 行为，容易只是换一套抽象名称。
- 需要防止违反项目约定，额外造通用集成层。

### C. 推翻产品控制面，全部交给 AgentScope 原生能力

用原生 SubAgent / TaskRepository / Workspace / AgentSession 承担几乎全部多 Agent 与任务管理。

优点：
- 代码表面更接近 SDK 推荐用法。
- 可以减少部分平台自有调度逻辑。

主要问题：
- 原生 SubAgent 后台 task 与产品级 WorkerJob/DAG 不等价。
- Worker 能力版本、冻结输入、业务依赖、结果 Schema、预算、租户授权、最终答案验收等仍需补回。
- 本地 SubAgent 后台 Future 不具备进程退出后自动续跑语义。
- 需要重新实现大量已经存在的业务治理和恢复逻辑。

**阶段性判断：C 不推荐。** A 与 B 值得进一步比较，当前更偏向 A；若后续审计发现 AgentScope 接入层分散且难以维护，再评估 B。

## 7. 阶段性建议

### 7.1 不要把 SubAgent 与 Worker 合并成一个概念

建议保留两个层次：

- **SubAgent（AgentScope 原生）**：用于客服聊天场景中的轻量专家协作、并行分析、短期委派。它的产物是供 Master 继续判断的协作结果。
- **Worker（AgentSquad 产品能力）**：用于用户明确要求持续推进、需要 DAG、任务状态、能力版本、权限、预算、恢复和产物交付的复杂任务。

两者底层都可以使用 HarnessAgent / AgentSession，但生命周期、状态权威和授权入口不同。

这并不意味着必须马上同时启用两套委派能力。需要先设计 Master 的路由规则：何时直接回答、何时调用 SubAgent、何时创建 GoalTask。不能让一个问题同时走 SubAgent 与 Worker DAG，造成重复执行和双重终稿。

### 7.2 先把 AgentScope 原生执行能力用好

优先验证和收敛：

1. AgentSession 作为后台任务的唯一执行调度入口。
2. Session Log 作为执行事实和 checkpoint 的唯一原生来源。
3. AgentEvent 用于实时反馈，SessionEvent 用于可靠恢复与重放。
4. `stream(true)` 的真实模型流式输出与事件转发。
5. stop / steer / respond / resume 的边界与业务命令状态机。
6. 不确定工具结果核对，保证恢复不重复外部副作用。
7. Harness 的 Memory / Context / Skill / MCP 与平台现有实现的职责边界。

### 7.3 现阶段不做

- 不立即打开 Master 的原生 SubAgent。
- 不将现有 Worker DAG 替换为原生 SubAgent。
- 不重写当前 AgentSession / Session Log 持久化接入。
- 不升级 AgentScope 版本。
- 不以 SDK 文档宣传代替固定版本 PoC。
- 不把本地测试结果宣称为生产多副本能力。

## 8. 下一轮必须完成的验证清单

| 优先级 | 验证项 | 方法 | 通过标准 |
|---|---|---|---|
| P0 | 固定 BETA1 模型流式 | 当前模型服务，`stream(true)`，采集 AgentEvent | 文本增量和工具事件真实产生，异常可识别 |
| P0 | AgentSession 后台事件观察 | 提交任务后断开/重连观察端 | 观察者退出不取消任务；可按已提交日志补齐 |
| P0 | Worker 进程退出恢复 | 保留现有测试并补充不同故障窗口 | 同一 turn 恢复，不重复 SUBMIT，不重复外部副作用 |
| P0 | Master steer / interrupt / resume | 运行中插话、停止、恢复并观察事件 | 不串错 turn，不丢输入，不错误宣告停止 |
| P1 | SubAgent 动态创建 | 固定声明、动态 spec、agent_generate 分别测试 | 能明确区分声明生成、实例创建、任务执行和持久会话 |
| P1 | SubAgent 进程崩溃 | 本地后台子任务中途杀进程 | 明确任务记录状态、是否可恢复、不能恢复时的终态 |
| P1 | SubAgent 结果反馈 Master | 后台并行任务完成与父会话空闲/运行中两种场景 | 结果通知可靠且不会重复注入 |
| P1 | 子 Agent 权限隔离 | 多用户、不同 Worker 能力、工具白名单与 DENY | 不可越权，用户上下文不串用 |
| P1 | Skill / MCP / RAG 接入 | 预加载、主动工具检索、结果沉淀三个阶段 | 权限、来源、时延和 token 成本可观察 |
| P2 | 记忆能力取舍 | Harness Memory 与平台自建记忆做对照 | 明确数据模型、触发策略、成本与数据治理 |
| P2 | 模型动态选择 | Master 与不同 Worker Profile | 模型切换不破坏会话恢复和预算控制 |

## 9. 当前可以作出的决策

| 决策问题 | 阶段性结论 |
|---|---|
| AgentScope Java 能否作为产品 Agent 执行内核？ | **可以，值得继续采用 HarnessAgent / AgentSession。** |
| 是否需要推翻整个项目？ | **目前没有证据支持。** |
| 是否应该用原生 SubAgent 替换 Worker？ | **不应该直接替换。两者职责不同。** |
| 当前 Worker 是否完全脱离 AgentScope？ | **不是，Worker 已使用 HarnessAgent 与 AgentSession，并依赖原生日志恢复。** |
| 当前是否充分使用 SDK 原生能力？ | **还没有。Master/Worker 都禁用了 SubAgent、Memory Hooks/Tools，模型流式也关闭。** |
| 最大的近期技术缺口？ | **实时模型流式、SubAgent 与 Worker 的边界、跨进程恢复完整性验证。** |
| 下一步是否立即改代码？ | **否。先完成 P0/P1 PoC，再决定 A 或 B。** |

## 10. 参考源码与文档

### AgentSquad 当前分支

- `server/pom.xml`：固定 AgentScope `2.1.0-BETA1`。
- `server/agent-squad-server/pom.xml`：引入 `agentscope-core`、`agentscope-harness`、JDBC 扩展和 OpenAI 模型扩展。
- `server/agent-squad-server/src/main/java/com/agentsquad/squad/agent/MasterAgent.java`：Master HarnessAgent、Session 提交、停止、恢复、答复、日志读取。
- `server/agent-squad-server/src/main/java/com/agentsquad/squad/integration/agentscope/WorkerFactory.java`：Worker HarnessAgent 与 AgentSession 创建、能力隔离和模型配置。
- `server/agent-squad-server/src/main/java/com/agentsquad/squad/runtime/service/WorkerRecoveryService.java`：Worker 原生回合恢复协调。
- `server/agent-squad-server/src/test/java/com/agentsquad/squad/runtime/WorkerCrashRecoveryIntegrationTest.java`：双 Spring 实例模拟 Worker writer 崩溃接管。
- `openspec/config.yaml`：固定评估候选 commit、运行权威与 SDK 集成约束。
- `README.md`：当前功能、部署边界和未验收事项。

### AgentScope Java 固定版本源码

以下链接固定到 commit `5451c73abe47053340683a1cc6d67b290b056648`，用于本轮版本审计：

- [AgentSession 实现](https://github.com/agentscope-ai/agentscope-java/blob/5451c73abe47053340683a1cc6d67b290b056648/agentscope-harness/src/main/java/io/agentscope/harness/agent/session/AgentSession.java)
- [HarnessAgent 实现](https://github.com/agentscope-ai/agentscope-java/blob/5451c73abe47053340683a1cc6d67b290b056648/agentscope-harness/src/main/java/io/agentscope/harness/agent/HarnessAgent.java)
- [SubagentsMiddleware 实现](https://github.com/agentscope-ai/agentscope-java/blob/5451c73abe47053340683a1cc6d67b290b056648/agentscope-harness/src/main/java/io/agentscope/harness/agent/middleware/SubagentsMiddleware.java)
- [WorkspaceTaskRepository 实现](https://github.com/agentscope-ai/agentscope-java/blob/5451c73abe47053340683a1cc6d67b290b056648/agentscope-harness/src/main/java/io/agentscope/harness/agent/subagent/task/WorkspaceTaskRepository.java)
- [SubAgent 文档](https://github.com/agentscope-ai/agentscope-java/blob/5451c73abe47053340683a1cc6d67b290b056648/docs/v2/zh/docs/harness/subagent.md)
- [会话操作、事件与恢复文档](https://github.com/agentscope-ai/agentscope-java/blob/5451c73abe47053340683a1cc6d67b290b056648/docs/v2/zh/docs/harness/session-log.md)
- [Harness 架构文档](https://github.com/agentscope-ai/agentscope-java/blob/5451c73abe47053340683a1cc6d67b290b056648/docs/v2/zh/docs/harness/architecture.md)

### 版本差异提醒

AgentScope 上游 main 在本轮检索时已有更新的 SubAgent、Session Log、Memory 与 Service 文档。它们可用于发现后续演进方向，但不属于项目当前固定依赖的直接能力证据。任何准备接入的新 API，必须先对照固定 commit 的源码、依赖兼容性和实际测试结果。


---

# 11. 上游 main README 与最新源码补充审计（2026-11-10）

本节根据用户提供的 AgentScope Java 中文 README，进一步对照 GitHub `main` 分支源码和文档。它描述的是**上游当前状态**，不能直接等同于 AgentSquad 当前固定依赖的能力。

## 11.1 一个需要立即修正的版本认知

上游 README 当前展示：

- Maven 快速开始示例使用 `agentscope-harness:2.0.3`。
- 同一仓库根 `pom.xml` 的开发版本是 `2.1.0-SNAPSHOT`。
- README 新闻区列出 `v2.0.0 GA`、RC 版本及 AgentScope Service 的后续演进。
- README 将 AgentScope 2.0 定位为面向企业级、分布式、生产环境的 Agent Harness。

而 AgentSquad 当前分支 POM 仍固定使用 `2.1.0-BETA1`（以项目 POM 为准）。因此，上一节针对固定 BETA1 的源码结论仍然有效，但不能拿它代表上游 main 的全部能力；反过来，也不能把 main 新增能力直接当成项目现有依赖可用。

**后续所有研究必须同时标记：项目实际依赖版本、上游稳定发布版本、上游 main 开发版本。** 在未做升级评估前，不修改项目依赖版本。

## 11.2 README 给出的产品能力地图

README 明确把 2.0 的核心能力归为三类：

1. Harness 工程化：长期任务执行、Skill、分层记忆、SubAgent、上下文压缩、Plan Mode。
2. 企业级分布式部署：多租户隔离、沙箱、权限管控、会话恢复。
3. 底层运行机制：类型化消息、事件流、Middleware、HITL。

它还明确区分：
- 普通问答、多轮对话、工作流节点：从 `call()` / `streamEvents()` 开始。
- 需要页面关闭后继续执行、任务排队或中断后续做：再引入 `AgentSession`。

这个建议很重要：**不应该把每条普通聊天消息都强制包装成一个长任务 Session。** 应按是否需要后台持续执行、排队、steer/interrupt/resume 决定是否使用 AgentSession。

## 11.3 当前 main 的 SubAgent 已明显增强

与此前固定 BETA1 审计相比，main 文档明确描述了更多产品交互能力：

- 三种声明方式：内置 `general-purpose`、工作区 Markdown spec、编程式声明；另有自定义工厂和远程 Agent。
- 同步委派与后台委派：`timeout_seconds > 0` 同步等待；`timeout_seconds = 0` 后台执行。
- 同一轮多个同步 SubAgent 调用可借助 Toolkit 并行执行，形成 fan-out / fan-in。
- 后台任务完成后，框架会在主 Agent 下一轮推理前自动注入结果提醒；不要求 Master 轮询。
- `agent_send` 可以向既有子 Agent 追加消息；`persistSession(true)` 可以按用户、父会话、Agent 类型和 label 复用子 Agent 会话。
- 可以通过 `expose_to_user` 把子 Agent 暴露为用户可直接对话的分支入口，并通过事件流通知前端。
- 支持为子 Agent 单独配置模型、工具、工作区隔离模式、步骤上限和上下文压缩。
- 文档特别提醒：有依赖的任务不能简单并行派发，应先明确依赖图；无依赖任务适合并行。

这意味着我们之前把 SubAgent 主要视作“Master 内部轻量专家工具”的描述，需要扩展：**上游 main 已把 SubAgent 做成支持后台运行、跨轮复用、事件回传、用户直接交互的协作单元。** 但它仍然不是自动等价于 AgentSquad 的业务 GoalTask / Worker DAG。

### 对 AgentSquad 的直接启示

未来客服聊天场景可以认真考虑使用原生 SubAgent 处理：
- 多专家并行排查；
- Master 等待或后台收集调查结果；
- 用户需要时直接进入某个专家分支继续交流；
- 跨轮持续协作的专家角色。

而复杂任务工作台仍需要平台控制：
- 任务目标和计划版本；
- 依赖关系和节点调度；
- Worker Definition 版本、输入输出约束；
- 用户/租户授权、预算和恢复策略；
- 产物验收及最终交付。

这支持“聊天自主协作与复杂任务执行共用运行底座、保留不同控制语义”的方向。

## 11.4 最新事件体系把三种事件流分得更清楚

main 的消息与事件文档明确区分：

1. **AgentEvent**：当前运行产生的实时强类型事件，例如文本增量、工具调用、工具进度、HITL、SubAgent 暴露等。
2. **SessionEvent**：已经提交到原生日志的不可变执行事实，服务于历史读取和恢复。
3. **Service 公共 SSE**：Service 将底层执行信息投影为面向调用方的 turn / item / action 事件。

三者格式和游标独立。特别需要注意：
- `streamEvents()` 用来启动执行并实时观察，不提供持久事件游标。
- 不能在 SSE 断线后重新调用 `streamEvents()` 来补齐断线期间的事件，因为那会重新执行请求。
- 页面恢复应读取持久日志 / snapshot，再按持久 cursor 续读。
- 实时事件抵达不代表它已经提交；AgentEvent ID、原生日志 seq 和 Service SSE cursor 不是同一个 ID。
- 一次逻辑 turn 可以包含多次恢复执行；单次 AgentEndEvent 不代表逻辑 turn 的完整生命周期已经结束。

这进一步验证了我们此前的核心判断：**模型实时输出、后台执行、持久事件、前端 SSE 连接必须分层设计。**

## 11.5 分布式能力：上游已有方案，但要核对项目使用方式

README 声称 2.0 支持 Redis / MySQL / PostgreSQL / OSS / COS 等后端的分布式 Session 与 Memory 管理，以及跨副本 Session 恢复；还强调 Harness 面向无状态水平扩展。

这对项目有很大价值，但不能据此直接认定 AgentSquad 已具备多副本执行能力。必须进一步核对：
- 当前项目的 SessionLogStore / AgentStateStore / Workspace / Memory 分别使用什么后端；
- 多副本下同一 session 的并发写入、锁或 CAS、事件顺序和恢复接管如何保证；
- 子 Agent 的实例路由和后台任务路由是否跨副本；
- 运行节点宕机后，谁负责重新调度以及如何避免重复副作用；
- 项目是否仍依赖单业务执行实例。

**SDK 有分布式存储实现，不等于应用已经完成分布式部署设计。**

## 11.6 AgentScope Service：值得调研，但不是现在就替换嵌入式 SDK

README 对 Service 的定位很清晰：Agent 控制面与 Dashboard，面向观测、注册和编排；可以托管 Harness、接入已有 Agent 应用，或复用 Coding Agent；通过 Endpoint / Invocation 提供后台任务、交互式助手或流程节点。

它强调应用仍保留自己的界面、数据授权和验收规则。这与 AgentSquad 的产品定位并不冲突。

需要单独调研的不是“要不要直接改成 Service”，而是：
- Service 的 Invocation / Snapshot / Event Log 是否能覆盖我们现有的任务执行与事件查询；
- Service 的 Team / Workflow 与我们产品 Worker DAG 的边界；
- 自托管 Harness 和接入已有 Agent 应用的模式；
- Service 是否能作为未来的可选控制面，而不是当前必须依赖的运行内核。

当前阶段仍建议维持嵌入式 Harness，先做能力对照，不引入 Service 依赖。

## 11.7 新增的研究结论

| 议题 | 根据上游 main 更新后的判断 |
|---|---|
| 普通聊天是否需要 AgentSession | 不一定；普通问答优先 `call/streamEvents`，长时间后台执行再用 AgentSession |
| 原生 SubAgent 的定位 | 不只是短期内部工具，已支持后台任务、持久会话、自动反向通知和用户分支对话 |
| SubAgent 能否替换 Worker DAG | 仍不能直接替换；产品业务计划、权限、预算和验收仍需控制面 |
| SSE 断线恢复 | 不能重新调用 streamEvents；必须基于持久日志和 snapshot/cursor |
| 分布式能力 | 上游已有多种后端与跨副本能力，但要核验项目当前实际配置和应用级接管逻辑 |
| AgentScope Service | 值得做专项对照研究，不建议当前直接替换嵌入式 Harness |

## 11.8 本节上游源码与文档

- [上游中文 README（main）](https://github.com/agentscope-ai/agentscope-java/blob/main/README_zh.md)
- [上游 main 根 POM](https://github.com/agentscope-ai/agentscope-java/blob/main/pom.xml)
- [SubAgent 文档（main）](https://github.com/agentscope-ai/agentscope-java/blob/main/docs/v2/zh/docs/harness/subagent.md)
- [消息与事件文档（main）](https://github.com/agentscope-ai/agentscope-java/blob/main/docs/v2/zh/docs/building-blocks/message-and-event.md)
- [生产部署文档（main）](https://github.com/agentscope-ai/agentscope-java/blob/main/docs/v2/zh/docs/others/going-to-production.md)
- [AgentScope Chat 可恢复聊天示例](https://github.com/agentscope-ai/agentscope-java/blob/main/agentscope-examples/agents/agentscope-chat/README_zh.md)


---

# 12. SubAgent 完整生命周期与 AgentSquad 当前实现对照（2026-11-10）

本节进一步阅读上游固定 commit 的 Java 实现，而不只依据 README / 文档；同时读取 AgentSquad `feat/20261103_log_optimize` 分支当前代码。研究范围聚焦：创建、执行、后台任务记录、完成通知、Master 接续、用户交互、持久化和进程恢复。

## 12.1 本轮审计基线

| 项目 | 本轮确认值 |
|---|---|
| AgentSquad 分支 | `feat/20261103_log_optimize` |
| AgentSquad AgentScope 依赖 | `2.1.0-BETA1`，来自 `server/pom.xml` |
| AgentScope 上游源码 | `main` 搜索命中源码对应 commit `9ab7a4fab7bf610fd0483f599b374fb0930be98a` |
| 上游 Harness 结构 | `agentscope-harness`，同时有 `AgentSession` 与 SubAgent TaskRepository 两套不同机制 |
| 本轮限制 | 读取源码和文档；没有修改 AgentSquad 业务代码，也没有运行项目测试 |

**注意：** 本节对上游实现细节的描述以链接所指 commit 为证据；AgentSquad 代码结论以本分支文件为证据。不能将上游 main 的实现直接视为 `2.1.0-BETA1` 已包含的 API。

## 12.2 先区分两条不同的后台执行路径

AgentScope Harness 内存在两种容易被混淆的后台执行机制：

### A. AgentSession：管理一个会话中的任务

`AgentSession` 处理的是用户与某个 Agent 会话之间的任务生命周期：

- `submit`：接收一项新任务，先持久化命令，再安排执行；支持幂等提交。
- `steer`：给当前运行中的任务补充要求，关联原 turn。
- `inject`：保存后续推理可用的材料，不主动启动任务。
- `interrupt`：请求当前执行在协作检查点停止。
- `respond`：回答挂起的人工确认 / 外部执行请求。
- `resume`：从已保存 checkpoint 继续最新未完成任务。
- `transcript / pending / inspect / tasks`：读取完整对话、待办、恢复检查和任务状态。

其核心特点是：**AgentSession 自己拥有后台执行订阅，不依赖 HTTP/SSE 观察连接存活。** 前端断开只影响观察，不会自动取消后台任务。

源码中 `AgentSession.start()` 启动周期 dispatcher（100ms）；dispatcher 从持久 inbox 中选取尚未启动的命令，检查 Session Log 的 owner / lease，再调用 `agent.prepareRun(...)` 启动执行。执行由 `AgentRun` 驱动，结束后释放本地 active 引用。应用重启后重新取得同一身份的 session 并调用 `start()`，可以重新处理持久队列。

它不是单纯的“把 Mono 放进后台线程”，而是“持久命令收件箱 + 日志状态 + 执行租约 + 后台调度器”的组合。

### B. SubAgent TaskRepository：管理 Master 委派出去的子任务

`agent_spawn` 的后台路径管理的是 Master 委派出去的子 Agent 任务：

1. Master 通过 `agent_spawn` 指定子 Agent 类型和任务。
2. `AgentSpawnTool` 选择同步执行、超时后转后台，或直接后台执行。
3. 后台任务交给 `TaskRepository.putTask(...)`。
4. Repository 持久化 `TaskRecord`，同时可能在当前节点持有一个 `CompletableFuture`。
5. 子 Agent 完成后更新任务状态，触发完成回调。
6. 回调可以把结果提醒写入父会话 inbox，并发送 wakeup 信号。
7. Master 下一轮推理读取提醒，整合子任务结果。

这里的 `task_id` 表示一次后台任务；`agent_key` 表示可继续通信的子 Agent 实例句柄。二者不是同一个东西。子 Agent 实例复用由 `persistSession` / manager 负责；后台任务状态与结果由 TaskRepository 负责。

**因此：AgentSession 不会自动替代 SubAgent TaskRepository；TaskRepository 也不负责用户主会话的完整任务队列、steer、HITL 和 checkpoint 恢复。**

## 12.3 上游 SubAgent 的实际执行方式

### 同步执行

`timeout_seconds > 0` 时，Master 在工具调用中等待子 Agent 结果。默认等待 30 秒，最大 600 秒。若普通同步调用超时，当前执行可以被 promote 为后台任务，返回 `task_id`，子任务继续运行。

同一轮多个同步 SubAgent 工具调用，Toolkit 默认并行执行，形成 fan-out / fan-in：一批工具结果全部返回后，Master 才进入下一轮推理。

### 后台执行

`timeout_seconds = 0` 时，工具尽快返回后台任务标识，Master 可以继续处理独立工作。任务状态可以通过 `task_output`、`task_list` 读取；需要等一组结果时用 `wait_async_results(task_ids=...)`，而不是误用默认 inbox-any 模式。

任务完成后是否自动回到 Master，取决于接入的通知路径。上游 `SubagentsMiddleware.wireMessageBus(...)` 的源码显示：完成回调会把结果摘要写入父 session inbox，并 enqueue wakeup，交由 `WakeupDispatcher` 重新触发空闲父会话。文档中的 system-reminder 是结果交付给 Master 的提示形式，不等同于前端 SSE 事件。

### 实时子 Agent 事件

`AgentSpawnTool` 的源码说明：如果当前 Reactor Context 存在 `SubagentEventBus`，子 Agent 的事件会被转发到父事件流，形成层级展平的实时事件；普通 `call()` 模式没有事件总线时，回退到非流式执行路径。

这解决的是“子 Agent 执行时的实时事件可见性”，但它不等同于持久事件日志，也不能单独解决断线后的补发。

## 12.4 上游后台任务的持久化和跨节点限制

上游 `WorkspaceTaskRepository` 的源码注释明确写出了分布式语义：

- 共享 Workspace 存储是 TaskRecord 的权威状态来源。
- 每个节点只在本地内存保存本节点运行的 `BackgroundTask` / Future。
- 任务执行 sticky 到发起节点；其它节点可以读取持久任务状态，但没有本地 Future 时，阻塞等待会退化为读取最新持久状态。
- 取消通过共享记录写入 cancelRequested，由发起节点检查并尽力取消本地执行。
- 远程任务通过 Agent Protocol HTTP transport 执行，记录远程地址等信息，支持跨节点查询与恢复远端任务状态。
- 本地任务依赖心跳和 orphan sweep 识别失联执行；默认心跳 30 秒、超过 10 分钟无心跳可被视为 orphan，sweep 周期 5 分钟。
- 多副本 sweep 可以用 StoreBackedPeriodicGate 去重；若部署了 Control Plane，优先使用由 Control Plane 托管的 TaskRepository。

这意味着不能把“TaskRecord 在共享存储中”误读成“本地 Java Future 可以在任意节点恢复”。**共享任务记录解决状态可见性；真正的执行接管还需要远程执行协议、租约/心跳、orphan 处理或集中控制面。**

上游还提供 `ControlPlaneTaskRepository`，任务状态由 Control Plane 持久化，本地节点保留当前运行的 Future，定期向 Control Plane heartbeat；它不是普通 WorkspaceTaskRepository 的简单换皮，而是将任务权威状态和维护职责放到控制面。

## 12.5 AgentSession 的恢复边界

源码与官方 session-log 文档共同确认：

- 命令先持久化，执行由后台 dispatcher 发起。
- `AgentSession.await()` 只观察任务状态，不负责启动、重试或取消执行。
- `interrupt()` 是协作式停止，不是强制杀死任意 Java 线程。
- `resume()` 恢复 checkpoint，不会恢复原来的 Java 线程、HTTP 请求或工具内部进度。
- 对于进程崩溃时“工具是否已经产生外部副作用”不确定的情况，框架会要求通过 `inspect().uncertainToolCalls()` 核实真实业务系统结果，再 `reconcileToolOutcomes`，之后才能继续。
- 进程重启后用相同 agentId / userId / sessionId / 日志后端重新构建 Agent，再调用 `session.start()` 恢复队列调度；已中断任务由应用明确决定是否 resume。
- 日志 head 中的 owner / lease 用于执行归属和接管判断；旧执行的写入租约未释放或到期时，新执行不能随意抢占。

这套设计没有承诺“任何工具执行到一半都能从字节级现场恢复”。它恢复的是 Agent 的已保存状态；工具的外部副作用必须具备幂等、查询核对或补偿能力。

## 12.6 AgentSquad 当前代码的真实情况

本轮直接读取了 AgentSquad 当前分支的 `MasterAgent.java`、`WorkerFactory.java`、`RuntimeSessionProvider.java` 和两个 POM。

### Master 当前明确禁用了原生 SubAgent

`MasterAgent.initialize()` 中构建 HarnessAgent 时显式调用：

```java
.disableSubagents()
```

并且 Master 的系统提示词明确要求：
- 普通交流、可直接交付的问题直接回答，不为每条消息创建目标；
- 用户明确要求 Worker、多任务并行或委派协作时，必须创建 GoalTask；
- 需要独立持续推进的工作由平台创建 GoalTask；
- 通过平台发布计划、节点依赖和 Worker 结果验收完成复杂任务。

这说明当前产品不是“忘了接入 SubAgent”，而是**有意识地把复杂任务委派交给了自己的业务控制面**。

### Worker 也明确禁用了原生 SubAgent

`WorkerFactory.create()` 构建每个叶子 Worker 时也调用 `.disableSubagents()`。Worker 提示词限定只处理当前节点，不发布计划、不结束父目标、不再委派其他 Agent。

因此当前结构是：

- Master：理解用户意图、决定是否建立 GoalTask、发布任务计划、最终整合并回复。
- GoalTask / Plan：平台持久化的业务目标、计划和节点依赖。
- Worker：按已发布 Definition 和输入快照执行单个节点，提交结构化结果。
- 平台调度：管理节点领取、状态、重试/取消、结果验收和最终汇合。
- AgentScope Harness：提供 Master / Worker 的模型推理、工具执行、Session Log、checkpoint 和底层运行能力。

### AgentSquad 已经使用 AgentSession，而不是完全自建主会话执行器

`MasterAgent` 通过 `sessionFor(...)` 获取原生 `AgentSession`；`submit` 用平台 commandId 作为幂等键，`stop` 调用原生 `interrupt`，`resume` 调用原生 `resume`，`respond` 调用原生 `respond`。

`WorkerFactory.sessionFor()` 也为 Worker 创建原生 AgentSession，并把业务 Binding、guard token、技能授权等上下文注入 RuntimeContext。

`RuntimeSessionProvider` 用 JDBC DistributedStore 的 baseStore 包装出 SessionLogStore，日志和恢复数据落在数据库后端。它还提供只读的 committed log 读取入口，不因页面读取启动 Agent、领取 Job 或续租。

### 但 AgentSquad 的执行接管仍有自己的业务层

`WorkerFactory` 使用本进程 `ConcurrentHashMap<bindingId, WorkerAgentHandleBO>` 缓存活跃 Worker Agent；首次创建会校验 Binding / Definition / 上下文快照，并通过 `dispatchService.claim(...)` 领取业务 guard；后续调用续租 guard。进程退出时关闭本地 Agent，但注释明确持久 Journal 和固定 Binding 保留供接管。

这意味着 AgentScope Session Log 负责 Agent 执行状态的恢复，AgentSquad 的业务 guard / Binding / 调度负责 Worker 执行权的恢复与业务一致性。两者是互补关系。

## 12.7 两套体系对照

| 维度 | AgentScope 原生 SubAgent | AgentScope AgentSession | AgentSquad 当前 GoalTask / Worker |
|---|---|---|---|
| 主要对象 | Master 委派的子 Agent 任务 | 一个 Agent 会话里的用户任务 | 业务目标、计划和节点 |
| 创建入口 | `agent_spawn` / `agent_send` | `session.submit` | `create_goal_task` 等业务工具 |
| 并行方式 | 同一轮工具并行、后台子任务 | 同一 Session 命令排队；不同 Session 可并发 | 计划节点按依赖关系调度 |
| 完成回传 | TaskRepository 回调 / inbox / wakeup | Session Log / task 状态 / 事件 | Worker 结构化结果 + 平台汇合 |
| 子 Agent 持续对话 | `persistSession` + `agent_send` | 不是它的职责 | Worker 按 Binding / Session 固定身份运行 |
| 用户直接进入子 Agent | 可选 expose-to-user + Gateway | 用户本来就在主 Session | 当前产品未把 Worker 作为聊天入口 |
| 中断与恢复 | 任务取消、远程任务状态恢复；不是完整 AgentSession checkpoint 语义 | 原生 interrupt / resume / reconcile | 业务取消/接管 + 原生 Session 恢复 |
| 依赖 DAG | 不提供完整业务 DAG | 不提供业务 DAG | 平台负责 |
| 业务权限与预算 | 子 Agent 工具/权限配置 | Session / RuntimeContext 层机制 | 平台 Definition、授权、模型预算与 guard |
| 结果验收 | Master 自己判断 | 不负责业务验收 | 平台 schema、验收和最终交付 |

## 12.8 对产品设计的影响

当前产品目标分为两个体验入口，但底层能力尽可能共用：

### 客服聊天入口

建议将来研究并接入原生 SubAgent，但不应直接放开所有工具：

- Master 仍然是用户唯一默认对话入口。
- 对于需要多视角分析、并行调查、专业知识补充的问题，Master 可以自主创建 SubAgent。
- SubAgent 结果通过原生事件和任务完成通知回到 Master。
- 只有明确需要用户独立跟某个专家继续沟通时，才考虑 expose-to-user；默认不要把内部 SubAgent 暴露成多个聊天对象。
- 主会话的长任务后台执行、排队、用户插话、中断恢复，继续由 AgentSession 管理。
- 前端统一展示事件，但必须区分主 Agent、子 Agent、工具、任务状态和持久日志事实。

### 复杂任务工作台

保留 AgentSquad 当前 GoalTask / Plan / Worker DAG：

- 计划与依赖必须持久化且可审计。
- Worker 的输入输出必须符合固定契约。
- 任务重试、取消、超时、预算和幂等由平台统一控制。
- Worker 不应因模型自主判断而越权创建其他 Worker 或修改父目标。
- AgentScope 的 Harness / Session / Middleware / Tool 能力作为运行内核，而非取代业务控制面。

## 12.9 当前最值得做的验证，不是立刻重构

上游 main 的 SubAgent 实现值得接入 PoC，但先做一个独立分支 / 示例，不触碰现有 GoalTask 业务链路。验证顺序建议：

1. **版本确认**：检查 `2.1.0-BETA1` 是否包含所需的 SubAgent 声明、后台 TaskRepository、事件总线和 wakeup API。若没有，逐项列出升级差异，不直接升级。
2. **最小同步委派**：Master 以两个只读工具专家并行执行，验证事件 source / replyId / toolCallId 的关联与最终汇总。
3. **后台完成通知**：一个短任务 + 一个长任务；Master 先完成当前回复，子任务结束后验证 inbox / wakeup 是否能安全唤醒原会话。
4. **用户插话**：任务运行时追加要求，验证 steer 的语义是否适合“对主 Agent 的新消息”以及是否会误改子 Agent 当前任务。
5. **中断和恢复**：分别模拟模型调用中断、工具执行中断、进程退出；核验任务状态、checkpoint、lease、uncertain tool outcomes。
6. **多副本**：两个应用节点同时启动；在节点 A 创建子任务，在节点 B 查询、取消和接收完成通知；杀掉 A 后验证是否能重新执行或明确失败，而不是任务永久挂起。
7. **权限隔离**：验证子 Agent 是否继承父权限、是否能获得未授权工具、用户/租户上下文是否贯穿事件与任务记录。
8. **成本与资源**：限制并发子 Agent 数量、每个子 Agent 的模型预算、最大深度、最大运行时间和后台任务积压。

PoC 的通过标准必须包含“不会重复执行外部副作用”和“不会丢失任务完成通知”，不能只以模型最终回答正确作为通过条件。

## 12.10 本轮阶段性结论

1. **当前 AgentSquad 没有使用原生 SubAgent 是明确的架构选择。** Master 和 Worker 都显式禁用了它；复杂任务由 GoalTask / Worker DAG 管理。
2. **AgentSquad 已经使用原生 AgentSession。** 主会话和 Worker Session 的任务提交、中断、答复与恢复已接入 Harness；不应把这些能力误判为尚未实现。
3. **原生 SubAgent 值得用于客服聊天中的自主专家协作。** 但它不应该取代复杂任务工作台的业务 DAG。
4. **AgentScope 的“持久任务状态”不自动等于“跨节点恢复执行”。** 本地 Future sticky 到发起节点，分布式恢复依赖具体 TaskRepository、远程协议、心跳 / orphan 机制或 Control Plane。
5. **下一步应先做版本差异和小型 PoC，再决定是否引入 SubAgent。** 不建议此时推翻现有架构或把 GoalTask 改造成 SubAgent。

## 12.11 本节源码与文档索引

### AgentScope 上游（源码 commit `9ab7a4fab7bf610fd0483f599b374fb0930be98a`）

- [AgentSession.java](https://github.com/agentscope-ai/agentscope-java/blob/9ab7a4fab7bf610fd0483f599b374fb0930be98a/agentscope-harness/src/main/java/io/agentscope/harness/agent/session/AgentSession.java)
- [AgentSpawnTool.java](https://github.com/agentscope-ai/agentscope-java/blob/9ab7a4fab7bf610fd0483f599b374fb0930be98a/agentscope-harness/src/main/java/io/agentscope/harness/agent/tool/AgentSpawnTool.java)
- [SubagentsMiddleware.java](https://github.com/agentscope-ai/agentscope-java/blob/9ab7a4fab7bf610fd0483f599b374fb0930be98a/agentscope-harness/src/main/java/io/agentscope/harness/agent/middleware/SubagentsMiddleware.java)
- [WorkspaceTaskRepository.java](https://github.com/agentscope-ai/agentscope-java/blob/9ab7a4fab7bf610fd0483f599b374fb0930be98a/agentscope-harness/src/main/java/io/agentscope/harness/agent/subagent/task/WorkspaceTaskRepository.java)
- [ControlPlaneTaskRepository.java](https://github.com/agentscope-ai/agentscope-java/blob/9ab7a4fab7bf610fd0483f599b374fb0930be98a/agentscope-extensions/agentscope-extensions-controlplane/src/main/java/io/agentscope/extensions/controlplane/store/ControlPlaneTaskRepository.java)
- [AgentScope Session Log 文档](https://github.com/agentscope-ai/agentscope-java/blob/main/docs/v2/zh/docs/harness/session-log.md)
- [AgentScope SubAgent 文档](https://github.com/agentscope-ai/agentscope-java/blob/main/docs/v2/zh/docs/harness/subagent.md)

### AgentSquad 当前分支

- [MasterAgent.java](https://github.com/liumeng-cn/agent-squad/blob/feat/20261103_log_optimize/server/agent-squad-server/src/main/java/com/agentsquad/squad/agent/MasterAgent.java)
- [WorkerFactory.java](https://github.com/liumeng-cn/agent-squad/blob/feat/20261103_log_optimize/server/agent-squad-server/src/main/java/com/agentsquad/squad/agent/WorkerFactory.java)
- [RuntimeSessionProvider.java](https://github.com/liumeng-cn/agent-squad/blob/feat/20261103_log_optimize/server/agent-squad-server/src/main/java/com/agentsquad/squad/agent/session/RuntimeSessionProvider.java)
- [server/pom.xml](https://github.com/liumeng-cn/agent-squad/blob/feat/20261103_log_optimize/server/pom.xml)
