# Squad V2 / AgentScope / Langfuse 链路观测

## 接入边界

`server/v2-server` 只在启用观测时注册 AgentScope 官方 `OtelTracingMiddleware`，
并通过职责单一的中间件补充 Span 属性；不替换原生 `HarnessAgent`、
`AgentSession`、`SessionLog` 或业务数据库。关闭观测不会建立导出连接，也不会阻断 Agent 启动。

参考：[AgentScope Java](https://github.com/agentscope-ai/agentscope-java) 的官方追踪中间件，
以及开源项目 [agent-manager](https://github.com/gaoyue1989/agent-manager) 的
分层结构、Apache-2.0 项目 [Ragent](https://github.com/nageoffer/ragent) 的 Langfuse 属性思路。
针对本项目的 `2.1.0-BETA1` 接口重新适配，并未直接复制其业务工具或运行环境。

## 组件职责

| 组件 | 真实挂载点 | 追踪作用 |
| --- | --- | --- |
| 官方 `OtelTracingMiddleware` | `onAgent` / `onModelCall` / `onActing` | 生成 `invoke_agent`、`chat`、`execute_tool` Span |
| `FrameworkTracingMiddleware` | 同上 | 为各级 Span 关联用户、原生 Session、Run ID |
| `ModelIoTracingMiddleware` | `onModelCall` | 模型真实输入/输出、工具选择与 token 用量 |
| `ToolCallTracingMiddleware` | `onActing` | 工具名册、执行状态及脱敏限长的参数和结果 |
| `ReasoningTracingMiddleware` | `onAgent` / `onReasoning` | 创建每轮 `reasoning` Span，并使模型调用成为其子节点 |
| `HttpTracingFilter` | Servlet Filter | 可选的 HTTP 接收 Span，独立于后台运行 |

Agent 装配的调用顺序为 `Otel → ModelIo → ToolCall → Framework → Reasoning`。
在本项目 SDK `2.1.0-BETA1` 中，这些中间件具有相同的默认 `order()`，按注册顺序形成洋葱链。
工具批次与 reasoning 位于同一个 `invoke_agent` 下，避免把工具耗时误算成纯模型推理耗时。

目前 V2 没有显式配置 Memory Flush、Consolidation 或独立的 Compaction 模型，
因此不为正常的 Agent 模型调用叠加 `TracingModelWrapper`（否则会重复记录）。
以后出现绕过 `onModelCall` 的直接模型调用，再在该调用处使用专门的 Model 装饰器。

## 配置和启动

默认关闭。连接到自托管 Langfuse v4 的示例：

```bash
export AGENTSQUAD_V2_OBSERVATION_ENABLED=true
export LANGFUSE_OTLP_ENDPOINT=http://127.0.0.1:3000/api/public/otel/v1/traces
export LANGFUSE_PUBLIC_KEY=pk-lf-your-project-key
export LANGFUSE_SECRET_KEY=sk-lf-your-project-key
export LANGFUSE_INGESTION_VERSION=4
```

以上密钥为示意占位符。真实值通过运行环境注入，不写进仓库。设置 `APP_ENV` 和 `APP_VERSION`
可附加部署环境、服务版本。开启后，`endpoint`、`public-key`、`secret-key` 缺任何一项都会拒绝启动，
避免运行中静默丢失所有 Span。

若希望在受控开发环境查看公开的用户/模型正文，再**显式**配置：

```bash
export AGENTSQUAD_V2_OBSERVATION_CAPTURE_CONTENT=true
export AGENTSQUAD_V2_OBSERVATION_MAX_CONTENT_CHARS=4096
```

开启后，工具参数和结果按 `max-tool-content-chars` 展示脱敏预览，默认上限 2048 字符，
超限保留前缀和截断标记；关闭时只保留名称、结构、状态和大小。
系统提示与工具定义正文用占位替换，不导出模型内部推理、GenerateOptions 扩展参数或密钥。正文按上限截断；
仍须自行确认采集行为符合用户授权、数据保护和保留策略。

HTTP 追踪默认关闭。如尚未通过 OTel Java Agent 自动埋点，且需要看请求处理时间，可开启：

```bash
export AGENTSQUAD_V2_OBSERVATION_HTTP_ENABLED=true
```

`HttpTracingFilter` 仅追踪 `POST /api/v2/chat/messages`，会话目录、查询、SSE 及其他接口均不记录 HTTP Span。
从 `traceparent` 提取 W3C 父上下文，记录请求方法、路由模板、状态码，
开启正文采集时，在 MVC 解析入参和组装返回值后，记录脱敏、限长的 JSON 出入参；
不采集 Cookie 和请求头，不重复读取或缓存 Servlet 数据流，不改变原有 `ApiResult` 响应。
HTTP 的输出是提交受理回执，最终模型回复查看同一 trace 下的 Agent 节点。
`AgentSession.submit()` 已进入异步运行阶段，HTTP Span
不应覆盖整个后台 Agent 生命周期；后续跨请求和后台运行的关联应使用业务 commandId/turnId
以及持久化 Trace Link，不伪造一个长时间 HTTP 父 Span。

## Langfuse 内的调用树

- `invoke_agent`：一次 Agent 调用，带 `langfuse.trace.name`、用户与原生 Session ID。
  开启正文采集后展示本轮用户输入与最终公开回复；子 Agent 继承主会话展示归属，
  自身会话另存为 `langfuse.observation.metadata.native_session_id`。
- `reasoning`：每轮 ReAct 推理有独立 Span，携带 `agentsquad.reasoning.round`，
  它下方是该轮模型 `chat` Span。Preview 展示本轮消息、工具结构与公开输出或工具选择，
  不将模型内部思考文本作为输入输出展示。
- `chat`：模型名、允许的生成参数、首字时间、工具名、模型工具调用名册和 token 用量。
  思考过程只保留开始/结束与字符数量，不记录文本。
- 模型和 reasoning 的 Input 分为 `messages`、`tools`、`tool_count`。
  System Prompt 保留角色、原长度和 `system:<指纹>`，正文替换为占位；
  普通工具与 MCP 工具统一保留模型可见名称、`tool:<指纹>`、参数类型和必填字段，
  描述、默认值和枚举正文省略。指纹用于识别定义是否变化，不用于还原内容。
  工具调用参数保留结构与类型，开启正文采集后附加脱敏值和结果文本预览。
  参数与结果采用独立的 2048 字符上限；敏感字段替换为占位，超长保留可读前缀。
  最近 12 条交互保留用户和助手公开文本。超限时保留可读预览及原长度，
  不再将整份输入替换为只有字符数的摘要。本地配置的属性上限为 32768 字符。
- `execute_tool`：官方工具批次 Span。输入列工具名称、ID、参数结构与脱敏值；输出展示每个工具的
  `pending`、`running`、`denied`、`awaiting_confirmation` 或 SDK 返回的结束状态、输出字符数及限长文本。
- 上述 Span 全部携带 `langfuse.user.id` 和 `langfuse.session.id`，
  以及统一的 `langfuse.trace.name`，可与 V2 产品对话目录中的用户和主 Session 对照。
  HTTP 节点记录提交消息的 JSON 入参和受理回执，Preview 不代表后台 Agent 的最终回复；查看最终结果应选择 Agent 节点。

SDK 原生生成的 `gen_ai.usage.*` 仍保留；额外补充 Langfuse 的
`langfuse.observation.usage_details`，缓存 token 不重复叠加到输入 token 总数。
同一工具批次中的多个工具不伪造成多个独立执行 Span，实际逐工具计时若需要应在工具体埋点。

## 可靠性及验收

导出器使用 OTel `BatchSpanProcessor`，每秒批量导出，最多排队 2048 条 Span，
每批最多 256 条，HTTP 超时 2 秒；网络故障或队列满时允许丢观测数据，不影响聊天执行。
关闭应用时 SDK 会关闭导出器并尽量刷出剩余批次。

验证范围：`ObservationTracingTest`（模拟工具调用、跨 Reactor 线程、思考脱敏、鉴权头、
无效密钥和服务拒绝导出），以及需显式开启的本地 `StudioLocalProbeTest`。
真实 Langfuse 部署的回读还需有可访问的实例和项目密钥，不将本地单元测试冒充线上验收。

```bash
cd server
mvn -pl v2-server -am -Dtest=ObservationTracingTest -Dsurefire.failIfNoSpecifiedTests=false test
```
