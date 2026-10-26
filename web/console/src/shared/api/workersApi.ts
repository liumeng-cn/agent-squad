import { apiGet, apiPatch, apiPost } from './http'
import type { WorkerDefinition, WorkerDefinitionPage } from './types'

export interface WorkerDefinitionDraft {
  workerKey: string
  workerVersion: string
  description: string
  inputSchema: unknown
  outputSchema: unknown
  toolAllowlist: string[]
  modelProfileKey: string
  runtimeProfileKey: string
}

export function listWorkerDefinitionsApi(cursor?: string): Promise<WorkerDefinitionPage> {
  const query = cursor ? `?cursor=${encodeURIComponent(cursor)}` : ''
  return apiGet<WorkerDefinitionPage>(`/api/v1/worker-definitions${query}`)
}

export function publishWorkerDefinitionApi(
  draft: WorkerDefinitionDraft,
  idempotencyKey: string,
): Promise<WorkerDefinition> {
  return apiPost<WorkerDefinition>('/api/v1/worker-definitions', draft, { idempotencyKey })
}

export function changeWorkerStatusApi(
  definition: WorkerDefinition,
  status: WorkerDefinition['status'],
  idempotencyKey: string,
): Promise<WorkerDefinition> {
  return apiPatch<WorkerDefinition>(
    `/api/v1/worker-definitions/${encodeURIComponent(definition.definitionId)}`,
    { status, expectedVersion: definition.version },
    { idempotencyKey },
  )
}
