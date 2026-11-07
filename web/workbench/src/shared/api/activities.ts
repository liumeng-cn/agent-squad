export const activityStatuses = ['QUEUED', 'RUNNING', 'WAITING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'UNKNOWN'] as const
export const activityGroups = ['PREPARATION', 'PLANNING', 'EXECUTION', 'VERIFICATION', 'DELIVERY'] as const
export const activityTypes = ['CONTEXT_PREPARE', 'FILE_READ', 'SKILL_LOAD', 'DATA_QUERY', 'TASK_CREATE', 'CAPABILITY_LOOKUP', 'PLAN_PUBLISH', 'WORKER_DISPATCH', 'TASK_CHANGE', 'MODEL_CALL', 'SCRIPT_EXECUTE', 'EXTERNAL_ACTION', 'MEMORY_WRITE', 'RESULT_VERIFY', 'OPERATION_RECONCILE', 'RESULT_AGGREGATE', 'RESULT_SUBMIT', 'ARTIFACT_COMMIT', 'ANSWER_PUBLISH', 'USER_INTERACTION', 'TOOL_CALL'] as const
export const activityWaitReasons = ['CAPACITY', 'DEPENDENCY', 'INPUT', 'APPROVAL', 'EXTERNAL_RESULT', 'RESUME_READY', 'RETRY_BACKOFF', 'EVENT_RECOVERY'] as const
export interface ExecutionActivity {
  activityId: string
  sourceMessageId: string | null
  executionId: string | null
  taskId: string | null
  jobId: string | null
  planVersion: number
  type: typeof activityTypes[number]
  group: typeof activityGroups[number]
  status: typeof activityStatuses[number]
  waitReason: typeof activityWaitReasons[number] | null
  summary: string
  startedAt: string | null
  endedAt: string | null
  version: number
}
export function decodeActivity(value: unknown): ExecutionActivity | undefined {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return
  const a = value as Record<string, unknown>
  if (typeof a.activityId !== 'string' || !a.activityId || typeof a.summary !== 'string' || a.summary.length > 120
      || !Number.isSafeInteger(a.version) || (a.version as number) < 1
      || !Number.isSafeInteger(a.planVersion) || (a.planVersion as number) < 0
      || !activityStatuses.some(s => s === a.status) || !activityGroups.some(g => g === a.group)
      || !activityTypes.some(t => t === a.type)
      || !(a.waitReason === null || activityWaitReasons.some(r => r === a.waitReason))
      || ['sourceMessageId', 'executionId', 'taskId', 'jobId', 'startedAt', 'endedAt'].some(k => a[k] !== null && typeof a[k] !== 'string')) return
  return a as unknown as ExecutionActivity
}
