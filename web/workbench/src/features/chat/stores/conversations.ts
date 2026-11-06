import type { ModelSelection } from '../../../shared/api/modelsApi'
import { defineStore } from 'pinia'
import { decodeReplyPreview, type ReplyPreview } from './replyPreviews'
import { decodeConversationDelta } from './conversationDeltas'
import { chatSnapshot, conversationPage, renameConversation, retryAnswer, sendMessage, stopAnswer, type TaskDirectiveView, type ChatExecution, type ChatTaskState, type ChatMessage, type ChatSnapshot, type ConversationEvent, type ConversationHeader, type MessageReceipt } from '../../../shared/api/chatApi'
import { ApiError, errorMessage } from '../../../shared/api/http'
import { deleteConversationApi } from '../../../shared/api/conversationsApi'
import { cancelTaskApi } from '../../../shared/api/tasksApi'
import { newIdempotencyKey } from '../../../shared/lib/idempotency'

let epoch = 0
let listRevision = 0
let stream: EventSource | null = null
let timer: ReturnType<typeof setTimeout> | null = null
let refreshTimer: ReturnType<typeof setTimeout> | null = null
let snapshotRequest = 0
let seenSequence = 0
let reconnectAttempt = 0
let refreshing = false
let refreshAgain = false
const controlKeys = new Map<string, string>()
const sequence = (cursor: string) => Number(cursor.split(':').at(-1) ?? 0)

interface State {
  directiveHistory: TaskDirectiveView[]
  directiveWatermarks: Record<string, number>
  previews: Record<string, ReplyPreview>
  retiredPreviewStreams: string[]
  items: ConversationHeader[]
  nextCursor: string | null
  listLoading: boolean
  listLoaded: boolean
  listError: string
  activeConversationId: string | null
  snapshot: ChatSnapshot | null
  messages: ChatMessage[]
  executionHistory: ChatExecution[]
  taskHistory: ChatTaskState[]
  nextBeforeSeq: number | null
  loading: boolean
  historyExpanded: boolean
  loadingHistory: boolean
  error: string
  actionError: string
  connection: 'connecting' | 'connected' | 'offline'
  submitting: boolean
  controlling: string | null
  draftRevision: number
  unavailable: boolean
}

