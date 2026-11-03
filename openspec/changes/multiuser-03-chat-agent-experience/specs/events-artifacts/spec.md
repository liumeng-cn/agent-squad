# Spec Delta: Events & Artifacts / R10

## MODIFIED Requirements

### Requirement: 统一正文与真实增量

Web 工作台 MUST 提供会话的持久消息记录、统一助手正文、真实执行状态、快照、增量与交互入口；普通回复和任务交付均关联明确消息与执行身份。在 SDK 能证明可见文本归属与安全性的前提下，平台 MAY 发布 `response.delta` 增量事件，携带稳定 execution/message/revision/chunkIndex，并始终以最终 `message.completed` 全文为结果权威；无法证明时 MUST 只使用持久最终回答，不伪造字符输出。（R10）

#### Scenario: 快照与 SSE
- **WHEN** 页面打开会话
- **THEN** 先读 snapshot（一致前缀与 watermark），再以 seq>cursor 订阅会话事件；游标未来值 400、过期 410，跨会话/用户拒绝

#### Scenario: 断线重连（A13）
- **WHEN** 断线、跨实例读取或半答案重生成
- **THEN** 一致水位无重复业务，按消息身份及 answerRevision 替换而不拼接；断线不等于后端失败

#### Scenario: 正文隔离
- **WHEN** 投影公开事件
- **THEN** 正文只来自 Master 授权回答轮次，Worker 日志与工具输出脱敏为进度/证据，不串入主正文；非持久 token 不冒充已保存正文

#### Scenario: 流式重试与取消
- **WHEN** 回复重复生成、重试、取消或断线重连
- **THEN** 相同 chunk 不重复追加，过期 revision 不污染新结果；未完成状态真实呈现，不能把工具/内部推理当用户可见回答

#### Scenario: SDK 无法区分可见文本
- **WHEN** SDK 不能证明事件属于安全可见的用户回答
- **THEN** 不公开原生文本片段，仅使用现有持久最终回答；记录流式功能受限而不伪造字符输出
