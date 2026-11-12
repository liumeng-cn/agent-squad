<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import SafeMarkdown from '../../../shared/ui/SafeMarkdown.vue'
import { getCsrfToken } from '../../../shared/api/csrf'

type AgentRef = {
  agentId?: string
  agentInstanceId?: string
  agentName?: string
  agentType?: string
  parentAgentInstanceId?: string | null
  rootAgentInstanceId?: string
  depth?: number
}

type SquadEvent = {
  protocolVersion?: string
  eventId?: string
  sequence?: number
  eventType?: string
  category?: string
  conversationId?: string
  turnId?: string
  agent?: AgentRef | null
  nativeExecution?: Record<string, unknown> | null
  nativeSource?: string | null
  relation?: Record<string, unknown> | null
  payload?: Record<string, unknown>
  timestamp?: string
}

type StreamBlockKind = 'TEXT' | 'THINKING' | 'DATA' | 'TOOL_CALL' | 'TOOL_RESULT'
type StreamBlock = {
  key: string
  kind: StreamBlockKind
  label: string
  text: string
  completed: boolean
  timestamp?: string
}
type AgentActivity =
  | { key: string; kind: 'event'; event: SquadEvent }
  | { key: string; kind: 'block'; block: StreamBlock }
type AgentNode = {
  key: string
  agent: AgentRef
  parentKey: string | null
  children: AgentNode[]
  activities: AgentActivity[]
  startedAt: number | null
  durationMs: number | null
}
type AgentRow = { node: AgentNode; depth: number }
type AnswerBlock = {
  key: string
  blockId: string
  kind: 'TEXT' | 'DATA'
  text: string
  completed: boolean
}

const draft = ref('')
const composerInput = ref<HTMLTextAreaElement | null>(null)
const lastPrompt = ref('')
const busy = ref(false)
const error = ref('')
const agentRoots = ref<AgentNode[]>([])
const answerBlocks = ref<AnswerBlock[]>([])
const answerFallback = ref('')
const executionExpanded = ref(true)
const executionClock = ref(Date.now())
const conversationScroller = ref<HTMLElement | null>(null)
const conversationContent = ref<HTMLElement | null>(null)
const followLatest = ref(true)
const expandedGroups = ref<Record<string, boolean>>({})
const showJumpToLatest = computed(() =>
  !followLatest.value && (visibleAgents.value.length > 0 || hasAnswerContent.value || busy.value),
)
const hasAnswerContent = computed(() => answerBlocks.value.some(block => block.text.length > 0))
const visibleAnswerFallback = computed(() => hasAnswerContent.value ? '' : answerFallback.value)
const showAnswer = computed(() => busy.value || hasAnswerContent.value || Boolean(answerFallback.value))

const agentIndex = new Map<string, AgentNode>()
const pendingChildren = new Map<string, AgentNode[]>()
const streamBlockIndex = new Map<string, StreamBlock>()
const answerBlockIndex = new Map<string, AnswerBlock>()
const seenEventIds = new Set<string>()

let controller: AbortController | null = null
let executionTicker: ReturnType<typeof setInterval> | undefined
let conversationResizeObserver: ResizeObserver | undefined
let followFrame: number | undefined

const visibleAgents = computed<AgentRow[]>(() => {
  const result: AgentRow[] = []
  const visit = (node: AgentNode, depth: number, ancestors: Set<string>) => {
    if (ancestors.has(node.key)) return
    const nextAncestors = new Set(ancestors)
    nextAncestors.add(node.key)
    result.push({ node, depth })
    for (const child of node.children) visit(child, depth + 1, nextAncestors)
  }
  for (const root of agentRoots.value) visit(root, 0, new Set<string>())
  return result
})

const eventLabels: Record<string, string> = {
  AGENT_START: '开始执行',
  AGENT_END: '执行完成',
  AGENT_RESULT: '返回结果',
  MODEL_CALL_START: '调用模型',
  MODEL_CALL_END: '模型调用完成',
  TEXT_BLOCK_START: '文本开始',
  TEXT_BLOCK_DELTA: '文本输出',
  TEXT_BLOCK_END: '文本完成',
  THINKING_BLOCK_START: '思考内容开始',
  THINKING_BLOCK_DELTA: '思考内容',
  THINKING_BLOCK_END: '思考内容完成',
  DATA_BLOCK_START: '数据开始',
  DATA_BLOCK_DELTA: '数据输出',
  DATA_BLOCK_END: '数据完成',
  TOOL_CALL_START: '工具调用',
  TOOL_CALL_DELTA: '工具参数',
  TOOL_CALL_END: '工具调用参数完成',
  TOOL_RESULT_START: '工具返回',
  TOOL_RESULT_TEXT_DELTA: '工具返回内容',
  TOOL_RESULT_DATA_DELTA: '工具返回数据',
  TOOL_RESULT_END: '工具返回完成',
  EXCEED_MAX_ITERS: '达到执行轮数上限',
  REQUIRE_USER_CONFIRM: '需要用户确认',
  REQUIRE_EXTERNAL_EXECUTION: '需要外部执行',
  USER_CONFIRM_RESULT: '收到确认结果',
  EXTERNAL_EXECUTION_RESULT: '收到外部执行结果',
  REQUEST_STOP: '收到停止请求',
  SUBAGENT_EXPOSED: '创建协作成员',
  HINT_BLOCK: '进度消息',
  ALL_TOOLS_DENIED: '工具调用被拒绝',
  CUSTOM: '自定义事件',
}

