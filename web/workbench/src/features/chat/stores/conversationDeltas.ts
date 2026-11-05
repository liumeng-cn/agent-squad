import type { ChatExecution, ChatMessage, ConversationEvent } from '../../../shared/api/chatApi'

import { executionActions, executionStatuses } from '../../../shared/api/chatTypes'

export type ConversationDelta =
  | { type: 'message.accepted'; message: ChatMessage; execution: ChatExecution }
  | { type: 'message.completed'; message: ChatMessage }
  | { type: 'execution.queued' | 'execution.status_changed'; execution: ChatExecution }
  | { type: 'conversation.title_updated'; title: string; titleVersion: number }

const record = (value: unknown): value is Record<string, unknown> => !!value && typeof value === 'object' && !Array.isArray(value)
const id = (value: unknown): value is string => typeof value === 'string' && value.length > 0
const nullableId = (value: unknown) => value === null || id(value)
const version = (value: unknown) => Number.isSafeInteger(value) && (value as number) >= 0

function message(value: unknown): value is ChatMessage {
  return record(value) && id(value.messageId) && version(value.sequence) && (value.sequence as number) > 0
    && (value.role === 'USER' || value.role === 'ASSISTANT') && typeof value.text === 'string'
    && version(value.revision) && typeof value.completed === 'boolean' && typeof value.occurredAt === 'string'
    && nullableId(value.sourceMessageId) && nullableId(value.executionId) && nullableId(value.taskId)
    && Array.isArray(value.artifacts)
}
function execution(value: unknown): value is ChatExecution {
  return record(value) && id(value.executionId) && id(value.sourceMessageId) && version(value.version)
    && executionStatuses.some(status => status === value.status) && nullableId(value.answerMessageId)
    && nullableId(value.failureId) && Array.isArray(value.allowedActions)
    && value.allowedActions.every(action => executionActions.some(allowed => allowed === action))
}

/** 先验证整个增量，再交给store修改状态，避免错误载荷推进水位或留下半次更新。 */
export function decodeConversationDelta(event: ConversationEvent): ConversationDelta | undefined {
  const payload = event.payload
  if (!record(payload)) return
  if (event.type === 'conversation.title_updated') {
    if (typeof payload.title === 'string' && version(payload.titleVersion)) {
      return { type: event.type, title: payload.title, titleVersion: payload.titleVersion as number }
    }
    return
  }
  if (payload.schemaVersion !== 2) return
  switch (event.type) {
    case 'message.accepted':
      if (message(payload.message) && payload.message.role === 'USER' && execution(payload.execution)
          && payload.message.messageId === payload.execution.sourceMessageId
          && payload.message.executionId === payload.execution.executionId) {
        return { type: event.type, message: payload.message, execution: payload.execution }
      }
      return
    case 'message.completed':
      if (message(payload.message) && payload.message.role === 'ASSISTANT' && payload.message.completed) {
        return { type: event.type, message: payload.message }
      }
      return
    case 'execution.queued':
    case 'execution.status_changed':
      if (execution(payload.execution)) return { type: event.type, execution: payload.execution }
  }
}
