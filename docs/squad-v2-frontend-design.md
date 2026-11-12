# Squad V2 前端架构与事件工作台设计

## 1. 文档目的

本文记录 Squad V2 SSE 联调页面的前端架构、数据模型、模块职责、事件处理规则和验证要求。后续增加真实聊天、SubAgent 展示、事件恢复或更复杂的交互时，以本文作为当前前端实现的设计基线。

本次工作先解决代码边界和事件处理正确性，不以增加 UI 复杂度为目标。

## 2. 范围与非目标

### 本次范围

- 拆分原本集中在 SquadV2View.vue 中的通信、事件聚合、展示和交互逻辑。
- 统一 TypeScript 与 Java SquadMsg 的传输类型。
- 实现可独立测试的 SSE 分片解码器。
- 实现纯事件模型，分别维护 Agent 实例、事件时间线、内容块、工具调用和主 Agent 回答。
- 将页面改为平铺事件流，按 Agent 实例标识事件发送者。
- 补充 SSE 解码和事件聚合的单元测试。

### 本次不做

- 不修改后端 SquadMsg 结构、AgentScope 原生事件名称或 SSE 接口。
- 不实现断线续传、Last-Event-ID 补发或事件持久化。
- 不把断开 SSE 连接解释为取消 Agent 执行。
- 不提前实现 Agent 树、DAG 可视化、复杂任务卡片或自定义工作流编辑器。
- 不将模拟事件接口包装成真实模型聊天接口。
- 不引入新的 Pinia Store 或额外状态管理框架。

当前测试接口只接收 intervalMillis，页面输入文字仅用于页面展示，后端仍返回预设的并行模拟事件。

## 3. 当前实现存在的问题

原页面把以下职责放在同一个 Vue 文件中：

1. fetch、AbortController、TextDecoder 和 SSE 帧解析。
2. Agent 实例索引、父子关系和等待挂载处理。
3. 内容块、工具调用、工具结果和最终答案的聚合。
4. 事件文案、时间格式化、JSON 判断和 Markdown 展示。
5. 滚动跟随、计时器、输入框和页面布局。

这导致页面文件同时承担协议适配、业务状态管理和 UI 渲染，任何事件类型变化都会影响页面主体。

此外还存在四个具体问题：

- TypeScript 事件类型与 Java 事件名不一致，例如 Java 的 AGENT_START 被写成 AGENT_STARTED。
- TypeScript 缺少 Java SquadMsg 中的 nativeExecution、nativeSource 字段，category 也缺少 DATA、HINT。
- AGENT_RESULT 仅在没有流式正文时才被采纳，导致正式结果可能被丢弃。
- SSE 读取结束时统一结束所有 Agent 计时，错误地把连接生命周期当成 Agent 生命周期。

## 4. 目标目录结构

    features/squad-v2/
    ├── api/
    │   └── squadTestApi.ts
    ├── components/
    │   ├── SquadComposer.vue
    │   ├── SquadEventItem.vue
    │   └── SquadEventTimeline.vue
    ├── composables/
    │   ├── useSquadEventStream.ts
    │   └── useSquadExecution.ts
    ├── model/
    │   ├── sseDecoder.ts
    │   ├── squadExecution.ts
    │   └── squadEventPresenter.ts
    │   └── __tests__/
    │       ├── sseDecoder.test.ts
    │       └── squadExecution.test.ts
    └── views/
        └── SquadV2View.vue

共享传输类型继续放在 shared/api/squadTypes.ts，不在页面或组件中重复定义。

## 5. 模块职责

| 模块 | 唯一职责 | 不应该做的事 |
| --- | --- | --- |
| squadTestApi | 建立测试接口 HTTP 请求，传递 CSRF、凭证和 AbortSignal | 不解析 SSE、不维护 Agent 状态 |
| sseDecoder | 将任意文本分片解析为完整 SSE Frame | 不解析 JSON、不理解 Agent 业务 |
| useSquadEventStream | 管理连接状态、读取流、调用解码器、将 SquadMsg 分发给调用方 | 不组织事件时间线、不渲染页面 |
| squadExecution | 根据 SquadMsg 更新 Agent、内容块、工具和答案状态 | 不依赖 Vue、不操作 DOM |
| useSquadExecution | 将纯事件模型包装为 Vue 响应式状态 | 不发起 HTTP 请求 |
| squadEventPresenter | 将事件类型和模型数据转换为展示文案 | 不修改事件或执行状态 |
| SquadEventItem | 展示单条事件、内容块或工具调用 | 不解析网络分片、不聚合事件 |
| SquadEventTimeline | 按顺序组织事件项 | 不理解 SSE 协议 |
| SquadComposer | 输入测试说明、提交或断开当前连接 | 不直接请求后端 |
| SquadV2View | 组合页面、协调两个 composable、处理页面滚动 | 不承载协议解析和事件聚合 |

