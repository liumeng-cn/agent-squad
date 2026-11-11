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

前端展示根 Agent 的 `TEXT_BLOCK_DELTA`，`AGENT_RESULT` 用完整 Msg 替换增量预览（不重复追加）。其余事件保留在可折叠事件列表。

## 多 SubAgent 并行实验

访问 `/app/native-chat/subagents`，调用 POST `/api/v1/native-chat/subagents`。Master 暴露三个官方 SubAgentTool：
- call_calculator → calculator 子智能体 → add_numbers
- call_word_counter → word_counter 子智能体 → count_words
- call_text_reverser → text_reverser 子智能体 → reverse_text

每个子智能体有独立 ReActAgent 和 Toolkit，只注册一个工具。子 Agent 事件通过 SubAgentConfig.forwardEvents(true) 转发到 Master 的 streamEvents()。Master Toolkit 显式配置 parallel(true)。

## 全事件测试工作台

访问 `/app/native-chat/events`。工作台按官方 `AgentEventType` 列出 31 种事件，提供分类筛选、事件说明、触发方式、模拟事件、真实 SSE 接收、原始 JSON 检查和来源/接收时间记录。

必须区分两类记录：
- **真实事件**：由现有 `/message` 或 `/subagents` 接口实际执行 AgentScope 后收到。
- **模拟事件**：前端根据事件样例构造，仅验证前端 JSON 解析、展示和检查能力，不能证明 SDK 能触发该事件。

目录中“有真实触发路径”只表示可以设计对应 SDK 执行场景，不代表当前已经成功触发。思考块取决于模型能力；数据块取决于模型/工具实际返回多模态 ContentBlock；人工确认、外部执行、停止和权限事件需要后续单独接入对应 SDK 机制。当前真实测试入口只复用已有聊天与 SubAgent 接口，未增加后端事件伪造接口。

## 已知限制

- 不持久化：页面刷新后历史消失；浏览器每次带最近最多 30 条 USER/ASSISTANT 文本以实现页面内多轮聊天。
- 单次 POST 的 SSE 断开/停止会取消当前 Agent Flux；没有跨请求续流、断线回放或运行恢复。
- SSE 中的 `STREAM_ERROR` 是服务端故障提示，不属于 AgentScope 原生事件。
- 鉴权沿用现有 Gateway + CSRF；请从项目网关访问。
- 每次请求创建并关闭独立 `ReActAgent`，不启用状态存储。当前仅用于验证交互和事件格式。
