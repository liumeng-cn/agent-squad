# AgentScope 原生聊天实验

## 范围

独立于旧业务 Message/Conversation/Task/Worker。只做 `POST /api/v1/native-chat/message` 返回 `text/event-stream` 与前端 `/app/native-chat` 页面。消息按 AgentScope Java 2.1.0-BETA1 原生 AgentEvent 类型平铺；不建立数据库表、不写本地历史、不创建会话管理功能。

## 配置

通过环境变量 `DEEPSEEK_API_KEY` 配置密钥（不可提交到 Git）；模型默认 `deepseek-chat`，URL 默认为 `https://api.deepseek.com`。

## 调用

浏览器登录原工作台后访问 `/app/native-chat`。POST JSON：

```json
{
  "message": "你好",
  "history": [
    {"role": "USER", "content": "你是谁？"},
    {"role": "ASSISTANT", "content": "我是聊天助手。"}
  ]
}
```

HTTP 响应 `text/event-stream`，SSE `event:` 为原生 `AgentEventType`，`id:` 是 AgentEvent 原生 ID，`data:` 是原生对象 JSON（包括 type、source、replyId/blockId 和增量字段）。服务只做 HTTP/SSE 封装，不另造事件枚举。

前端展示根 Agent 的 `TEXT_BLOCK_DELTA`，`AGENT_RESULT` 用完整 Msg 替换增量预览（不重复追加）。其余事件保留在可折叠事件列表。当前没有注册工具或 SubAgent，后续可在此接口扩展同一原生事件通道。

## 已知限制

- 不持久化：页面刷新后历史消失；浏览器每次带最近最多 30 条 USER/ASSISTANT 文本以实现页面内多轮聊天。
- 单次 POST 的 SSE 断开/停止会取消当前 Agent Flux；没有跨请求续流、断线回放或运行恢复。
- SSE 中的 `STREAM_ERROR` 是服务端故障提示，不属于 AgentScope 原生事件。
- 鉴权沿用现有 Gateway + CSRF；请从项目网关访问，不要从浏览器绕过网关直连后端。
- 每次请求创建并关闭独立 `ReActAgent`，不启用状态存储。当前仅用于验证交互和事件格式。


## 多 SubAgent 并行实验

访问 /app/native-chat/subagents，调用 POST /api/v1/native-chat/subagents。Master 暴露三个官方 SubAgentTool：
- call_calculator → calculator 子智能体 → add_numbers
- call_word_counter → word_counter 子智能体 → count_words
- call_text_reverser → text_reverser 子智能体 → reverse_text

每个子智能体有独立 ReActAgent 和 Toolkit，只注册一个工具。子 Agent 事件通过 SubAgentConfig.forwardEvents(true) 转发到 Master 的 streamEvents()。Master Toolkit 显式配置 parallel(true)：模型在同一轮发出三个 SubAgent 工具调用时，工具执行器并行执行它们。Master Prompt 要求一次性发起全部三个调用。

注意：Toolkit 保证同一轮多个工具调用的并行执行；是否在同一轮生成三个工具调用，仍取决于模型遵循指令并返回多个 tool calls。页面展示原生事件的 source，可观察每个子智能体的启动、文本、工具调用和结束事件。
