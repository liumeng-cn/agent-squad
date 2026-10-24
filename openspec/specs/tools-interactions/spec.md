# 工具、审批与外部操作（Tools & Interactions）

## Purpose

受控执行能力负责连接、操作账本、追问、审批与核对；不承诺外部事务恰好一次，未知副作用先核对。（M07）

## Requirements

### Requirement: 缺信息追问与敏感操作确认

缺信息追问、敏感操作确认，答复后继续原目标。（R07）

#### Scenario: INPUT 追问
- **WHEN** 模型缺信息调用 request_input
- **THEN** 建立持久 Interaction，用户答复后 respond 继续原 Turn，保留 jobId/taskId 不新建目标

#### Scenario: 挂起后重启（A07）
- **WHEN** INPUT/APPROVAL 挂起后实例重启
- **THEN** 查原 requestId、原 Job/Task，重复答复不重复执行

### Requirement: 操作账本与幂等

框架 tool 调用进入 wrapper 时按 bindingId/frameworkTurnId/toolCallId 查唯一 Operation；WRITE 还须 businessIntentKey+requestHash 在 taskId+toolKey 范围幂等。

#### Scenario: WRITE 需审批（A07）
- **WHEN** 工具 effect=WRITE
- **THEN** 预检建立 PREPARED→WAITING_APPROVAL 的 Operation 与 Interaction，批准后才发送外部请求

#### Scenario: READ 直接执行（A08）
- **WHEN** 工具 effect=READ
- **THEN** 直接执行并完整记录账本参数与结果

#### Scenario: 幂等去重
- **WHEN** 同业务写入以相同 intentKey 重放
- **THEN** 在 taskId+toolKey 范围内不重复创建外部副作用

### Requirement: 审批单一决定

工具预检在原生 ASK 前建立稳定 Operation/Interaction，原生 pending 按 binding/Turn/toolCallId 绑定同一记录；批准与拒绝只有一条决定，平台拒绝不能被原生 ALLOW 绕过。

#### Scenario: 批准驱动执行
- **WHEN** 用户批准且 requestHash 匹配
- **THEN** 执行器重验授权后执行，不再要求第二次确认

#### Scenario: 拒绝无外部变更（B05）
- **WHEN** 用户拒绝审批
- **THEN** Operation=CANCELLED，无外部变更

### Requirement: 未知副作用核对

IN_FLIGHT 后失联统一 UNKNOWN，不直接失败也不自动重发；KEY 确认外部幂等、QUERY 按关联号查证、NONE 需人工证据处置。（R08）

#### Scenario: 回执丢失（A08）
- **WHEN** 写操作成功但本地回执丢失
- **THEN** Operation=UNKNOWN 阻止结束，查证后映射原 toolCallId 继续

#### Scenario: 人工核对
- **WHEN** 人工核对未知操作
- **THEN** 只提供 CONFIRMED_SUCCEEDED/CONFIRMED_NOT_EXECUTED/UNRESOLVED，不能用“忽略错误”清掉未知事实

#### Scenario: 执行器事务隔离
- **WHEN** 连接器执行外部请求
- **THEN** 数据库事务不包外部网络，IN_FLIGHT 先独立提交再发外部请求

### Requirement: 取消不视为回滚

取消后阻断新工作，明确已发生的操作；确认未执行后重试仍重验授权、审批与幂等约束。（R11）

#### Scenario: 取消与在途操作
- **WHEN** 取消时操作已 IN_FLIGHT
- **THEN** 转核对，不强制回滚，已发生的操作明确记录
