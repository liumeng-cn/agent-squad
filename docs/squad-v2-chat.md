# AG-UI 接入试验（当前链路）

页面布局保持不变。POST /api/v2/chat/messages 使用官方 agentscope-extensions-agui 的 AguiAgentAdapter → HarnessAgent.streamEvents，输出标准 AG-UI SSE。前端 agui.ts 将事件映射到现有页面状态。已删除旧 SessionLog 投影代码、私有事件模型及对应测试。

固定 SDK 2.1.0-BETA1 的受控模型测试已验证多轮上下文、正文和终态。此入口不经过 AgentSession.submit，因此不承诺原提交幂等、断开继续执行和日志重放。页面仍不自动重发输入。人工确认只显示等待提示，暂未接入恢复入口。工具参数结束不表示工具完成，页面等待 TOOL_CALL_RESULT。

公开事件通过 ChatEventConverter 逐类转换为 ChatEventVO；仅保留页面所需字段。未支持事件不输出，SDK 类型不作为 HTTP 响应契约；waiting 表示暂停等待交互。

## 三种子 Agent 测试入口

聊天应用启用模型后，重启会装配以下三个角色；内置角色无需额外注册。

| agent_id | 定义来源 | 测试用途 |
|---|---|---|
| general-purpose | SDK 内置 | 通用任务隔离 |
| markdown-tester | 工作区 subagents/markdown-tester.md | 验证 Markdown 定义加载 |
| java-tester | TestSubagentDefinitions 编程式声明 | 验证 Java 声明加载 |

Markdown 模板位于应用资源 subagents/markdown-tester.md；首次装配复制到配置的 agentsquad.v2.agent.workspace 下，重启不覆盖已有文件。两个专用测试角色继承父模型，steps=4。提示词中的不修改文件属于行为指引，不构成程序级工具权限限制。

在当前聊天页面输入：

> 请分别调用 general-purpose、markdown-tester、java-tester 三个子 Agent，每个计算 17+25。请实际使用 agent_spawn，timeout_seconds=60，等待完成后汇总各角色的结果，不要由主 Agent 代答。

Markdown 和 Java 角色分别要求以 [Markdown子Agent]、[Java子Agent] 开头回答。是否遵循指令需在真实模型下验证。当前页面展示父级工具调用及最终汇总，尚不展示 subagent.* 独立过程；没有单独启用前端子 Agent 面板。
