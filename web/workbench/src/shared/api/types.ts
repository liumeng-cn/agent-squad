/**
 * 后端公开 DTO 的 TypeScript 镜像；字段名与 api/openapi.json 保持一致。
 */

export type ApiCode = 0 | 400 | 401 | 403 | 404 | 405 | 409 | 410 | 415 | 422 | 429 | 500 | 503

export interface ApiResult<T> {
  code: ApiCode
  data: T
  msg: string
}

export type AccountRole = 'USER' | 'ADMIN' | 'OPERATOR'

export interface AuthView {
  userId: string
  username: string
  roles: AccountRole[]
  csrfToken: string
  mustChangePassword: boolean
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
  taskId: string | null
  status: string
  targetFrameworkTurnId: string | null
  reason: string | null
}

export interface CommandResponse {
  commandId: string
  taskId: string | null
  status: string
  nativeTurnId: string | null
  nativeRunId: string | null
  nativeStatus: string | null
}

export const workerJobStatuses = ['BLOCKED', 'READY', 'RUNNING', 'WAITING_INPUT', 'WAITING_APPROVAL', 'RECOVERING', 'RECOVERY_BLOCKED', 'RECONCILING', 'CANCELLING', 'SUCCEEDED', 'PARTIAL', 'FAILED', 'CANCELLED', 'SKIPPED', 'SUPERSEDED'] as const
export type WorkerJobStatus = typeof workerJobStatuses[number]
export type WorkerSkipReason = 'DEPENDENCY_FAILED'
export interface WorkerJob {
  required?: boolean
  jobId: string
  taskId: string
  nodeKey: string
  planVersion: number
  workerKey: string
  workerVersion: string
  status: WorkerJobStatus
  version: number
  bindingId?: string
  reusedFromJobId?: string
  result?: unknown
  title: string
  dependsOn: string[]
  skipReason?: WorkerSkipReason
}

export interface WorkerDefinition {
  definitionId: string
  workerKey: string
  workerVersion: string
  description: string
  inputSchema: unknown
  outputSchema: unknown
  toolAllowlist: string[]
  modelProfileKey: string
  runtimeProfileKey: string
  status: 'ENABLED' | 'DISABLED' | 'REVOKED'
  version: number
}

export interface WorkerDefinitionPage {
  items: WorkerDefinition[]
  nextCursor: string | null
}

export interface Interaction {
  interactionId: string
  taskId?: string
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

export type AccountStatus = 'ACTIVE' | 'DISABLED'

export interface AdminUserView {
  userId: string
  username: string
  displayName: string | null
  role: AccountRole
  status: AccountStatus
  version: number
  mustChangePassword: boolean
  createTime: string
}

export interface AdminUserPage {
  items: AdminUserView[]
  nextCursor: string | null
}

export interface AdminUserCreateRequest {
  username: string
  displayName?: string
  role?: AccountRole
}

export interface AdminUserCreateResult {
  userId: string
  temporaryPassword: string
}

export interface AdminUserUpdateRequest {
  expectedVersion: number
  displayName?: string
  role?: AccountRole
  status?: AccountStatus
}

export interface ResetPasswordResult {
  temporaryPassword: string
}
export type AcceptanceVerificationType = 'DETERMINISTIC' | 'MODEL_REVIEW' | 'USER_REVIEW'
export type PlanAcceptanceVerdict = 'PASS' | 'FAIL' | 'UNKNOWN'
export type DeterministicAcceptanceCheck = 'REQUIRED_RESULTS_READY'
export interface PlanAcceptanceCriterion {
  criterionId: string
  text: string
  required: boolean
  verificationType: AcceptanceVerificationType
  legacy?: boolean
  deterministicCheck?: DeterministicAcceptanceCheck | null
}
export interface AcceptanceReview {
  criterionId: string
  verdict: PlanAcceptanceVerdict
  evidenceRefs: string[]
}
export interface AcceptanceCheckView {
  criterionId: string
  text: string
  required: boolean
  verificationType: AcceptanceVerificationType
  verdict: PlanAcceptanceVerdict
}
export interface PlanAcceptanceResult extends AcceptanceReview {
  text: string
  required: boolean
  verificationType: AcceptanceVerificationType
  verifier: string
  planVersion: number
  goalRevision: number
}
