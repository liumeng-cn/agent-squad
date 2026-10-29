import { defineStore } from 'pinia'
import { chatSnapshot, conversationPage, renameConversation, retryAnswer, sendMessage, stopAnswer, type ChatExecution, type ChatTaskState, type ChatMessage, type ChatSnapshot, type ConversationEvent, type ConversationHeader, type MessageReceipt } from '../../../shared/api/chatApi'
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
      this.dispose(); this.activeConversationId = null; this.snapshot = null; this.messages = []; this.executionHistory = []; this.taskHistory = []
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
      this.dispose(); this.activeConversationId = id; this.snapshot = null; this.messages = []; this.executionHistory = []; this.taskHistory = []
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
    mergeHistoryStates(snapshot: ChatSnapshot) {
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
        if (sequence(snapshot.cursor) < seenSequence) { this.queueRefresh(); return false }
        if (this.snapshot && snapshot.conversation.generation !== this.snapshot.conversation.generation) this.messages = []
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
        if (cause instanceof ApiError && [401, 403, 404].includes(cause.status)) {
          this.unavailable = true; this.snapshot = null; this.messages = []; this.executionHistory = []; this.taskHistory = []; stream?.close()
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
    connect() {
      stream?.close()
      const snapshot = this.snapshot; const id = this.activeConversationId; const current = epoch
      if (!snapshot || !id || this.unavailable) return
      const source = new EventSource(`/api/v1/conversations/${encodeURIComponent(id)}/events?after=${encodeURIComponent(snapshot.cursor)}`)
      stream = source
      source.onopen = () => { if (current === epoch) { this.connection = 'connected'; reconnectAttempt = 0 } }
      source.onmessage = message => {
        if (current !== epoch || id !== this.activeConversationId) return
        try {
          const event = JSON.parse(message.data) as ConversationEvent
          if (event.conversationId !== id || event.generation !== this.snapshot?.conversation.generation) return
          if (event.sequence <= seenSequence) return
          if (event.sequence !== seenSequence + 1) {
            source.close(); this.connection = 'connecting'; this.scheduleReconnect(); return
          }
          seenSequence = event.sequence
          this.queueRefresh()
          if (['conversation.title_updated', 'message.accepted', 'message.completed'].includes(event.type)) void this.refreshList()
        } catch { source.close(); this.scheduleReconnect() }
      }
      source.onerror = () => {
        if (current !== epoch) return
        source.close(); this.connection = 'offline'; this.scheduleReconnect()
      }
    },
    scheduleReconnect() {
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
    async send(text: string, artifactIds: string[], key: string): Promise<MessageReceipt> {
      if (this.submitting) throw new Error('正在确认上一条消息')
      const current = epoch; const id = this.activeConversationId
      this.submitting = true
      try {
        const receipt = await sendMessage(id, text, artifactIds, key)
        if (current === epoch) { if (id) await this.refreshSnapshot(); void this.refreshList() }
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
