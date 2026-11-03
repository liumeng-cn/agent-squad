# Spec Delta: Events & Artifacts / R10

## ADDED Requirements

### Requirement: 用户可见回复的交付语义可判定

系统 MUST 在持久消息/公共事件投影中区分过程通知与权威最终结果，保持消息身份与执行来源可追溯。SSE/快照重放 MUST 不改变最终交付裁决，也不得重复显示同一已发布消息。（R10）

#### Scenario: 受理通知与最终成果
- **WHEN** Master 创建 GoalTask 并发送受理通知，Task 之后完成
- **THEN** 页面可以展示状态通知与一份最终成果，通知不冒充另一份终稿

#### Scenario: 同源双发布尝试
- **WHEN** RESPONSE 与 TASK 分别尝试将完整成果发布为同一有效交付代际的 FINAL
- **THEN** 平台只提交权威所有者的 FINAL，并记录另一条尝试被拒绝的可诊断事实；不依赖前端按正文相似度过滤

#### Scenario: SSE 重连与快照合并
- **WHEN** 用户刷新或 SSE 断线重连后重放 `message.completed` 和任务完成事件
- **THEN** 按持久 messageId/事件序号合并，页面不会将同一终稿作为新消息再次呈现
