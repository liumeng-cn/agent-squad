import { apiDelete, apiGet } from './http'
import type { ConversationPage } from './types'

export function listConversationsApi(cursor?: string, limit = 20): Promise<ConversationPage> {
  const query = new URLSearchParams()
  if (cursor) {
    query.set('cursor', cursor)
  }
  query.set('limit', String(limit))
  return apiGet(`/api/v1/conversations?${query.toString()}`)
}

export interface DeleteOptions {
  idempotencyKey: string
  ifMatch: string
}

export function deleteConversationApi(conversationId: string, options: DeleteOptions): Promise<void> {
  return apiDelete(`/api/v1/conversations/${conversationId}`, options)
}
