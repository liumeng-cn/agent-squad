<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { getCsrfToken } from '../../../shared/api/csrf'

type EventCategory = 'Agent 生命周期' | '模型调用' | '文本' | '思考' | '数据块' | '工具调用' | '工具结果' | '执行控制' | '人工确认' | '停止' | 'SubAgent' | '提示' | '权限' | '扩展'
type EventDefinition = { type: string; category: EventCategory; description: string; trigger: string; sample: Record<string, unknown>; actual: boolean }
type EventRecord = { id: string; type: string; source: string | null; data: Record<string, unknown>; origin: '真实事件' | '模拟事件'; receivedAt: string }

const definitions: EventDefinition[] = [
  { type: 'AGENT_START', category: 'Agent 生命周期', description: 'Agent 开始处理一次调用', trigger: '发送普通聊天消息', actual: true, sample: { sessionId: 'session-demo', replyId: 'reply-demo', name: 'EventWorkbenchAgent', role: 'assistant' } },
  { type: 'AGENT_END', category: 'Agent 生命周期', description: 'Agent 本次调用结束', trigger: '普通聊天自然结束', actual: true, sample: { replyId: 'reply-demo' } },
  { type: 'AGENT_RESULT', category: 'Agent 生命周期', description: '携带最终 Msg 的结果事件', trigger: '普通聊天成功返回', actual: true, sample: { result: { content: [{ type: 'text', text: '模拟最终回答' }] } } },
  { type: 'MODEL_CALL_START', category: '模型调用', description: '开始调用大模型', trigger: '发送普通聊天消息', actual: true, sample: { replyId: 'reply-demo' } },
  { type: 'MODEL_CALL_END', category: '模型调用', description: '大模型调用结束，可能携带 Token 用量', trigger: '模型调用完成', actual: true, sample: { replyId: 'reply-demo', usage: { inputTokens: 12, outputTokens: 24 } } },
  { type: 'TEXT_BLOCK_START', category: '文本', description: '文本内容块开始', trigger: '模型输出文本块', actual: true, sample: { replyId: 'reply-demo', blockId: 'text-1' } },
  { type: 'TEXT_BLOCK_DELTA', category: '文本', description: '文本增量片段', trigger: '模型流式输出', actual: true, sample: { replyId: 'reply-demo', blockId: 'text-1', delta: 'Hello from fixture' } },
  { type: 'TEXT_BLOCK_END', category: '文本', description: '文本内容块结束', trigger: '模型完成文本块', actual: true, sample: { replyId: 'reply-demo', blockId: 'text-1' } },
  { type: 'THINKING_BLOCK_START', category: '思考', description: '思考内容块开始', trigger: '模型支持并返回思考内容', actual: false, sample: { replyId: 'reply-demo', blockId: 'thinking-1' } },
  { type: 'THINKING_BLOCK_DELTA', category: '思考', description: '思考内容增量', trigger: '模型流式返回思考内容', actual: false, sample: { replyId: 'reply-demo', blockId: 'thinking-1', delta: '模拟思考片段' } },
  { type: 'THINKING_BLOCK_END', category: '思考', description: '思考内容块结束', trigger: '模型完成思考块', actual: false, sample: { replyId: 'reply-demo', blockId: 'thinking-1' } },
  { type: 'DATA_BLOCK_START', category: '数据块', description: '多模态数据块开始', trigger: '模型或消息产生数据内容块', actual: false, sample: { replyId: 'reply-demo', blockId: 'data-1' } },
  { type: 'DATA_BLOCK_DELTA', category: '数据块', description: '多模态数据增量', trigger: '数据内容分片到达', actual: false, sample: { replyId: 'reply-demo', blockId: 'data-1', delta: 'base64-fragment' } },
  { type: 'DATA_BLOCK_END', category: '数据块', description: '多模态数据块结束', trigger: '数据内容块完成', actual: false, sample: { replyId: 'reply-demo', blockId: 'data-1' } },
  { type: 'TOOL_CALL_START', category: '工具调用', description: '模型开始生成工具调用', trigger: '提示模型调用已注册工具', actual: true, sample: { replyId: 'reply-demo', toolCallId: 'call-1', toolCallName: 'demo_tool' } },
  { type: 'TOOL_CALL_DELTA', category: '工具调用', description: '工具参数增量生成', trigger: '模型以流式方式生成工具参数', actual: true, sample: { replyId: 'reply-demo', toolCallId: 'call-1', toolCallName: 'demo_tool', delta: '{"value":' } },
  { type: 'TOOL_CALL_END', category: '工具调用', description: '工具调用参数生成完成', trigger: '模型完成工具参数', actual: true, sample: { replyId: 'reply-demo', toolCallId: 'call-1', toolCallName: 'demo_tool' } },
  { type: 'TOOL_RESULT_START', category: '工具结果', description: '工具开始返回结果', trigger: '工具执行开始返回', actual: true, sample: { replyId: 'reply-demo', toolCallId: 'call-1', toolCallName: 'demo_tool' } },
  { type: 'TOOL_RESULT_TEXT_DELTA', category: '工具结果', description: '工具文本结果增量', trigger: '工具流式返回文本', actual: true, sample: { replyId: 'reply-demo', toolCallId: 'call-1', toolCallName: 'demo_tool', delta: '工具执行结果' } },
  { type: 'TOOL_RESULT_DATA_DELTA', category: '工具结果', description: '工具返回非文本数据块', trigger: '工具返回图片或其他 ContentBlock', actual: false, sample: { replyId: 'reply-demo', toolCallId: 'call-1', toolCallName: 'image_tool', data: { type: 'image', data: 'fixture-base64' } } },
  { type: 'TOOL_RESULT_END', category: '工具结果', description: '工具结果结束，包含执行状态', trigger: '工具执行完成', actual: true, sample: { replyId: 'reply-demo', toolCallId: 'call-1', toolCallName: 'demo_tool', state: '已完成' } },
  { type: 'EXCEED_MAX_ITERS', category: '执行控制', description: 'ReAct 执行轮数超过上限', trigger: '设置较小最大轮数并持续触发工具', actual: true, sample: { replyId: 'reply-demo', maxIters: 1, currentIter: 2 } },
  { type: 'REQUIRE_USER_CONFIRM', category: '人工确认', description: '工具执行前要求用户确认', trigger: '启用人工确认或权限确认机制', actual: true, sample: { replyId: 'reply-demo', toolCalls: [{ id: 'call-1', name: 'delete_file', input: { path: '/tmp/demo' } }] } },
  { type: 'REQUIRE_EXTERNAL_EXECUTION', category: '人工确认', description: '请求在 Agent 外部执行工具', trigger: '配置外部工具执行流程', actual: true, sample: { replyId: 'reply-demo', toolCalls: [{ id: 'call-2', name: 'external_tool', input: {} }] } },
  { type: 'USER_CONFIRM_RESULT', category: '人工确认', description: '用户提交确认或拒绝结果', trigger: '响应 REQUIRE_USER_CONFIRM', actual: true, sample: { replyId: 'reply-demo', confirmResults: [{ toolCallId: 'call-1', confirmed: true }] } },
  { type: 'EXTERNAL_EXECUTION_RESULT', category: '人工确认', description: '外部工具执行结果回传', trigger: '将外部执行结果交还 Agent', actual: true, sample: { replyId: 'reply-demo', toolResults: [{ id: 'call-2', name: 'external_tool', output: 'ok' }] } },
  { type: 'REQUEST_STOP', category: '停止', description: '中间件请求 Agent 在当前阶段后停止', trigger: 'Middleware 发出 RequestStopEvent', actual: true, sample: { reason: '测试主动停止', generateReason: 'MIDDLEWARE_STOP_REQUESTED' } },
  { type: 'SUBAGENT_EXPOSED', category: 'SubAgent', description: '子智能体作为用户入口暴露', trigger: 'spawn 子智能体并设置 expose_to_user=true', actual: true, sample: { subagentId: 'subagent-demo', agentId: 'agent-demo', sessionId: 'session-demo', label: '研究助手' } },
  { type: 'HINT_BLOCK', category: '提示', description: '发送一次性提示内容，不按增量拆分', trigger: '团队消息、后台工具结果或用户插话', actual: true, sample: { replyId: 'reply-demo', blockId: 'hint-1', hintSource: 'system', hint: '后台任务已完成' } },
  { type: 'ALL_TOOLS_DENIED', category: '权限', description: '最近一轮所有工具调用均被拒绝', trigger: '拒绝本轮所有工具调用', actual: true, sample: { deniedToolCalls: [{ id: 'call-1', name: 'restricted_tool', input: {} }] } },
  { type: 'CUSTOM', category: '扩展', description: '应用自定义通知事件', trigger: '中间件或业务层发送 CustomEvent', actual: true, sample: { name: 'state_updated', value: { taskId: 'task-demo', status: 'RUNNING' } } },
]

