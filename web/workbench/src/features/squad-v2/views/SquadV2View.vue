<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref } from 'vue'
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
type TimelineRow = { key: string; event: SquadEvent; agentName: string; durationLabel?: string }
type AgentGroup = {
  key: string
  agent: AgentRef
  rows: TimelineRow[]
  children: AgentGroup[]
  depth: number
  completed: boolean
  durationLabel?: string
}

const draft = ref('')
const composerInput = ref<HTMLTextAreaElement | null>(null)
const lastPrompt = ref('')
const busy = ref(false)
const error = ref('')
const status = ref('等待发送消息')
const rows = ref<TimelineRow[]>([])
const agentsByInstance = new Map<string, AgentRef>()
const agentStartedAt = new Map<string, number>()
const finalAnswerRows = new Map<string, TimelineRow>()
const expandedGroups = ref<Record<string, boolean>>({})
const eventHistoryElements = new Map<string, HTMLElement>()
const bottom = ref<HTMLElement | null>(null)
let controller: AbortController | null = null


const timeline = computed(() => rows.value)
const eventCount = computed(() => rows.value.length)
const groupedTimeline = computed<AgentGroup[]>(() => {
  const agentRefs = new Map<string, AgentRef>()
  const groupRows = new Map<string, TimelineRow[]>()

  const agentKey = (agent?: AgentRef | null) =>
    agent?.agentInstanceId || agent?.agentId || ''

  for (const row of rows.value) {
    const agent = row.event.agent
    const key = agentKey(agent)
    if (agent && key) agentRefs.set(key, agent)

    if (row.event.eventType === 'SUBAGENT_EXPOSED') {
      const child = row.event.payload?.childAgent as AgentRef | undefined
      const childKey = agentKey(child)
      if (child && childKey) agentRefs.set(childKey, child)
    }
  }

  const groups = new Map<string, AgentGroup>()
  for (const [key, agent] of agentRefs) {
    groups.set(key, {
      key,
      agent,
      rows: [],
      children: [],
      depth: 0,
      completed: false,
    })
  }

  for (const row of rows.value) {
    const key = agentKey(row.event.agent)
    const group = groups.get(key)
    if (group) group.rows.push(row)
  }

  const roots: AgentGroup[] = []
  for (const group of groups.values()) {
    const parentId = group.agent.parentAgentInstanceId
    const parent = parentId ? groups.get(parentId) : undefined
    if (parent && parent !== group) parent.children.push(group)
    else roots.push(group)
  }

  const firstSequence = (group: AgentGroup) =>
    group.rows.length ? Math.min(...group.rows.map(row => Number(row.event.sequence || 0))) : Number.MAX_SAFE_INTEGER

  for (const group of groups.values()) {
    group.rows.sort((a, b) => Number(a.event.sequence || 0) - Number(b.event.sequence || 0))
    group.children.sort((a, b) => firstSequence(a) - firstSequence(b))
    const endRow = [...group.rows].reverse().find(row => row.event.eventType === 'AGENT_END')
    group.completed = Boolean(endRow)
    group.durationLabel = endRow?.durationLabel
  }

  roots.sort((a, b) => {
    if (a.agent.agentType === 'MASTER') return -1
    if (b.agent.agentType === 'MASTER') return 1
    return firstSequence(a) - firstSequence(b)
  })

  const flattened: AgentGroup[] = []
  const append = (group: AgentGroup, depth: number, ancestors = new Set<string>()) => {
    if (ancestors.has(group.key)) return
    const nextAncestors = new Set(ancestors)
    nextAncestors.add(group.key)
    group.depth = depth
    flattened.push(group)
    for (const child of group.children) append(child, depth + 1, nextAncestors)
  }
  for (const root of roots) append(root, 0)

  return flattened
})

