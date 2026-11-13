# v2-chat Specification

## Purpose

以官方 AG-UI 适配器实现当前页面的持续聊天，实时呈现回复正文及可折叠执行过程。

## Requirements

### Requirement: 原生会话提交

系统 SHALL 使用 AguiAgentAdapter 调用 HarnessAgent.streamEvents，按服务端用户与 sessionId 隔离上下文，requestId 映射为 runId。不承诺提交幂等，不新增业务表、历史查询或补收。

#### Scenario: 连续发送

- **WHEN** 同一页面会话完成首轮并发送下一条消息
- **THEN** 框架保留前一轮上下文，页面保留当前打开期间的消息

### Requirement: 单接口事件流

系统 SHALL 使用 POST /api/v2/chat/messages 返回 SSE，使用默认 SSE 事件名，数据结构由 api/openapi.json 定义。

#### Scenario: 正文与过程更新

- **WHEN** 官方适配器输出文本及调用事件
- **THEN** 只向当前轮次投影公开正文、模型调用、工具状态及上下文整理过程
- **AND** 将供应商响应中明确返回的思考文本作为 REASONING_MESSAGE_CONTENT 单独呈现，不公开提示词、参数、原始工具结果或完整日志
- **AND** 未知日志安全忽略，不影响后续已知事件

#### Scenario: 终态与断开

- **WHEN** 执行完成、失败、中断或等待交互
- **THEN** 使用 RUN_FINISHED 或 RUN_ERROR 收敛页面状态并结束响应
- **AND** 网络断开遵循 SDK 流取消语义，不承诺继续执行或断线补收

### Requirement: 阅读优先的聊天界面

页面 SHALL 使用右侧浅灰用户气泡、左侧无气泡 Agent 正文及底部输入区；过程区独立可折叠，按调用标识归并状态，不伪造百分比或思考阶段。

#### Scenario: 流式阅读

- **WHEN** 用户向上查看当前页面消息
- **THEN** 暂停自动跟随并提供回到底部入口
- **AND** 运行时默认展开过程，结束时默认收起，用户手动选择优先

#### Scenario: 连接失败

- **WHEN** 流在终态事件之前结束
- **THEN** 保留已有正文并提示交付失败，不自动重新提交输入

### Requirement: 独立模型思考区

系统 SHALL 将主模型调用返回的 thinking 文本与正文分开归并，以 modelCallId 区分多次模型调用。星火 thinking.type 通过配置显式设置，不通过提示词伪造思考内容。

#### Scenario: 思考内容增量

- **WHEN** 主模型响应返回思考文本
- **THEN** 页面在正文上方显示独立可折叠的模型思考区
- **AND** 未返回思考内容时不显示空区域，辅助模型调用及加密推理元数据不进入公开流
