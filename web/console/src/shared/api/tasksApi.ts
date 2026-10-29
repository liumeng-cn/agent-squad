import { apiGet, apiPost } from './http'
import type { CommandReceipt, TaskResponse } from './types'

export function getTaskApi(taskId: string): Promise<TaskResponse> {
  return apiGet(`/api/v1/tasks/${taskId}`)
}

export function cancelTaskApi(
  taskId: string,
  expectedVersion: number,
  idempotencyKey: string,
): Promise<CommandReceipt> {
  return apiPost(`/api/v1/tasks/${taskId}/cancel`, { expectedVersion }, { idempotencyKey })
}
