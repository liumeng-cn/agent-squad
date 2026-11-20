export type ChatStatus = 'RUNNING' | 'COMPLETED' | 'FAILED' | 'WAITING' | 'INTERRUPTED'
export type ActivityKind = 'MODEL' | 'TOOL' | 'CONTEXT' | 'PROGRESS'

interface EventEnvelope {
  sessionId: string
  requestId: string
  messageId: string
  timestamp: number
}
export type ChatEvent = EventEnvelope & (
  | { type: 'MESSAGE_STARTED' | 'MESSAGE_COMPLETED' | 'MESSAGE_FAILED'; status: ChatStatus; message?: string }
  | { type: 'MESSAGE_DELTA' | 'MESSAGE_CONTENT'; text: string }
  | { type: 'THINKING_DELTA'; modelCallId: string; text: string }
  | { type: 'ACTIVITY_UPDATED'; activityId: string; kind: ActivityKind; title: string; status: ChatStatus }
  | { type: 'HEARTBEAT' }
)
export interface ChatRequest {
  sessionId: string
  requestId: string
  message: string
}
export interface ChatActivity {
  id: string
  kind: ActivityKind
  title: string
  status: ChatStatus
  startedAt: number
  finishedAt?: number
}
export interface ChatMessage {
  turnId?: string
  inputStatus?: InputStatus
  id: string
  role: 'user' | 'assistant'
  content: string
  status: ChatStatus
  startedAt: number
  finishedAt?: number
  thinkingBlocks?: { modelCallId: string; content: string }[]
  activities: ChatActivity[]
  error?: string
}

/** 原生会话公开状态，和 Java/OpenAPI 枚举保持一致。 */
export type ExecutionStatus = 'IDLE' | 'QUEUED' | ChatStatus
export type InputStatus = 'ACCEPTED' | 'APPLIED' | 'UNAPPLIED'
export type InputDisposition = 'NEW_TURN' | 'STEERING'
export interface ChatReceipt {
  convId: string
  sessionId: string
  interactionId: string
  turnId: string
  inputId: string
  disposition: InputDisposition
}
export interface ConversationInput {
  messageIds: string[]
  id: string
  turnId: string
  content: string
  status: InputStatus
}
export interface SessionMessage {
  id: string
  turnId: string
  role: 'USER' | 'ASSISTANT'
  finishedAt?: number | null
  content: string
  status: ExecutionStatus
  startedAt: number
  activities: { id: string; title: string; status: ExecutionStatus; startedAt: number; finishedAt?: number | null }[]
  thinkingBlocks: { modelCallId: string; content: string }[]
}
export interface ChatSession {
  interactions?: ChatInteraction[]
  answeredRequestIds?: string[]
  acceptedInputIds?: string[]
  summary?: import('./conv').ConvSummary
  sessionId: string
  cursor: number
  inputCursor: number
  turnId?: string
  runId?: string
  status: ExecutionStatus
  messages: SessionMessage[]
  inputs: ConversationInput[]
}
export interface ChatInteraction {
  requestId: string
  turnId: string
  question: string
  options: string[]
}
export interface ChatAnswer {
  convId: string
  turnId: string
  requestId: string
  answer: string
}
export interface SubmitChatInput {
  convId: string
  interactionId: string
  observedTurnId?: string
  message: string
}

export const inputStatusDescriptions: Record<InputStatus, string> = {
  ACCEPTED: '已收到，等待应用', APPLIED: '已应用', UNAPPLIED: '尚未应用，执行已结束',
}
