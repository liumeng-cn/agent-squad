# squad-message-protocol Specification

## Purpose

定义 Squad 服务向 Web 客户端推送 Agent 执行事件的统一协议，隔离 AgentScope 原生事件与前端展示模型。V1 优先保证结构完整、归属清晰和可调试性，暂不以压缩传输体积为目标。

## Requirements

### Requirement: 统一 SquadMsg 外层结构

系统 SHALL 使用统一的 SquadMsg Envelope 承载所有 SSE 业务事件，包含协议版本、事件 ID、Turn 内序号、事件类型、事件分类、会话 ID、Turn ID、Agent 身份、事件关联、事件 Payload 和发生时间。

#### Scenario: 推送 Agent 事件

- **WHEN** AgentScope 或 Squad 产生可展示事件
- **THEN** 服务端将其转换为 SquadMsg 并通过统一 SSE 事件名称推送
- **AND** SSE 事件名称固定为 squad.message
- **AND** 具体业务事件类型由 SquadMsg.eventType 表达

### Requirement: Agent 实例父子关系

系统 SHALL 使用 Agent 实例作为前端执行树节点，而不是按 Agent 定义合并节点。

#### Scenario: 创建 SubAgent

- **WHEN** Master 创建一个 SubAgent 实例
- **THEN** 服务端发送 AGENT_CREATED 事件
- **AND** SquadAgentRef 包含 agentId、agentInstanceId、agentName、agentType、parentAgentInstanceId、rootAgentInstanceId 和 depth
- **AND** 前端使用 agentInstanceId 唯一标识节点，并使用 parentAgentInstanceId 建立父子关系

#### Scenario: 同一 Agent 定义被多次创建

- **WHEN** 同一个 agentId 被创建为多个并行实例
- **THEN** 每个实例具有不同的 agentInstanceId
- **AND** 前端分别展示这些实例，不合并执行状态

### Requirement: 事件关联

系统 SHALL 使用独立的 SquadEventRelation 描述事件之间的关联。

#### Scenario: 文本增量

- **WHEN** Agent 输出文本增量
- **THEN** 事件通过 relation.messageId 和 relation.blockId 关联到对应消息与内容块

#### Scenario: 工具执行

- **WHEN** Agent 发起工具调用并产生执行结果
- **THEN** 工具调用与结果通过 relation.toolCallId 关联

#### Scenario: 子 Agent 由父事件触发

- **WHEN** 某事件导致创建或唤醒另一个 Agent 实例
- **THEN** 通过 relation.causedByEventId 和 relation.targetAgentInstanceId 描述触发关系

### Requirement: 事件类型由代码枚举约束

系统 SHALL 在 Java 中使用 SquadEventType、SquadEventCategory 和 SquadAgentType 枚举，在 TypeScript 中使用对应的字面量联合类型。

#### Scenario: 新增事件类型

- **WHEN** 新增 SquadMsg 事件类型
- **THEN** Java 枚举、TypeScript 类型与 OpenAPI Schema 同步更新
- **AND** 不允许业务代码散落使用未定义的事件类型字符串

### Requirement: 思考内容单独表达

系统 SHALL 使用独立的 THINKING_BLOCK_STARTED、THINKING_BLOCK_DELTA 和 THINKING_BLOCK_COMPLETED 事件表达可公开的思考摘要或执行进度。

#### Scenario: 推送思考内容

- **WHEN** 框架或应用提供可公开的思考摘要
- **THEN** 服务端使用 THINKING 类事件推送
- **AND** 不得将模型内部隐藏推理内容作为普通消息或思考内容推送

### Requirement: 内存事件缓冲

系统 SHALL 在 V1 使用内存缓冲事件，不承诺跨连接或跨实例恢复。

#### Scenario: SSE 连接断开

- **WHEN** 客户端连接中断
- **THEN** 服务端不承诺从断点补发已错过事件
- **AND** 关闭 SSE 连接不等价于取消 Agent 执行

### Requirement: 事件顺序

系统 SHALL 为同一 Turn 内的事件分配全局递增 sequence，并通过 SSE id 携带相同序号。

#### Scenario: 多 Agent 并发产生事件

- **WHEN** Master 和多个 SubAgent 并发产生事件
- **THEN** 服务端在发布事件时分配唯一且递增的 Turn 内 sequence
- **AND** JSON Envelope 的 sequence 与 SSE id 一致

### Requirement: 未知扩展事件

系统 SHALL 保留事件 Payload 的开放扩展能力。

#### Scenario: 前端收到未知事件

- **WHEN** 客户端收到无法识别的 eventType 或 category
- **THEN** 客户端保留原始事件数据并使用通用事件样式展示，不因单个未知事件中断整个事件流