不为每个小函数单独创建文件。只有具有独立职责、可以单独验证或未来会被复用的模块才拆分。

## 6. 传输协议约定

### 6.1 SquadMsg

前端类型以 Java SquadMsg 的实际字段为准：

| 字段 | 含义 | 前端处理 |
| --- | --- | --- |
| protocolVersion | 协议版本 | 保留，不参与展示判断 |
| eventId | 事件唯一 ID | 首选去重键 |
| sequence | 当前 Turn 的事件序号 | 排序；缺失时使用接收顺序 |
| eventType | 原生事件名或 squad. 平台事件名 | 事件处理主依据 |
| category | 展示分类 | 辅助展示，不替代 eventType |
| conversationId | 会话 ID | 去重与诊断上下文 |
| turnId | 请求轮次 ID | 去重与诊断上下文 |
| agent | Agent 实例身份 | 使用 agentInstanceId 识别发送者 |
| nativeExecution | 原生执行身份 | 原样保留，供诊断扩展 |
| nativeSource | 原生事件来源 | 原样保留，供诊断扩展 |
| relation | 事件关联信息 | 关联 blockId、toolCallId 等 |
| payload | 原生事件数据 | 由对应事件处理逻辑读取 |
| timestamp | 事件发生时间 | 展示时间，不用于替代 sequence |

Java SquadMsg.eventType 的传输字段是 String。Java SquadEventType 是已知原生事件名称清单，不应成为前端拒收未知事件的理由。TypeScript 提供已知名称提示，同时保留开放字符串。

### 6.2 SSE 层与业务事件层

- SSE event 字段只是传输层事件名称。
- 业务事件类型始终读取 JSON SquadMsg.eventType。
- SSE id 对应 sequence，不应直接覆盖 eventId。
- 当 JSON sequence 缺失且 SSE id 是数字时，才使用 SSE id 补充 sequence。
- 一条 SSE 事件的 data 可能跨多个网络分片。
- 一个网络分片可能包含多条 SSE 事件。
- data 多行时按换行符拼接。
- 连接结束时必须处理最后一条没有空行结束的事件。

当前本地模拟接口使用 SseEmitter 发送真实 HTTP SSE。前端不依赖具体 SSE event 名称，避免传输标签与业务类型耦合。

## 7. 事件模型

### 7.1 Agent 实例

Agent 以 agentInstanceId 为唯一键。

agentId 表示 Agent 定义，同一个定义可以同时创建多个实例，不能作为实例索引。parentAgentInstanceId、rootAgentInstanceId 和 depth 保留在 AgentRef 中，为后续树状展示提供依据；第一阶段不要求页面构建树。

Agent 状态仅由 Agent 生命周期事件维护：

| 事件 | Agent 状态 |
| --- | --- |
| 首次收到 Agent 身份 | NOT_STARTED |
| AGENT_START | RUNNING |
| AGENT_END | ENDED |

SSE 连接结束、连接异常或浏览器主动断开，不改变 Agent 状态。只有收到对应 Agent 的 AGENT_END 才将该实例标记为 ENDED。

### 7.2 平铺时间线

时间线采用一维列表，保留不同 Agent 事件的交错顺序。每个时间线项有独立 id、sequence、接收顺序、Agent 身份和 timestamp。

时间线包含三种项目：

- event：普通生命周期、模型、控制、提示和未知事件。
- block：文本、思考摘要和数据块。多个增量合并为一个内容块，保留该块首次出现的 sequence。
- tool：同一 Agent 下通过 toolCallId 关联工具调用和工具返回。

平铺不是把所有增量都渲染成独立卡片，而是按事件语义合并内容块和工具过程，同时保留不同项目之间的先后关系。

主 Agent 的公开 TEXT/DATA 内容单独放在回答区域，避免同一段正文在事件时间线和回答区重复展示。子 Agent 的内容块仍显示在时间线中。

### 7.3 内容块

内容块按 Agent 实例和 blockId 组合定位：

    agentInstanceId + blockType + blockId

blockId 优先读取 payload.blockId，其次读取 relation.blockId。缺少 blockId 时使用该 Agent、该内容类型当前活动块作为增量归属；没有活动块时创建临时块，并在收到结束事件或 Agent 结束时关闭。

TEXT、THINKING、DATA 分别维护。不同 Agent 的相同 blockId 不会互相覆盖。