const eventLabels: Record<string, string> = {
  AGENT_START: '开始执行', AGENT_END: '执行完成', AGENT_RESULT: '最终回答',
  MODEL_CALL_START: '开始调用模型', MODEL_CALL_END: '模型调用完成',
  TEXT_BLOCK_START: '开始输出文本', TEXT_BLOCK_DELTA: '文本输出', TEXT_BLOCK_END: '文本输出完成',
  THINKING_BLOCK_START: '开始整理思路', THINKING_BLOCK_DELTA: '思路更新', THINKING_BLOCK_END: '思路整理完成',
  DATA_BLOCK_START: '开始输出数据', DATA_BLOCK_DELTA: '数据更新', DATA_BLOCK_END: '数据输出完成',
  TOOL_CALL_START: '准备调用工具', TOOL_CALL_DELTA: '生成工具参数', TOOL_CALL_END: '工具参数准备完成',
  TOOL_RESULT_START: '工具开始执行', TOOL_RESULT_TEXT_DELTA: '工具返回结果', TOOL_RESULT_DATA_DELTA: '工具返回数据', TOOL_RESULT_END: '工具执行完成',
  EXCEED_MAX_ITERS: '执行轮数达到上限', REQUIRE_USER_CONFIRM: '等待用户确认', REQUIRE_EXTERNAL_EXECUTION: '等待外部执行',
  USER_CONFIRM_RESULT: '收到确认结果', EXTERNAL_EXECUTION_RESULT: '收到外部执行结果', REQUEST_STOP: '请求停止',
  SUBAGENT_EXPOSED: '创建协作成员', HINT_BLOCK: '收到提示', ALL_TOOLS_DENIED: '工具调用被拒绝', CUSTOM: '自定义事件'
}
function eventLabel(type?: string) { return type ? (eventLabels[type] || '执行事件') : '执行事件' }
function isThinkingEvent(row: TimelineRow) {
  return row.event.category === 'THINKING' || row.event.eventType?.startsWith('THINKING_BLOCK_')
}
function visibleRows(source: TimelineRow[]) {
  return source.filter(row => !isThinkingEvent(row))
}
function thinkingRows(source: TimelineRow[]) {
  return source.filter(isThinkingEvent)
}

function latestNotification(group: AgentGroup): TimelineRow | undefined {
  const notifications = visibleRows(group.rows)
  return notifications[notifications.length - 1]
}

function toggleEventHistory(groupKey: string) {
  const expanded = !expandedGroups.value[groupKey]
  expandedGroups.value = { ...expandedGroups.value, [groupKey]: expanded }
  if (expanded) scrollExpandedEventHistory()
}

function setEventHistoryRef(groupKey: string, element: unknown) {
  if (element instanceof HTMLElement) eventHistoryElements.set(groupKey, element)
  else eventHistoryElements.delete(groupKey)
}

function scrollExpandedEventHistory() {
  void nextTick(() => {
    for (const [key, element] of eventHistoryElements) {
      if (expandedGroups.value[key]) element.scrollTop = element.scrollHeight
    }
  })
}
function formatDuration(ms: number) { return ms < 1000 ? `${Math.max(0, ms)} 毫秒` : `${(ms / 1000).toFixed(1)} 秒` }

function contentOf(event: SquadEvent): string {
  const payload = event.payload || {}
  if (typeof payload.delta === 'string') return payload.delta
  if (typeof payload.hint === 'string') return payload.hint
  if (typeof payload.toolCallName === 'string') return '调用工具：' + ({ search: '资料检索', add_numbers: '数字计算' }[payload.toolCallName] || '辅助工具')
  if (typeof payload.result === 'string') return payload.result
  if (payload.result && typeof payload.result === 'object') {
    const result = payload.result as { content?: Array<{ type?: string; text?: string }> }
    return (result.content || []).filter(item => item.type === 'text').map(item => item.text || '').join('')
  }
  if (event.eventType === 'SUBAGENT_EXPOSED') {
    const child = payload.childAgent as AgentRef | undefined
    return child?.agentName ? '新增协作成员：' + child.agentName : '新增协作成员'
  }
  if (typeof payload.name === 'string') return payload.name
  if (typeof payload.model === 'string') return '模型调用中'
  if (payload.usage) return '模型调用完成'
  return ''
}

