<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref } from 'vue'
import { getCsrfToken } from '../../../shared/api/csrf'

type AgentName = 'calculator' | 'word_counter' | 'text_reverser'
type AgentState = { started: boolean; finished: boolean; text: string; toolCalls: string[] }
type EventItem = { id: string; type: string; source: string | null; data: Record<string, unknown> }
const task = ref('请并行完成三个子任务：1）计算 128 + 256；2）统计英文句子 “AgentScope supports parallel subagents” 的单词数量；3）反转文本 “AgentScope”。必须分别调用三个子智能体，每个子智能体调用自己的工具。')
const busy = ref(false), error = ref(''), answer = ref('')
const events = ref<EventItem[]>([])
const bottom = ref<HTMLElement | null>(null)
const agents = ref<Record<AgentName, AgentState>>({
  calculator: { started: false, finished: false, text: '', toolCalls: [] },
  word_counter: { started: false, finished: false, text: '', toolCalls: [] },
  text_reverser: { started: false, finished: false, text: '', toolCalls: [] },
})
let controller: AbortController | null = null
const forwardedBuffers = new Map<string, string>()
const agentCards = computed(() => [
  { id: 'calculator' as const, title: '计算子智能体', tool: 'add_numbers', description: '计算两个数字之和' },
  { id: 'word_counter' as const, title: '文本统计子智能体', tool: 'count_words', description: '统计文本中的词数' },
  { id: 'text_reverser' as const, title: '文本反转子智能体', tool: 'reverse_text', description: '反转输入文本' },
])
function sourceAgent(source: string | null): AgentName | null {
  if (!source) return null
  const leaf = source.split('/').at(-1)
  return leaf === 'calculator' || leaf === 'word_counter' || leaf === 'text_reverser' ? leaf : null
}
function finalText(value: unknown): string {
  if (!value || typeof value !== 'object') return ''
  const msg = value as { content?: Array<{ type?: string; text?: string }> }
  return (msg.content || []).filter(block => block.type === 'text').map(block => block.text || '').join('\n')
}
function receive(raw: Record<string, unknown>) {
  if (raw.type === 'TOOL_RESULT_TEXT_DELTA' && raw.source == null) {
    const toolName = String(raw.toolCallName || '')
    const childId: AgentName | null = toolName === 'call_calculator' ? 'calculator'
      : toolName === 'call_word_counter' ? 'word_counter'
      : toolName === 'call_text_reverser' ? 'text_reverser' : null
    if (childId) {
      const callId = String(raw.toolCallId || toolName)
      const buffered = (forwardedBuffers.get(callId) || '') + String(raw.delta || '')
      forwardedBuffers.set(callId, buffered)
      try {
        const childEvent = JSON.parse(buffered) as Record<string, unknown>
        forwardedBuffers.delete(callId)
        applyChildEvent(childId, childEvent)
      } catch {
        // 子事件 JSON 可能跨多个 SSE 增量分片。
      }
    }
  }
  const type = String(raw.type || 'UNKNOWN'), source = typeof raw.source === 'string' ? raw.source : null
  events.value.push({ id: String(raw.id || crypto.randomUUID()), type, source, data: raw })
  const id = sourceAgent(source)
  if (id) {
    const agent = agents.value[id]
    if (type === 'AGENT_START') agent.started = true
    if (type === 'AGENT_END') agent.finished = true
    if (type === 'TEXT_BLOCK_DELTA') agent.text += String(raw.delta || '')
    if (type === 'TOOL_CALL_START') agent.toolCalls.push(String(raw.toolCallName || 'tool'))
    if (type === 'AGENT_RESULT') {
      const text = finalText(raw.result)
      if (text) agent.text = text
    }
  } else if (type === 'TEXT_BLOCK_DELTA' && source === null) answer.value += String(raw.delta || '')
  else if (type === 'AGENT_RESULT' && source === null) {
    const text = finalText(raw.result)
    if (text) answer.value = text
  }
  void nextTick(() => bottom.value?.scrollIntoView({ block: 'end', behavior: 'auto' }))
}
function applyChildEvent(id: AgentName, event: Record<string, unknown>) {
  const agent = agents.value[id]
  const type = String(event.type || '')
  if (type === 'AGENT_START') agent.started = true
  if (type === 'AGENT_END') agent.finished = true
  if (type === 'TEXT_BLOCK_DELTA') agent.text += String(event.delta || '')
  if (type === 'TOOL_CALL_START') agent.toolCalls.push(String(event.toolCallName || 'tool'))
  if (type === 'AGENT_RESULT') {
    const text = finalText(event.result)
    if (text) agent.text = text
  }
  events.value.push({ id: String(event.id || crypto.randomUUID()), type, source: id, data: event })
}
function parseFrame(frame: string) {
  const payload = frame.split('\n').filter(line => line.startsWith('data:'))
    .map(line => line.slice(5).trimStart()).join('\n')
  if (!payload) return
  try {
    const value = JSON.parse(payload) as Record<string, unknown>
    if (value.type) receive(value)
    else if (value.message) error.value = String(value.message)
  } catch { error.value = '收到无法解析的 SSE 数据' }
}
function reset() {
  answer.value = ''; events.value = []
  for (const agent of Object.values(agents.value)) {
    agent.started = false; agent.finished = false; agent.text = ''; agent.toolCalls = []
  }
}
async function run() {
  if (busy.value || !task.value.trim()) return
  error.value = ''; reset(); forwardedBuffers.clear(); busy.value = true; controller = new AbortController()
  try {
    const headers: Record<string, string> = { 'Content-Type': 'application/json', Accept: 'text/event-stream' }
    const csrf = getCsrfToken()
    if (csrf) headers['X-CSRF-Token'] = csrf
    const base = import.meta.env.VITE_API_BASE_URL || ''
    const response = await fetch(`${base}/api/v1/native-chat/subagents`, {
      method: 'POST', credentials: base ? 'include' : 'same-origin', headers,
      body: JSON.stringify({ task: task.value.trim() }), signal: controller.signal,
    })
    if (!response.ok || !response.body) throw new Error(`请求失败（HTTP ${response.status}）`)
    const reader = response.body.getReader(), decoder = new TextDecoder()
    let pending = ''
    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      pending += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n')
      let boundary: number
      while ((boundary = pending.indexOf('\n\n')) !== -1) {
        parseFrame(pending.slice(0, boundary)); pending = pending.slice(boundary + 2)
      }
    }
    pending += decoder.decode()
    if (pending.trim()) parseFrame(pending)
  } catch (cause) {
    if (!controller.signal.aborted) error.value = cause instanceof Error ? cause.message : '请求失败'
  } finally {
    busy.value = false; controller = null
  }
}
function stop() { controller?.abort() }
onBeforeUnmount(stop)
</script>

