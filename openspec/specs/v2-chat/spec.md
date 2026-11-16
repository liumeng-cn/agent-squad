# v2-chat Specification

## Purpose

使用 AgentScope 原生会话命令完成持续对话、运行中补充要求和停止，并通过独立 SSE 观察公开状态。

## Requirements

### Requirement: 会话与交互身份

系统 SHALL 按服务端用户、固定 Agent 和 sessionId 定位会话，interactionId 标识一次发送。新任务直接以 interactionId 调用原生 submit，重复提交相同输入不得重复执行；复用标识提交不同输入必须拒绝。

#### Scenario: 连续发送

- **WHEN** 同一会话完成首轮并发送下一条消息
- **THEN** 原生上下文保留，返回新的 turnId；恢复执行可使用同一 turnId 下的新 runId

### Requirement: 运行中补充要求

系统 SHALL 在携带 observedTurnId 时调用原生 steer；明确收到准入竞争拒绝且日志确认目标轮次完成后，才将同一输入改为 submit。未知存储失败不得降级提交，不承诺 steer 网络重试幂等。

#### Scenario: Master、工具或子 Agent 运行

- **WHEN** 用户补充当前 Master 轮次的要求
- **THEN** 公开原生插话收据及 ACCEPTED、APPLIED、UNAPPLIED 状态
- **AND** 不修改已派发的工具参数，不自动将 Master 插话转发到子 Agent

### Requirement: 提交与订阅分离

系统 SHALL 由 POST /api/v2/chat/messages 返回 ApiResult 收据，后台执行由原生 AgentSession 持有。独立 GET SSE 返回带执行和输入游标的完整公开快照，同一实例相同会话共享读取，订阅结束后释放读取资源。

#### Scenario: 断线和刷新

- **WHEN** 页面刷新或连接断开后重新订阅
- **THEN** 恢复公开历史和当前状态，不重新提交输入，不取消后台执行，不把观察故障标记为执行失败
- **AND** 快照仅含用户正文、助手正文、工具状态及主模型明确返回的思考文本，不含提示词、工具原始载荷或加密推理元数据

### Requirement: 停止和显式继续

系统 SHALL 使用 runId 约束原生 interrupt；页面显示正在停止直到原生日志确认结果。中断或失败轮次可显式请求 resume，未确认工具结果和待答交互仍遵循 SDK 准入，不自动重试外部操作。

#### Scenario: 停止期间的展示

- **WHEN** 停止请求已接收但执行尚未结束
- **THEN** 保留已有正文并展示正在停止；不能仅凭停止收据宣布执行结束

### Requirement: 阅读优先与草稿保留

系统 SHALL 保留现有聊天布局、可折叠过程和向上阅读行为；运行中允许发送补充，并提供独立停止入口。草稿只在明确接收后清空。

#### Scenario: 接收结果未知

- **WHEN** 发送请求超时或服务故障导致接收结果未知
- **THEN** 保留草稿和已生成内容，提示核对会话，不自动重发或自动开启下一轮

#### Scenario: 插话保持阅读连续性

- **WHEN** 运行中的任务收到补充并开始后续模型调用
- **THEN** 已显示的正文和思考按稳定回复段保留，补充与后续输出按发生顺序追加
- **AND** 客户端复用消息与过程对象，不因快照重建清空内容或重置向上阅读位置

#### Scenario: 清空当前页面会话

- **WHEN** 空闲或已结束的页面点击清空
- **THEN** 页面使用新 sessionId，并断开旧观察；不删除原生持久记录，不因迟到旧快照恢复旧内容
- **AND** 执行中、发送中或接收结果未知时不可清空
