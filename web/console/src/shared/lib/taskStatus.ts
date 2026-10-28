/**
 * 任务状态机公共裁决；业务组件不自行拼终态判断。
 */
export const TERMINAL_STATUSES: ReadonlySet<string> = new Set([
  'SUCCEEDED',
  'PARTIAL',
  'FAILED',
  'CANCELLED',
])

const STOPPABLE_STATUSES: ReadonlySet<string> = new Set([
  'QUEUED',
  'RUNNING',
  'WAITING_WORKERS',
  'WAITING_INPUT',
  'WAITING_APPROVAL',
])

const RESUMABLE_STATUSES: ReadonlySet<string> = new Set([
  'RECOVERY_BLOCKED',
  'RECONCILING',
])

export function isTerminal(status: string): boolean {
  return TERMINAL_STATUSES.has(status)
}

export function canStop(status: string): boolean {
  return STOPPABLE_STATUSES.has(status)
}

export function canResume(status: string): boolean {
  return RESUMABLE_STATUSES.has(status)
}

export const TASK_STATUS_TEXT: Record<string, string> = {
  QUEUED: '已接收，等待执行',
  RUNNING: '正在处理',
  BLOCKED: '等待处理',
  READY: '等待处理',
  SUPERSEDED: '已调整',
  WAITING_WORKERS: '正在处理',
  WAITING_INPUT: '等待补充信息',
  WAITING_APPROVAL: '等待操作确认',
  RECOVERY_BLOCKED: '需要进一步处理',
  RECONCILING: '结果待核对',
  CANCELLING: '正在停止',
  SUCCEEDED: '已完成',
  PARTIAL: '部分完成',
  FAILED: '处理失败',
  CANCELLED: '已取消',
}

export function statusText(status: string): string {
  return TASK_STATUS_TEXT[status] ?? status
}

export function statusBadgeClass(status: string): string {
  if (status === 'SUCCEEDED') {
    return 'badge-success'
  }
  if (status === 'FAILED' || status === 'CANCELLED') {
    return 'badge-danger'
  }
  if (status === 'CANCELLING') {
    return 'badge-stopping'
  }
  if (isTerminal(status)) {
    return 'badge-neutral'
  }
  return 'badge-running'
}