function eventLabel(type?: string) {
  return type ? (eventLabels[type] || '执行事件') : '执行事件'
}

function agentInstanceKey(agent?: AgentRef | null) {
  return agent?.agentInstanceId || agent?.agentId || ''
}

function isMasterAgent(agent?: AgentRef | null) {
  if (!agent) return false
  if (agent.agentType === 'MASTER') return true
  const id = agentInstanceKey(agent)
  return Boolean(id && !agent.parentAgentInstanceId && agent.rootAgentInstanceId === id)
}

function agentName(node: AgentNode) {
  if (node.agent.agentName) return node.agent.agentName
  return isMasterAgent(node.agent) ? '主协调员' : '协作成员'
}

function canAttach(node: AgentNode, parent: AgentNode) {
  let current: AgentNode | undefined = parent
  while (current) {
    if (current.key === node.key) return false
    current = current.parentKey ? agentIndex.get(current.parentKey) : undefined
  }
  return true
}

function removePendingNode(node: AgentNode) {
  for (const [parentKey, children] of pendingChildren) {
    const next = children.filter(child => child.key !== node.key)
    if (next.length) pendingChildren.set(parentKey, next)
    else pendingChildren.delete(parentKey)
  }
}

function detachNode(node: AgentNode) {
  if (node.parentKey) {
    const parent = agentIndex.get(node.parentKey)
    if (parent) parent.children = parent.children.filter(child => child.key !== node.key)
  }
  agentRoots.value = agentRoots.value.filter(root => root.key !== node.key)
  removePendingNode(node)
}

function attachNode(node: AgentNode) {
  const parent = node.parentKey ? agentIndex.get(node.parentKey) : undefined
  if (parent && canAttach(node, parent)) {
    if (!parent.children.some(child => child.key === node.key)) parent.children.push(node)
  } else if (node.parentKey) {
    const waiting = pendingChildren.get(node.parentKey) || []
    if (!waiting.some(child => child.key === node.key)) waiting.push(node)
    pendingChildren.set(node.parentKey, waiting)
  } else if (!agentRoots.value.some(root => root.key === node.key)) {
    agentRoots.value.push(node)
  }

  const waitingForThisNode = pendingChildren.get(node.key) || []
  if (waitingForThisNode.length) {
    pendingChildren.delete(node.key)
    for (const child of waitingForThisNode) {
      if (child.parentKey === node.key && canAttach(child, node)
        && !node.children.some(existing => existing.key === child.key)) {
        node.children.push(child)
      }
    }
  }

  agentRoots.value.sort((left, right) => Number(isMasterAgent(right.agent)) - Number(isMasterAgent(left.agent)))
}

function ensureAgent(agent: AgentRef, fallbackParentKey?: string): AgentNode | null {
  const key = agentInstanceKey(agent)
  if (!key) return null

  let node = agentIndex.get(key)
  const incomingParent = agent.parentAgentInstanceId || fallbackParentKey || null

  if (!node) {
    node = reactive<AgentNode>({
      key,
      agent: { ...agent },
      parentKey: incomingParent,
      children: [],
      activities: [],
      startedAt: null,
      durationMs: null,
    })
    agentIndex.set(key, node)
    attachNode(node)
    return node
  }

  node.agent = { ...node.agent, ...agent }
  if (incomingParent && incomingParent !== node.parentKey) {
    detachNode(node)
    node.parentKey = incomingParent
    attachNode(node)
  } else if (!node.parentKey && incomingParent) {
    node.parentKey = incomingParent
    detachNode(node)
    attachNode(node)
  }
  return node
}

function promoteUnattachedAgents() {
  for (const children of pendingChildren.values()) {
    for (const child of children) {
      if (!agentRoots.value.some(root => root.key === child.key)) agentRoots.value.push(child)
    }
  }
  pendingChildren.clear()
}

function formatExecutionTime(ms: number) {
  const seconds = Math.max(0, Math.floor(ms / 1000))
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const remainder = seconds % 60
  return hours > 0
    ? String(hours).padStart(2, '0') + ':' + String(minutes).padStart(2, '0') + ':' + String(remainder).padStart(2, '0')
    : String(minutes).padStart(2, '0') + ':' + String(remainder).padStart(2, '0')
}

function timerText(node: AgentNode) {
  if (node.durationMs !== null) return formatExecutionTime(node.durationMs)
  if (node.startedAt !== null) return formatExecutionTime(executionClock.value - node.startedAt)
  return '00:00'
}

function startExecutionTimer() {
  if (executionTicker) clearInterval(executionTicker)
  executionClock.value = Date.now()
  executionTicker = setInterval(() => { executionClock.value = Date.now() }, 1000)
}

function finishExecutionTimer() {
  const finishedAt = Date.now()
  executionClock.value = finishedAt
  for (const node of agentIndex.values()) {
    if (node.startedAt !== null) {
      node.durationMs = Math.max(0, finishedAt - node.startedAt)
      node.startedAt = null
    }
  }
  if (executionTicker) clearInterval(executionTicker)
  executionTicker = undefined
}

