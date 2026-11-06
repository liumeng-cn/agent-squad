import { apiGet, apiPost } from './http'
import type { AcceptanceCheckView, CommandReceipt, TaskResponse, WorkerJob } from './types'

export function getTaskApi(taskId: string): Promise<TaskResponse> {
  return apiGet(`/api/v1/tasks/${taskId}`)
}

export function getTaskJobs(taskId: string, planVersion?: number): Promise<{ items: WorkerJob[]; nextCursor: string | null; acceptanceChecks: AcceptanceCheckView[]; planVersion: number; currentPlanVersion: number }> {
  const query = planVersion === undefined ? '' : `?planVersion=${planVersion}`
  return apiGet(`/api/v1/tasks/${encodeURIComponent(taskId)}/jobs${query}`)
}

export function cancelTaskApi(
  taskId: string,
  expectedVersion: number,
  idempotencyKey: string,
): Promise<CommandReceipt> {
  return apiPost(`/api/v1/tasks/${taskId}/cancel`, { expectedVersion }, { idempotencyKey })
}
