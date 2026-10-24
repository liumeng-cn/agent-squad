/**
 * 后端公开 DTO 的 TypeScript 镜像；字段名与 api/openapi.json 保持一致。
 */

export interface AuthView {
  userId: string
  username: string
  roles: string[]
  csrfToken: string
}

export interface ConversationResponse {
  conversationId: string
  title: string
  generation: number
  status: string
  version: number
  activeTaskId: string | null
}

export interface ConversationPage {
  items: ConversationResponse[]
  nextCursor: string | null
}

export interface TaskResponse {
  taskId: string
  conversationId: string
  status: string
  version: number
  planVersion: number
  answerRevision: number
  cancelRequestedAt: string | null
  blockedReason: string | null
  createdAt: string | null
  updatedAt: string | null
}

export interface GoalTaskPage {
  items: TaskResponse[]
  nextCursor: string | null
}

export interface TaskReceipt {
  taskId: string
  commandId: string
  status: string
}

export interface CommandReceipt {
  commandId: string
  taskId: string
  status: string
  targetFrameworkTurnId: string | null
  reason: string | null
}

export interface CommandResponse {
  commandId: string
  taskId: string
  status: string
  nativeTurnId: string | null
  nativeRunId: string | null
  nativeStatus: string | null
}

export interface WorkerJob {
  jobId: string
  taskId: string
  nodeKey: string
  planVersion: number
  workerKey: string
  workerVersion: string
  status: string
  version: number
  bindingId: string
  result: unknown
}

export interface Interaction {
  interactionId: string
  taskId: string
  jobId: string
  kind: string
  origin: string
  question: string
  answerSchema: unknown
  requestHash: string
  planVersion: number
  expiresAt: string
  status: string
  version: number
  approvalContext: unknown
}

export interface AnswerView {
  revision: number
  text: string
  completed: boolean
}

export interface TaskSnapshot {
  task: TaskResponse
  goalMessage: string
  jobs: WorkerJob[]
  pending: Interaction[]
  answer: AnswerView
  cursor: string
}

export interface TaskEvent {
  sequence: number
  taskId: string
  type: string
  payloadJson: string
  occurredAt: string
}
