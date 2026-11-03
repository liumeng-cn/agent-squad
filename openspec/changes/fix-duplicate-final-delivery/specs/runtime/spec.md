# Spec Delta: Runtime / R02 R03 R08

## ADDED Requirements

### Requirement: 同源消息的单一权威最终交付位

系统 MUST 对每个用户请求的有效交付代际维护可恢复、可审计的唯一最终交付所有者和完成消息身份。RESPONSE 与 GoalTask 两种发布路径 MUST 在持久发布事务中执行相同的归属和幂等裁决；允许 ACK/PROGRESS 消息，不允许同一代际两条权威 FINAL。（R02/R03/R08）

#### Scenario: RESPONSE 完成与 GoalTask 创建竞争
- **WHEN** 对同一原始消息的 RESPONSE FINAL 发布与 GoalTask 创建并发
- **THEN** 仅一个操作成功取得该代际最终交付权，另一操作得到可诊断冲突；不得同时交付

#### Scenario: 已委派任务的最终完成
- **WHEN** GoalTask 持有交付权且在其完成前原 RESPONSE 已结束
- **THEN** 任务仍可完成并发布一条 FINAL，原 RESPONSE 不得通过其它投影再次发布同源 FINAL

#### Scenario: 崩溃后完成事件重放
- **WHEN** 最终交付已持久发布，但原生回执、公共事件或 SSE 回放重复到达
- **THEN** 系统复用原 `publishedMessageId` 与已发布事实，不创建新的权威最终消息

#### Scenario: 重试与取消
- **WHEN** 发生自动重试、任务取消或旧代际迟到回报
- **THEN** 不产生额外 FINAL；失败/取消保留真实状态，明确发起的重做使用可区分的新代际
