import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { interruptChat, readSession, resumeChat, submitChat, subscribeSession } from '../api/chatApi'
import { ApiError, errorMessage } from '../../../shared/http/http'
import type { ChatMessage, ChatSession, ChatStatus, SubmitChatInput } from '../model/chatTypes'
import { useAuthStore } from '../../auth/stores/auth'
import { createSession } from '../api/sessionApi'
import { useSessionsStore } from '../stores/sessions'
import { inputStatusDescriptions } from '../model/chatTypes'

const RECONNECT_DELAY_MS = 1500
interface ChatOptions { fresh?: boolean; onCreated?: (id: string) => void }
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
  const SESSION_KEY = userId === 'anonymous' ? 'squad-v2.sessionId' : `squad-v2.${userId}.sessionId`
  let sessionId = selectedId || sessionStorage.getItem(SESSION_KEY) || crypto.randomUUID()
  sessionStorage.setItem(SESSION_KEY, sessionId)
  const draftKey = `squad.chat.${userId}.${sessionId}.draft`
  const pendingKey = `squad.chat.${userId}.${sessionId}.pending`
  const draft = ref(sessionStorage.getItem(draftKey) || '')
  const scrollTop = ref(0)
  const registered = ref(!options.fresh)
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
    if (disposed || !registered.value) return
    const current = new AbortController()
    controller = current
    try {
      await subscribeSession(sessionId, current.signal, snapshot => {
        connected.value = true
        apply(snapshot)
      })
    } catch {
      // 观察失败不改变原生执行状态，不重发消息。
    } finally {
      if (controller === current) {
        connected.value = false
        if (!disposed) reconnectTimer = setTimeout(connect, RECONNECT_DELAY_MS)
      }
    }
  }

  async function send(input: string): Promise<boolean> {
    const message = input.trim()
    if (!message || sending.value || unresolvedInput.value || stopping.value) return false
    error.value = ''
    sending.value = true
    const request = pending?.message === message ? pending : {
      sessionId, interactionId: crypto.randomUUID(), observedTurnId: busy.value ? state.value?.turnId : undefined, message,
    }
    pending = request
    const previousTurnId = state.value?.turnId
    let submitted = false
    try {
      if (selectedId && !registered.value) {
        const summary = await createSession(sessionId)
        if (useAuthStore().user?.userId !== userId) throw new Error('登录用户已变化')
        registered.value = true
        useSessionsStore().upsert(summary)
        options.onCreated?.(sessionId)
        if (!disposed) void connect()
      }
      savePending(request)
      submitted = true
      const receipt = await submitChat(request)
      if (receipt?.disposition === 'NEW_TURN') replacedTurnId.value = previousTurnId
      savePending()
      unresolvedInput.value = false
      if (draft.value.trim() === message) saveDraft('')
      // 发送后立即读一次快照；随后由共享 SSE 接管，快照失败不否认已经接收的输入。
      try { apply(await readSession(sessionId)) } catch { /* 已接收的输入由后续订阅核对，不重新提交。 */ }
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
    if (!runId || stopping.value) return
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
    if (!turnId || sending.value) return
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
    sessionId = crypto.randomUUID()
    sessionStorage.setItem(SESSION_KEY, sessionId)
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
  return { attach, detach, state, draft, saveDraft, scrollTop, registered, messages, busy, inputs, error, connected, stopping, unresolvedInput, canResume, waiting, canClear, clear, send, stop, resume }
}
