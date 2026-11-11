<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref } from 'vue'
import { getCsrfToken } from '../../../shared/api/csrf'

type EventItem = { id: string; type: string; source: string | null; data: Record<string, unknown> }
type ConversationItem = { role: 'USER' | 'ASSISTANT'; content: string }
const draft = ref('')
const busy = ref(false)
const error = ref('')
const history = ref<ConversationItem[]>([])
const visible = ref<EventItem[]>([])
const reply = ref('')
const bottom = ref<HTMLElement | null>(null)
let controller: AbortController | null = null

function textFromResult(value: unknown): string {
  if (!value || typeof value !== 'object') return ''
  const msg = value as { content?: Array<{ type?: string; text?: string }> }
  return (msg.content || []).filter(block => block.type === 'text').map(block => block.text || '').join('\\n')
}
function dispatch(raw: Record<string, unknown>) {
  const type = String(raw.type || 'UNKNOWN')
  const source = typeof raw.source === 'string' ? raw.source : null
  visible.value.push({ id: String(raw.id || crypto.randomUUID()), type, source, data: raw })
  if (type === 'TEXT_BLOCK_DELTA' && source === null) reply.value += String(raw.delta || '')
  if (type === 'AGENT_RESULT' && source === null) {
    const final = textFromResult(raw.result)
    if (final) reply.value = final
  }
  if (visible.value.length > 1000) visible.value.splice(0, visible.value.length - 1000)
  void nextTick(() => bottom.value?.scrollIntoView({ block: 'end', behavior: 'auto' }))
}
function parseFrame(frame: string) {
  const lines = frame.split('\\n')
  const payload = lines.filter(line => line.startsWith('data:')).map(line => line.slice(5).trimStart()).join('\\n')
  if (!payload) return
  try {
    const json = JSON.parse(payload) as Record<string, unknown>
    if (json.type) dispatch(json)
    else if (json.message) error.value = String(json.message)
  } catch { error.value = '收到无法解析的 SSE 数据' }
}
async function send() {
  const message = draft.value.trim()
  if (!message || busy.value) return
  draft.value = ''; error.value = ''; visible.value = []; reply.value = ''
  const prior = history.value.slice(-30).map(({ role, content }) => ({ role, content }))
  history.value.push({ role: 'USER', content: message })
  busy.value = true; controller = new AbortController()
  try {
    const headers: Record<string, string> = { 'Content-Type': 'application/json', Accept: 'text/event-stream' }
    const csrf = getCsrfToken()
    if (csrf) headers['X-CSRF-Token'] = csrf
    const base = import.meta.env.VITE_API_BASE_URL || ''
    const response = await fetch(`${base}/api/v1/native-chat/message`, {
      method: 'POST', credentials: base ? 'include' : 'same-origin', headers,
      body: JSON.stringify({ message, history: prior }), signal: controller.signal,
    })
    if (!response.ok || !response.body) throw new Error(`请求失败（HTTP ${response.status}）`)
    const reader = response.body.getReader(), decoder = new TextDecoder()
    let pending = ''
    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      pending += decoder.decode(value, { stream: true }).replace(/\\r\\n/g, '\\n')
      let boundary: number
      while ((boundary = pending.indexOf('\\n\\n')) !== -1) {
        parseFrame(pending.slice(0, boundary)); pending = pending.slice(boundary + 2)
      }
    }
    pending += decoder.decode()
    if (pending.trim()) parseFrame(pending)
  } catch (cause) {
    if (!controller.signal.aborted) error.value = cause instanceof Error ? cause.message : '请求失败'
  } finally {
    if (reply.value.trim()) history.value.push({ role: 'ASSISTANT', content: reply.value })
    busy.value = false; controller = null
  }
}
function stop() { controller?.abort() }
function clear() {
  if (busy.value) return
  history.value = []; visible.value = []; reply.value = ''; error.value = ''
}
onBeforeUnmount(stop)
</script>