function addEvent(event: SquadEvent) {
  const agent = event.agent || {}
  const agentName = agent.agentName || '协作成员'
  const instanceId = agent.agentInstanceId || agent.agentId || 'unknown'
  if (agent.agentInstanceId) agentsByInstance.set(agent.agentInstanceId, agent)
  if (event.eventType === 'SUBAGENT_EXPOSED') {
    const child = event.payload?.childAgent as AgentRef | undefined
    if (child?.agentInstanceId) agentsByInstance.set(child.agentInstanceId, child)
  }

  const row: TimelineRow = {
    key: event.eventId || String(event.sequence || crypto.randomUUID()),
    event,
    agentName,
  }
  rows.value.push(row)
  rows.value.sort((a, b) => Number(a.event.sequence || 0) - Number(b.event.sequence || 0))

  if (event.eventType === 'AGENT_START') {
    agentStartedAt.set(instanceId, Date.now())
  } else if (event.eventType === 'AGENT_RESULT') {
    finalAnswerRows.set(instanceId, row)
  } else if (event.eventType === 'AGENT_END') {
    const startedAt = agentStartedAt.get(instanceId)
    if (startedAt !== undefined) {
      const durationLabel = formatDuration(Date.now() - startedAt)
      const answerRow = finalAnswerRows.get(instanceId)
      if (answerRow) answerRow.durationLabel = durationLabel
      else row.durationLabel = durationLabel
      agentStartedAt.delete(instanceId)
    }
  }
  scrollExpandedEventHistory()
}

function parseFrame(frame: string) {
  const lines = frame.split('\n')
  const eventName = lines.find(line => line.startsWith('event:'))?.slice(6).trim()
  const payload = lines.filter(line => line.startsWith('data:'))
    .map(line => line.slice(5).trimStart()).join('\n')
  if (!payload) return
  try {
    const value = JSON.parse(payload) as SquadEvent
    if (value.eventType) addEvent(value)
    else if (eventName === 'squad.test.completed') status.value = '测试事件已全部发送'
  } catch {
    error.value = '收到无法解析的 SSE 数据'
  }
}

async function send() {
  const message = draft.value.trim()
  if (!message || busy.value) return
  rows.value = []
  agentsByInstance.clear()
  agentStartedAt.clear()
  finalAnswerRows.clear()
  expandedGroups.value = {}
  eventHistoryElements.clear()
  lastPrompt.value = message
  error.value = ''
  status.value = '正在连接测试事件流…'
  busy.value = true
  controller = new AbortController()
  draft.value = ''
  await nextTick()
  resizeComposer()

  try {
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
    }
    const csrf = getCsrfToken()
    if (csrf) headers['X-CSRF-Token'] = csrf
    const base = import.meta.env.VITE_API_BASE_URL || ''
    const response = await fetch(`${base}/api/v2/test/events/stream`, {
      method: 'POST',
      credentials: base ? 'include' : 'same-origin',
      headers,
      body: JSON.stringify({ intervalMillis: 300 }),
      signal: controller.signal,
    })
    if (!response.ok || !response.body) throw new Error(`请求失败（HTTP ${response.status}）`)

    status.value = '正在接收事件'
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
    status.value = '事件流已结束'
  } catch (cause) {
    if (!controller.signal.aborted) {
      error.value = cause instanceof Error ? cause.message : '事件流连接失败'
      status.value = '连接异常'
    } else {
      status.value = '已停止接收'
    }
  } finally {
    busy.value = false
    controller = null
  }
}