### 7.4 工具调用

工具调用和工具返回按以下键关联：

    agentInstanceId + toolCallId

toolCallId 优先读取 payload.toolCallId，其次读取 relation.toolCallId。结果先于调用到达时创建占位项，后续调用信息到达后补充名称和参数。

展示状态只描述工具事件进度：

- CALLING：工具参数生成中。
- READY：调用参数已准备好。
- RETURNING：正在接收工具结果。
- COMPLETED：工具结果结束事件已到达。

工具状态不代替 Agent 生命周期或服务端业务结果。

### 7.5 最终回答

主 Agent 的 TEXT/DATA 内容块构成实时回答预览。AGENT_RESULT 到达后，其内容成为最终回答的权威来源，即使此前已经收到流式增量也必须采纳。

最终回答优先级：

1. AGENT_RESULT 中的 result。
2. 尚未收到 AGENT_RESULT 时，按 sequence 拼接的主 Agent 流式回答块。

这样既能实时展示正文，也不会丢弃最终结果。流式预览保留在模型中，便于诊断最终结果与预览不一致的情况。

## 8. 数据流

    用户提交测试说明
          |
          v
    SquadV2View
          |
          +---- useSquadExecution.reset()
          |
          v
    useSquadEventStream.start()
          |
          v
    squadTestApi.openSquadTestStream()
          |
          v
    fetch ReadableStream
          |
          v
    TextDecoder
          |
          v
    SseDecoder.push / flush
          |
          v
    JSON SquadMsg
          |
          v
    useSquadExecution.applyEvent
          |
          v
    squadExecution.applySquadEvent
          |
          +---- Agent 实例状态
          +---- 平铺事件时间线
          +---- 内容块索引
          +---- 工具调用索引
          +---- 最终回答
          |
          v
    Vue computed
          |
          +---- SquadEventTimeline
          +---- SquadEventItem
          +---- 主 Agent 回答区域

## 9. 错误处理和边界

- HTTP 非 2xx、无 response.body 或错误 Content-Type：连接进入 ERROR。
- 用户主动断开：连接进入 DISCONNECTED；不发送取消 Agent 的语义。
- JSON 无法解析或事件信封字段不完整：记录解析错误并继续读取后续事件。
- 未知 eventType：作为普通事件保留原始 SquadMsg，不中断流。
- 重复 eventId：忽略重复事件。
- eventId 缺失时，以 conversationId、turnId、sequence 组合去重。
- sequence 缺失时，以接收顺序稳定展示。
- AGENT_END 只结束对应 Agent 和它未关闭的内容块。
- SSE EOF 不批量结束 Agent，也不自动补造 AGENT_END。
- 当前版本不提供断线补发；重新发起测试会清空当前页面模型。

## 10. 测试要求

### SSE 解码测试

- 多行 data。
- CRLF 与 LF。
- CRLF 被拆到不同网络分片。
- 一个分片包含多条事件。
- 注释和未知字段。
- SSE id 持续规则。
- 最后一条事件没有空行结束。

### 事件模型测试

- 同一个 agentId 对应多个 agentInstanceId。
- Agent 事件交错到达。
- 相同 blockId 的增量合并。
- 缺少 blockId 时的活动块回退。
- toolCallId 关联调用和结果。
- 重复 eventId 去重。
- AGENT_RESULT 覆盖流式预览。
- AGENT_END 只结束对应 Agent。
- 未知事件保留原始数据。

### 集成验收

- 使用本地真实 HTTP SSE 测试接口。
- 验证多 Agent 并行事件能够平铺展示。
- 验证文本增量、工具参数和工具结果。
- 验证连接正常结束后 Agent 状态仍由生命周期事件决定。
- 运行前端 TypeScript 检查、Vite build 和 Vitest。

## 11. 后续演进边界

后续如果要增加能力，按以下顺序演进：

1. 先补充事件模型和测试，再增加事件展示组件。
2. 需要 Agent 树时，基于已保存的 parentAgentInstanceId 派生树，不重写事件聚合。
3. 需要断线恢复时，新增服务端可恢复游标和补发契约；前端使用 sequence/eventId 去重，不能只靠 EventSource 自动重连。
4. 接入真实聊天时，将测试 API 替换为正式 API，保持 SquadMsg 解析和事件模型不变。
5. 需要取消任务时，调用独立的服务端取消命令；不能把 AbortController 当成 Agent 取消能力。

本设计刻意不提前引入全局 Store、复杂树形状态、通用事件总线或插件式渲染注册表。等实际出现多个页面共享执行状态或多种展示策略时，再根据真实需求抽象。
