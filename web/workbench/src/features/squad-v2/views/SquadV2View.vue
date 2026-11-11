<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
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
type TimelineRow = { key: string; event: SquadEvent; depth: number; agentName: string; isAgentStart: boolean; durationLabel?: string }

const draft = ref('')
const lastPrompt = ref('')
const busy = ref(false)
const error = ref('')
const status = ref('等待发送消息')
const rows = ref<TimelineRow[]>([])
const agentsByInstance = new Map<string, AgentRef>()
const agentStartedAt = new Map<string, number>()
const finalAnswerRows = new Map<string, TimelineRow>()
const bottom = ref<HTMLElement | null>(null)
let controller: AbortController | null = null


const timeline = computed(() => rows.value)
const eventCount = computed(() => rows.value.length)

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

function resolveDepth(agent: AgentRef, visited = new Set<string>()): number {
  const instanceId = agent.agentInstanceId
  if (!instanceId || visited.has(instanceId)) return Math.max(0, Number(agent.depth || 0))
  visited.add(instanceId)
  const parentId = agent.parentAgentInstanceId
  if (!parentId) return 0
  const parent = agentsByInstance.get(parentId)
  return parent ? resolveDepth(parent, visited) + 1 : Math.max(1, Number(agent.depth || 1))
}

function addEvent(event: SquadEvent) {
  const agent = event.agent || {}
  const agentName = agent.agentName || '协作成员'
  const instanceId = agent.agentInstanceId || agent.agentId || 'unknown'
  if (agent.agentInstanceId) agentsByInstance.set(agent.agentInstanceId, agent)
  if (event.eventType === 'AGENT_START') agentStartedAt.set(instanceId, Date.now())
  if (event.eventType === 'AGENT_END') {
    const startedAt = agentStartedAt.get(instanceId)
    if (startedAt !== undefined) {
      const durationLabel = formatDuration(Date.now() - startedAt)
      const answerRow = finalAnswerRows.get(instanceId)
      if (answerRow) answerRow.durationLabel = durationLabel
      else row.durationLabel = durationLabel
      agentStartedAt.delete(instanceId)
    }
  }
  if (event.eventType === 'SUBAGENT_EXPOSED') {
    const child = event.payload?.childAgent as AgentRef | undefined
    if (child?.agentInstanceId) agentsByInstance.set(child.agentInstanceId, child)
  }
  const row: TimelineRow = {
    key: event.eventId || String(event.sequence || crypto.randomUUID()),
    event,
    depth: resolveDepth(agent),
    agentName,
    isAgentStart: event.eventType === 'AGENT_START',
  }
  rows.value.push(row)
  if (event.eventType === 'AGENT_RESULT') finalAnswerRows.set(instanceId, row)
  rows.value.sort((a, b) => Number(a.event.sequence || 0) - Number(b.event.sequence || 0))
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
  lastPrompt.value = message
  error.value = ''
  status.value = '正在连接测试事件流…'
  busy.value = true
  controller = new AbortController()
  draft.value = ''

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
          <div><strong>执行过程</strong><small>按发生顺序展示 · 保留成员从属关系</small></div>
        </div>

        <div class="event-stream">
          <article v-for="row in timeline" :key="row.key"
            class="event-row" :class="{ 'agent-start': row.isAgentStart }"
            :style="{ '--depth': row.depth }">
            <div class="tree-indent">
              <span v-for="n in row.depth" :key="n" class="tree-branch" />
              <span class="tree-node" />
            </div>
            <div class="event-content">
              <div class="event-meta">
                <span class="agent-name">{{ row.agentName }}</span>
                <span class="event-type">{{ eventLabel(row.event.eventType) }}</span>
                <span class="event-time">{{ displayTime(row.event.timestamp) }}</span>
              </div>
              <p v-if="contentOf(row.event)" class="event-text">{{ contentOf(row.event) }}</p>
              <div v-if="row.durationLabel" class="answer-duration">用时 {{ row.durationLabel }}</div>
            </div>
          </article>
        </div>
        <div ref="bottom" />
      </div>
    </section>

    <p v-if="error" class="error-message">{{ error }}</p>

    <footer class="composer-wrap">
      <form class="composer" @submit.prevent="send">
        <textarea v-model="draft" :disabled="busy" rows="1" maxlength="12000"
          placeholder="输入你想完成的任务…" @keydown.enter.exact.prevent="send" />
        <button v-if="busy" type="button" class="send-button stop-button" @click="stop" aria-label="停止">■</button>
        <button v-else type="submit" class="send-button" :disabled="!draft.trim()" aria-label="发送">↑</button>
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
.event-stream{position:relative;margin-left:14px}.event-row{display:flex;position:relative;min-height:43px;padding:5px 0 7px;gap:0}.tree-indent{width:calc(22px + var(--depth)*25px);flex:none;position:relative;display:flex;align-items:stretch}.tree-branch{width:25px;position:relative;flex:none;border-left:1px solid #dedede}.tree-branch:last-of-type:after{content:"";position:absolute;left:-1px;top:0;bottom:50%;border-left:1px solid #dedede}.tree-node{position:absolute;left:calc(var(--depth)*25px);top:15px;width:7px;height:7px;border-radius:50%;background:#aaa;border:2px solid white;box-shadow:0 0 0 1px #ccc}.tree-node:before{content:'';position:absolute;right:100%;top:3px;width:12px;border-top:1px solid #dedede}
.event-row.agent-start .tree-node{background:#303030;box-shadow:0 0 0 1px #303030}
.event-content{min-width:0;flex:1;border-bottom:1px solid #f0f0f0;padding:3px 0 8px}.event-meta{display:flex;align-items:center;gap:8px;flex-wrap:wrap}.agent-name{font-size:11px;font-weight:600;color:#555}.event-type{font:10px ui-monospace,SFMono-Regular,monospace;color:#858585;background:#f6f6f6;padding:3px 6px;border-radius:4px}.event-time{font-size:10px;color:#aaa;margin-left:auto}
.event-text{white-space:pre-wrap;overflow-wrap:anywhere;font-size:12px;line-height:1.65;color:#333;margin:6px 0 0}.answer-duration{margin-top:7px;font-size:10px;color:#999;display:flex;align-items:center;gap:4px}.answer-duration:before{content:'◷';font-size:12px}.event-details{margin-top:5px;font-size:10px;color:#888}.event-details summary{cursor:pointer}.event-details pre{white-space:pre-wrap;overflow-wrap:anywhere;background:#f7f7f7;padding:9px;border-radius:6px;font-size:10px}
.error-message{color:#b42318;text-align:center;font-size:12px;margin:0 20px 8px}
.composer-wrap{width:min(860px,calc(100% - 40px));margin:0 auto;padding:10px 0 18px;text-align:center;flex:none}
.composer{display:flex;align-items:flex-end;gap:10px;border:1px solid #d9d9d9;border-radius:18px;padding:10px 11px 10px 16px;box-shadow:0 2px 12px #00000008}
.composer textarea{flex:1;min-height:25px;max-height:160px;resize:vertical;border:0;outline:0;font-family:inherit;font-size:13px;line-height:1.6;color:inherit;background:transparent;padding:3px 0}.composer textarea::placeholder{color:#aaa}
.send-button{width:34px;height:34px;flex:none;border:0;border-radius:50%;background:#242424;color:#fff;font-size:20px;line-height:1;cursor:pointer}.send-button:disabled{background:#d7d7d7;cursor:not-allowed}.stop-button{font-size:12px}
.composer-wrap>small{display:block;color:#aaa;font-size:10px;margin-top:8px}
@media(max-width:600px){.topbar{padding:0 16px}.conversation,.composer-wrap{width:calc(100% - 28px)}.conversation{padding-top:18px}.event-time{width:100%;margin-left:0}.tree-indent{width:calc(17px + var(--depth)*19px)}.tree-branch{width:19px}.tree-node{left:calc(var(--depth)*19px)}}
</style>
