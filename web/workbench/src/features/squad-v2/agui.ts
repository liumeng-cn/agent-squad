import type { ChatEvent, ChatRequest } from './chatTypes'

/** AG-UI 传输事件；开放字段只在协议边界读取。 */
export interface AguiEvent {
  type: string
  threadId?: string
  runId?: string
  messageId?: string
  delta?: string
  toolCallId?: string
  toolCallName?: string
  message?: string
  outcome?: { type?: string }
}

/** 标准事件适配现有页面状态，页面布局和组件无需改变。 */
export function projectAgui(event: AguiEvent, request: ChatRequest): ChatEvent[] {
  const base = { sessionId: request.sessionId, requestId: request.requestId,
    messageId: request.requestId, timestamp: Date.now() }
  if ((event.threadId && event.threadId !== request.sessionId)
    || (event.runId && event.runId !== request.requestId)) return []
  switch (event.type) {
    case 'RUN_STARTED': return [{ ...base, type: 'MESSAGE_STARTED', status: 'RUNNING' }]
    case 'TEXT_MESSAGE_CONTENT': return [{ ...base, type: 'MESSAGE_DELTA', text: event.delta ?? '' }]
    case 'REASONING_MESSAGE_CONTENT': return [{ ...base, type: 'THINKING_DELTA',
      modelCallId: event.messageId ?? request.requestId, text: event.delta ?? '' }]
    case 'TOOL_CALL_START': return [{ ...base, type: 'ACTIVITY_UPDATED',
      activityId: event.toolCallId!, kind: 'TOOL', title: event.toolCallName ?? '工具调用', status: 'RUNNING' }]
    // TOOL_CALL_END 仅表示参数生成结束，工具结果到达后才结束页面过程项。
    case 'TOOL_CALL_RESULT': return [{ ...base, type: 'ACTIVITY_UPDATED',
      activityId: event.toolCallId!, kind: 'TOOL', title: '工具调用', status: 'COMPLETED' }]
    case 'RUN_FINISHED': return [{ ...base, type: 'MESSAGE_COMPLETED',
      status: event.outcome?.type === 'interrupt' ? 'WAITING' : 'COMPLETED',
      message: event.outcome?.type === 'interrupt' ? '执行等待交互，当前页面暂不支持恢复' : undefined }]
    case 'RUN_ERROR': return [{ ...base, type: 'MESSAGE_FAILED', status: 'FAILED',
      message: event.message ?? '执行失败' }]
    default: return []
  }
}
