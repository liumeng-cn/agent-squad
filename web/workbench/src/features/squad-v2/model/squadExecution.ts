import type { SquadAgentRef, SquadMsg } from '../../../shared/api/squadTypes'

export type SquadAgentStatus = 'NOT_STARTED' | 'RUNNING' | 'ENDED'
export type SquadBlockType = 'TEXT' | 'THINKING' | 'DATA'
export type SquadToolStatus = 'CALLING' | 'READY' | 'RETURNING' | 'COMPLETED'

export interface SquadAgentExecution {
  agent: SquadAgentRef
  status: SquadAgentStatus
  startedAt: string | null
  endedAt: string | null
}

interface TimelineItemBase {
  id: string
  sequence: number | null
  order: number
  agent: SquadAgentRef | null
  timestamp: string
}

export interface SquadEventTimelineItem extends TimelineItemBase {
  kind: 'event'
  event: SquadMsg
}

export interface SquadBlockTimelineItem extends TimelineItemBase {
  kind: 'block'
  blockType: SquadBlockType
  blockId: string
  text: string
  completed: boolean
}

export interface SquadToolTimelineItem extends TimelineItemBase {
  kind: 'tool'
  toolCallId: string
  toolName: string
  inputText: string
  outputText: string
  status: SquadToolStatus
}

export type SquadTimelineItem =
  | SquadEventTimelineItem
  | SquadBlockTimelineItem
  | SquadToolTimelineItem

export interface SquadExecutionState {
  agents: Map<string, SquadAgentExecution>
  timeline: SquadTimelineItem[]
  answerBlocks: SquadBlockTimelineItem[]
  finalAnswer: string | null
  seenEventIds: Set<string>
  blocksByKey: Map<string, SquadBlockTimelineItem>
  activeBlocks: Map<string, string>
  toolsByKey: Map<string, SquadToolTimelineItem>
  nextOrder: number
}

export function createSquadExecutionState(): SquadExecutionState {
  return {
    agents: new Map(),
    timeline: [],
    answerBlocks: [],
    finalAnswer: null,
    seenEventIds: new Set(),
    blocksByKey: new Map(),
    activeBlocks: new Map(),
    toolsByKey: new Map(),
    nextOrder: 0,
  }
}

export function resetSquadExecution(state: SquadExecutionState): void {
  state.agents.clear()
  state.timeline.splice(0)
  state.answerBlocks.splice(0)
  state.finalAnswer = null
  state.seenEventIds.clear()
  state.blocksByKey.clear()
  state.activeBlocks.clear()
  state.toolsByKey.clear()
  state.nextOrder = 0
}

export function applySquadEvent(state: SquadExecutionState, event: SquadMsg): boolean {
  const identity = event.eventId
    ? 'event:' + event.eventId
    : event.sequence !== null
      ? 'sequence:' + event.conversationId + ':' + event.turnId + ':' + event.sequence
      : null

  if (identity && state.seenEventIds.has(identity)) return false
  if (identity) state.seenEventIds.add(identity)

  const order = state.nextOrder
  state.nextOrder += 1
  const agent = event.agent || null
  const agentInstanceId = agent?.agentInstanceId || ''
  const type = event.eventType

  if (agentInstanceId && agent) {
    const existing = state.agents.get(agentInstanceId)
    if (existing) {
      existing.agent = { ...existing.agent, ...agent }
    } else {
      state.agents.set(agentInstanceId, {
        agent: { ...agent },
        status: 'NOT_STARTED',
        startedAt: null,
        endedAt: null,
      })
    }
  }

  if (type === 'SUBAGENT_EXPOSED') {
    const child = event.payload.childAgent as SquadAgentRef | undefined
    if (child?.agentInstanceId && !state.agents.has(child.agentInstanceId)) {
      state.agents.set(child.agentInstanceId, {
        agent: { ...child },
        status: 'NOT_STARTED',
        startedAt: null,
        endedAt: null,
      })
    }
  }

  if (type === 'AGENT_START' && agentInstanceId) {
    const execution = state.agents.get(agentInstanceId)
    if (execution) {
      execution.status = 'RUNNING'
      execution.startedAt = event.timestamp || null
      execution.endedAt = null
    }
  }

  if (type === 'AGENT_END' && agentInstanceId) {
    const execution = state.agents.get(agentInstanceId)
    if (execution) {
      execution.status = 'ENDED'
      execution.endedAt = event.timestamp || null
    }
    completeAgentBlocks(state, agentInstanceId)
  }

  if (type === 'AGENT_RESULT' && event.payload.result !== undefined) {
    // AGENT_RESULT 是最终结果。它可以覆盖此前的流式预览，不能因已有增量而被丢弃。
    state.finalAnswer = resultTextOf(event.payload.result)
    return true
  }

  const blockType = blockTypeOf(type)
  if (blockType) {
    if (!agentInstanceId) {
      addEventItem(state, event, order)
      return true
    }
    appendBlockEvent(state, event, agent, agentInstanceId, blockType, order)
    return true
  }

  if (isToolEvent(type)) {
    const toolCallId = stringValue(event.payload.toolCallId)
      || stringValue(event.relation?.toolCallId)
    if (toolCallId && agentInstanceId) {
      appendToolEvent(state, event, agent, agentInstanceId, toolCallId, order)
    } else {
      addEventItem(state, event, order)
    }
    return true
  }

  addEventItem(state, event, order)
  return true
}