function eventContent(event: SquadEvent): string {
  const payload = event.payload || {}
  if (typeof payload.hint === 'string' && payload.hint) return payload.hint
  if (typeof payload.delta === 'string' && payload.delta) return payload.delta
  if (typeof payload.toolCallName === 'string') return '调用工具：' + payload.toolCallName
  if (typeof payload.toolName === 'string') return '调用工具：' + payload.toolName
  if (event.eventType === 'SUBAGENT_EXPOSED') {
    const child = payload.childAgent as AgentRef | undefined
    return child?.agentName ? '创建协作成员：' + child.agentName : '创建协作成员'
  }
  if (payload.result !== undefined) return resultTextOf(payload.result)
  if (payload.data !== undefined) {
    return typeof payload.data === 'string' ? payload.data : (JSON.stringify(payload.data, null, 2) ?? '')
  }
  if (typeof payload.model === 'string') return '模型：' + payload.model
  if (payload.usage !== undefined) return '用量：' + (JSON.stringify(payload.usage) ?? '')
  if (typeof payload.error === 'string') return payload.error
  if (typeof payload.reason === 'string') return payload.reason
  if (typeof payload.name === 'string') return payload.name
  if (payload.value !== undefined) {
    return typeof payload.value === 'string' ? payload.value : (JSON.stringify(payload.value, null, 2) ?? '')
  }
  return ''
}

function resultTextOf(result: unknown): string {
  if (typeof result === 'string') return result
  if (!result || typeof result !== 'object') return ''
  const value = result as { content?: unknown[] }
  if (Array.isArray(value.content)) {
    const texts = value.content
      .filter((item): item is { type?: string; text?: string } => Boolean(item && typeof item === 'object'))
      .filter(item => item.type === 'text' && typeof item.text === 'string')
      .map(item => item.text || '')
    if (texts.length) return texts.join('\n')
  }
  try { return JSON.stringify(result, null, 2) ?? '' } catch { return String(result) }
}

function isJsonText(value: string) {
  try { JSON.parse(value); return true } catch { return false }
}

function prettyJsonText(value: string) {
  try { return JSON.stringify(JSON.parse(value), null, 2) ?? value } catch { return value }
}

function eventTime(event: SquadEvent) {
  return displayTime(event.timestamp)
}

