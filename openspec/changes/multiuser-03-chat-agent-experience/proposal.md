# Proposal：C3 ChatGPT 式对话体验与 Agent 任务正确性

## Why

现有 `MasterAgent` 配置 `stream(false)`；`ConversationEventStreamer` 在 DB 事件上推 SSE；前端 `conversations.ts` 收到事件后使用 80ms 去抖重新拉全量快照。当前 `SafeMarkdown.vue` 仅支持 Markdown 子集；会话切换 `ChatComposer` 会清空未发送草稿。`ResponseContextService` 对超 200k 字符的会话直接拒绝。真实 `deepseek-chat` 验收还记录“首次规划尚未发布时早到 Directive”导致任务失败的竞态。

## Scope

- 实现**受控的真实流式文本反馈**、可靠的最终消息提交与 SSE 断线回放，不暴露内部工具调用/模型隐私推理/Worker 原始工作记录。
- 拓宽安全 Markdown 展示，保留对 HTML/XSS 的防护；增加独立会话草稿、附件状态保留和前端回归测试。
- 引入确定性的上下文窗口/摘要生成与可审计版本，超限时明确降级策略；不牺牲记忆删除/授权撤销屏障。
- 修复**早到在途补充**：应用指令需要真实 directiveId，计划发布与待处理指令协调有可重试安全点，不能因模型猜 ID 耗尽迭代。

## Non-goals

- 不实现 Codex/Git/Diff、任意宿主文件写入、模型选择商城、自动联网搜索。
- 不向用户展示 Chain-of-thought 或 SDK 内部工作日志。
- 不为“看起来有进度”生成假的 token、进度条、百分比。

## Dependencies / Acceptance

- C1+C2 测试基线通过；多用户交叉流式事件隔离可验证。
- 真实模型普通聊天能出现安全可见的增量文本（如果 SDK 能提供结构化、可归属、可安全展示的事件）；SDK 不满足时必须明确记为阻碍，保留正确的终态输出，不能靠定时打字冒充流式。
- 断网重连、消息修订、取消、附件、中文输入法与 Markdown 恶意文本均有自动化测试；早到 Directive 竞态确定性测试与真实模型复测通过。
