import type { SquadAgentRef, SquadMsg } from '../../../shared/api/squadTypes'
import type { SquadTimelineItem } from './squadExecution'

const EVENT_LABELS: Record<string, string> = {
  AGENT_START: '开始执行',
  AGENT_END: '执行结束',
  AGENT_RESULT: '返回最终结果',
  MODEL_CALL_START: '调用模型',
  MODEL_CALL_END: '模型调用结束',
  TEXT_BLOCK_START: '文本开始',
  TEXT_BLOCK_DELTA: '文本输出',
  TEXT_BLOCK_END: '文本完成',
  THINKING_BLOCK_START: '思考摘要开始',
  THINKING_BLOCK_DELTA: '思考摘要',
  THINKING_BLOCK_END: '思考摘要完成',
  DATA_BLOCK_START: '数据开始',
  DATA_BLOCK_DELTA: '数据输出',
  DATA_BLOCK_END: '数据完成',
  TOOL_CALL_START: '准备调用工具',
  TOOL_CALL_DELTA: '生成工具参数',
  TOOL_CALL_END: '工具参数已生成',
  TOOL_RESULT_START: '工具开始返回',
  TOOL_RESULT_TEXT_DELTA: '工具返回内容',
  TOOL_RESULT_DATA_DELTA: '工具返回数据',
  TOOL_RESULT_END: '工具返回完成',
  EXCEED_MAX_ITERS: '达到执行轮数上限',
  REQUIRE_USER_CONFIRM: '需要用户确认',
  REQUIRE_EXTERNAL_EXECUTION: '等待外部执行',
  USER_CONFIRM_RESULT: '收到确认结果',
  EXTERNAL_EXECUTION_RESULT: '收到外部执行结果',
  REQUEST_STOP: '收到停止请求',
  SUBAGENT_EXPOSED: '创建协作成员',
  HINT_BLOCK: '执行提示',
  ALL_TOOLS_DENIED: '工具调用被拒绝',
  CUSTOM: '自定义事件',
}

export function eventLabel(eventType: string): string {
  return EVENT_LABELS[eventType] || eventType || '未知事件'
}

export function agentDisplayName(agent: SquadAgentRef | null): string {
  if (!agent) return '系统'
  if (agent.agentName) return agent.agentName
  return agent.agentType === 'MASTER' ? '主协调员' : '协作成员'
}

export function timelineItemLabel(item: SquadTimelineItem): string {
  if (item.kind === 'block') {
    if (item.blockType === 'THINKING') return '思考摘要'
    if (item.blockType === 'DATA') return '数据输出'
    return '文本输出'
  }
  if (item.kind === 'tool') return item.toolName
  return eventLabel(item.event.eventType)
}

export function timelineItemSummary(item: SquadTimelineItem): string {
  if (item.kind === 'block') return item.text || '等待内容'
  if (item.kind === 'tool') {
    if (item.status === 'COMPLETED') return item.outputText || '工具已返回'
    if (item.status === 'RETURNING') return item.outputText || '正在接收工具结果'
    if (item.status === 'READY') return item.inputText || '等待工具结果'
    return item.inputText || '正在准备工具参数'
  }
  return eventSummary(item.event)
}

export function eventSummary(event: SquadMsg): string {
  const payload = event.payload || {}
  if (typeof payload.hint === 'string' && payload.hint) return payload.hint
  if (typeof payload.model === 'string') return '模型：' + payload.model
  if (payload.usage !== undefined) return '用量：' + formatJson(payload.usage)
  if (typeof payload.reason === 'string') return payload.reason
  if (typeof payload.error === 'string') return payload.error
  if (payload.error && typeof payload.error === 'object') {
    const error = payload.error as { message?: unknown }
    if (typeof error.message === 'string') return error.message
  }
  if (event.eventType === 'SUBAGENT_EXPOSED') {
    const child = payload.childAgent as SquadAgentRef | undefined
    return child?.agentName ? '创建协作成员：' + child.agentName : '创建协作成员'
  }
  if (payload.result !== undefined) return resultText(payload.result)
  if (payload.data !== undefined) return formatJson(payload.data)
  if (typeof payload.name === 'string') return payload.name
  if (payload.value !== undefined) return formatJson(payload.value)
  return '收到事件'
}

export function resultText(result: unknown): string {
  if (typeof result === 'string') return result
  if (!result || typeof result !== 'object') return formatJson(result)
  const value = result as { content?: unknown[] }
  if (Array.isArray(value.content)) {
    const texts = value.content
      .filter((item): item is { type?: string; text?: string } => Boolean(item && typeof item === 'object'))
      .filter(item => item.type === 'text' && typeof item.text === 'string')
      .map(item => item.text || '')
    if (texts.length) return texts.join('\n')
  }
  return formatJson(result)
}

export function formatJson(value: unknown): string {
  try {
    return JSON.stringify(value, null, 2) ?? ''
  } catch {
    return String(value)
  }
}

export function formatEventTime(timestamp: string): string {
  if (!timestamp) return ''
  const date = new Date(timestamp)
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleTimeString([], {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
}

export function agentStatusLabel(status: string): string {
  if (status === 'RUNNING') return '执行中'
  if (status === 'ENDED') return '已结束'
  return '等待开始'
}