function displayTime(value?: string) {
  if (!value) return ''
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? ''
    : date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

function addActivityEvent(node: AgentNode, event: SquadEvent, key: string) {
  node.activities.push({ key, kind: 'event', event })
}

function blockIdentity(node: AgentNode, event: SquadEvent, kind: StreamBlockKind) {
  const payload = event.payload || {}
  const rawId = kind === 'TOOL_CALL' || kind === 'TOOL_RESULT'
    ? payload.toolCallId
    : payload.blockId
  const id = typeof rawId === 'string' && rawId
    ? rawId
    : event.eventId || String(event.sequence || kind)
  return node.key + ':' + kind + ':' + id
}

function blockLabel(kind: StreamBlockKind, payload: Record<string, unknown>) {
  const name = typeof payload.toolCallName === 'string'
    ? payload.toolCallName
    : typeof payload.toolName === 'string' ? payload.toolName : ''
  if (kind === 'THINKING') return '思考内容'
  if (kind === 'DATA') return '数据输出'
  if (kind === 'TOOL_CALL') return name ? '工具调用：' + name : '工具调用'
  if (kind === 'TOOL_RESULT') return name ? '工具返回：' + name : '工具返回'
  return '文本输出'
}

function appendAgentBlock(node: AgentNode, event: SquadEvent, kind: StreamBlockKind) {
  const payload = event.payload || {}
  const key = blockIdentity(node, event, kind)
  let block = streamBlockIndex.get(key)
  if (!block) {
    block = reactive<StreamBlock>({
      key,
      kind,
      label: blockLabel(kind, payload),
      text: '',
      completed: false,
      timestamp: event.timestamp,
    })
    streamBlockIndex.set(key, block)
    node.activities.push({ key, kind: 'block', block })
  }

  if ((kind === 'TOOL_CALL' || kind === 'TOOL_RESULT') && block.label.endsWith('工具调用') === false) {
    block.label = blockLabel(kind, payload)
  } else if (kind === 'TOOL_CALL' || kind === 'TOOL_RESULT') {
    block.label = blockLabel(kind, payload)
  }

  if (typeof payload.delta === 'string') {
    block.text += payload.delta
  } else if (payload.data !== undefined) {
    const value = typeof payload.data === 'string'
      ? payload.data
      : (JSON.stringify(payload.data, null, 2) ?? '')
    block.text += value
  } else if (payload.result !== undefined && !block.text) {
    block.text = resultTextOf(payload.result)
  }

  if (event.eventType?.endsWith('_END')) block.completed = true
}

function ensureAnswerBlock(agentKey: string, event: SquadEvent, kind: AnswerBlock['kind']) {
  const payload = event.payload || {}
  const rawId = typeof payload.blockId === 'string' && payload.blockId
    ? payload.blockId
    : event.eventId || String(event.sequence || 'answer')
  const key = agentKey + ':' + kind + ':' + rawId
  let block = answerBlockIndex.get(key)
  if (!block) {
    block = reactive<AnswerBlock>({
      key,
      blockId: rawId,
      kind,
      text: '',
      completed: false,
    })
    answerBlockIndex.set(key, block)
    answerBlocks.value.push(block)
  }
  return block
}

function appendAnswerEvent(event: SquadEvent, agentKey: string) {
  const type = event.eventType || ''
  const payload = event.payload || {}

  if (type.startsWith('TEXT_BLOCK_')) {
    const block = ensureAnswerBlock(agentKey, event, 'TEXT')
    if (type === 'TEXT_BLOCK_DELTA' && typeof payload.delta === 'string') block.text += payload.delta
    if (type === 'TEXT_BLOCK_END') block.completed = true
    return
  }

  if (type.startsWith('DATA_BLOCK_')) {
    const block = ensureAnswerBlock(agentKey, event, 'DATA')
    if (type === 'DATA_BLOCK_DELTA') {
      if (typeof payload.delta === 'string') block.text += payload.delta
      else if (payload.data !== undefined) {
        const value = typeof payload.data === 'string'
          ? payload.data
          : (JSON.stringify(payload.data, null, 2) ?? '')
        block.text += value
      }
    }
    if (type === 'DATA_BLOCK_END') block.completed = true
    return
  }

  if (type === 'AGENT_RESULT') {
    const result = resultTextOf(payload.result)
    if (result && !hasAnswerContent.value) answerFallback.value = result
    return
  }

  if (type === 'AGENT_END') {
    for (const block of answerBlocks.value) block.completed = true
  }
}

function appendEvent(event: SquadEvent) {
  const identity = event.eventId || (event.sequence !== undefined ? 'sequence:' + event.sequence : '')
  if (identity && seenEventIds.has(identity)) return
  if (identity) seenEventIds.add(identity)

  const agent = event.agent || null
  const node = agent ? ensureAgent(agent) : null
  if (!node) return

  const type = event.eventType || ''
  const key = identity || crypto.randomUUID()

  if (type === 'AGENT_START') {
    node.startedAt = Date.now()
    node.durationMs = null
  } else if (type === 'AGENT_END' && node.startedAt !== null) {
    node.durationMs = Math.max(0, Date.now() - node.startedAt)
    node.startedAt = null
  }

  if (type === 'SUBAGENT_EXPOSED') {
    const child = event.payload?.childAgent as AgentRef | undefined
    if (child) ensureAgent({
      ...child,
      parentAgentInstanceId: child.parentAgentInstanceId || node.key,
    }, node.key)
  }

  const master = isMasterAgent(agent)
  if (master && (type.startsWith('TEXT_BLOCK_') || type.startsWith('DATA_BLOCK_')
    || type === 'AGENT_RESULT' || type === 'AGENT_END')) {
    appendAnswerEvent(event, node.key)
    if (type === 'AGENT_END') addActivityEvent(node, event, key)
  } else if (type.startsWith('TEXT_BLOCK_')) {
    appendAgentBlock(node, event, 'TEXT')
  } else if (type.startsWith('THINKING_BLOCK_')) {
    appendAgentBlock(node, event, 'THINKING')
  } else if (type.startsWith('DATA_BLOCK_')) {
    appendAgentBlock(node, event, 'DATA')
  } else if (type.startsWith('TOOL_CALL_')) {
    appendAgentBlock(node, event, 'TOOL_CALL')
  } else if (type.startsWith('TOOL_RESULT_')) {
    appendAgentBlock(node, event, 'TOOL_RESULT')
  } else {
    addActivityEvent(node, event, key)
  }

  scheduleFollowLatest()
}

function latestActivity(node: AgentNode): AgentActivity | undefined {
  return node.activities[node.activities.length - 1]
}

function activityLabel(activity: AgentActivity) {
  if (activity.kind === 'event') return eventLabel(activity.event.eventType)
  return activity.block.label
}

function activityContent(activity: AgentActivity) {
  return activity.kind === 'event' ? eventContent(activity.event) : activity.block.text
}

function activityPreview(activity: AgentActivity) {
  return activityContent(activity).replace(/\s+/g, ' ').trim()
}

function activityTime(activity: AgentActivity) {
  return activity.kind === 'event' ? eventTime(activity.event) : displayTime(activity.block.timestamp)
}

function isJsonBlock(block: StreamBlock) {
  if (block.kind === 'DATA') return true
  if (!block.text.trim()) return false
  return isJsonText(block.text)
}

function prettyBlockContent(block: StreamBlock) {
  return isJsonBlock(block) ? prettyJsonText(block.text) : block.text
}

function isJsonAnswer(block: AnswerBlock) {
  if (block.kind === 'DATA') return true
  return block.completed && isJsonText(block.text)
}

function prettyAnswerContent(block: AnswerBlock) {
  return isJsonAnswer(block) ? prettyJsonText(block.text) : block.text
}

function resetRun() {
  agentRoots.value = []
  answerBlocks.value = []
  answerFallback.value = ''
  expandedGroups.value = {}
  agentIndex.clear()
  pendingChildren.clear()
  streamBlockIndex.clear()
  answerBlockIndex.clear()
  seenEventIds.clear()
}

function onConversationScroll() {
  const element = conversationScroller.value
  if (!element) return
  followLatest.value = element.scrollHeight - element.scrollTop - element.clientHeight < 96
}

function scrollConversationToLatest(smooth = false) {
  const element = conversationScroller.value
  if (!element) return
  followLatest.value = true
  if (smooth) element.scrollTo({ top: element.scrollHeight, behavior: 'smooth' })
  else element.scrollTop = element.scrollHeight
}

function scheduleFollowLatest() {
  if (!followLatest.value || followFrame !== undefined) return
  void nextTick(() => {
    if (followFrame !== undefined) return
    if (typeof requestAnimationFrame === 'undefined') {
      const element = conversationScroller.value
      if (element && followLatest.value) element.scrollTop = element.scrollHeight
      return
    }
    followFrame = requestAnimationFrame(() => {
      followFrame = undefined
      const element = conversationScroller.value
      if (followLatest.value && element) element.scrollTop = element.scrollHeight
    })
  })
}

function parseFrame(frame: string) {
  const lines = frame.split('\n')
  const eventName = lines.find(line => line.startsWith('event:'))?.slice(6).trim()
  const sseId = lines.find(line => line.startsWith('id:'))?.slice(3).trim()
  const payload = lines.filter(line => line.startsWith('data:'))
    .map(line => line.slice(5).trimStart()).join('\n')
  if (!payload) return

  try {
    const event = JSON.parse(payload) as SquadEvent
    if (!event.eventId && sseId) event.eventId = sseId
    if (event.eventType) appendEvent(event)
    else if (eventName === 'squad.test.completed') scheduleFollowLatest()
  } catch {
    error.value = '收到无法解析的 SSE 数据'
  }
}

async function send() {
  const message = draft.value.trim()
  if (!message || busy.value) return

  resetRun()
  lastPrompt.value = message
  error.value = ''
  followLatest.value = true
  executionExpanded.value = true
  busy.value = true
  startExecutionTimer()
  const activeController = new AbortController()
  controller = activeController
  draft.value = ''

  await nextTick()
  resizeComposer()
  scheduleFollowLatest()

  try {
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
    }
    const csrf = getCsrfToken()
    if (csrf) headers['X-CSRF-Token'] = csrf
    const base = import.meta.env.VITE_API_BASE_URL || ''
    const response = await fetch(base + '/api/v2/test/events/stream', {
      method: 'POST',
      credentials: base ? 'include' : 'same-origin',
      headers,
      body: JSON.stringify({ scenario: 'parallel', intervalMillis: 300 }),
      signal: activeController.signal,
    })
    if (!response.ok || !response.body) throw new Error('请求失败（HTTP ' + response.status + '）')

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let pending = ''
    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      pending += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n')
      let boundary: number
      while ((boundary = pending.indexOf('\n\n')) !== -1) {
        parseFrame(pending.slice(0, boundary))
        pending = pending.slice(boundary + 2)
      }
    }
    pending += decoder.decode()
    if (pending.trim()) parseFrame(pending)
  } catch (cause) {
    if (!activeController.signal.aborted) {
      error.value = cause instanceof Error ? cause.message : '事件流连接失败'
    }
  } finally {
    finishExecutionTimer()
    promoteUnattachedAgents()
    busy.value = false
    executionExpanded.value = false
    if (controller === activeController) controller = null
    await nextTick()
    composerInput.value?.focus()
    scheduleFollowLatest()
  }
}

