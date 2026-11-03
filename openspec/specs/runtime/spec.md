# 任务运行集成（Runtime）

## Purpose

任务运行集成能力负责 GoalTask、Command 与 Binding guard 的持久受理、产品状态机、恢复入口与取消语义，以运行协议为状态权威与故障窗口依据。（M02）

## Requirements

### Requirement: 最终表结构与数据库管理边界

项目 MUST 以 `server/agent-squad-server/src/main/resources/db/migration/V1__baseline.sql` 维护唯一最终表结构；开发环境由应用启动时重放该 baseline 建表，不维护迁移版本链与历史数据回填。本地开发库可直接修改表结构，修改后 MUST 同步回 baseline；生产与共享数据库的创建、变更与数据处理由用户独立管理。

#### Scenario: 应用启动
- **WHEN** 应用连接已配置的数据库启动
- **THEN** 使用现有表结构，不自动执行表结构 SQL 或历史迁移


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

### Requirement: 取消语义

系统 MUST 遵守以下约束：取消后阻断新工作，明确已发生的操作，不把取消视为回滚。（R11）

#### Scenario: 取消与派发竞争（A11）
- **WHEN** 取消与工具派发、用户回复或恢复竞争
- **THEN** 屏障后不发新业务，已 IN_FLIGHT 操作核对完才收尾

#### Scenario: 取消不补偿
- **WHEN** 取消流程执行
- **THEN** 不执行补偿（补偿须另建授权 Operation），所有业务 dispatch 封闭、执行停止或失效、未知操作处理后置 CANCELLED

### Requirement: 产品状态机与恢复入口

系统 MUST 遵守以下约束：状态由平台业务事实决定，framework Run 状态仅为其一证据；resume 仅接受受限状态并校验版本、配置、授权、记忆代际与原生 pending/uncertain。

#### Scenario: 恢复受理
- **WHEN** resume 目标处于可恢复状态且校验通过
- **THEN** 受理为 QUEUED 并写入 RESUME 命令；已排队返回已有回执

#### Scenario: 终态不可恢复
- **WHEN** 目标已 SUCCEEDED/PARTIAL/FAILED/CANCELLED 时请求 resume
- **THEN** 拒绝，重做创建新任务

### Requirement: 消息顺序与执行独立性

系统 MUST 为同一会话确定消息受理顺序，分开记录回复执行与目标生命周期。任务在途不得阻断普通消息，普通回复结束不得误结束任务；待处理消息与执行状态须可恢复。（R02/R03）

#### Scenario: 工作中继续交流
- **WHEN** 任务执行期间用户补充约束或询问进展
- **THEN** 消息得到持久收据与回复；目标修改由 Master 形成明确受控指令，内部汇合不伪装成用户消息

#### Scenario: 停止范围明确（A11）
- **WHEN** 用户停止当前回答或停止对话中的工作
- **THEN** 界面明确作用对象；停止回答不隐式取消另一任务，取消任务沿用在途写入核对屏障，刷新不重放控制请求


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
