# Spec Delta

## MODIFIED Requirements

### Requirement: 目标持久受理与幂等

系统 MUST 将用户消息与回复执行持久受理；只有 Master 判定需要独立推进的目标时才创建 GoalTask。同作用域同幂等键同输入返回原收据，不同输入返回冲突。（R02）

#### Scenario: 同键重送（A01）
- **WHEN** 同 Idempotency-Key 且同输入重复提交消息
- **THEN** 返回原 MessageReceipt，不创建第二条消息、回复执行或模型调用；普通问答不创建 GoalTask

#### Scenario: 同键换输入
- **WHEN** 同 Idempotency-Key 但输入内容或附件不同
- **THEN** 返回 409

#### Scenario: 会话忙
- **WHEN** 同一会话已有未终结目标时再次发消息
- **THEN** 持久接受消息并按顺序交给 Master，不因任务在途返回会话忙；一个会话仍最多一个未终结目标，不能由每条消息隐式创建新目标

### Requirement: 受理事务原子性

系统 MUST 锁定 Conversation 行，在同一平台事务写入消息、回复执行、持久命令和受理事件，提交后返回 202；Master 创建目标时另以稳定调用身份原子写入 GoalTask、任务绑定、派发命令与关联事件。

#### Scenario: 202 语义
- **WHEN** 平台受理提交返回 202
- **THEN** 仅表示消息持久接受，可关闭页面；不表示模型已启动或 SDK inbox 已接受

#### Scenario: Master 创建目标重放
- **WHEN** 创建目标已提交但模型未收到工具回执而重放
- **THEN** 返回原目标，不重复建任务、预留预算或派发；创建任务失败不吞掉原消息

### Requirement: 实例退出恢复

实例退出后系统 MUST 从持久提交点恢复消息回复与目标执行，不丢消息或目标、不重复执行；未知副作用先核对。（R08）

#### Scenario: submit 前崩溃（A02）
- **WHEN** 平台受理消息后、SDK submit 前实例退出
- **THEN** 重启后原 Command 按原 Binding 重投，不丢消息或任务

#### Scenario: 收据前崩溃（A03）
- **WHEN** SDK 已接受、平台收据前实例退出
- **THEN** 重启后同 key 补回执，复用同一原生 Turn，不重复执行

#### Scenario: Turn 完成但导出丢回执（A12）
- **WHEN** 原生 Turn 已完成但公开导出失败
- **THEN** 补投影而不再推理，完成候选有持久成功证据才发布

#### Scenario: 异常回合不伪装成功
- **WHEN** 原生 run/end 为 failed/suspended/interrupted/unknown 且无取消意图
- **THEN** 撤销该回合完成候选，记录结构化故障；仅安全且有预算的情况有限恢复，无法恢复进入明确失败或受限状态，不假装成功或无限重试

## ADDED Requirements

### Requirement: 消息顺序与执行独立性

系统 MUST 为同一会话确定消息受理顺序，分开记录回复执行与目标生命周期。任务在途不得阻断普通消息，普通回复结束不得误结束任务；待处理消息与执行状态须可恢复。（R02/R03）

#### Scenario: 工作中继续交流
- **WHEN** 任务执行期间用户补充约束或询问进展
- **THEN** 消息得到持久收据与回复；目标修改由 Master 形成明确受控指令，内部汇合不伪装成用户消息

#### Scenario: 停止范围明确（A11）
- **WHEN** 用户停止当前回答或停止对话中的工作
- **THEN** 界面明确作用对象；停止回答不隐式取消另一任务，取消任务沿用在途写入核对屏障，刷新不重放控制请求