function resizeComposer() {
  const input = composerInput.value
  if (!input) return
  input.style.height = 'auto'
  const maxHeight = 180
  input.style.height = Math.min(input.scrollHeight, maxHeight) + 'px'
  input.style.overflowY = input.scrollHeight > maxHeight ? 'auto' : 'hidden'
}

function handleComposerKeydown(event: KeyboardEvent) {
  if (event.key !== 'Enter' || event.shiftKey || event.isComposing || event.keyCode === 229 || busy.value) return
  event.preventDefault()
  send()
}

function stop() {
  controller?.abort()
}

onMounted(() => {
  if (conversationContent.value && typeof ResizeObserver !== 'undefined') {
    conversationResizeObserver = new ResizeObserver(scheduleFollowLatest)
    conversationResizeObserver.observe(conversationContent.value)
  }
  composerInput.value?.focus()
})

onBeforeUnmount(() => {
  stop()
  if (executionTicker) clearInterval(executionTicker)
  conversationResizeObserver?.disconnect()
  if (followFrame !== undefined && typeof cancelAnimationFrame !== 'undefined') cancelAnimationFrame(followFrame)
})
</script>

<template>
  <main class="squad-v2-page">
    <header class="topbar">
      <div class="brand">
        <span class="brand-mark">S</span>
        <div>
          <strong>智能协作</strong>
          <small>任务执行工作台</small>
        </div>
      </div>
    </header>

    <section ref="conversationScroller" class="conversation" @scroll="onConversationScroll">
      <div ref="conversationContent" class="conversation-content">
        <div v-if="!lastPrompt && !busy && !visibleAgents.length" class="welcome">
          <div class="welcome-mark">✳</div>
          <h1>让我们一起完成任务</h1>
          <p>输入消息开始体验。当前使用模拟事件，展示多个协作成员的实时执行过程与所属关系。</p>
        </div>

        <div v-else class="timeline">
          <article v-if="lastPrompt" class="user-message">
            <span class="avatar user-avatar">U</span>
            <div>
              <small>我</small>
              <p>{{ lastPrompt }}</p>
            </div>
          </article>

          <section v-if="busy || visibleAgents.length" class="execution-panel">
            <button
              type="button"
              class="execution-header"
              :aria-expanded="executionExpanded"
              @click="executionExpanded = !executionExpanded"
            >
              <span class="execution-heading-left">
                <span class="execution-mark" :class="{ running: busy }"></span>
                <strong>执行过程</strong>
              </span>
              <span class="execution-heading-right">
                <span>{{ executionExpanded ? '收起' : '展开' }}</span>
                <span :class="['execution-chevron', { expanded: executionExpanded }]">⌄</span>
              </span>
            </button>

            <Transition name="execution-fold">
              <div v-show="executionExpanded" class="execution-panel-content">
                <div class="agent-tree">
                  <article
                    v-for="item in visibleAgents"
                    :key="item.node.key"
                    class="agent-card"
                    :class="{ 'nested-agent-card': item.depth > 0 }"
                    :style="{ '--agent-depth': item.depth }"
                  >
                    <header class="agent-heading">
                      <strong class="agent-name">{{ agentName(item.node) }}</strong>
                      <time class="agent-timer" :aria-label="'执行耗时 ' + timerText(item.node)">
                        <span class="timer-icon">◷</span>{{ timerText(item.node) }}
                      </time>
                    </header>

                    <div v-if="latestActivity(item.node) && !expandedGroups[item.node.key]" class="agent-preview">
                      <span class="activity-kind">{{ activityLabel(latestActivity(item.node)!) }}</span>
                      <span class="activity-preview-content">{{ activityPreview(latestActivity(item.node)!) || ' ' }}</span>
                    </div>

                    <button
                      v-if="item.node.activities.length"
                      type="button"
                      class="activity-expand-button"
                      :aria-expanded="Boolean(expandedGroups[item.node.key])"
                      @click="expandedGroups[item.node.key] = !expandedGroups[item.node.key]"
                    >
                      {{ expandedGroups[item.node.key] ? '收起记录' : '展开记录（' + item.node.activities.length + '）' }}
                      <span :class="['expand-chevron', { expanded: expandedGroups[item.node.key] }]">⌄</span>
                    </button>

                    <Transition name="activity-expand">
                      <div v-if="expandedGroups[item.node.key]" class="agent-history">
                        <article
                          v-for="activity in item.node.activities"
                          :key="activity.key"
                          class="activity-item"
                          :class="activity.kind === 'block' ? 'activity-' + activity.block.kind.toLowerCase() : 'activity-event'"
                        >
                          <div class="activity-meta">
                            <span class="activity-kind">{{ activityLabel(activity) }}</span>
                            <time>{{ activityTime(activity) }}</time>
                          </div>

                          <template v-if="activity.kind === 'block'">
                            <SafeMarkdown
                              v-if="activity.block.kind === 'TEXT' || activity.block.kind === 'THINKING'"
                              :text="activity.block.text"
                              :streaming="busy && !activity.block.completed"
                            />
                            <pre v-else-if="activity.block.text" class="activity-code">{{ prettyBlockContent(activity.block) }}</pre>
                          </template>
                          <p v-else-if="eventContent(activity.event)" class="activity-event-content">
                            {{ eventContent(activity.event) }}
                          </p>
                        </article>
                      </div>
                    </Transition>
                  </article>
                </div>
              </div>
            </Transition>
          </section>

          <section v-if="showAnswer" class="answer-section">
            <div class="answer-heading">
              <strong>回答</strong>
            </div>

            <div v-if="hasAnswerContent" class="answer-blocks">
              <article v-for="block in answerBlocks" :key="block.key" class="answer-block">
                <pre v-if="isJsonAnswer(block)" class="answer-json"><code>{{ prettyAnswerContent(block) }}</code></pre>
                <SafeMarkdown
                  v-else
                  :text="block.text"
                  :streaming="busy && !block.completed"
                />
              </article>
            </div>
            <template v-else-if="visibleAnswerFallback">
              <pre v-if="isJsonText(visibleAnswerFallback)" class="answer-json"><code>{{ prettyJsonText(visibleAnswerFallback) }}</code></pre>
              <SafeMarkdown v-else :text="visibleAnswerFallback" :streaming="busy" />
            </template>
            <div v-else class="answer-placeholder">正文输出后会显示在这里。</div>
          </section>
        </div>
      </div>

      <button
        v-if="showJumpToLatest"
        type="button"
        class="jump-latest"
        @click="scrollConversationToLatest(true)"
      >回到最新 <span aria-hidden="true">↓</span></button>
    </section>

    <p v-if="error" class="error-message">{{ error }}</p>

    <footer class="composer-wrap">
      <form class="composer" @submit.prevent="send">
        <textarea
          ref="composerInput"
          v-model="draft"
          :disabled="busy"
          rows="1"
          maxlength="12000"
          :placeholder="busy ? '正在执行任务…' : '输入消息，描述你希望完成的任务…'"
          @input="resizeComposer"
          @keydown="handleComposerKeydown"
        />
        <div class="composer-toolbar">
          <div class="composer-hints">
            <span>Enter 发送</span>
            <span class="hint-separator">·</span>
            <span>Shift + Enter 换行</span>
            <span v-if="draft.length" class="character-count">{{ draft.length.toLocaleString() }} / 12,000</span>
          </div>
          <button
            v-if="busy"
            type="button"
            class="send-button stop-button"
            disabled
            aria-label="停止执行"
            title="停止执行功能将在后续版本开放"
          >■</button>
          <button
            v-else
            type="submit"
            class="send-button"
            :disabled="!draft.trim()"
            aria-label="发送消息"
            title="发送消息"
          >↑</button>
        </div>
      </form>
      <small>当前为测试页面 · 使用模拟执行数据</small>
    </footer>
  </main>
