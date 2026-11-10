import type { ModelSelection } from './modelsApi'
import type { ExecutionActivity } from './activities'
import { apiGet, apiPatch, apiPost } from './http'
import type { Artifact } from './taskDetailsApi'
import type { ConversationResponse, Interaction, TaskResponse } from './types'
import type { ChatExecutionStatus, ChatExecutionAction } from './chatTypes'

export interface ConversationHeader extends ConversationResponse {
  titleSource: string
  titleVersion: number
  lastActivityAt: string
}
export interface ChatMessage {
  messageId: string
  sequence: number
  role: 'USER' | 'ASSISTANT'
  text: string
  revision: number
  completed: boolean
  sourceMessageId: string | null
  executionId: string | null
  taskId: string | null
  occurredAt: string
  artifacts: Artifact[]
}
export interface ChatExecution {
  executionId: string
  sourceMessageId: string
  status: ChatExecutionStatus
  version: number
  answerMessageId: string | null
  failureId: string | null
  allowedActions: ChatExecutionAction[]
}
export interface ExecutionFailure {
  failureId: string
  executionId: string | null
  expectedVersion: number | null
  taskId: string | null
  sourceMessageId: string | null
  category: string
  code: string
  actor: string
  safeMessage: string
  diagnosticId: string
  status: string
  attempt: number
  maxAttempts: number
  nextAttemptAt: string | null
  allowedActions: string[]
}
export type TaskDirectiveStatus = 'ACCEPTED' | 'APPLIED' | 'REJECTED'
export type TaskDirectiveWaitReason = 'NONE' | 'WAITING_SAFE_POINT' | 'WAITING_OPERATION' | 'WAITING_USER' | 'WAITING_COORDINATOR' | 'COORDINATING'
export interface TaskDirectiveView {
  directiveId: string
  taskId: string
  sourceMessageId: string
  status: TaskDirectiveStatus
  waitReason: TaskDirectiveWaitReason
  appliedVersion: number | null
  goalRevision: number | null
  reasonCode: string | null
  reasonMessage: string | null
}
export interface ChatSnapshot {
  activities?: ExecutionActivity[]
  conversation: ConversationHeader
  messages: ChatMessage[]
  nextBeforeSeq: number | null
  activeExecutions: ChatExecution[]
  activeTask: TaskResponse | null
  interactions: Interaction[]
  deliverables: Artifact[]
  failures: ExecutionFailure[]
  operationIds: string[]
  executions: ChatExecution[]
  directives: TaskDirectiveView[]
  taskStates: ChatTaskState[]
  cursor: string
}
export interface ChatTaskState { taskId: string; sourceMessageId: string | null; status: string; version: number }
export interface MessageReceipt {
  conversationId: string
  messageId: string
  executionId: string
  commandId: string
  status: string
  cursor: string
}
export interface ConversationEvent {
  conversationId: string
  generation: number
  sequence: number
  type: string
  occurredAt: string
  payload: Record<string, unknown>
}
export function conversationPage(cursor?: string): Promise<{ items: ConversationHeader[]; nextCursor: string | null }> {
  const query = new URLSearchParams({ limit: '40' })
  if (cursor) query.set('cursor', cursor)
  return apiGet(`/api/v1/conversations?${query}`)
}
export function chatSnapshot(id: string, before?: number): Promise<ChatSnapshot> {
  const query = new URLSearchParams({ limit: '40' })
  if (before !== undefined) query.set('beforeMessageSeq', String(before))
  return apiGet(`/api/v1/conversations/${encodeURIComponent(id)}/snapshot?${query}`)
}
export function sendMessage(id: string | null, text: string, artifactIds: string[], key: string, selection?: ModelSelection): Promise<MessageReceipt> {
  return apiPost(id ? `/api/v1/conversations/${encodeURIComponent(id)}/messages` : '/api/v1/conversations/messages', { text, artifactIds, ...selection }, { idempotencyKey: key })
}
export function renameConversation(id: string, title: string, expectedTitleVersion: number): Promise<ConversationHeader> {
  return apiPatch(`/api/v1/conversations/${encodeURIComponent(id)}/title`, { title, expectedTitleVersion })
}
export function stopAnswer(id: string, expectedVersion: number, key: string): Promise<unknown> {
  return apiPost(`/api/v1/executions/${encodeURIComponent(id)}/cancel`, { expectedVersion }, { idempotencyKey: key })
}
export function retryAnswer(id: string, expectedVersion: number, key: string): Promise<MessageReceipt> {
  return apiPost(`/api/v1/executions/${encodeURIComponent(id)}/retry`, { expectedVersion }, { idempotencyKey: key })
}
