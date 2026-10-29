# Worker 定义与可恢复执行（Worker）

## Purpose

Worker 能力负责能力版本注册、局部输入、持久派发与结构化结果；WorkerDefinition 描述能力版本，不等于实例或 Skill，不手写 ReAct 或自由后台 spawn。（M04）

## Requirements

### Requirement: 能力按契约接入

系统 MUST 遵守以下约束：新 Worker 按能力契约（workerKey/version、inputSchema/outputSchema、toolAllowlist、modelProfileKey、runtimeProfileKey）接入，不修改 Master 业务分支。（R12）

#### Scenario: 新能力按 Schema 注册（A17）
- **WHEN** 新能力按已发布 Definition 注册
- **THEN** Master 无业务代码改动，可自动发现使用

#### Scenario: 定义不可变与状态管理
- **WHEN** 发布或更新 WorkerDefinition
- **THEN** 同一 workerKey/version 不可重复发布，版本内容不可变，停用只改状态、REVOKED 不可恢复

### Requirement: 持久派发与恢复

系统 MUST 遵守以下约束：WorkerDispatcher 从 READY Job 创建或取得固定 RuntimeBinding，先 CAS guard 再用 WorkerFactory 构建叶子 HarnessAgent；Job 初始输入固定，Command ID 作为 SDK submit key，崩溃恢复原 Session/Turn，不以新 submit 替代 resume。（R06/R08）

#### Scenario: 同 Job 两实例（A05）
- **WHEN** 同一 Job 被两个实例领取
- **THEN** 仅一个有效业务 guard，原生日志拒绝旧 writer，旧工具/结果拒绝

#### Scenario: 分支崩溃恢复（A06）
- **WHEN** 一个成功分支、另一分支崩溃
- **THEN** 只恢复崩溃分支、不重复成功分支，结果候选不提前算成功

#### Scenario: 实例退出恢复
- **WHEN** Worker 实例退出后重启
- **THEN** 新实例发现相同 Job，只继续未完成部分，旧实例迟到不能覆盖

### Requirement: Job 状态机与结果

系统 MUST 遵守以下约束：Job 状态 BLOCKED→READY→RUNNING→SUCCEEDED/PARTIAL/FAILED，支持 WAITING_INPUT/WAITING_APPROVAL/RECOVERY_BLOCKED/RECONCILING/CANCELLING/CANCELLED/SUPERSEDED；Worker 以 report_job_result 提交候选，ResultFinalizer 校验 guard、Plan、产物与输出 Schema 后写终态。

#### Scenario: 候选已写而 tool result 丢失（A09）
- **WHEN** 数据库候选已写而原生 tool result 丢失
- **THEN** 按原逻辑键返回同一候选，不重新执行业务

#### Scenario: Turn 失败不算成功
- **WHEN** 结果候选已保存但原生 Turn 失败
- **THEN** 不当作成功，重启不自动将 RUNNING 标失败，先查持久原生状态

### Requirement: Worker 权限边界

系统 MUST 遵守以下约束：Worker 可读获准上下文和调用受控工具，但不能写全局记忆、发布 Plan、结束 GoalTask 或再派 Worker；禁用无界递归委派。

#### Scenario: 越权拒绝
- **WHEN** Worker 尝试写全局记忆、发布 Plan、结束 GoalTask 或递归委派
- **THEN** 平台拒绝，Worker 只能通过 report_job_result 提交结构化结果
