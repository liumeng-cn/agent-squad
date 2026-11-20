import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { interruptChat, readSession, resumeChat, submitChat, subscribeSession } from '../api/chatApi'
import { ApiError, errorMessage } from '../../../shared/http/http'
import type { ChatMessage, ChatSession, ChatStatus, SubmitChatInput } from '../model/chatTypes'
import { useAuthStore } from '../../auth/stores/auth'
import { readConv } from '../api/convApi'
import { useConvsStore } from '../stores/convs'
import { inputStatusDescriptions } from '../model/chatTypes'

const RECONNECT_DELAY_MS = 1500
interface ChatOptions { fresh?: boolean; sessionId?: string; onCreated?: (id: string) => void }
const controllers = new Map<string, ReturnType<typeof createController>>()
export function resetChatSessions() {
  for (const controller of controllers.values()) controller.detach()
  controllers.clear()
}

export function useChat(userId = 'anonymous', selectedId?: string, options: ChatOptions = {}) {
  const key = `${userId}:${selectedId}`
  let controller = selectedId ? controllers.get(key) : undefined
  if (!controller) {
    controller = createController(userId, selectedId, options)
    if (selectedId) controllers.set(key, controller)
  }
  onMounted(controller.attach)
  onBeforeUnmount(controller.detach)
  return controller
}