<template>
  <main class="native-chat">
    <header class="native-header">
      <div><router-link to="/app/conversations">← 返回工作台</router-link><h1>AgentScope 原生聊天</h1><p>DeepSeek · POST /message · SSE · AgentEvent</p></div>
      <div class="header-actions">
        <router-link class="tool-link" to="/app/native-chat/subagents">SubAgent 实验</router-link>
        <router-link class="tool-link" to="/app/native-chat/events">事件测试工作台</router-link>
        <button type="button" :disabled="busy" @click="clear">清空当前页面</button>
      </div>
    </header>
    <section class="native-body" aria-label="聊天内容">
      <article v-for="(item, index) in history" :key="index" :class="['native-bubble', item.role === 'USER' ? 'mine' : 'assistant']">
        <small>{{ item.role === 'USER' ? '你' : 'DeepSeek' }}</small><p>{{ item.content }}</p>
      </article>
      <article v-if="busy || reply" class="native-bubble assistant">
        <small>DeepSeek {{ busy ? '· 输出中' : '' }}</small><p>{{ reply || '正在处理…' }}</p>
      </article>
      <p v-if="error" class="native-error" role="alert">{{ error }}</p>
      <details class="native-events">
        <summary>原生事件流（{{ visible.length }}）</summary>
        <ul><li v-for="event in visible" :key="event.id">
          <span class="native-type">{{ event.type }}</span><span>{{ event.source || 'Master' }}</span>
          <code v-if="event.type === 'TEXT_BLOCK_DELTA'">{{ String(event.data.delta || '') }}</code>
        </li></ul>
      </details>
      <div ref="bottom" />
    </section>
    <form class="native-composer" @submit.prevent="send">
      <textarea v-model="draft" :disabled="busy" rows="3" maxlength="12000" placeholder="发送消息给 DeepSeek……" @keydown.ctrl.enter.prevent="send" />
      <div><span>Ctrl + Enter 发送 · 当前页面临时上下文，不保存历史</span>
        <button v-if="busy" type="button" @click="stop">停止</button>
        <button v-else type="submit" :disabled="!draft.trim()">发送</button></div>
    </form>
  </main>
</template>

<style scoped>
.native-chat{height:100vh;display:flex;flex-direction:column;max-width:1100px;margin:auto;color:var(--text-primary,#222);background:var(--surface,#fff)}
.native-header{display:flex;justify-content:space-between;align-items:center;border-bottom:1px solid #ddd;padding:20px 28px;gap:16px}
.native-header h1{font-size:22px;margin:8px 0 2px}.native-header p{margin:0;color:#777;font-size:13px}
.native-header a{color:#4f6fec;text-decoration:none;font-size:13px}
.native-header button,.native-composer button{border:1px solid #bbb;background:#fff;color:#333;border-radius:8px;padding:9px 16px;cursor:pointer}
.header-actions{display:flex;align-items:center;gap:8px;flex-wrap:wrap}.header-actions .tool-link{border:1px solid #cbd5ef;border-radius:8px;padding:8px 11px;color:#3458a5;background:#f7f9ff}
.native-body{flex:1;min-height:0;overflow:auto;padding:28px;display:flex;flex-direction:column;gap:16px}
.native-bubble{max-width:82%;padding:14px 18px;border-radius:14px;background:#f4f5f8;white-space:pre-wrap;overflow-wrap:anywhere}
.native-bubble.mine{align-self:flex-end;background:#e9efff}.native-bubble.assistant{align-self:flex-start}
.native-bubble small{font-size:12px;color:#667}.native-bubble p{margin:8px 0 0;line-height:1.7}
.native-error{color:#a22}.native-events{margin-top:12px;border:1px solid #ddd;border-radius:10px;padding:12px}
.native-events ul{list-style:none;padding:0;max-height:230px;overflow:auto}
.native-events li{display:flex;gap:12px;align-items:center;border-bottom:1px solid #eee;padding:7px 0;font-size:12px;flex-wrap:wrap}
.native-type{font-weight:600;color:#385bb3}.native-events code{overflow-wrap:anywhere}
.native-composer{padding:16px 28px 24px;border-top:1px solid #ddd}
.native-composer textarea{display:block;width:100%;box-sizing:border-box;resize:vertical;border:1px solid #ccc;border-radius:12px;padding:14px;font:inherit}
.native-composer>div{display:flex;align-items:center;justify-content:space-between;margin-top:10px;gap:12px}
.native-composer span{color:#888;font-size:12px}
@media(max-width:600px){.native-header,.native-body,.native-composer{padding-left:16px;padding-right:16px}.native-bubble{max-width:94%}}
</style>
