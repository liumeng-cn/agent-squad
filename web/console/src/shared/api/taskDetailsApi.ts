import { apiGet, apiPost } from './http'
import type { CommandReceipt, Interaction } from './types'

export interface Artifact {
  artifactId: string
  fileName: string
  mediaType: string
  sizeBytes: number
  sha256: string
  status: 'READY'
  expiresAt?: string
}
export interface Operation {
  operationId: string
  taskId: string
  jobId?: string
  toolKey: string
  effect: string
  status: string
  requestHash: string
  version: number
  summary: string
}
export interface OperationDetail {
  operation: Operation
  argumentsPreview: Record<string, unknown>
  resultSummary: string
  evidenceRefs: string[]
  canReconcile: boolean
}
export function listOperations(taskId: string, cursor?: string): Promise<{ items: Operation[]; nextCursor?: string }> {
  const query = new URLSearchParams({ limit: '100' })
  if (cursor) query.set('cursor', cursor)
  return apiGet(`/api/v1/tasks/${taskId}/operations?${query}`)
}
export function getOperation(id: string): Promise<OperationDetail> {
  return apiGet(`/api/v1/operations/${id}`)
}
export function reconcileOperation(detail: OperationDetail, disposition: string, evidenceRefs: string[], note: string): Promise<Operation> {
  return apiPost(`/api/v1/operations/${detail.operation.operationId}/reconcile`, {
    expectedVersion: detail.operation.version, disposition, evidenceRefs, note,
  })
}
export function listArtifacts(taskId: string): Promise<Artifact[]> {
  return apiGet(`/api/v1/tasks/${taskId}/artifacts`)
}
export function listInteractions(taskId: string, cursor?: string): Promise<{ items: Interaction[]; nextCursor?: string }> {
  const query = new URLSearchParams({ limit: '100' })
  if (cursor) query.set('cursor', cursor)
  return apiGet(`/api/v1/tasks/${taskId}/interactions?${query}`)
}
export function respondInteraction(id: string, expectedVersion: number, answer: unknown, key: string): Promise<CommandReceipt> {
  return apiPost(`/api/v1/interactions/${id}/responses`, { expectedVersion, answer }, { idempotencyKey: key })
}

export function uploadArtifact(file: File, key: string): Promise<Artifact> {
  const form = new FormData()
  form.append('file', file)
  return apiPost('/api/v1/artifacts', form, { idempotencyKey: key })
}
