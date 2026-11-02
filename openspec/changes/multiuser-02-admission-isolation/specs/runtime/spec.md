# Spec Delta: Runtime / R02 R08 R11

## ADDED Requirements

### Requirement: 多用户有界执行与公平调度

系统 MUST 在持久幂等/租约约束下按用户和全局容量调度模型、任务和 Worker，保留同会话 MESSAGE_SUBMIT 先后顺序。容量满时已 202 受理的命令不得丢失。

#### Scenario: 慢用户不拖垮其他用户
- **WHEN** A 的 SDK Submit/工具调用异常缓慢，B 提交消息、审批回复或取消
- **THEN** B 在明确的有界排队时间内获得执行/控制机会；A 的异常不导致全平台队列停摆

#### Scenario: 已受理消息不丢失
- **WHEN** 全局或单用户运行配额耗尽
- **THEN** 新请求可被明确拒绝；已得到 202 的命令持久排队并在容量恢复后按原 ID 继续，不新建第二次副作用

### Requirement: 独立进程崩溃恢复验证

系统 MUST 以不同操作系统进程及不同本地目录验证原生 SDK 会话恢复依赖，只有通过跨进程接管与 writer fencing 试验才允许宣称多业务实例运行。

#### Scenario: 接管与 SDK journal
- **WHEN** A 已接受原生回合后异常退出，B 从共享持久库接管
- **THEN** 复用原 commandId/Binding，不丢状态、不重复外部写入；如果恢复依赖 A 的本地文件，拒绝启用多业务实例部署并报告限制
