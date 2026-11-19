# Agent Squad V2 接入核对

研究当前分支 `feat/20261111_squad_v2`，Commit `7dc3c5fecb42f4d3240605581607bec8ceb83dee`；保留现有 ChatAgentProperties.java、tools/services.py 修改。SDK 源码依据为 2.1.0-BETA1 Source Jar。下文 SDK 路径相对各 artifact 解包根，项目路径相对仓库根。

## 真实装配与入口

【源码确认】`server/v2-server/src/main/java/com/agentsquad/squad/v2/config/ChatAgentConfiguration.java:40-68` 的 `chatAgent()` 在 enabled=true 时创建单个 HarnessAgent，只显式传 agentId、模型、Toolkit、workspace、子 Agent 与 tracing middleware；没有设置 SessionLogStore、AgentStateStore、SnapshotStore、BaseStore 或 DistributedStore。

【源码确认】`server/v2-server/src/main/resources/application-local.yml:19-22` 指定 agent-id=squad-master、workspace=/Users/liumeng/work/pWork/workspace。该位置是配置事实，未扫描实际用户文件，也未读取模型凭据。

【源码确认】`server/v2-server/src/main/java/com/agentsquad/squad/v2/service/ChatRuntimeService.java:31-36` 的 open()将服务端 userId 与 sessionId 放入 RuntimeContext，交给 `HarnessAgent.session()`；read():46-50 对 demo 使用 fixture，对其他会话使用 converter.snapshot 原生句柄。`server/v2-server/src/main/java/com/agentsquad/squad/v2/service/ChatService.java:32-64` 的 send() 当前已使用 steer、SessionTurns.read、SessionRecovery.cancelPending、submit；interrupt():72-74 与 resume():82-85 走原生句柄。synchronized(session) 是本 JVM 协调，不能当作跨节点锁。

| 存储能力 | 官方接口 | 官方默认实现 | V2 当前实现 | 实际位置 | 持久化 |
|---|---|---|---|---|---|
| 原生会话历史 | SessionLogStore | WorkspaceSessionLogStore | 未覆盖默认 | workspace 用户目录下 .agentscope-runtime | 文件持久化 |
| 原子 Journal 对象 | AtomicSessionStorage | 由 Filesystem.sessionStorage 决定 | LocalSessionStorage | 同上 | 文件持久化 |
| Inbox | SessionInbox | Journal 驱动 | submit/steer 原生入口 | Journal inbox 对象 | 原生持久化语义；当前未重新实测 |
| 普通 Workspace 文件 | AbstractFilesystem | 默认 LocalFilesystemSpec | 本地默认 | 配置 workspace，经 USER namespace 解析 | 文件持久化 |
| 独立 AgentState | AgentStateStore | 原生模式不隐式启用 | 未装配 | 无独立后端 | 不适用；Journal checkpoint 另算 |
| Snapshot | 各子系统快照接口 | 按 sandbox 等功能配置 | 无显式配置 | 未确认存在实际对象 | 未接入证据 |
| 远程通用 KV | BaseStore | 无通用隐式远程后端 | 未装配 | 无 | 未接入 |
| 页面列表标题 | 非 SDK Store | 不适用 | ChatSessionService fixture | classpath preview/*.json | 写操作仅回显 |

【源码确认】官方装配证据：`agentscope-harness/io/agentscope/harness/agent/HarnessAgent.java:2553-2622,2724-2727`；`HarnessAgentBuilderSupport.resolveFilesystem():147-167` 默认 LocalFilesystemSpec；`filesystem/local/LocalFilesystem.sessionStorage():597-599` 创建 LocalSessionStorage，路径为 resolvePath(rc,".")/.agentscope-runtime；`session/WorkspaceSessionLogStore.open():93-97` 以文件系统原子存储和 SessionKey.storagePath 创建 JournalSessionLog。

【源码确认】页面能力不能等同原生会话能力：`server/v2-server/src/main/java/com/agentsquad/squad/v2/service/ChatSessionService.java:24-58` 的 list/create/rename() 返回 preview JSON，create 不创建原生 Journal，rename 不写原生日志。原生历史详情与 fixture 列表混合存在。

## 验证边界

【文档确认】既有 `docs/research/agentscope-store-20261116/README.md` 记录正常关闭后新 JVM 恢复及真实数据样例。这是历史实验记录，不代表本次重跑；其中“当前 HTTP 直接走 AG-UI、没有 Inbox”已被当前 ChatService.submit 实现超越，不能照搬。

【源码推导】当前文件持久化不等于具备跨机器多实例持久化：应用没有远程 BaseStore，workspace 为本机绝对路径。SDK Journal 的 CAS/fencing 能力不能自动让不同机器的独立目录共享。未进行共享盘、双实例、强杀、重复工具副作用验证，因此不宣称多实例生产可用。

【待验证】未运行模型、数据库或当前测试；需要测试报告时应单独标明受控模型与真实外部模型边界。本次没有启动应用、修改数据库或配置。

## 状态恢复与子 Agent 补充核对

【源码确认】原生 checkpoint 不是“未持久化 AgentState”：`agentscope-core/io/agentscope/core/ReActAgent.java:722-750` 的 activateSlotForContext() 在 SessionRecorder 存在时优先调用 activeRecorder.initial().restore()，不存在历史状态才 freshState；只有无 recorder 且 stateStore 非空时才进入独立 AgentStateStore 分支。`agentscope-core/io/agentscope/core/session/SessionProjection.java:46-47,91-115` 处理 state/checkpoint、migration/baseline、state/restored 并构造 AgentState；`agentscope-core/io/agentscope/core/session/SessionRecorder.java:383-394` 负责 checkpoint 记录入口。因此 V2 无显式 StateStore 不代表重启丢失全部模型上下文。

【源码确认】`server/v2-server/src/main/java/com/agentsquad/squad/v2/agent/subagent/TestSubagentDefinitions.java:25-49` 将 Markdown 定义首次复制到配置 workspace/subagents/markdown-tester.md，已有文件不覆盖；Java 声明 java-tester 只给 inline body，没有显式 workspace 或 workspaceMode。定义目录与运行目录不同。`agentscope-harness/io/agentscope/harness/agent/subagent/SubagentDeclaration.java:397` 默认 ISOLATED；`agentscope-harness/io/agentscope/harness/agent/HarnessAgentBuilderSupport.java:715-732` 的工作区解析在无定义 workspace 且 ISOLATED 时生成 mainWorkspace/agents/<name>/workspace。最终还会受该子 Agent 文件系统 namespace/user 隔离影响，不能把父 Session 的目录当作所有子日志的目录。

【源码推导】跨实例限制分三层：共享文件物理可达性、AtomicSessionStorage 的一致性、模型/工具外部副作用恢复。当前只确认本机路径配置和 SDK 原生 Journal 装配，应用 synchronized(session) 不能覆盖后两层。新机器使用同样字符串的路径不表示同一数据；也没有本次共享文件系统语义与故障切换实测。

【待验证】本次主研究生成的 runtime-samples.json 属于受控 SDK 存储样例，其证据范围见该文件与 README；不能据此宣称当前 HTTP 全链路或双实例测试通过。
