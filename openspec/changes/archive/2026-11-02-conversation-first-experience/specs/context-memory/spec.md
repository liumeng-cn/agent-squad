# Spec Delta

## MODIFIED Requirements

### Requirement: 显式长期记忆治理

会话压缩与用户显式长期记忆 MUST 可纠正、可删除；普通回复和任务执行均遵守校验与删除屏障，模型自动提炼不主动写 ACTIVE。（R09）

#### Scenario: 显式写入
- **WHEN** 用户经 write_explicit_memory 受控工具写入记忆
- **THEN** 形成明确记录并附用户原话来源，普通日志或 Skill 指令不构成记忆授权

#### Scenario: 纠正覆盖旧值（A15）
- **WHEN** 用户纠正记忆
- **THEN** 新授权视图重建，旧 checkpoint/摘要不复活旧值

#### Scenario: 删除后不回流（A15）
- **WHEN** 用户删除记忆
- **THEN** 旧检查点/摘要/索引不能使内容回流，无法清理时相关回复执行或任务进入受限状态，不因没有任务而跳过屏障

## ADDED Requirements

### Requirement: 连续会话与局部授权

Master MUST 在用户归属与会话代际有效的前提下读取截至本次消息的已提交历史，刷新不重跑历史推理；会话记录不自动升级为长期记忆，Worker 仍只取得授权局部快照。（R03/R04/R09）

#### Scenario: 连续追问
- **WHEN** 用户在上一条回复后继续追问
- **THEN** Master 能引用已提交对话，不要求用户重复原文，不默认新建任务；另一会话或用户的历史不可见

#### Scenario: 删除与派发竞争
- **WHEN** 会话删除或代际失效与新执行派发竞争
- **THEN** 失效绑定不能继续注入旧历史或发新业务；删除后的迟到投影不得重新公开内容
