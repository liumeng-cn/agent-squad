import { apiGet, apiPost } from './http'
import type { CommandReceipt, CommandResponse, TaskResponse, TaskSnapshot } from './types'

export function getTaskApi(taskId: string): Promise<TaskResponse> {
  return apiGet(`/api/v1/tasks/${taskId}`)
}

export function getTaskSnapshotApi(taskId: string): Promise<TaskSnapshot> {
  return apiGet(`/api/v1/tasks/${taskId}/snapshot`)
}

export function cancelTaskApi(
  taskId: string,
  expectedVersion: number,
  idempotencyKey: string,
): Promise<CommandReceipt> {
  return apiPost(`/api/v1/tasks/${taskId}/cancel`, { expectedVersion }, { idempotencyKey })
}

export function resumeTaskApi(
  taskId: string,
  expectedVersion: number,
  idempotencyKey: string,
): Promise<CommandReceipt> {
  return apiPost(`/api/v1/tasks/${taskId}/resume`, { expectedVersion }, { idempotencyKey })
}

export function getCommandApi(commandId: string): Promise<CommandResponse> {
  return apiGet(`/api/v1/commands/${commandId}`)
}
