export const executionStatuses = ['QUEUED', 'RUNNING', 'WAITING_INPUT', 'WAITING_APPROVAL', 'RECOVERING', 'RECONCILING', 'CANCELLING', 'SUCCEEDED', 'PARTIAL', 'FAILED', 'CANCELLED'] as const
export type ChatExecutionStatus = typeof executionStatuses[number]
export const executionActions = ['STOP', 'RETRY', 'PROVIDE_INPUT', 'APPROVE', 'CONNECT', 'RECONCILE'] as const
export type ChatExecutionAction = typeof executionActions[number]
