<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
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
type TimelineRow = { key: string; event: SquadEvent; agentName: string }
type AnswerBlock = {
  key: string
  agentInstanceId: string
  blockId: string
  kind: 'TEXT' | 'DATA' | 'RESULT'
  text: string
  completed: boolean
}
type AgentGroup = {
  key: string
  agent: AgentRef
  rows: TimelineRow[]
  children: AgentGroup[]
  depth: number
  started: boolean
  completed: boolean
  timerLabel: string
}

const draft = ref('')
const composerInput = ref<HTMLTextAreaElement | null>(null)
const lastPrompt = ref('')
const busy = ref(false)
const error = ref('')
const status = ref('等待发送消息')
const rows = ref<TimelineRow[]>([])
const answerBlocks = ref<AnswerBlock[]>([])
const executionExpanded = ref(true)
const executionState = ref('等待开始')
const executionClock = ref(Date.now())
const conversationScroller = ref<HTMLElement | null>(null)
const conversationContent = ref<HTMLElement | null>(null)
const followLatest = ref(true)
const showJumpToLatest = computed(() => !followLatest.value && (timeline.value.length > 0 || answerBlocks.value.length > 0))
const agentStartedAt = new Map<string, number>()
const agentFinishedDuration = new Map<string, number>()
let controller: AbortController | null = null
let executionTicker: ReturnType<typeof setInterval> | undefined
let conversationResizeObserver: ResizeObserver | undefined
let followFrame: number | undefined


const timeline = computed(() => rows.value)
const groupedTimeline = computed<AgentGroup[]>(() => {
  const agentRefs = new Map<string, AgentRef>()
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
      started: false,
      completed: false,
      timerLabel: '等待开始',
    })
  }

  for (const row of rows.value) {
    const key = agentKey(row.event.agent)
    const group = groups.get(key)
    if (group) group.rows.push(row)
  }

  const roots: AgentGroup[] = []
  for (const group of groups.values()) {
    group.rows.sort((a, b) => Number(a.event.sequence || 0) - Number(b.event.sequence || 0))
    group.children.sort((a, b) => firstSequence(a) - firstSequence(b))
    group.completed = group.rows.some(row => row.event.eventType === 'AGENT_END')
    const startedAt = agentStartedAt.get(group.key)
    const finishedDuration = agentFinishedDuration.get(group.key)
    group.started = startedAt !== undefined || finishedDuration !== undefined
    group.timerLabel = finishedDuration !== undefined
      ? formatExecutionTime(finishedDuration)
      : startedAt !== undefined
        ? formatExecutionTime(Math.max(0, executionClock.value - startedAt))
        : '等待开始'
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
function formatExecutionTime(ms: number) {
  const seconds = Math.max(0, Math.floor(ms / 1000))
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const remainder = seconds % 60
  return hours > 0
    ? `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(remainder).padStart(2, '0')}`
    : `${String(minutes).padStart(2, '0')}:${String(remainder).padStart(2, '0')}`
}

function startExecutionTimer() {
  if (executionTicker) clearInterval(executionTicker)
  const startedAt = Date.now()
  executionClock.value = startedAt
  executionState.value = '执行中'
  executionTicker = setInterval(() => { executionClock.value = Date.now() }, 1000)
}

function finishExecutionTimer(state: string) {
  executionClock.value = Date.now()
  executionState.value = state
  if (executionTicker) clearInterval(executionTicker)
  executionTicker = undefined
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
    if (followFrame !== undefined || typeof requestAnimationFrame === 'undefined') return
    followFrame = requestAnimationFrame(() => {
      followFrame = undefined
      if (followLatest.value && conversationScroller.value) {
        conversationScroller.value.scrollTop = conversationScroller.value.scrollHeight
      }
    })
  })
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
  try { return JSON.stringify(result, null, 2) } catch { return String(result) }
}

function answerBlock(agentInstanceId: string, blockId: string, kind: AnswerBlock['kind']): AnswerBlock {
  const key = `${agentInstanceId}:${kind}:${blockId}`
  let block = answerBlocks.value.find(item => item.key === key)
  if (!block) {
    block = { key, agentInstanceId, blockId, kind, text: '', completed: false }
    answerBlocks.value.push(block)
  }
  return block
}