<template>
  <main class="subagent-page">
    <header class="page-header">
      <div><router-link to="/app/native-chat">← 返回原生聊天</router-link><h1>多 SubAgent 并行实验</h1>
        <p>DeepSeek Master · 3 个 SubAgent · 3 个独立工具 · 原生 AgentEvent</p></div>
      <span class="parallel-tag">Toolkit parallel = true</span>
    </header>
    <section class="task-panel">
      <label for="subagent-task">给 Master 的任务</label>
      <textarea id="subagent-task" v-model="task" :disabled="busy" rows="3" maxlength="8000" />
      <div class="task-actions"><span>Master 应在同一轮发起三个 SubAgent 调用</span>
        <button v-if="busy" type="button" @click="stop">停止执行</button>
        <button v-else type="button" :disabled="!task.trim()" @click="run">创建并行任务</button></div>
    </section>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <section class="agent-grid" aria-label="子智能体执行状态">
      <article v-for="agent in agentCards" :key="agent.id" class="agent-card">
        <div class="agent-card-top"><h2>{{ agent.title }}</h2>
          <span :class="['status', agents[agent.id].finished ? 'done' : agents[agent.id].started ? 'running' : 'waiting']">
            {{ agents[agent.id].finished ? '已完成' : agents[agent.id].started ? '执行中' : '等待启动' }}
          </span></div>
        <p>{{ agent.description }}</p>
        <div class="tool-name">专属工具：<code>{{ agent.tool }}</code></div>
        <div class="agent-output">{{ agents[agent.id].text || '等待子智能体事件…' }}</div>
        <small>工具调用：{{ agents[agent.id].toolCalls.length }}</small>
      </article>
    </section>
    <section v-if="answer" class="master-answer"><h2>Master 汇总</h2><p>{{ answer }}</p></section>
    <section class="event-panel">
      <h2>实时原生事件 <small>({{ events.length }})</small></h2>
      <div class="event-list">
        <article v-for="event in events" :key="event.id" class="event-row">
          <span class="event-type">{{ event.type }}</span><span class="event-source">{{ event.source || 'Master' }}</span>
          <code v-if="event.type === 'TEXT_BLOCK_DELTA'">{{ String(event.data.delta || '') }}</code>
          <span v-else-if="event.type === 'TOOL_CALL_START'">{{ String(event.data.toolCallName || '') }}</span>
        </article>
        <p v-if="!events.length">运行后会在这里看到 Master 与三个子智能体的事件</p>
      </div>
    </section>
    <div ref="bottom" />
  </main>
