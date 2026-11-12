/**
 * SquadMsg SSE 协议类型。
 *
 * 与 server/agent-squad-common 中的 SquadMsg、SquadAgentRef、
 * SquadExecutionRef 和 SquadEventRelation 保持一致。
 *
 * eventType 在 Java 传输模型中是 String。这里列出已知原生事件供提示，
 * 同时保留开放字符串，确保 SDK 或平台新增事件时旧前端仍能接收。
 */

export type SquadAgentType = 'MASTER' | 'SUBAGENT'

export type KnownSquadEventCategory =
  | 'LIFECYCLE'
  | 'MODEL'
  | 'MESSAGE'
  | 'THINKING'
  | 'DATA'
  | 'TOOL'
  | 'SUBAGENT'
  | 'CONTROL'
  | 'CONFIRMATION'
  | 'HINT'
  | 'ERROR'
  | 'CUSTOM'

export type SquadEventCategory = KnownSquadEventCategory | (string & {})

export type KnownSquadEventType =
  | 'AGENT_START'
  | 'AGENT_END'
  | 'AGENT_RESULT'
  | 'MODEL_CALL_START'
  | 'MODEL_CALL_END'
  | 'TEXT_BLOCK_START'
  | 'TEXT_BLOCK_DELTA'
  | 'TEXT_BLOCK_END'
  | 'THINKING_BLOCK_START'
  | 'THINKING_BLOCK_DELTA'
  | 'THINKING_BLOCK_END'
  | 'DATA_BLOCK_START'
  | 'DATA_BLOCK_DELTA'
  | 'DATA_BLOCK_END'
  | 'TOOL_CALL_START'
  | 'TOOL_CALL_DELTA'
  | 'TOOL_CALL_END'
  | 'TOOL_RESULT_START'
  | 'TOOL_RESULT_TEXT_DELTA'
  | 'TOOL_RESULT_DATA_DELTA'
  | 'TOOL_RESULT_END'
  | 'EXCEED_MAX_ITERS'
  | 'REQUIRE_USER_CONFIRM'
  | 'REQUIRE_EXTERNAL_EXECUTION'
  | 'USER_CONFIRM_RESULT'
  | 'EXTERNAL_EXECUTION_RESULT'
  | 'REQUEST_STOP'
  | 'SUBAGENT_EXPOSED'
  | 'HINT_BLOCK'
  | 'ALL_TOOLS_DENIED'
  | 'CUSTOM'

export type SquadEventType = KnownSquadEventType | (string & {})

/** Agent 定义与本次运行实例的身份。 */
export interface SquadAgentRef {
  agentId: string
  agentInstanceId: string
  agentName: string
  agentType: SquadAgentType
  parentAgentInstanceId: string | null
  rootAgentInstanceId: string
  depth: number
}

/** AgentScope 原生 ExecutionIdentity 的平台传输副本。 */
export interface SquadExecutionRef {
  agentId: string | null
  sessionId: string | null
  turnId: string | null
  runId: string | null
}

/** 事件之间的关联信息；只填写当前事件实际拥有的关联字段。 */
export interface SquadEventRelation {
  parentEventId?: string | null
  causedByEventId?: string | null
  messageId?: string | null
  blockId?: string | null
  toolCallId?: string | null
  executionId?: string | null
  targetAgentInstanceId?: string | null
}

/** 所有 Squad SSE 事件共用的外层结构。 */
export interface SquadMsg<TPayload extends Record<string, unknown> = Record<string, unknown>> {
  protocolVersion: string
  eventId: string
  sequence: number | null
  eventType: SquadEventType
  category: SquadEventCategory
  conversationId: string
  turnId: string
  agent: SquadAgentRef | null
  nativeExecution: SquadExecutionRef | null
  nativeSource: string | null
  relation: SquadEventRelation | null
  payload: TPayload
  timestamp: string
}

export interface SquadTextBlockPayload {
  blockId?: string
  delta?: string
  data?: unknown
}

export interface SquadToolPayload {
  toolCallId?: string
  toolCallName?: string
  toolName?: string
  input?: unknown
  inputDelta?: string
  output?: unknown
  outputDelta?: string
  delta?: string
  data?: unknown
  result?: unknown
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