const categories = ['全部', ...new Set(definitions.map(item => item.category))]
const selectedCategory = ref('全部')
const search = ref('')
const records = ref<EventRecord[]>([])
const selectedRecord = ref<EventRecord | null>(null)
const status = ref('')
const busy = ref(false)
let controller: AbortController | null = null
const filtered = computed(() => definitions.filter(item =>
  (selectedCategory.value === '全部' || item.category === selectedCategory.value)
  && (item.type.toLowerCase().includes(search.value.toLowerCase()) || item.description.includes(search.value)),
))
const actualCount = computed(() => definitions.filter(item => item.actual).length)
const simulatedCount = computed(() => definitions.length - actualCount.value)

function append(type: string, data: Record<string, unknown>, origin: EventRecord['origin'], id?: string, source: string | null = null) {
  const record: EventRecord = { id: id || crypto.randomUUID().replaceAll('-', ''), type, source, data: { type, ...data }, origin, receivedAt: new Date().toLocaleTimeString() }
  records.value.unshift(record)
  selectedRecord.value = record
}
function simulate(item: EventDefinition) {
  append(item.type, { ...item.sample, id: crypto.randomUUID().replaceAll('-', ''), createdAt: new Date().toISOString() }, '模拟事件')
  status.value = item.type + '：已生成模拟事件。该结果只验证前端解析与展示，不代表 SDK 实际触发。'
}
function simulateLifecycle() {
  const group = definitions.filter(item => ['AGENT_START', 'MODEL_CALL_START', 'TEXT_BLOCK_START', 'TEXT_BLOCK_DELTA', 'TEXT_BLOCK_END', 'MODEL_CALL_END', 'AGENT_RESULT', 'AGENT_END'].includes(item.type))
  for (const item of [...group].reverse()) append(item.type, { ...item.sample, createdAt: new Date().toISOString() }, '模拟事件')
  status.value = '已生成一组模拟的 Agent 文本执行生命周期。'
}
function parseFrame(frame: string) {
  const lines = frame.split('\n')
  const eventName = lines.find(line => line.startsWith('event:'))?.slice(6).trim()
  const id = lines.find(line => line.startsWith('id:'))?.slice(3).trim()
  const payload = lines.filter(line => line.startsWith('data:')).map(line => line.slice(5).trimStart()).join('\n')
  if (!payload) return
  try {
    const data = JSON.parse(payload) as Record<string, unknown>
    const type = String(data.type || eventName || 'UNKNOWN')
    append(type, data, '真实事件', String(data.id || id || crypto.randomUUID()), typeof data.source === 'string' ? data.source : null)
  } catch {
    append('UNPARSEABLE_FRAME', { eventName, payload }, '真实事件', id)
  }
}
async function runReal(path: 'message' | 'subagents') {
  if (busy.value) return
  const message = path === 'message'
    ? window.prompt('输入一条消息。建议：请调用工具完成 12 + 30。', '请调用工具完成 12 + 30。')
    : window.prompt('输入 SubAgent 测试任务。', '请并行调用三个子智能体，分别计算 12 + 30、统计 AgentScope 的单词数、反转 AgentScope。')
  if (!message?.trim()) return
  busy.value = true
  status.value = '正在连接真实 AgentScope SSE...'
  controller = new AbortController()
  try {
    const headers: Record<string, string> = { 'Content-Type': 'application/json', Accept: 'text/event-stream' }
    const csrf = getCsrfToken()
    if (csrf) headers['X-CSRF-Token'] = csrf
    const base = import.meta.env.VITE_API_BASE_URL || ''
    const response = await fetch(`${base}/api/v1/native-chat/${path}`, {
      method: 'POST', credentials: base ? 'include' : 'same-origin', headers,
      body: JSON.stringify(path === 'message' ? { message, history: [] } : { task: message }),
      signal: controller.signal,
    })
    if (!response.ok || !response.body) throw new Error(`请求失败（HTTP ${response.status}）`)
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let pending = ''
    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      pending += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n')
      let boundary: number
      while ((boundary = pending.indexOf('\n\n')) >= 0) {
        parseFrame(pending.slice(0, boundary))
        pending = pending.slice(boundary + 2)
      }
    }
    pending += decoder.decode()
    if (pending.trim()) parseFrame(pending)
    status.value = '真实 SSE 流已结束。请检查事件列表与原始 JSON。'
  } catch (error) {
    if (!controller.signal.aborted) status.value = error instanceof Error ? error.message : '真实事件请求失败'
  } finally {
    busy.value = false
    controller = null
  }
}
function stop() { controller?.abort(); status.value = '已请求断开当前 SSE；这不等于 Agent 可恢复。' }
function clear() { records.value = []; selectedRecord.value = null; status.value = '' }
async function copySelected() {
  if (!selectedRecord.value) return
  await navigator.clipboard.writeText(JSON.stringify(selectedRecord.value.data, null, 2))
  status.value = '已复制当前事件 JSON。'
}
onBeforeUnmount(stop)
</script>

