# AgentScope 原生事件与 Squad SSE 协议

## 版本基线

本协议按 AgentScope Java `2.1.0-BETA1` 固定评估提交
`5451c73abe47053340683a1cc6d67b290b056648` 的
`io.agentscope.core.event.AgentEvent` / `AgentEventType` 定义。

只使用新版 `ReActAgent#streamEvents(...)` 产生的 `AgentEvent`。
旧版 `io.agentscope.core.agent.Event`、`EventType`、`EventSource`
均已 Deprecated，不作为新协议基础。

## 事件类型

Squad 对以下原生事件名原样透传，不做重命名、不合并：

- Agent：`AGENT_START`、`AGENT_END`、`AGENT_RESULT`
- 模型：`MODEL_CALL_START`、`MODEL_CALL_END`
- 文本：`TEXT_BLOCK_START`、`TEXT_BLOCK_DELTA`、`TEXT_BLOCK_END`
- 思考块：`THINKING_BLOCK_START`、`THINKING_BLOCK_DELTA`、`THINKING_BLOCK_END`
- 数据块：`DATA_BLOCK_START`、`DATA_BLOCK_DELTA`、`DATA_BLOCK_END`
- 工具调用：`TOOL_CALL_START`、`TOOL_CALL_DELTA`、`TOOL_CALL_END`
- 工具结果：`TOOL_RESULT_START`、`TOOL_RESULT_TEXT_DELTA`、`TOOL_RESULT_DATA_DELTA`、`TOOL_RESULT_END`
- 执行控制：`EXCEED_MAX_ITERS`、`REQUEST_STOP`
- 人工确认/外部执行：`REQUIRE_USER_CONFIRM`、`REQUIRE_EXTERNAL_EXECUTION`、`USER_CONFIRM_RESULT`、`EXTERNAL_EXECUTION_RESULT`
- SubAgent：`SUBAGENT_EXPOSED`
- 其它：`HINT_BLOCK`、`ALL_TOOLS_DENIED`、`CUSTOM`

## Squad SSE 信封

`SquadMsg.eventType` 使用 String，值为 SDK 原生事件规范名称。
不使用 Java enum 作为反序列化边界，避免 SDK 新增事件导致旧客户端/服务端无法解析。

- `eventId`：原生 AgentEvent.id
- `timestamp`：原生 AgentEvent.createdAt
- `nativeExecution`：原生 ExecutionIdentity（agentId、sessionId、turnId、runId）
- `nativeSource`：原生 AgentEvent.source，原样保留
- `payload`：原生事件完整字段，保留 type、replyId、blockId、delta、toolCallId 等字段
- `conversationId`、`sequence`：由 Squad 平台补充
- `category`：仅供前端分组展示，不改变原生事件语义

AgentScope 新版 AgentEvent 的 source 与旧版 EventSource 不是同一个 API。
当新版事件未提供 source 时保持 null，不从旧版字段或平台推测补齐。
ExecutionIdentity 缺失时保持 null，不伪造 runId/sessionId。

## 平台扩展事件

平台事件不冒充 AgentScope 原生事件，统一使用 `squad.` 前缀。
平台生命周期事件如 `squad.turn.started`、`squad.turn.completed`、
`squad.turn.failed`、`squad.execution.interrupted` 仅由 Squad 自身产生。

## 前端处理约定

前端优先按 `eventType` 处理事件，再使用 `category` 做展示分组。
未知的原生事件仍按字符串接收；可展示原始事件摘要，不应因未知类型中断整条 SSE。
