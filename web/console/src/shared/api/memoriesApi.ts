import { apiDelete, apiGet, apiPatch, apiPost } from './http'

export interface Memory {
  memoryId: string
  key: string
  content: string
  kind: 'EXPLICIT'
  status: 'ACTIVE' | 'DELETED' | 'EXPIRED'
  version: number
  expiresAt?: string | null
  sourceMessageId?: string | null
}

export interface MemoryPage {
  items: Memory[]
  nextCursor?: string | null
  memoryRevision: number
  affectedScopes: number
}

export interface MemoryMutationResult {
  memory: Memory
  memoryRevision: number
  affectedScopes: number
  impact: string
}

export interface MemoryDraft {
  key: string
  content: string
  expiresAt?: string | null
}

export function listMemories(cursor?: string): Promise<MemoryPage> {
  const query = new URLSearchParams({ limit: '20' })
  if (cursor) query.set('cursor', cursor)
  return apiGet(`/api/v1/memories?${query}`)
}

export function createMemory(draft: MemoryDraft): Promise<MemoryMutationResult> {
  return apiPost('/api/v1/memories', draft)
}

export function updateMemory(memory: Memory, content: string, expiresAt?: string | null): Promise<MemoryMutationResult> {
  return apiPatch(`/api/v1/memories/${memory.memoryId}`, { expectedVersion: memory.version, content, expiresAt })
}

export function deleteMemory(memory: Memory): Promise<MemoryMutationResult> {
  return apiDelete(`/api/v1/memories/${memory.memoryId}?expectedVersion=${memory.version}`)
}