<template>
  <main class="event-workbench">
    <header class="header">
      <div>
        <router-link to="/app/native-chat">← 返回原生聊天</router-link>
        <h1>AgentScope 事件测试工作台</h1>
        <p>基于 AgentScope Java 2.1.0-BETA1 · 原生事件协议检查</p>
      </div>
      <div class="header-actions">
        <button type="button" :disabled="busy" @click="runReal('message')">真实聊天测试</button>
        <button type="button" :disabled="busy" @click="runReal('subagents')">真实 SubAgent 测试</button>
        <button v-if="busy" type="button" class="danger" @click="stop">停止 SSE</button>
      </div>
    </header>

    <section class="summary">
      <div><strong>{{ definitions.length }}</strong><span>官方事件类型</span></div>
      <div><strong>{{ actualCount }}</strong><span>可通过现有场景观察</span></div>
      <div><strong>{{ simulatedCount }}</strong><span>需专门模型/工具条件</span></div>
      <div><strong>{{ records.length }}</strong><span>本次收到的事件</span></div>
    </section>

    <p v-if="status" class="notice">{{ status }}</p>

    <section class="layout">
      <div class="catalog">
        <div class="catalog-heading">
          <h2>事件目录</h2>
          <input v-model="search" placeholder="搜索事件或说明" />
        </div>
        <nav class="category-tabs">
          <button v-for="category in categories" :key="category" :class="{ active: selectedCategory === category }" @click="selectedCategory = category">{{ category }}</button>
        </nav>
        <div class="event-list">
          <article v-for="item in filtered" :key="item.type" class="event-card">
            <div class="event-card-heading">
              <div><strong>{{ item.type }}</strong><small>{{ item.category }}</small></div>
              <span :class="item.actual ? 'tag-real' : 'tag-fixture'">{{ item.actual ? '有真实触发路径' : '依赖特定条件' }}</span>
            </div>
            <p>{{ item.description }}</p>
            <small class="trigger">触发方式：{{ item.trigger }}</small>
            <button type="button" class="simulate" @click="simulate(item)">模拟触发</button>
          </article>
        </div>
      </div>

      <aside class="inspector">
        <div class="inspector-heading">
          <h2>事件记录</h2>
          <div><button type="button" @click="simulateLifecycle">模拟基础生命周期</button><button type="button" @click="clear">清空</button></div>
        </div>
        <div v-if="!records.length" class="empty">还没有事件。可以模拟单个事件，或连接真实 AgentScope SSE。</div>
        <button v-for="record in records" :key="record.id" type="button" class="record" :class="{ selected: selectedRecord?.id === record.id }" @click="selectedRecord = record">
          <strong>{{ record.type }}</strong>
          <span>{{ record.origin }} · {{ record.receivedAt }}</span>
          <small>{{ record.source || 'Master / 无 source' }}</small>
        </button>
        <div v-if="selectedRecord" class="json-panel">
          <div class="json-heading"><strong>原始 JSON</strong><button type="button" @click="copySelected">复制 JSON</button></div>
          <pre>{{ JSON.stringify(selectedRecord.data, null, 2) }}</pre>
          <small>事件来源：{{ selectedRecord.origin }}；接收时间：{{ selectedRecord.receivedAt }}</small>
        </div>
      </aside>
    </section>

    <footer>
      <strong>测试说明：</strong>
      “模拟触发”只用于验证前端事件解析、列表展示和 JSON 检查；只有“真实聊天测试”和“真实 SubAgent 测试”收到的事件才来自 AgentScope 实际执行。
      目录中的“有真实触发路径”表示理论上有对应 SDK 执行路径，不代表当前实验已经成功触发。
    </footer>
  </main>
