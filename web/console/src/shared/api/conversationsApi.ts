import { apiDelete, apiGet, apiPost } from './http'
import type { ConversationPage, ConversationResponse, GoalTaskPage, TaskReceipt } from './types'

export function listConversationsApi(cursor?: string, limit = 20): Promise<ConversationPage> {
  const query = new URLSearchParams()
  if (cursor) {
    query.set('cursor', cursor)
  }
  query.set('limit', String(limit))
  return apiGet(`/api/v1/conversations?${query.toString()}`)
}

export function createConversationApi(title: string): Promise<ConversationResponse> {
  return apiPost('/api/v1/conversations', { title })
}

export function getConversationApi(conversationId: string): Promise<ConversationResponse> {
  return apiGet(`/api/v1/conversations/${conversationId}`)
}

export interface DeleteOptions {
  idempotencyKey: string
  ifMatch: string
}

export function deleteConversationApi(conversationId: string, options: DeleteOptions): Promise<void> {
  return apiDelete(`/api/v1/conversations/${conversationId}`, options)
}

export function listConversationTasksApi(
  conversationId: string,
  cursor?: string,
  limit = 20,
): Promise<GoalTaskPage> {
  const query = new URLSearchParams()
  if (cursor) {
    query.set('cursor', cursor)
  }
  query.set('limit', String(limit))
  return apiGet(`/api/v1/conversations/${conversationId}/tasks?${query.toString()}`)
}

export function createTaskApi(
  conversationId: string,
  message: string,
  idempotencyKey: string,
): Promise<TaskReceipt> {
  return apiPost(`/api/v1/conversations/${conversationId}/tasks`, { message }, { idempotencyKey })
}
