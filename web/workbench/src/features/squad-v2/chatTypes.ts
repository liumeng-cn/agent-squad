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