export function getSquadTimeline(state: SquadExecutionState): SquadTimelineItem[] {
  return [...state.timeline].sort((left, right) => {
    const leftSequence = left.sequence ?? Number.MAX_SAFE_INTEGER
    const rightSequence = right.sequence ?? Number.MAX_SAFE_INTEGER
    return leftSequence - rightSequence || left.order - right.order
  })
}

export function getSquadAnswerText(state: SquadExecutionState): string {
  if (state.finalAnswer !== null) return state.finalAnswer
  return [...state.answerBlocks]
    .sort((left, right) => {
      const leftSequence = left.sequence ?? Number.MAX_SAFE_INTEGER
      const rightSequence = right.sequence ?? Number.MAX_SAFE_INTEGER
      return leftSequence - rightSequence || left.order - right.order
    })
    .map(block => block.text)
    .join('')
}

function appendBlockEvent(
  state: SquadExecutionState,
  event: SquadMsg,
  agent: SquadAgentRef | null,
  agentInstanceId: string,
  blockType: SquadBlockType,
  order: number,
): void {
  const type = event.eventType
  const payload = event.payload || {}
  const activeKey = agentInstanceId + ':' + blockType
  const providedBlockId = stringValue(payload.blockId) || stringValue(event.relation?.blockId)
  const blockId = providedBlockId
    || state.activeBlocks.get(activeKey)
    || ('implicit:' + activeKey + ':' + String(event.sequence ?? order))
  const key = agentInstanceId + ':' + blockType + ':' + blockId
  let block = state.blocksByKey.get(key)

  if (!block) {
    block = {
      id: key,
      sequence: event.sequence,
      order,
      agent,
      timestamp: event.timestamp || '',
      kind: 'block',
      blockType,
      blockId,
      text: '',
      completed: false,
    }
    state.blocksByKey.set(key, block)
    if (agent?.agentType === 'MASTER' && blockType !== 'THINKING') {
      state.answerBlocks.push(block)
    } else {
      state.timeline.push(block)
    }
  }

  if (type.endsWith('_DELTA')) {
    if (typeof payload.delta === 'string') {
      block.text += payload.delta
    } else if (payload.data !== undefined) {
      block.text += stringifyValue(payload.data)
    }
  } else if (type.endsWith('_END')) {
    block.completed = true
    if (state.activeBlocks.get(activeKey) === blockId) state.activeBlocks.delete(activeKey)
  }

  if (type.endsWith('_START') && !providedBlockId) {
    state.activeBlocks.set(activeKey, blockId)
  }
  if (!providedBlockId && type.endsWith('_DELTA') && !state.activeBlocks.has(activeKey)) {
    state.activeBlocks.set(activeKey, blockId)
  }
}