</template>

<style scoped>
.event-workbench{max-width:1440px;margin:0 auto;padding:24px 28px 40px;color:var(--text-primary,#222)}
.header{display:flex;justify-content:space-between;align-items:center;gap:20px;border-bottom:1px solid #ddd;padding-bottom:18px}
.header a{font-size:13px;color:#4f6fec;text-decoration:none}.header h1{font-size:24px;margin:8px 0}.header p{font-size:13px;color:#888;margin:0}
.header-actions{display:flex;gap:8px;flex-wrap:wrap}.header-actions button,.inspector-heading button,.json-heading button{border:1px solid #ccc;background:#fff;border-radius:8px;padding:8px 12px;cursor:pointer}
.header-actions button:first-child{background:#315fcb;color:#fff;border-color:#315fcb}.header-actions button:disabled{opacity:.5}.header-actions .danger{color:#a22}
.summary{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin:18px 0}
.summary>div{border:1px solid #ddd;border-radius:10px;padding:13px 16px;display:flex;flex-direction:column;gap:4px}
.summary strong{font-size:23px}.summary span{font-size:12px;color:#777}
.notice{padding:10px 13px;background:#f2f5fb;border-radius:8px;font-size:13px}
.layout{display:grid;grid-template-columns:minmax(0,1.2fr) minmax(360px,.8fr);gap:18px;align-items:start}
.catalog,.inspector{border:1px solid #ddd;border-radius:12px;padding:16px;min-width:0}
.catalog-heading,.inspector-heading,.json-heading{display:flex;justify-content:space-between;align-items:center;gap:10px}
.catalog-heading h2,.inspector-heading h2{font-size:17px;margin:0}
.catalog-heading input{width:220px;max-width:50%;border:1px solid #ccc;border-radius:8px;padding:8px}
.category-tabs{display:flex;gap:6px;flex-wrap:wrap;margin:14px 0}
.category-tabs button{border:1px solid #ddd;border-radius:20px;background:#fff;padding:6px 10px;font-size:12px;cursor:pointer}
.category-tabs button.active{background:#eaf0ff;border-color:#8ba5ef;color:#284caa}
.event-list{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:9px;max-height:690px;overflow:auto;padding-right:3px}
.event-card{border:1px solid #e4e4e4;border-radius:9px;padding:11px;min-width:0}
.event-card-heading{display:flex;justify-content:space-between;align-items:flex-start;gap:8px}
.event-card-heading>div{display:flex;flex-direction:column;gap:3px;min-width:0}.event-card-heading strong{font-size:12px;overflow-wrap:anywhere}.event-card-heading small,.event-card p,.trigger{font-size:11px;color:#777}
.tag-real,.tag-fixture{font-size:10px;white-space:nowrap;border-radius:5px;padding:3px 5px;background:#edf5ef;color:#327044}.tag-fixture{background:#fff4df;color:#906000}
.event-card p{line-height:1.5;min-height:32px}.trigger{display:block;line-height:1.5;min-height:32px}
.simulate{margin-top:8px;border:1px solid #c8d4f2;background:#f5f7ff;color:#3155a0;border-radius:6px;padding:6px 9px;font-size:11px;cursor:pointer}
.inspector-heading{margin-bottom:12px}.inspector-heading>div{display:flex;gap:6px}.inspector-heading button{font-size:11px;padding:6px 8px}
.empty{padding:35px 10px;text-align:center;color:#888;font-size:13px}
.record{display:flex;width:100%;flex-direction:column;align-items:flex-start;gap:3px;text-align:left;padding:9px;border:1px solid #eee;background:#fff;border-radius:7px;margin-bottom:6px;cursor:pointer}
.record.selected{border-color:#7795e7;background:#f5f7ff}.record strong{font-size:12px}.record span,.record small{font-size:10px;color:#888}
.json-panel{margin-top:14px;border-top:1px solid #ddd;padding-top:12px}.json-heading strong{font-size:13px}.json-heading button{font-size:11px}
.json-panel pre{max-height:390px;overflow:auto;background:#f6f7f9;padding:12px;border-radius:8px;font-size:11px;white-space:pre-wrap;overflow-wrap:anywhere}
.json-panel>small{font-size:10px;color:#888}
footer{margin-top:18px;padding:13px;border:1px solid #e5e5e5;border-radius:9px;font-size:12px;color:#666;line-height:1.7}
@media(max-width:900px){.header{align-items:flex-start;flex-direction:column}.layout{grid-template-columns:1fr}.event-list{max-height:none}}
@media(max-width:560px){.event-workbench{padding:16px}.summary{grid-template-columns:repeat(2,1fr)}.event-list{grid-template-columns:1fr}.catalog-heading{align-items:flex-start;flex-direction:column}.catalog-heading input{max-width:none;width:100%;box-sizing:border-box}}
</style>
