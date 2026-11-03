import { apiGet, apiPatch, apiPost } from './http'

export interface Connection {
  connectionId: string
  name: string
  provider: string
  environment: string
  resourcePolicy: Record<string, unknown>
  secretPresent: boolean
  revision: number
  enabled: boolean
}
export interface ConnectionPage { items: Connection[]; nextCursor?: string | null }
export interface ConnectionDraft {
  name: string
  provider: string
  environment: string
  resourcePolicy: Record<string, unknown>
  secret: string
}
export interface ConnectionUpdate {
  expectedVersion: number
  enabled?: boolean
  resourcePolicy?: Record<string, unknown>
  secret?: string
}

export function listConnections(cursor?: string): Promise<ConnectionPage> {
  const query = new URLSearchParams({ limit: '20' })
  if (cursor) query.set('cursor', cursor)
  return apiGet(`/api/v1/connections?${query}`)
}
export function getConnection(connectionId: string): Promise<Connection> {
  return apiGet(`/api/v1/connections/${encodeURIComponent(connectionId)}`)
}
export function createConnection(draft: ConnectionDraft, idempotencyKey: string): Promise<Connection> {
  return apiPost('/api/v1/connections', draft, { idempotencyKey })
}
export function updateConnection(connectionId: string, update: ConnectionUpdate, idempotencyKey: string): Promise<Connection> {
  return apiPatch(`/api/v1/connections/${encodeURIComponent(connectionId)}`, update, { idempotencyKey })
}