/** 会话身份跨刷新保存；后台执行不依赖页面订阅，连接失败不伪装为任务失败。 */
function createController(userId: string, selectedId?: string, options: ChatOptions = {}) {
  const SESSION_KEY = userId === 'anonymous' ? 'squad-v2.convId' : `squad-v2.${userId}.convId`
  let convId = selectedId || sessionStorage.getItem(SESSION_KEY) || crypto.randomUUID()
  sessionStorage.setItem(SESSION_KEY, convId)
  const draftKey = `squad.chat.${userId}.${convId}.draft`
  const pendingKey = `squad.chat.${userId}.${convId}.pending`
  const draft = ref(sessionStorage.getItem(draftKey) || '')
  const scrollTop = ref(0)
  let sessionId = options.sessionId ?? (selectedId ? undefined : convId)
  const registered = ref(Boolean(sessionId))
  const state = ref<ChatSession>()
  const messages = ref<ChatMessage[]>([])
  const error = ref('')
  const connected = ref(false)
  const stopping = ref(false)
  const sending = ref(false)
  const unresolvedInput = ref(false)
  const busy = computed(() => state.value?.status === 'RUNNING' || state.value?.status === 'QUEUED')
  const canClear = computed(() => !busy.value && !stopping.value && !sending.value && !unresolvedInput.value)
  const replacedTurnId = ref<string>()
  const canResume = computed(() => !sending.value && state.value?.turnId !== replacedTurnId.value
    && (state.value?.status === 'INTERRUPTED' || state.value?.status === 'FAILED'))
  const waiting = computed(() => state.value?.status === 'WAITING')
  const inputs = computed(() => (state.value?.inputs ?? []).filter(item => item.status !== 'APPLIED' && !state.value?.messages.some(message => item.messageIds.includes(message.id)))
    .map(item => ({ ...item, label: inputStatusDescriptions[item.status] })))
  let controller: AbortController | undefined
  let reconnectTimer: ReturnType<typeof setTimeout> | undefined
  let disposed = false
  let pending: SubmitChatInput | undefined
  try {
    pending = JSON.parse(sessionStorage.getItem(pendingKey) || 'null') ?? undefined
    if (pending) unresolvedInput.value = true
  } catch { sessionStorage.removeItem(pendingKey) }
  function saveDraft(value: string) { draft.value = value; sessionStorage.setItem(draftKey, value) }
  function savePending(value?: SubmitChatInput) {
    pending = value
    if (value) sessionStorage.setItem(pendingKey, JSON.stringify(value))
    else sessionStorage.removeItem(pendingKey)
  }

  function apply(snapshot: ChatSession) {
    if (snapshot.sessionId !== sessionId) return
    const current = state.value
    if (current && (snapshot.cursor < current.cursor || snapshot.inputCursor < current.inputCursor)) return
    state.value = snapshot
    if (pending && snapshot.acceptedInputIds?.includes(pending.interactionId)) {
      if (draft.value.trim() === pending.message) saveDraft('')
      savePending(); unresolvedInput.value = false; error.value = ''
    }
    const existingMessages = new Map(messages.value.map(message => [message.id, message]))
    messages.value = snapshot.messages.map(message => {
      const next: ChatMessage = {
        inputStatus: snapshot.inputs.find(item => item.messageIds.includes(message.id))?.status,
        id: message.id, turnId: message.turnId, role: message.role === 'USER' ? 'user' : 'assistant', content: message.content,
        status: (message.status === 'QUEUED' || message.status === 'IDLE' ? 'RUNNING' : message.status) as ChatStatus,
        startedAt: message.startedAt, finishedAt: message.finishedAt ?? undefined, thinkingBlocks: message.thinkingBlocks,
        activities: message.activities.map(activity => ({ ...activity, kind: 'TOOL', finishedAt: activity.finishedAt ?? undefined,
          status: activity.status === 'QUEUED' || activity.status === 'IDLE' ? 'RUNNING' : activity.status })),
      }
      const previous = existingMessages.get(next.id)
      if (!previous) return next
      const activities = new Map(previous.activities.map(activity => [activity.id, activity]))
      next.activities = next.activities.map(activity => {
        const existing = activities.get(activity.id)
        return existing ? Object.assign(existing, activity) : activity
      })
      const thoughts = new Map(previous.thinkingBlocks?.map(block => [block.modelCallId, block]))
      next.thinkingBlocks = next.thinkingBlocks?.map(block => {
        const existing = thoughts.get(block.modelCallId)
        return existing ? Object.assign(existing, block) : block
      })
      return Object.assign(previous, next)
    })
    if (snapshot.status !== 'RUNNING') stopping.value = false
  }

  async function connect() {
    if (disposed || !sessionId) return
    const current = new AbortController()
    controller = current
    let retry = true
    try {
      await subscribeSession(sessionId, current.signal, snapshot => {
        connected.value = true
        apply(snapshot)
      })
    } catch (cause) {
      if (cause instanceof ApiError && cause.code >= 400 && cause.code < 500) {
        retry = false
        error.value = errorMessage(cause)
      }
      // 观察失败不改变原生执行状态，不重发消息。
    } finally {
      if (controller === current) {
        connected.value = false
        if (!disposed && retry) reconnectTimer = setTimeout(connect, RECONNECT_DELAY_MS)
      }
    }
  }

  async function send(input: string): Promise<boolean> {
    const message = input.trim()
    if (!message || sending.value || unresolvedInput.value || stopping.value) return false
    error.value = ''
    sending.value = true
    const previousTurnId = state.value?.turnId
    let submitted = false
    try {
      const request = pending?.message === message ? pending : {
        convId: registered.value ? convId : undefined,
        sessionId, interactionId: crypto.randomUUID(),
        observedTurnId: busy.value ? state.value?.turnId : undefined, message,
      }
      savePending(request)
      submitted = true
      const receipt = await submitChat(request)
      if (userId !== 'anonymous' && useAuthStore().user?.userId !== userId) return false
      if (!registered.value) {
        convId = receipt.convId
        sessionId = receipt.sessionId
        registered.value = true
        controllers.delete(`${userId}:${selectedId}`)
        controllers.set(`${userId}:${convId}`, api)
        options.onCreated?.(convId)
        if (!disposed) void connect()
      }
      if (receipt.convId) {
        const summary = await readConv(receipt.convId).catch(() => undefined)
        if (summary && !disposed) useConvsStore().upsert(summary)
      }
      if (receipt?.disposition === 'NEW_TURN') replacedTurnId.value = previousTurnId
      savePending()
      unresolvedInput.value = false
      if (draft.value.trim() === message) saveDraft('')
      // 发送后立即读一次快照；随后由共享 SSE 接管，快照失败不否认已经接收的输入。
      try { if (sessionId) apply(await readSession(sessionId)) } catch { /* 已接收的输入由后续订阅核对，不重新提交。 */ }
      return true
    } catch (cause) {
      error.value = errorMessage(cause)
      if (submitted && (!(cause instanceof ApiError) || cause.status >= 500 || cause.code >= 500)) {
        unresolvedInput.value = true
        error.value = '接收结果尚未确认，草稿已保留；请核对会话记录，不要重复发送。'
      }
      if (!unresolvedInput.value) savePending()
      return false
    } finally {
      sending.value = false
    }
  }

  async function stop() {
    const runId = state.value?.runId
    if (!sessionId || !runId || stopping.value) return
    stopping.value = true
    error.value = ''
    try {
      await interruptChat(sessionId, runId)
      apply(await readSession(sessionId))
    } catch (cause) {
      stopping.value = false
      error.value = errorMessage(cause)
    }
  }

  async function resume() {
    const turnId = state.value?.turnId
    if (!sessionId || !turnId || sending.value) return
    sending.value = true
    try {
      await resumeChat(sessionId, turnId)
      apply(await readSession(sessionId))
    } catch (cause) { error.value = errorMessage(cause) }
    finally { sending.value = false }
  }

  function clear() {
    if (!canClear.value) return
    controller?.abort()
    if (reconnectTimer) clearTimeout(reconnectTimer)
    convId = crypto.randomUUID()
    sessionId = selectedId ? undefined : convId
    registered.value = Boolean(sessionId)
    sessionStorage.setItem(SESSION_KEY, convId)
    state.value = undefined
    messages.value = []
    pending = undefined
    error.value = ''
    connected.value = false
    void connect()
  }

  function attach() { disposed = false; void connect() }
  function detach() {
    disposed = true
    if (reconnectTimer) clearTimeout(reconnectTimer)
    controller?.abort()
  }
  const api = { attach, detach, apply, state, draft, saveDraft, scrollTop, registered, messages, busy, inputs, error, connected, stopping, unresolvedInput, canResume, waiting, canClear, clear, send, stop, resume }
  return api
}
