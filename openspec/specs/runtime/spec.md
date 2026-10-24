# 任务运行集成（Runtime）

## Purpose

任务运行集成能力负责 GoalTask、Command 与 Binding guard 的持久受理、产品状态机、恢复入口与取消语义，以运行协议为状态权威与故障窗口依据。（M02）

## Requirements

### Requirement: 目标持久受理与幂等

一个目标持久接受、可离线等待；同幂等键重送不重复受理，同键换输入返回冲突。（R02）

#### Scenario: 同键重送（A01）
- **WHEN** 同 Idempotency-Key 且同输入重复提交
- **THEN** 返回原 TaskReceipt，不创建第二个 GoalTask，无第二次执行

#### Scenario: 同键换输入
- **WHEN** 同 Idempotency-Key 但输入内容不同
- **THEN** 返回 409

#### Scenario: 会话忙
- **WHEN** 同一会话已有未终结目标时再次提交
- **THEN** 返回 409，一个会话最多一个未终结目标

### Requirement: 受理事务原子性

受理锁定 Conversation 行，同一平台事务创建 GoalTask、MasterBinding、SUBMIT Command 与 task.accepted 事件，提交后返回 202。

#### Scenario: 202 语义
- **WHEN** 平台受理提交返回 202
- **THEN** 仅表示持久接受，可关闭页面；不表示模型已启动或 SDK inbox 已接受

### Requirement: 实例退出恢复

实例退出后从持久提交点恢复，不丢目标也不重复执行；未知副作用先核对。（R08）

#### Scenario: submit 前崩溃（A02）
- **WHEN** 平台受理后、SDK submit 前实例退出
- **THEN** 重启后原 Command 按原 Binding 重投，不丢目标

#### Scenario: 收据前崩溃（A03）
- **WHEN** SDK 已接受、平台收据前实例退出
- **THEN** 重启后同 key 补回执，复用同一原生 Turn，不重复执行

#### Scenario: Turn 完成但导出丢回执（A12）
- **WHEN** 原生 Turn 已完成但公开导出失败
- **THEN** 补投影而不再推理，完成候选有持久成功证据才发布

#### Scenario: 异常回合不伪装成功
- **WHEN** 原生 run/end 为 failed/suspended/interrupted/unknown 且无取消意图
- **THEN** 目标置 RECOVERY_BLOCKED 并保存阻塞原因、清除该回合完成候选；显式恢复被 SDK 连续拒绝则命令 BLOCKED、目标回到 RECOVERY_BLOCKED

### Requirement: 取消语义

取消后阻断新工作，明确已发生的操作，不把取消视为回滚。（R11）

#### Scenario: 取消与派发竞争（A11）
- **WHEN** 取消与工具派发、用户回复或恢复竞争
- **THEN** 屏障后不发新业务，已 IN_FLIGHT 操作核对完才收尾

#### Scenario: 取消不补偿
- **WHEN** 取消流程执行
- **THEN** 不执行补偿（补偿须另建授权 Operation），所有业务 dispatch 封闭、执行停止或失效、未知操作处理后置 CANCELLED

### Requirement: 产品状态机与恢复入口

状态由平台业务事实决定，framework Run 状态仅为其一证据；resume 仅接受受限状态并校验版本、配置、授权、记忆代际与原生 pending/uncertain。

#### Scenario: 恢复受理
- **WHEN** resume 目标处于可恢复状态且校验通过
- **THEN** 受理为 QUEUED 并写入 RESUME 命令；已排队返回已有回执

#### Scenario: 终态不可恢复
- **WHEN** 目标已 SUCCEEDED/PARTIAL/FAILED/CANCELLED 时请求 resume
- **THEN** 拒绝，重做创建新任务
