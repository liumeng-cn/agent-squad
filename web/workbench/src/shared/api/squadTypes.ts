/**
 * SquadMsg SSE 协议类型。
 *
 * 与 server/agent-squad-common 中的 Java 枚举和模型保持一致。
 */

export type SquadAgentType = 'MASTER' | 'SUBAGENT'

export type SquadEventCategory =
  | 'LIFECYCLE'
  | 'MODEL'
  | 'MESSAGE'
  | 'THINKING'
  | 'TOOL'
  | 'SUBAGENT'
  | 'CONTROL'
  | 'CONFIRMATION'
  | 'ERROR'
  | 'CUSTOM'

export type SquadEventType =
  | 'TURN_STARTED'
  | 'TURN_COMPLETED'
  | 'TURN_FAILED'
  | 'TURN_CANCELLED'
  | 'AGENT_CREATED'
  | 'AGENT_STARTED'
  | 'AGENT_COMPLETED'
  | 'AGENT_FAILED'
  | 'AGENT_CANCELLED'
  | 'MODEL_CALL_STARTED'
  | 'MODEL_CALL_COMPLETED'
  | 'MODEL_CALL_FAILED'
  | 'MESSAGE_STARTED'
  | 'TEXT_BLOCK_STARTED'
  | 'TEXT_BLOCK_DELTA'
  | 'TEXT_BLOCK_COMPLETED'
  | 'MESSAGE_COMPLETED'
  | 'THINKING_BLOCK_STARTED'
  | 'THINKING_BLOCK_DELTA'
  | 'THINKING_BLOCK_COMPLETED'
  | 'TOOL_CALL_STARTED'
  | 'TOOL_CALL_DELTA'
  | 'TOOL_CALL_COMPLETED'
  | 'TOOL_EXECUTION_STARTED'
  | 'TOOL_RESULT_DELTA'
  | 'TOOL_EXECUTION_COMPLETED'
  | 'TOOL_EXECUTION_FAILED'
  | 'SUBAGENT_CREATED'
  | 'SUBAGENT_STARTED'
  | 'SUBAGENT_COMPLETED'
  | 'SUBAGENT_FAILED'
  | 'EXECUTION_INTERRUPTED'
  | 'EXECUTION_RESUMED'
  | 'CONFIRMATION_REQUIRED'
  | 'CONFIRMATION_RESOLVED'
  | 'ERROR'
  | 'CUSTOM'

export interface SquadAgentRef {
  /** Agent 定义 ID。 */
  agentId: string
  /** 本次创建的 Agent 实例 ID。 */
  agentInstanceId: string
  /** Agent 展示名称。 */
  agentName: string
  /** Agent 在执行树中的角色。 */
  agentType: SquadAgentType
  /** 父 Agent 实例 ID，根 Agent 为空。 */
  parentAgentInstanceId: string | null
  /** 根 Agent 实例 ID。 */
  rootAgentInstanceId: string
  /** 当前 Agent 在执行树中的层级，根节点为 0。 */
  depth: number
}

export interface SquadEventRelation {
  parentEventId?: string
  causedByEventId?: string
  messageId?: string
  blockId?: string
  toolCallId?: string
  executionId?: string
  targetAgentInstanceId?: string
}

/** 所有 Squad SSE 事件共用的外层结构。 */
export interface SquadMsg<TPayload extends Record<string, unknown> = Record<string, unknown>> {
  protocolVersion: string
  eventId: string
  sequence: number
  eventType: SquadEventType
  category: SquadEventCategory
  conversationId: string
  turnId: string
  /** Turn 级系统事件允许为空。 */
  agent: SquadAgentRef | null
  relation: SquadEventRelation | null
  payload: TPayload
  timestamp: string
}

export interface SquadTextDeltaPayload {
  delta: string
}

export interface SquadToolPayload {
  toolCallId: string
  toolName: string
  input?: unknown
  inputDelta?: string
  output?: unknown
  outputDelta?: string
  durationMs?: number
  error?: {
    code?: string
    message: string
  }
}

export interface SquadModelCallPayload {
  model?: string
  provider?: string
  durationMs?: number
  usage?: {
    inputTokens?: number
    outputTokens?: number
    totalTokens?: number
  }
  error?: {
    code?: string
    message: string
  }
}