function appendAnswerEvent(event: SquadEvent) {
  const agent = event.agent
  if (!isRootAgent(agent)) return
  const agentInstanceId = agent?.agentInstanceId || agent?.agentId || 'root'
  const payload = event.payload || {}
  const blockId = typeof payload.blockId === 'string'
    ? payload.blockId
    : typeof payload.replyId === 'string' ? payload.replyId : 'default'
  const type = event.eventType || ''

  if (type.startsWith('TEXT_BLOCK_')) {
    const block = answerBlock(agentInstanceId, blockId, 'TEXT')
    if (type === 'TEXT_BLOCK_DELTA' && typeof payload.delta === 'string') block.text += payload.delta
    if (type === 'TEXT_BLOCK_END') block.completed = true
    return
  }

  if (type.startsWith('DATA_BLOCK_')) {
    const block = answerBlock(agentInstanceId, blockId, 'DATA')
    if (type === 'DATA_BLOCK_DELTA') {
      if (typeof payload.delta === 'string') block.text += payload.delta
      else if (payload.data !== undefined) {
        const value = typeof payload.data === 'string' ? payload.data : JSON.stringify(payload.data, null, 2)
        block.text += value || ''
      }
    }
    if (type === 'DATA_BLOCK_END') block.completed = true
    return
  }

  if (type === 'AGENT_RESULT') {
    const finalText = resultTextOf(payload.result)
    if (!finalText) return
    const streamedText = answerBlocks.value
      .filter(block => block.agentInstanceId === agentInstanceId && block.kind !== 'RESULT')
      .map(block => block.text).join('')
    if (streamedText === finalText || streamedText.includes(finalText)) return
    const resultBlock = answerBlock(agentInstanceId, `result-${event.eventId || event.sequence || 'final'}`, 'RESULT')
    resultBlock.text = finalText
    resultBlock.completed = true
    return
  }

  if (type === 'AGENT_END') {
    for (const block of answerBlocks.value) {
      if (block.agentInstanceId === agentInstanceId) block.completed = true
    }
  }
}

function isJsonAnswer(block: AnswerBlock): boolean {
  if (block.kind === 'DATA') return true
  try { JSON.parse(block.text); return true } catch { return false }
}
function prettyAnswerJson(block: AnswerBlock): string {
  try { return JSON.stringify(JSON.parse(block.text), null, 2) } catch { return block.text }
}