function resizeComposer() {
  const input = composerInput.value
  if (!input) return
  input.style.height = 'auto'
  const maxHeight = 180
  input.style.height = `${Math.min(input.scrollHeight, maxHeight)}px`
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

function displayTime(value?: string) {
  if (!value) return ''
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '' : date.toLocaleTimeString()
}

onBeforeUnmount(stop)
</script>

<template>
  <main class="squad-v2-page">
    <header class="topbar">
      <div class="brand"><span class="brand-mark">S</span><div><strong>智能协作</strong><small>任务执行工作台</small></div></div>
      <div class="run-status"><span :class="['status-dot', { live: busy }]" />{{ status }}<span class="event-count">{{ eventCount }} 条事件</span></div>
    </header>

    <section class="conversation">
      <div v-if="!timeline.length" class="welcome">
        <div class="welcome-mark">✳</div>
        <h1>让我们一起完成任务</h1>
        <p>输入消息开始体验。当前使用本地模拟事件，展示多个协作成员的执行过程与从属关系。</p>
      </div>

      <div v-else class="timeline">
        <article class="user-message">
          <span class="avatar user-avatar">U</span>
          <div><small>我</small><p>{{ lastPrompt }}</p></div>
        </article>

        <div class="timeline-heading">
          <span class="assistant-avatar">✳</span>
          <div><strong>执行过程</strong><small>按所属关系分组展示 · 思考过程单独收纳</small></div>
        </div>

        <div class="grouped-execution">
          <article
            v-for="group in groupedTimeline"
            :key="group.key"
            class="member-card agent-group-card"
            :class="{ 'nested-agent-group': group.depth > 0 }"
            :style="{ '--group-depth': group.depth }"
          >
            <header class="member-header">
              <div class="member-title">
                <strong>{{ group.agent.agentName || (group.depth === 0 ? '主协调员' : '协作成员') }}</strong>
                <span v-if="group.depth > 0" class="member-parent">所属成员：{{ group.agent.parentAgentInstanceId }}</span>
              </div>
              <span :class="['member-status', { completed: group.completed }]">
                {{ group.completed ? '已完成' : '执行中' }}
              </span>
            </header>

            <div class="event-notice">
              <div class="event-notice-line">
                <template v-if="latestNotification(group)">
                  <span class="event-type">{{ eventLabel(latestNotification(group)?.event.eventType) }}</span>
                  <span class="event-notice-content" :title="contentOf(latestNotification(group)!.event)">
                    {{ contentOf(latestNotification(group)!.event) || '事件已更新' }}
                  </span>
                  <time class="event-time">{{ displayTime(latestNotification(group)?.event.timestamp) }}</time>
                </template>
                <span v-else class="event-notice-empty">等待事件</span>
                <button
                  v-if="visibleRows(group.rows).length > 1"
                  type="button"
                  class="event-expand-button"
                  @click="toggleEventHistory(group.key)"
                >
                  {{ expandedGroups[group.key] ? '收起' : `展开全部（${visibleRows(group.rows).length}）` }}
                  <span :class="['expand-chevron', { expanded: expandedGroups[group.key] }]">⌄</span>
                </button>
              </div>

              <div
                v-if="expandedGroups[group.key]"
                :ref="element => setEventHistoryRef(group.key, element)"
                class="event-history"
              >
                <div v-for="row in visibleRows(group.rows)" :key="row.key" class="event-notice-line history-line">
                  <span class="event-type">{{ eventLabel(row.event.eventType) }}</span>
                  <span class="event-notice-content" :title="contentOf(row.event)">
                    {{ contentOf(row.event) || '—' }}
                  </span>
                  <time class="event-time">{{ displayTime(row.event.timestamp) }}</time>
                </div>
              </div>
            </div>

            <details v-if="thinkingRows(group.rows).length" class="thinking-panel">
              <summary>思考过程（{{ thinkingRows(group.rows).length }} 条）</summary>
              <article v-for="row in thinkingRows(group.rows)" :key="row.key" class="thinking-row">
                <span class="thinking-label">{{ eventLabel(row.event.eventType) }}</span>
                <p v-if="contentOf(row.event)">{{ contentOf(row.event) }}</p>
              </article>
            </details>
            <div v-if="group.durationLabel" class="answer-duration">总用时 {{ group.durationLabel }}</div>
          </article>
        </div>
        <div ref="bottom" />
      </div>
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
            @click="stop"
            aria-label="停止执行"
            title="停止执行"
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
.squad-v2-page{height:100vh;min-height:560px;display:flex;flex-direction:column;background:var(--surface,#fff);color:var(--text-primary,#202124)}
.topbar{height:62px;flex:none;border-bottom:1px solid var(--border,#e7e7e7);display:flex;align-items:center;justify-content:space-between;padding:0 28px}
.brand{display:flex;align-items:center;gap:10px}.brand-mark,.assistant-avatar{width:30px;height:30px;border-radius:10px;background:#242424;color:white;display:grid;place-items:center;font-weight:700}
.brand strong{display:block;font-size:14px}.brand small{display:block;color:#888;font-size:10px;margin-top:2px}
.run-status{display:flex;align-items:center;gap:8px;color:#777;font-size:12px}.status-dot{width:7px;height:7px;border-radius:50%;background:#aaa}.status-dot.live{background:#16a36a;box-shadow:0 0 0 3px #16a36a22}.event-count{border-left:1px solid #ddd;padding-left:10px}
.conversation{width:min(860px,calc(100% - 40px));margin:0 auto;flex:1;min-height:0;overflow:auto;padding:32px 0 24px;box-sizing:border-box}
.welcome{min-height:100%;display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center;padding:30px;box-sizing:border-box}
.welcome-mark{font-size:32px;width:58px;height:58px;border-radius:20px;background:#f2f2f2;display:grid;place-items:center}
.welcome h1{font-size:25px;font-weight:600;margin:20px 0 8px}.welcome p{max-width:540px;color:#858585;font-size:13px;line-height:1.8}
.suggestions{display:flex;gap:8px;flex-wrap:wrap;justify-content:center;margin-top:14px}.suggestions button{border:1px solid #e2e2e2;background:white;border-radius:18px;padding:8px 12px;font-size:12px;color:#555;cursor:pointer}
.user-message{display:flex;justify-content:flex-end;gap:10px;margin-bottom:30px}.user-message>div{background:#f3f3f3;padding:11px 15px;border-radius:16px;max-width:70%}.user-message small,.timeline-heading small{display:block;font-size:10px;color:#888}.user-message p{font-size:13px;margin:5px 0 0}.avatar{width:27px;height:27px;display:grid;place-items:center;border-radius:50%;font-size:11px}.user-avatar{background:#dedede}
.timeline-heading{display:flex;align-items:center;gap:10px;margin-bottom:15px}.assistant-avatar{border-radius:50%;font-size:15px}.timeline-heading strong{font-size:13px}.timeline-heading small{margin-top:3px}
.grouped-execution{display:flex;flex-direction:column;gap:9px}
.member-card{border:1px solid #e9e9e9;border-radius:11px;padding:9px 12px;background:#fff;min-width:0}
.agent-group-card{margin-left:calc(var(--group-depth)*20px);position:relative}
.agent-group-card.nested-agent-group{border-left:2px solid #e3e3e3}
.member-header{display:flex;align-items:center;justify-content:space-between;gap:10px;margin-bottom:6px;min-width:0}
.member-title{display:flex;align-items:center;gap:8px;min-width:0}
.member-header strong{font-size:12px;color:#444;white-space:nowrap}
.member-parent{font-size:10px;color:#aaa;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.member-status{font-size:10px;flex:none;color:#9a7a35;background:#faf5e8;padding:3px 7px;border-radius:5px}
.member-status.completed{color:#23845b;background:#edf7f1}
.event-notice{min-width:0}
.event-notice-line{display:flex;align-items:center;gap:8px;min-width:0;height:29px;overflow:hidden}
.event-type{flex:none;font-size:10px;color:#777;background:#f5f5f5;padding:3px 6px;border-radius:4px;white-space:nowrap}
.event-notice-content{flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:11px;line-height:1.4;color:#555}
.event-notice-empty{flex:1;min-width:0;font-size:11px;color:#aaa}
.event-time{flex:none;font-size:10px;color:#aaa;white-space:nowrap}
.event-expand-button{flex:none;display:flex;align-items:center;gap:4px;border:0;background:transparent;color:#777;font-size:10px;padding:4px 2px;cursor:pointer;white-space:nowrap}
.event-expand-button:hover{color:#222}
.expand-chevron{display:inline-block;font-size:13px;transition:transform .16s ease}
.expand-chevron.expanded{transform:rotate(180deg)}
.event-history{max-height:190px;overflow-y:auto;overscroll-behavior:contain;border-top:1px solid #f0f0f0;margin-top:4px;padding-top:3px;scrollbar-width:thin}
.history-line{height:27px;border-bottom:1px solid #f5f5f5}
.history-line:last-child{border-bottom:0}
.thinking-panel{margin-top:5px;padding:6px 8px;background:#f8f8f8;border-radius:7px;color:#777;font-size:10px}
.thinking-panel summary{cursor:pointer;user-select:none}
.thinking-row{padding:6px 0 0;color:#666}
.thinking-label{font-size:10px;color:#999}
.thinking-row p{margin:3px 0 0;white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.5;font-size:11px;color:#555}
.answer-duration{margin-top:5px;font-size:10px;color:#999}
.error-message{color:#b42318;text-align:center;font-size:12px;margin:0 20px 8px}
.composer-wrap{width:min(860px,calc(100% - 40px));margin:0 auto;padding:10px 0 18px;text-align:center;flex:none}
.composer{display:flex;flex-direction:column;gap:8px;border:1px solid #d9d9d9;border-radius:20px;padding:13px 13px 11px 17px;background:#fff;box-shadow:0 3px 14px #00000008;transition:border-color .16s ease,box-shadow .16s ease}
.composer:focus-within{border-color:#b8b8b8;box-shadow:0 3px 16px #0000000d}
.composer textarea{display:block;width:100%;min-height:28px;max-height:180px;resize:none;overflow-y:hidden;border:0;outline:0;font-family:inherit;font-size:14px;line-height:1.65;color:inherit;background:transparent;padding:2px 0;box-sizing:border-box}
.composer textarea::placeholder{color:#aaa}
.composer textarea:disabled{opacity:.65;cursor:wait}
.composer-toolbar{display:flex;align-items:center;justify-content:space-between;gap:12px;min-height:34px}
.composer-hints{display:flex;align-items:center;gap:6px;min-width:0;color:#a0a0a0;font-size:10px}
.hint-separator{color:#ccc}
.character-count{margin-left:8px;color:#aaa;font-variant-numeric:tabular-nums}
.send-button{width:35px;height:35px;flex:none;border:0;border-radius:50%;background:#242424;color:#fff;font-size:21px;line-height:1;cursor:pointer;display:grid;place-items:center;transition:background .15s ease,transform .15s ease}
.send-button:not(:disabled):hover{background:#444;transform:translateY(-1px)}
.send-button:focus-visible{outline:2px solid #777;outline-offset:3px}
.send-button:disabled{background:#e4e4e4;color:#fff;cursor:not-allowed}
.stop-button{font-size:12px;background:#555}
.composer-wrap>small{display:block;color:#aaa;font-size:10px;margin-top:8px}
@media(max-width:600px){.topbar{padding:0 16px}.conversation,.composer-wrap{width:calc(100% - 28px)}.conversation{padding-top:18px}.event-time{width:100%;margin-left:0}.tree-indent{width:calc(17px + var(--depth)*19px)}.tree-branch{width:19px}.tree-node{left:calc(var(--depth)*19px)}.composer{padding:11px 11px 9px 13px}.composer textarea{font-size:14px}.composer-hints{font-size:9px}}
</style>