</template>

<style scoped>
.subagent-page{min-height:100vh;max-width:1200px;margin:auto;padding:24px 28px 40px;box-sizing:border-box;color:var(--text-primary,#222)}
.page-header{display:flex;justify-content:space-between;align-items:center;gap:16px;border-bottom:1px solid #ddd;padding-bottom:18px}
.page-header a{color:#4f6fec;text-decoration:none;font-size:13px}.page-header h1{font-size:23px;margin:8px 0}.page-header p{margin:0;color:#777;font-size:13px}
.parallel-tag{font-size:12px;background:#eef4ff;color:#345ca8;padding:7px 10px;border-radius:8px;white-space:nowrap}
.task-panel,.agent-card,.master-answer,.event-panel{border:1px solid #ddd;border-radius:12px;padding:16px;margin-top:18px}
.task-panel label{display:block;font-weight:600;margin-bottom:8px}.task-panel textarea{width:100%;box-sizing:border-box;border:1px solid #ccc;border-radius:9px;padding:12px;font:inherit;resize:vertical}
.task-actions{display:flex;justify-content:space-between;align-items:center;gap:12px;margin-top:10px}.task-actions span,.agent-card small{font-size:12px;color:#888}
.task-actions button{border:0;background:#315fcb;color:white;padding:10px 16px;border-radius:8px;cursor:pointer}.task-actions button:disabled{opacity:.45;cursor:not-allowed}
.agent-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:14px}
.agent-card{margin-top:18px;min-width:0}.agent-card-top{display:flex;justify-content:space-between;gap:8px;align-items:center}.agent-card h2,.master-answer h2,.event-panel h2{font-size:16px;margin:0}
.agent-card p{font-size:13px;color:#777}.status{font-size:11px;padding:4px 7px;border-radius:6px;white-space:nowrap}.status.waiting{background:#f1f1f1;color:#666}.status.running{background:#fff4d9;color:#946200}.status.done{background:#e5f5e9;color:#26713b}
.tool-name{font-size:12px;color:#666}.tool-name code{color:#345ca8}.agent-output{min-height:74px;margin:12px 0;padding:10px;background:#f7f8fa;border-radius:8px;white-space:pre-wrap;overflow-wrap:anywhere;font-size:13px;line-height:1.6}
.master-answer p{white-space:pre-wrap;line-height:1.7}.event-panel h2 small{font-size:12px;color:#888;font-weight:400}
.event-list{max-height:340px;overflow:auto;margin-top:10px}.event-row{display:flex;gap:12px;align-items:flex-start;padding:8px 0;border-bottom:1px solid #eee;font-size:12px;flex-wrap:wrap}
.event-type{font-weight:700;color:#345ca8;min-width:150px}.event-source{color:#777;min-width:140px}.event-row code{white-space:pre-wrap;overflow-wrap:anywhere}
.error{color:#a22}
@media(max-width:760px){.subagent-page{padding:16px}.agent-grid{grid-template-columns:1fr}.page-header{align-items:flex-start;flex-direction:column}.parallel-tag{white-space:normal}}
</style>
