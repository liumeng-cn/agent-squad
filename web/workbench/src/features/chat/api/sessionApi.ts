import { apiGet, apiPatch, apiPost } from '../../../shared/http/http'
import type { SessionPage, SessionSummary } from '../model/session'
export const createSession = (sessionId: string) => apiPost<SessionSummary>('/api/v2/chat/sessions', { sessionId })
export const listSessions = (cursor?: string) => apiGet<SessionPage>(`/api/v2/chat/sessions?limit=30${cursor ? '&cursor=' + encodeURIComponent(cursor) : ''}`)
export const renameSession = (sessionId: string, title: string, version: number) => apiPatch<SessionSummary>(`/api/v2/chat/sessions/${encodeURIComponent(sessionId)}`, { title, version })