/** 会话消息是显示权威，刷新、翻页和断线只读，不重发历史请求。 */
export const useConversationsStore = defineStore('conversations', {
  state: (): State => ({
    directiveHistory: [], directiveWatermarks: {},
    previews: {}, retiredPreviewStreams: [],
    items: [], nextCursor: null, listLoading: false, listLoaded: false, listError: '',
    activeConversationId: null, snapshot: null, messages: [], executionHistory: [], taskHistory: [], nextBeforeSeq: null,
    loading: false, historyExpanded: false, loadingHistory: false, error: '', actionError: '', connection: 'connecting',
    submitting: false, controlling: null, draftRevision: 0, unavailable: false,
  }),
  getters: {
    conversation: state => state.snapshot?.conversation ?? null,
    latestExecution: state => state.snapshot?.activeExecutions.at(-1) ?? null,
  },
  actions: {
    dispose() {
      epoch++; snapshotRequest++; seenSequence = 0
      this.previews = {}; this.retiredPreviewStreams = []
      stream?.close(); stream = null
      if (timer) clearTimeout(timer)
      if (refreshTimer) clearTimeout(refreshTimer)
      timer = null; refreshTimer = null; refreshing = false; refreshAgain = false
      reconnectAttempt = 0; controlKeys.clear()
    },
    resetAll() {
      this.dispose(); listRevision++; this.$reset()
    },
    startDraft() {
      this.dispose(); this.activeConversationId = null; this.snapshot = null; this.messages = []; this.executionHistory = []; this.taskHistory = []; this.directiveHistory = []; this.directiveWatermarks = {}
      this.nextBeforeSeq = null; this.historyExpanded = false; this.error = ''; this.actionError = ''; this.unavailable = false
      this.loading = false; this.submitting = false; this.controlling = null; this.draftRevision++
    },
    async refreshList() {
      const current = ++listRevision
      this.listLoading = true; this.listError = ''
      try {
        const page = await conversationPage()
        if (current !== listRevision) return
        this.items = page.items; this.nextCursor = page.nextCursor; this.listLoaded = true
      } catch (cause) { if (current === listRevision) this.listError = errorMessage(cause) }
      finally { if (current === listRevision) this.listLoading = false }
    },
    async loadMore() {
      if (this.listLoading) return
      if (!this.listLoaded) return this.refreshList()
      if (!this.nextCursor) return
      const current = listRevision; this.listLoading = true
      try {
        const page = await conversationPage(this.nextCursor)
        if (current !== listRevision) return
        const items = new Map(this.items.map(item => [item.conversationId, item]))
        page.items.forEach(item => items.set(item.conversationId, item))
        this.items = [...items.values()]; this.nextCursor = page.nextCursor
      } catch (cause) { if (current === listRevision) this.listError = errorMessage(cause) }
      finally { if (current === listRevision) this.listLoading = false }
    },
    async open(id: string) {
      this.dispose(); this.activeConversationId = id; this.snapshot = null; this.messages = []; this.executionHistory = []; this.taskHistory = []; this.directiveHistory = []; this.directiveWatermarks = {}
      this.nextBeforeSeq = null; this.historyExpanded = false; this.error = ''; this.actionError = ''; this.unavailable = false
      this.submitting = false; this.controlling = null; this.loadingHistory = false; this.loading = true
      this.connection = 'connecting'
      const current = epoch
      const ok = await this.refreshSnapshot()
      if (current !== epoch) return
      this.loading = false
      if (ok) this.connect()
      else if (!this.unavailable) this.scheduleReconnect()
    },
    mergeMessages(incoming: ChatMessage[]) {
      const merged = new Map(this.messages.map(message => [message.messageId, message]))
      for (const message of incoming) {
        const previous = merged.get(message.messageId)
        if (!previous || message.revision >= previous.revision) merged.set(message.messageId, message)
      }
      this.messages = [...merged.values()].sort((a, b) => a.sequence - b.sequence)
    },
    mergeDirectives(incoming: TaskDirectiveView[], watermark: number) {
      const merged = new Map(this.directiveHistory.map(item => [item.directiveId, item]))
      for (const item of incoming) {
        const previous = merged.get(item.directiveId)
        if ((this.directiveWatermarks[item.directiveId] ?? -1) > watermark) continue
        // 已决定的要求不能被旧历史或重复受理事件改回等待。
        if (previous && previous.status !== 'ACCEPTED' && item.status !== previous.status) continue
        merged.set(item.directiveId, item); this.directiveWatermarks[item.directiveId] = watermark
      }
      this.directiveHistory = [...merged.values()]
    },
    mergeHistoryStates(snapshot: ChatSnapshot) {
      this.mergeDirectives(snapshot.directives ?? [], sequence(snapshot.cursor))
      const executions = new Map(this.executionHistory.map(item => [item.executionId, item]))
      for (const item of [...snapshot.executions, ...snapshot.activeExecutions]) {
        const previous = executions.get(item.executionId)
        if (!previous || previous.version <= item.version) executions.set(item.executionId, item)
      }
      this.executionHistory = [...executions.values()]
      const tasks = new Map(this.taskHistory.map(item => [item.taskId, item]))
      for (const item of snapshot.taskStates) {
        const previous = tasks.get(item.taskId)
        if (!previous || previous.version <= item.version) tasks.set(item.taskId, item)
      }
      this.taskHistory = [...tasks.values()]
    },
    async refreshSnapshot(): Promise<boolean> {
      const id = this.activeConversationId
      if (!id) return false
      const current = epoch; const request = ++snapshotRequest
      try {
        const snapshot = await chatSnapshot(id)
        if (current !== epoch || request !== snapshotRequest || id !== this.activeConversationId) return false
        if (sequence(snapshot.cursor) < seenSequence) return this.snapshot !== null
        if (this.snapshot && snapshot.conversation.generation !== this.snapshot.conversation.generation) {
          this.messages = []; this.directiveHistory = []; this.directiveWatermarks = {}
        }
        this.snapshot = snapshot; this.mergeMessages(snapshot.messages); this.mergeHistoryStates(snapshot)
        if (!this.historyExpanded) this.nextBeforeSeq = snapshot.nextBeforeSeq
        seenSequence = Math.max(seenSequence, sequence(snapshot.cursor))
        this.error = ''; this.unavailable = false
        const index = this.items.findIndex(item => item.conversationId === id)
        if (index >= 0) this.items[index] = snapshot.conversation
        return true
      } catch (cause) {
        if (current !== epoch || id !== this.activeConversationId) return false
        this.error = errorMessage(cause)
        if (cause instanceof ApiError && [401, 403, 404].includes(cause.code)) {
          this.unavailable = true; this.snapshot = null; this.messages = []; this.executionHistory = []; this.taskHistory = []; this.directiveHistory = []; this.directiveWatermarks = {}; stream?.close()
        }
        return false
      }
    },
    queueRefresh() {
      if (refreshing) { refreshAgain = true; return }
      if (refreshTimer) return
      const current = epoch
      refreshTimer = setTimeout(async () => {
        refreshTimer = null; refreshing = true
        try { await this.refreshSnapshot() }
        finally {
          if (current === epoch) {
            refreshing = false
            if (refreshAgain) { refreshAgain = false; this.queueRefresh() }
          }
        }
      }, 80)
    },
    /** 完整消息只更新已知身份；缺字段或新消息仍读取权威快照。 */
    applyEventPayload(event: ConversationEvent): boolean {
      const delta = decodeConversationDelta(event)
      if (delta) {
        if (delta.type === 'directive.status_changed') {
          this.mergeDirectives([delta.directive], event.sequence)
          return true
        }
        if (delta.type === 'plan.published' || delta.type === 'job.reused') {
          // 连续事件水位会触发对应步骤读取，不为计划通知重读整个会话。
          return true
        }
        if (delta.type === 'conversation.title_updated') {
          if (this.snapshot && delta.titleVersion >= this.snapshot.conversation.titleVersion) {
            this.snapshot.conversation.title = delta.title
            this.snapshot.conversation.titleVersion = delta.titleVersion
            const item = this.items.find(item => item.conversationId === this.activeConversationId)
            if (item && delta.titleVersion >= item.titleVersion) {
              item.title = delta.title; item.titleVersion = delta.titleVersion
            }
          }
          return true
        }
        if ('message' in delta) {
          if (delta.message.role === 'ASSISTANT' && delta.message.executionId) delete this.previews[delta.message.executionId]
          this.mergeMessages([delta.message])
          if (this.snapshot) {
            const messages = new Map(this.snapshot.messages.map(message => [message.messageId, message]))
            const previous = messages.get(delta.message.messageId)
            if (!previous || previous.revision <= delta.message.revision) messages.set(delta.message.messageId, delta.message)
            this.snapshot.messages = [...messages.values()].sort((a, b) => a.sequence - b.sequence)
          }
        }
        if ('execution' in delta) {
          const incoming = delta.execution
          const previous = this.executionHistory.find(item => item.executionId === incoming.executionId)
          if (!previous || previous.version <= incoming.version) {
            if (['SUCCEEDED', 'PARTIAL', 'FAILED', 'CANCELLED', 'RECOVERING', 'CANCELLING'].includes(incoming.status)) delete this.previews[incoming.executionId]

            this.executionHistory = [...this.executionHistory.filter(item => item.executionId !== incoming.executionId), incoming]
            if (this.snapshot) {
              this.snapshot.executions = [...this.snapshot.executions.filter(item => item.executionId !== incoming.executionId), incoming]
              this.snapshot.activeExecutions = this.snapshot.activeExecutions.filter(item => item.executionId !== incoming.executionId)
              if (!['SUCCEEDED', 'PARTIAL', 'FAILED', 'CANCELLED'].includes(incoming.status)) this.snapshot.activeExecutions.push(incoming)
            }
          }
        }
        return true
      }
      // 只兼容可安全替换的旧完成载荷；其余旧协议走明确的快照恢复。
      const payload = event.payload
      if (event.type !== 'message.completed' || payload.schemaVersion !== undefined) return false
      if (typeof payload.messageId !== 'string' || typeof payload.text !== 'string'
          || !Number.isSafeInteger(payload.revision)) return false
      const index = this.messages.findIndex(item => item.messageId === payload.messageId)
      if (index < 0) return false
      const previous = this.messages[index]!
      const revision = payload.revision as number
      if (revision < previous.revision) return true
      const updated = { ...previous, text: payload.text, revision, completed: true }
      this.messages[index] = updated
      if (this.snapshot) this.snapshot.messages = this.snapshot.messages.map(item => item.messageId === updated.messageId ? updated : item)
      return true
    },
    applyPreview(value: unknown) {
      const incoming = decodeReplyPreview(value)
      if (!incoming || this.retiredPreviewStreams.includes(incoming.streamId)) return
      const execution = this.snapshot?.activeExecutions.find(item => item.executionId === incoming.executionId
        && item.sourceMessageId === incoming.sourceMessageId)
      if (!execution || !['QUEUED', 'RUNNING', 'RECOVERING'].includes(execution.status)
          || this.messages.some(message => message.executionId === incoming.executionId && message.role === 'ASSISTANT' && message.completed)) return
      const previous = this.previews[incoming.executionId]
      if (incoming.phase === 'ABORTED') {
        this.retiredPreviewStreams = [...this.retiredPreviewStreams.slice(-15), incoming.streamId]
        if (previous?.streamId === incoming.streamId) delete this.previews[incoming.executionId]
        return
      }
      if (previous?.streamId === incoming.streamId && incoming.revision <= previous.revision) return
      if (previous && previous.streamId !== incoming.streamId) {
        this.retiredPreviewStreams = [...this.retiredPreviewStreams.slice(-15), previous.streamId]
      }
      this.previews[incoming.executionId] = incoming
    },
    connect() {
      stream?.close()
      this.previews = {}; this.retiredPreviewStreams = []
      const snapshot = this.snapshot; const id = this.activeConversationId; const current = epoch
      if (!snapshot || !id || this.unavailable) return
      const source = new EventSource(`/api/v1/conversations/${encodeURIComponent(id)}/events?after=${encodeURIComponent(snapshot.cursor)}`)
      stream = source
      source.onopen = () => { if (current === epoch && stream === source) { this.connection = 'connected'; reconnectAttempt = 0 } }
      source.onmessage = message => {
        if (current !== epoch || id !== this.activeConversationId || stream !== source) return
        try {
          const event = JSON.parse(message.data) as ConversationEvent
          if (event.conversationId !== id) return
          if (event.generation !== this.snapshot?.conversation.generation) {
            source.close(); this.connection = 'connecting'; this.scheduleReconnect(); return
          }
          if (!Number.isSafeInteger(event.sequence) || event.sequence < 0) throw new Error('无效事件序号')
          if (event.sequence <= seenSequence) return
          if (event.sequence !== seenSequence + 1) {
            source.close(); this.connection = 'connecting'; this.scheduleReconnect(); return
          }
          if (!this.applyEventPayload(event)) {
            source.close(); this.connection = 'connecting'; this.scheduleReconnect(); return
          }
          seenSequence = event.sequence
          if (this.snapshot) this.snapshot.cursor = `${id}:${event.generation}:${event.sequence}`
        } catch { source.close(); this.scheduleReconnect() }
      }
      source.addEventListener('output.preview', message => {
        if (current !== epoch || id !== this.activeConversationId || stream !== source) return
        try { this.applyPreview(JSON.parse((message as MessageEvent).data)) } catch { /* 临时预览可丢弃，不改变持久水位 */ }
      })
      source.onerror = () => {
        if (current !== epoch || stream !== source) return
        source.close(); this.previews = {}; this.connection = 'offline'; this.scheduleReconnect()
      }
    },
    scheduleReconnect() {
      this.previews = {}
      if (timer || this.unavailable) return
      const current = epoch
      timer = setTimeout(async () => {
        timer = null
        if (current !== epoch) return
        if (await this.refreshSnapshot()) this.connect()
        else if (current === epoch) this.scheduleReconnect()
      }, Math.min(10000, 500 * 2 ** Math.min(reconnectAttempt++, 5)))
    },
    async loadOlder() {
      if (!this.activeConversationId || !this.nextBeforeSeq || this.loadingHistory) return
      const current = epoch; const id = this.activeConversationId
      this.loadingHistory = true
      try {
        const page = await chatSnapshot(id, this.nextBeforeSeq)
        if (current !== epoch || page.conversation.generation !== this.snapshot?.conversation.generation) return
        this.mergeMessages(page.messages); this.mergeHistoryStates(page); this.nextBeforeSeq = page.nextBeforeSeq; this.historyExpanded = true
      } catch (cause) { if (current === epoch) this.actionError = errorMessage(cause) }
      finally { if (current === epoch) this.loadingHistory = false }
    },
    async send(text: string, artifactIds: string[], key: string, selection?: ModelSelection): Promise<MessageReceipt> {
      if (this.submitting) throw new Error('正在确认上一条消息')
      const current = epoch; const id = this.activeConversationId
      this.submitting = true
      try {
        const receipt = await sendMessage(id, text, artifactIds, key, selection)
        if (current === epoch && !id) void this.refreshList()
        return receipt
      } finally { if (current === epoch) this.submitting = false }
    },
    keyFor(action: string, id: string, version: number) {
      const identity = `${action}:${id}:${version}`
      if (!controlKeys.has(identity)) controlKeys.set(identity, newIdempotencyKey())
      return controlKeys.get(identity)!
    },
    async stop(kind: 'answer' | 'task', id: string, version: number) {
      if (this.controlling) return
      this.controlling = id; this.actionError = ''; const current = epoch
      try {
        const key = this.keyFor(kind, id, version)
        if (kind === 'answer') await stopAnswer(id, version, key)
        else await cancelTaskApi(id, version, key)
        if (current === epoch) await this.refreshSnapshot()
      } catch (cause) {
        if (current === epoch) { this.actionError = errorMessage(cause); await this.refreshSnapshot() }
      } finally { if (current === epoch) this.controlling = null }
    },
    async retry(id: string, version: number) {
      if (this.controlling) return
      this.controlling = id; this.actionError = ''; const current = epoch
      try {
        await retryAnswer(id, version, this.keyFor('retry', id, version))
        if (current === epoch) await this.refreshSnapshot()
      } catch (cause) {
        if (current === epoch) { this.actionError = errorMessage(cause); await this.refreshSnapshot() }
      } finally { if (current === epoch) this.controlling = null }
    },
    async rename(title: string) {
      const conversation = this.conversation
      if (!conversation) return
      try {
        await renameConversation(conversation.conversationId, title, conversation.titleVersion)
        await this.refreshSnapshot(); await this.refreshList()
      } catch (cause) { this.actionError = errorMessage(cause); await this.refreshSnapshot(); throw cause }
    },
    async remove(): Promise<boolean> {
      const conversation = this.conversation
      if (!conversation) return false
      try {
        await deleteConversationApi(conversation.conversationId, { idempotencyKey: this.keyFor('delete', conversation.conversationId, conversation.version), ifMatch: String(conversation.version) })
        this.startDraft(); await this.refreshList(); return true
      } catch (cause) { this.actionError = errorMessage(cause); await this.refreshSnapshot(); return false }
    },
  },
})