function appendToolEvent(
  state: SquadExecutionState,
  event: SquadMsg,
  agent: SquadAgentRef | null,
  agentInstanceId: string,
  toolCallId: string,
  order: number,
): void {
  const key = agentInstanceId + ':' + toolCallId
  const payload = event.payload || {}
  const type = event.eventType
  let tool = state.toolsByKey.get(key)

  if (!tool) {
    tool = {
      id: key,
      sequence: event.sequence,
      order,
      agent,
      timestamp: event.timestamp || '',
      kind: 'tool',
      toolCallId,
      toolName: stringValue(payload.toolCallName) || stringValue(payload.toolName) || '工具调用',
      inputText: '',
      outputText: '',
      status: 'CALLING',
    }
    state.toolsByKey.set(key, tool)
    state.timeline.push(tool)
  }

  const name = stringValue(payload.toolCallName) || stringValue(payload.toolName)
  if (name) tool.toolName = name

  if (type === 'TOOL_CALL_START') {
    tool.status = 'CALLING'
    if (payload.input !== undefined) tool.inputText = stringifyValue(payload.input)
  } else if (type === 'TOOL_CALL_DELTA') {
    const delta = stringValue(payload.inputDelta) || stringValue(payload.delta)
    if (delta) tool.inputText += delta
  } else if (type === 'TOOL_CALL_END') {
    tool.status = 'READY'
  } else if (type === 'TOOL_RESULT_START') {
    tool.status = 'RETURNING'
  } else if (type === 'TOOL_RESULT_TEXT_DELTA') {
    tool.status = 'RETURNING'
    tool.outputText += stringValue(payload.delta) || stringValue(payload.outputDelta)
  } else if (type === 'TOOL_RESULT_DATA_DELTA') {
    tool.status = 'RETURNING'
    const data = payload.data !== undefined ? payload.data : payload.output
    if (data !== undefined) tool.outputText += stringifyValue(data)
    else tool.outputText += stringValue(payload.delta)
  } else if (type === 'TOOL_RESULT_END') {
    tool.status = 'COMPLETED'
    if (!tool.outputText && payload.result !== undefined) {
      tool.outputText = resultTextOf(payload.result)
    }
  }
}

function completeAgentBlocks(state: SquadExecutionState, agentInstanceId: string): void {
  for (const block of state.blocksByKey.values()) {
    if (block.agent?.agentInstanceId === agentInstanceId) block.completed = true
  }
  for (const [key] of state.activeBlocks) {
    if (key.startsWith(agentInstanceId + ':')) state.activeBlocks.delete(key)
  }
}

function addEventItem(state: SquadExecutionState, event: SquadMsg, order: number): void {
  const id = event.eventId
    || ('sequence:' + event.conversationId + ':' + event.turnId + ':' + String(event.sequence ?? order))
  state.timeline.push({
    id,
    sequence: event.sequence,
    order,
    agent: event.agent || null,
    timestamp: event.timestamp || '',
    kind: 'event',
    event,
  })
}

function blockTypeOf(type: string): SquadBlockType | null {
  if (type.startsWith('TEXT_BLOCK_')) return 'TEXT'
  if (type.startsWith('THINKING_BLOCK_')) return 'THINKING'
  if (type.startsWith('DATA_BLOCK_')) return 'DATA'
  return null
}

function isToolEvent(type: string): boolean {
  return type.startsWith('TOOL_CALL_') || type.startsWith('TOOL_RESULT_')
}

function resultTextOf(result: unknown): string {
  if (typeof result === 'string') return result
  if (!result || typeof result !== 'object') return stringifyValue(result)
  const value = result as { content?: unknown[] }
  if (Array.isArray(value.content)) {
    const texts = value.content
      .filter((item): item is { type?: string; text?: string } => Boolean(item && typeof item === 'object'))
      .filter(item => item.type === 'text' && typeof item.text === 'string')
      .map(item => item.text || '')
    if (texts.length) return texts.join('\n')
  }
  return stringifyValue(result)
}

function stringifyValue(value: unknown): string {
  if (typeof value === 'string') return value
  if (value === undefined || value === null) return ''
  try {
    return JSON.stringify(value, null, 2) ?? String(value)
  } catch {
    return String(value)
  }
}

function stringValue(value: unknown): string {
  return typeof value === 'string' ? value : ''
}
