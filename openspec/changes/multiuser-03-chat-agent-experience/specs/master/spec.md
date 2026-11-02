# Spec Delta: Master / R03 R06

## ADDED Requirements

### Requirement: 规划回合对早到目标修改的确定性协调

当用户目标补充在首次计划发布前到达，任务 Master MUST 能从受权平台事实获得真实 directiveId 与 expectedVersion，并在安全点应用或拒绝；不得凭来源消息 ID 猜测修改 ID，也不得因反复无效工具调用消耗迭代后静默失败。

#### Scenario: 规划期间早到补充
- **WHEN** Master 首次规划尚未完成，新的补充指令已经 ACCEPTED
- **THEN** 阻断旧计划的安全发布并给出可执行待处理 directiveId 或排入协调回合，成功处理后使用最新 goalRevision 规划

#### Scenario: 旧计划结果迟到
- **WHEN** 新目标修订已经应用但旧计划 Worker 回报结果
- **THEN** 旧结果不得直接构成新目标完成候选，任务保持有界、可诊断与可恢复
