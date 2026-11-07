import type { ChatExecution, ChatMessage, ConversationEvent, TaskDirectiveView } from '../../../shared/api/chatApi'
import { decodeActivity, type ExecutionActivity } from '../../../shared/api/activities'

import { executionActions, executionStatuses } from '../../../shared/api/chatTypes'

export type ConversationDelta =
  | { type: 'activity.updated'; activity: ExecutionActivity }
  | { type: 'message.accepted'; message: ChatMessage; execution: ChatExecution }
  | { type: 'message.completed'; message: ChatMessage }
  | { type: 'execution.queued' | 'execution.status_changed'; execution: ChatExecution }
  | { type: 'conversation.title_updated'; title: string; titleVersion: number }
  | { type: 'directive.status_changed'; directive: TaskDirectiveView }
  | { type: 'plan.published' | 'job.reused'; taskId: string; planVersion: number }

const record = (value: unknown): value is Record<string, unknown> => !!value && typeof value === 'object' && !Array.isArray(value)
const id = (value: unknown): value is string => typeof value === 'string' && value.length > 0
const nullableId = (value: unknown) => value === null || id(value)
const version = (value: unknown) => Number.isSafeInteger(value) && (value as number) >= 0

function message(value: unknown): value is ChatMessage {
  return record(value) && id(value.messageId) && version(value.sequence) && (value.sequence as number) > 0
    && (value.role === 'USER' || value.role === 'ASSISTANT') && typeof value.text === 'string'
    && version(value.revision) && typeof value.completed === 'boolean' && typeof value.occurredAt === 'string'
    && nullableId(value.sourceMessageId) && nullableId(value.executionId) && nullableId(value.taskId)
    && Array.isArray(value.artifacts)
}
function execution(value: unknown): value is ChatExecution {
  return record(value) && id(value.executionId) && id(value.sourceMessageId) && version(value.version)
    && executionStatuses.some(status => status === value.status) && nullableId(value.answerMessageId)
    && nullableId(value.failureId) && Array.isArray(value.allowedActions)
    && value.allowedActions.every(action => executionActions.some(allowed => allowed === action))
}

/** 先验证整个增量，再交给store修改状态，避免错误载荷推进水位或留下半次更新。 */
export function decodeConversationDelta(event: ConversationEvent): ConversationDelta | undefined {
  const payload = event.payload
  if (!record(payload)) return
  if (event.type === 'conversation.title_updated') {
    if (typeof payload.title === 'string' && version(payload.titleVersion)) {
      return { type: event.type, title: payload.title, titleVersion: payload.titleVersion as number }
    }
    return
  }
  if (payload.schemaVersion !== 2) return
  switch (event.type) {
    case 'activity.updated': {
      const activity = decodeActivity(payload.activity)
      if (activity) return { type: event.type, activity }
      return
    }
    case 'directive.status_changed': {
      const d = payload.directive
      if (record(d) && id(d.directiveId) && id(d.taskId) && id(d.sourceMessageId)
          && ['ACCEPTED', 'APPLIED', 'REJECTED'].includes(d.status as string)
          && ['NONE', 'WAITING_SAFE_POINT', 'WAITING_OPERATION', 'WAITING_USER', 'WAITING_COORDINATOR', 'COORDINATING'].includes(d.waitReason as string)
          && (d.reasonCode === null || typeof d.reasonCode === 'string')
          && (d.reasonMessage === null || typeof d.reasonMessage === 'string')
          && (d.status === 'APPLIED' ? version(d.appliedVersion) && (d.appliedVersion as number) > 0 && version(d.goalRevision) && (d.goalRevision as number) > 0 && d.waitReason === 'NONE'
            : d.appliedVersion === null && d.goalRevision === null)
          && (d.status === 'ACCEPTED' ? d.waitReason !== 'NONE' : d.waitReason === 'NONE')
          && (d.status !== 'REJECTED' || id(d.reasonCode) && id(d.reasonMessage))) {
        return { type: event.type, directive: d as unknown as TaskDirectiveView }
      }
      return
    }
    case 'plan.published':
      if (id(payload.taskId) && version(payload.planVersion) && (payload.planVersion as number) > 0
          && Array.isArray(payload.jobIds) && payload.jobIds.every(id) && payload.jobIds.length <= 16
          && (payload.joinPolicy === 'ALL_REQUIRED' || payload.joinPolicy === 'BEST_EFFORT')
          && Array.isArray(payload.acceptanceCriteria) && payload.acceptanceCriteria.length <= 20) {
        return { type: event.type, taskId: payload.taskId, planVersion: payload.planVersion as number }
      }
      return
    case 'job.reused':
      if (id(payload.taskId) && version(payload.planVersion) && (payload.planVersion as number) > 0
          && id(payload.jobId) && id(payload.reusedFromJobId)) {
        return { type: event.type, taskId: payload.taskId, planVersion: payload.planVersion as number }
      }
      return
    case 'message.accepted':
      if (message(payload.message) && payload.message.role === 'USER' && execution(payload.execution)
          && payload.message.messageId === payload.execution.sourceMessageId
          && payload.message.executionId === payload.execution.executionId) {
        return { type: event.type, message: payload.message, execution: payload.execution }
      }
      return
    case 'message.completed':
      if (message(payload.message) && payload.message.role === 'ASSISTANT' && payload.message.completed) {
        return { type: event.type, message: payload.message }
      }
      return
    case 'execution.queued':
    case 'execution.status_changed':
      if (execution(payload.execution)) return { type: event.type, execution: payload.execution }
  }
}