</template>

<style scoped>
.squad-v2-page{height:100vh;height:100dvh;min-height:560px;display:flex;flex-direction:column;background:var(--surface,#fff);color:var(--text-primary,#202124)}
.topbar{height:60px;flex:none;border-bottom:1px solid var(--border,#e9e9e9);display:flex;align-items:center;padding:0 28px}
.brand{display:flex;align-items:center;gap:10px}
.brand-mark{width:29px;height:29px;border-radius:9px;background:#242424;color:#fff;display:grid;place-items:center;font-size:13px;font-weight:700}
.brand strong{display:block;font-size:13px;font-weight:650}
.brand small{display:block;color:#999;font-size:10px;margin-top:2px}
.conversation{position:relative;width:min(900px,calc(100% - 40px));margin:0 auto;flex:1;min-height:0;overflow:auto;padding:26px 0 24px;box-sizing:border-box;overscroll-behavior:contain;scrollbar-gutter:stable}
.conversation-content{min-height:100%;display:flex;flex-direction:column}
.timeline{display:flex;flex-direction:column;gap:16px}
.welcome{min-height:100%;display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center;padding:30px;box-sizing:border-box}
.welcome-mark{font-size:31px;width:58px;height:58px;border-radius:19px;background:#f2f2f2;display:grid;place-items:center}
.welcome h1{font-size:24px;font-weight:600;margin:19px 0 8px;letter-spacing:-.4px}
.welcome p{max-width:540px;color:#858585;font-size:13px;line-height:1.8}
.user-message{display:flex;justify-content:flex-end;gap:10px;margin-bottom:2px}
.user-message>div{background:#f3f3f3;padding:10px 14px;border-radius:15px;max-width:72%}
.user-message small{display:block;font-size:10px;color:#888}
.user-message p{font-size:13px;line-height:1.6;margin:4px 0 0;white-space:pre-wrap;overflow-wrap:anywhere}
.avatar{width:27px;height:27px;display:grid;place-items:center;border-radius:50%;font-size:11px;flex:none}
.user-avatar{background:#dedede}
.execution-panel{border:1px solid #ececec;border-radius:13px;background:#fff;overflow:hidden}
.execution-header{width:100%;display:flex;align-items:center;justify-content:space-between;gap:12px;padding:11px 13px;border:0;background:transparent;color:inherit;text-align:left;cursor:pointer}
.execution-heading-left,.execution-heading-right{display:flex;align-items:center;gap:9px;min-width:0}
.execution-heading-left strong{font-size:12px;font-weight:600;color:#555}
.execution-mark{width:7px;height:7px;border-radius:50%;background:#b9b9b9;flex:none}
.execution-mark.running{background:#6d9d83;box-shadow:0 0 0 3px #6d9d8318}
.execution-heading-right{font-size:10px;color:#999;flex:none}
.execution-chevron{font-size:13px;transition:transform .2s cubic-bezier(.2,.7,.2,1)}
.execution-chevron.expanded{transform:rotate(180deg)}
.execution-panel-content{padding:0 11px 11px}
.execution-fold-enter-active,.execution-fold-leave-active{transition:opacity .2s ease,transform .2s cubic-bezier(.2,.7,.2,1);transform-origin:top}
.execution-fold-enter-from,.execution-fold-leave-to{opacity:0;transform:translateY(-5px)}
.agent-tree{display:flex;flex-direction:column;gap:7px}
.agent-card{min-width:0;border:1px solid #ededed;border-radius:10px;background:#fff;overflow:hidden}
.agent-card.nested-agent-card{margin-left:calc(var(--agent-depth)*18px);border-left:2px solid #e1e5e2}
.agent-heading{display:flex;align-items:center;justify-content:space-between;gap:12px;min-width:0;padding:9px 11px;background:#fbfbfb}
.agent-name{min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:11px;font-weight:650;color:#4e4e4e}
.agent-timer{display:inline-flex;align-items:center;gap:5px;flex:none;font-size:11px;font-variant-numeric:tabular-nums;color:#888}
.timer-icon{font-size:12px;color:#aaa}
.agent-preview{display:flex;align-items:center;gap:8px;min-width:0;padding:8px 11px}
.activity-kind{flex:none;max-width:130px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:10px;color:#858585;background:#f6f6f6;border-radius:4px;padding:3px 6px}
.activity-preview-content{flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:11px;line-height:1.5;color:#666}
.activity-expand-button{display:flex;align-items:center;justify-content:center;gap:5px;width:100%;padding:5px 10px;border:0;border-top:1px solid #f3f3f3;background:#fff;color:#999;font-size:10px;cursor:pointer;transition:background .16s ease,color .16s ease}
.activity-expand-button:hover{background:#fafafa;color:#555}
.expand-chevron{display:inline-block;font-size:12px;transition:transform .18s ease}
.expand-chevron.expanded{transform:rotate(180deg)}
.agent-history{padding:2px 11px 7px;border-top:1px solid #f1f1f1}
.activity-item{min-width:0;padding:8px 0;border-bottom:1px solid #f5f5f5}
.activity-item:last-child{border-bottom:0}
.activity-meta{display:flex;align-items:center;gap:8px;margin-bottom:5px}
.activity-meta time{margin-left:auto;flex:none;font-size:10px;color:#aaa}
.activity-item .answer-markdown,.activity-item :deep(.answer-markdown){font-size:12px;line-height:1.65;color:#555;overflow-wrap:anywhere}
.activity-item :deep(.answer-markdown p){margin:2px 0 5px}
.activity-item :deep(.answer-markdown p:last-child){margin-bottom:0}
.activity-event-content{margin:0;color:#666;font-size:11px;line-height:1.6;white-space:pre-wrap;overflow-wrap:anywhere}
.activity-code{max-width:100%;margin:0;padding:9px 10px;border-radius:7px;background:#f7f7f7;color:#555;font:11px/1.6 ui-monospace,SFMono-Regular,Menlo,monospace;white-space:pre-wrap;overflow-wrap:anywhere}
.activity-thinking{border-bottom-color:#efede7}
.activity-thinking .activity-kind{background:#f7f5ef;color:#897e62}
.answer-section{padding:2px 1px 8px}
.answer-heading{display:flex;align-items:center;gap:9px;margin-bottom:10px}
.answer-heading strong{font-size:13px;font-weight:600;color:#555}
.answer-blocks{display:flex;flex-direction:column;gap:13px}
.answer-block{min-width:0;color:var(--text-primary,#202124);font-size:14px;line-height:1.75;overflow-wrap:anywhere}
.answer-block :deep(.answer-markdown){font-size:14px;line-height:1.75}
.answer-json{max-width:100%;overflow:auto;margin:0;padding:13px 15px;border:1px solid #ededed;border-radius:10px;background:#fafafa;color:#444;font:12px/1.65 ui-monospace,SFMono-Regular,Menlo,monospace;white-space:pre-wrap;overflow-wrap:anywhere}
.answer-placeholder{display:inline-flex;align-items:center;min-height:24px;color:#aaa;font-size:12px}
.jump-latest{position:sticky;bottom:12px;align-self:center;display:flex;align-items:center;gap:7px;margin:10px auto 0;border:1px solid #e8e8e8;border-radius:18px;padding:7px 12px;background:#ffffffed;color:#777;font-size:11px;box-shadow:0 2px 10px #0000000a;cursor:pointer;backdrop-filter:blur(8px)}
.jump-latest:hover{color:#444;border-color:#dcdcdc}
.error-message{color:#b42318;text-align:center;font-size:12px;margin:0 20px 8px}
.composer-wrap{width:min(900px,calc(100% - 40px));margin:0 auto;padding:10px 0 17px;text-align:center;flex:none}
.composer{display:flex;flex-direction:column;gap:8px;border:1px solid #d9d9d9;border-radius:19px;padding:12px 12px 10px 16px;background:#fff;box-shadow:0 3px 14px #00000008;transition:border-color .18s ease,box-shadow .18s ease}
.composer:focus-within{border-color:#b8b8b8;box-shadow:0 3px 16px #0000000d}
.composer textarea{display:block;width:100%;min-height:28px;max-height:180px;resize:none;overflow-y:hidden;border:0;outline:0;font-family:inherit;font-size:14px;line-height:1.65;color:inherit;background:transparent;padding:2px 0;box-sizing:border-box}
.composer textarea::placeholder{color:#aaa}
.composer textarea:disabled{opacity:.65;cursor:wait}
.composer-toolbar{display:flex;align-items:center;justify-content:space-between;gap:12px;min-height:34px}
.composer-hints{display:flex;align-items:center;gap:6px;min-width:0;color:#a0a0a0;font-size:10px}
.hint-separator{color:#ccc}
.character-count{margin-left:8px;color:#aaa;font-variant-numeric:tabular-nums}
.send-button{width:35px;height:35px;flex:none;border:0;border-radius:50%;background:#242424;color:#fff;font-size:21px;line-height:1;cursor:pointer;display:grid;place-items:center;transition:background .16s ease,transform .16s ease}
.send-button:not(:disabled):hover{background:#444;transform:translateY(-1px)}
.send-button:focus-visible{outline:2px solid #777;outline-offset:3px}
.send-button:disabled{background:#e4e4e4;color:#fff;cursor:not-allowed}
.stop-button{font-size:12px;background:#555}
.composer-wrap>small{display:block;color:#aaa;font-size:10px;margin-top:8px}
@media(max-width:600px){
  .topbar{padding:0 16px}
  .conversation,.composer-wrap{width:calc(100% - 28px)}
  .conversation{padding-top:18px}
  .agent-card.nested-agent-card{margin-left:calc(var(--agent-depth)*10px)}
  .agent-heading{padding:8px 9px}
  .agent-preview{gap:5px;padding:7px 9px}
  .activity-kind{max-width:95px;font-size:9px}
  .activity-preview-content{font-size:10px}
  .agent-history{padding:2px 9px 6px}
  .execution-header{padding:10px}
  .execution-panel-content{padding:0 8px 8px}
  .composer{padding:10px 10px 9px 13px}
  .composer textarea{font-size:14px}
  .composer-hints{font-size:9px}
}
</style>