function addEvent(event: SquadEvent) {
  const agent = event.agent || {}
  const agentName = agent.agentName || '协作成员'
  const instanceId = agent.agentInstanceId || agent.agentId || 'unknown'

  const row: TimelineRow = {
    key: event.eventId || String(event.sequence || crypto.randomUUID()),
    event,
    agentName,
  }
  rows.value.push(row)
  rows.value.sort((a, b) => Number(a.event.sequence || 0) - Number(b.event.sequence || 0))
  appendAnswerEvent(event)

  if (event.eventType === 'AGENT_START') {
    agentStartedAt.set(instanceId, Date.now())
    agentFinishedDuration.delete(instanceId)
  } else if (event.eventType === 'AGENT_END') {
    const startedAt = agentStartedAt.get(instanceId)
    if (startedAt !== undefined) {
      agentFinishedDuration.set(instanceId, Math.max(0, Date.now() - startedAt))
      agentStartedAt.delete(instanceId)
    }
  }

  scheduleFollowLatest()
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
  answerBlocks.value = []
  followLatest.value = true
  executionExpanded.value = true
  agentStartedAt.clear()
  agentFinishedDuration.clear()
  lastPrompt.value = message
  error.value = ''
  status.value = '正在连接测试事件流…'
  busy.value = true
  startExecutionTimer()
  controller = new AbortController()
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
    const wasAborted = controller?.signal.aborted ?? false
    busy.value = false
    finishExecutionTimer(error.value ? '执行异常' : wasAborted ? '已停止' : '已完成')
    executionExpanded.value = false
    controller = null
    scheduleFollowLatest()
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



onMounted(() => {
  if (conversationContent.value && typeof ResizeObserver !== 'undefined') {
    conversationResizeObserver = new ResizeObserver(scheduleFollowLatest)
    conversationResizeObserver.observe(conversationContent.value)
  }
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
      <div class="brand"><span class="brand-mark">S</span><div><strong>智能协作</strong><small>任务执行工作台</small></div></div>
      <div class="run-status"><span :class="['status-dot', { live: busy }]" />{{ status }}</div>
    </header>

    <section ref="conversationScroller" class="conversation" @scroll="onConversationScroll">
      <div ref="conversationContent" class="conversation-content">
        <div v-if="!lastPrompt && !busy && !timeline.length" class="welcome">
          <div class="welcome-mark">✳</div>
          <h1>让我们一起完成任务</h1>
          <p>输入消息开始体验。当前使用本地模拟事件，展示多个协作成员的执行过程与从属关系。</p>
        </div>

        <div v-else class="timeline">
          <article v-if="lastPrompt" class="user-message">
            <span class="avatar user-avatar">U</span>
            <div><small>我</small><p>{{ lastPrompt }}</p></div>
          </article>

          <section v-if="busy || groupedTimeline.length" class="execution-panel">
            <button
              type="button"
              class="execution-header"
              :aria-expanded="executionExpanded"
              @click="executionExpanded = !executionExpanded"
            >
              <span class="execution-heading-left">
                <span class="execution-mark" :class="{ running: busy }"></span>
                <strong>执行过程</strong>
                <span class="execution-state">{{ busy ? '进行中' : executionState }}</span>
              </span>
              <span class="execution-heading-right">
                <span>{{ executionExpanded ? '收起' : '展开' }}</span>
                <span :class="['execution-chevron', { expanded: executionExpanded }]">⌄</span>
              </span>
            </button>

            <Transition name="execution-fold">
              <div v-show="executionExpanded" class="execution-panel-content">
                <div class="grouped-execution">
                  <article
                    v-for="group in groupedTimeline"
                    :key="group.key"
                    class="agent-row"
                    :class="{ 'nested-agent-row': group.depth > 0 }"
                    :style="{ '--group-depth': group.depth }"
                  >
                    <strong class="agent-row-name">
                      {{ group.agent.agentName || (group.depth === 0 ? '主协调员' : '协作成员') }}
                    </strong>
                    <span :class="['member-status', { completed: group.completed }]">
                      {{ group.completed ? '已完成' : group.started ? '执行中' : '等待开始' }}
                    </span>
                    <time class="agent-row-timer" :aria-label="'执行用时 ' + group.timerLabel">
                      <span class="timer-icon">◷</span>{{ group.timerLabel }}
                    </time>
                  </article>
                </div>
              </div>
            </Transition>
          </section>

          <section v-if="answerBlocks.length || busy" class="answer-section">
            <div class="answer-heading">
              <strong>回答</strong>
              <span v-if="busy" class="answer-live"><i></i>正在生成</span>
            </div>
            <div v-if="answerBlocks.length" class="answer-blocks">
              <article v-for="block in answerBlocks" :key="block.key" class="answer-block">
                <small v-if="block.kind === 'RESULT'" class="answer-block-label">最终回答</small>
                <pre v-if="isJsonAnswer(block)" class="answer-json"><code>{{ prettyAnswerJson(block) }}</code></pre>
                <SafeMarkdown v-else :text="block.text" :streaming="busy && !block.completed" />
                <span v-if="busy && !block.completed && !block.text" class="answer-placeholder">正在生成内容…</span>
              </article>
            </div>
            <div v-else class="answer-placeholder">正在等待正文输出…</div>
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
.squad-v2-page{height:100vh;min-height:560px;display:flex;flex-direction:column;background:var(--surface,#fff);color:var(--text-primary,#202124)}
.topbar{height:62px;flex:none;border-bottom:1px solid var(--border,#e7e7e7);display:flex;align-items:center;justify-content:space-between;padding:0 28px}
.brand{display:flex;align-items:center;gap:10px}.brand-mark,.assistant-avatar{width:30px;height:30px;border-radius:10px;background:#242424;color:white;display:grid;place-items:center;font-weight:700}
.brand strong{display:block;font-size:14px}.brand small{display:block;color:#888;font-size:10px;margin-top:2px}
.run-status{display:flex;align-items:center;gap:8px;color:#777;font-size:12px}.status-dot{width:7px;height:7px;border-radius:50%;background:#aaa}.status-dot.live{background:#16a36a;box-shadow:0 0 0 3px #16a36a22}
.conversation{position:relative;width:min(860px,calc(100% - 40px));margin:0 auto;flex:1;min-height:0;overflow:auto;padding:26px 0 24px;box-sizing:border-box;overscroll-behavior:contain}
.conversation-content{min-height:100%;display:flex;flex-direction:column}
.timeline{display:flex;flex-direction:column;gap:18px}
.welcome{min-height:100%;display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center;padding:30px;box-sizing:border-box}
.welcome-mark{font-size:32px;width:58px;height:58px;border-radius:20px;background:#f2f2f2;display:grid;place-items:center}
.welcome h1{font-size:25px;font-weight:600;margin:20px 0 8px}.welcome p{max-width:540px;color:#858585;font-size:13px;line-height:1.8}
.suggestions{display:flex;gap:8px;flex-wrap:wrap;justify-content:center;margin-top:14px}.suggestions button{border:1px solid #e2e2e2;background:white;border-radius:18px;padding:8px 12px;font-size:12px;color:#555;cursor:pointer}
.user-message{display:flex;justify-content:flex-end;gap:10px;margin-bottom:4px}.user-message>div{background:#f3f3f3;padding:11px 15px;border-radius:16px;max-width:70%}.user-message small,.timeline-heading small{display:block;font-size:10px;color:#888}.user-message p{font-size:13px;margin:5px 0 0}.avatar{width:27px;height:27px;display:grid;place-items:center;border-radius:50%;font-size:11px}.user-avatar{background:#dedede}
.assistant-avatar{border-radius:50%;font-size:15px}
.execution-panel{border:1px solid #ececec;border-radius:13px;background:#fff;overflow:hidden}
.execution-header{width:100%;display:flex;align-items:center;justify-content:space-between;gap:12px;padding:11px 13px;border:0;background:transparent;color:inherit;text-align:left;cursor:pointer}
.execution-heading-left,.execution-heading-right{display:flex;align-items:center;gap:9px;min-width:0}
.execution-heading-left strong{font-size:12px;font-weight:600;color:#555}

.execution-state{font-size:10px;color:#999}
.execution-mark{width:7px;height:7px;border-radius:50%;background:#b9b9b9;flex:none}
.execution-mark.running{background:#6d9d83;box-shadow:0 0 0 3px #6d9d8318}
.execution-heading-right{font-size:10px;color:#999;flex:none}
.execution-chevron{font-size:13px;transition:transform .18s ease}
.execution-chevron.expanded{transform:rotate(180deg)}
.execution-panel-content{padding:0 11px 11px}
.execution-fold-enter-active,.execution-fold-leave-active{transition:opacity .16s ease,transform .16s ease;transform-origin:top}
.execution-fold-enter-from,.execution-fold-leave-to{opacity:0;transform:translateY(-4px)}
.grouped-execution{display:flex;flex-direction:column;gap:5px}
.agent-row{display:flex;align-items:center;gap:9px;min-width:0;padding:8px 10px;border:1px solid #ededed;border-radius:8px;background:#fff}
.agent-row.nested-agent-row{margin-left:calc(var(--group-depth)*18px);border-left:2px solid #e5e5e5}
.agent-row-name{flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:11px;font-weight:600;color:#555}
.member-status{font-size:10px;flex:none;color:#9a7a35;background:#faf5e8;padding:3px 7px;border-radius:5px}
.member-status.completed{color:#56856c;background:#f0f6f2}
.agent-row-timer{display:inline-flex;align-items:center;gap:5px;flex:none;min-width:66px;justify-content:flex-end;font-size:11px;font-variant-numeric:tabular-nums;color:#777}
.timer-icon{font-size:12px;color:#aaa}
.answer-duration{margin-top:5px;font-size:10px;color:#999}
.answer-section{padding:2px 1px 8px}
.answer-heading{display:flex;align-items:center;gap:9px;margin-bottom:10px}
.answer-heading strong{font-size:13px;font-weight:600;color:#555}
.answer-live{display:inline-flex;align-items:center;gap:5px;color:#999;font-size:10px}
.answer-live i{width:5px;height:5px;border-radius:50%;background:#8baa98;animation:answer-pulse 1.8s ease-in-out infinite}
@keyframes answer-pulse{50%{opacity:.35}}
.answer-blocks{display:flex;flex-direction:column;gap:16px}
.answer-block{min-width:0;color:var(--text-primary,#202124);font-size:14px;line-height:1.75;overflow-wrap:anywhere}
.answer-block-label{display:block;margin-bottom:7px;color:#888;font-size:10px}
.answer-json{max-width:100%;overflow:auto;padding:13px 15px;border:1px solid #ededed;border-radius:10px;background:#fafafa;color:#444;font-size:12px;line-height:1.6;white-space:pre}
.answer-json code{font-family:ui-monospace,SFMono-Regular,Menlo,monospace}
.answer-placeholder{display:inline-flex;align-items:center;min-height:24px;color:#aaa;font-size:12px}
.jump-latest{position:sticky;bottom:12px;align-self:center;display:flex;align-items:center;gap:7px;margin:10px auto 0;border:1px solid #e8e8e8;border-radius:18px;padding:7px 12px;background:#ffffffed;color:#777;font-size:11px;box-shadow:0 2px 10px #0000000a;cursor:pointer;backdrop-filter:blur(8px)}
.jump-latest:hover{color:#444;border-color:#dcdcdc}
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
@media(max-width:600px){.topbar{padding:0 16px}.conversation,.composer-wrap{width:calc(100% - 28px)}.conversation{padding-top:18px}.agent-row{gap:7px;padding:8px 7px}.agent-row.nested-agent-row{margin-left:calc(var(--group-depth)*10px)}.agent-row-name{font-size:10px}.agent-row-timer{min-width:60px;font-size:10px}.execution-header{padding:10px}.execution-panel-content{padding:0 8px 8px}.composer{padding:11px 11px 9px 13px}.composer textarea{font-size:14px}.composer-hints{font-size:9px}}
</style>
