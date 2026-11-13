# AG-UI 接入试验（当前链路）

页面布局保持不变。POST /api/v2/chat/messages 使用官方 agentscope-extensions-agui 的 AguiAgentAdapter → HarnessAgent.streamEvents，输出标准 AG-UI SSE。前端 agui.ts 将事件映射到现有页面状态。已删除旧 SessionLog 投影代码、私有事件模型及对应测试。

固定 SDK 2.1.0-BETA1 的受控模型测试已验证多轮上下文、正文和终态。此入口不经过 AgentSession.submit，因此不承诺原提交幂等、断开继续执行和日志重放。页面仍不自动重发输入。人工确认只显示等待提示，暂未接入恢复入口。工具参数结束不表示工具完成，页面等待 TOOL_CALL_RESULT。
