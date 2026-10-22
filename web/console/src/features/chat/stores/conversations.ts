import { defineStore } from 'pinia'
import {
  createConversationApi,
  deleteConversationApi,
  getConversationApi,
  listConversationTasksApi,
  listConversationsApi,
} from '../../../shared/api/conversationsApi'
import { errorMessage } from '../../../shared/api/http'
import { newIdempotencyKey } from '../../../shared/lib/idempotency'
import { getTaskSnapshotApi } from '../../../shared/api/tasksApi'
import type { ConversationResponse, TaskResponse, TaskSnapshot } from '../../../shared/api/types'

interface ConversationsState {
  items: ConversationResponse[]
  nextCursor: string | null
  loading: boolean
  listError: string | null
  activeConversationId: string | null
  conversation: ConversationResponse | null
  conversationError: string | null
  taskHistory: TaskResponse[]
  historySnapshots: TaskSnapshot[]
}

export const useConversationsStore = defineStore('conversations', {
  state: (): ConversationsState => ({
    items: [],
    nextCursor: null,
    loading: false,
    listError: null,
    activeConversationId: null,
    conversation: null,
    conversationError: null,
    taskHistory: [],
    historySnapshots: [],
  }),

  actions: {
    /** 拉取会话首页或下一页；服务端只返回 ACTIVE 会话。 */
    async loadMore(): Promise<void> {
      if (this.loading) {
        return
      }
      this.loading = true
      this.listError = null
      try {
        const page = await listConversationsApi(this.nextCursor ?? undefined)
        this.items = this.nextCursor ? [...this.items, ...page.items] : page.items
        this.nextCursor = page.nextCursor
      } catch (error) {
        this.listError = errorMessage(error)
      } finally {
        this.loading = false
      }
    },

    /** 打开指定会话并加载其目标历史；恢复场景下由会话的 activeTaskId 决定订阅哪个目标。 */
    async open(conversationId: string): Promise<void> {
      if (this.activeConversationId === conversationId && this.conversation !== null) {
        return
      }
      this.activeConversationId = conversationId
      this.conversation = null
      this.conversationError = null
      this.taskHistory = []
      this.historySnapshots = []
      try {
        const conversation = await getConversationApi(conversationId)
        const page = await listConversationTasksApi(conversationId)
        if (this.activeConversationId !== conversationId) {
          return
        }
        this.conversation = conversation
        this.taskHistory = page.items
        await this.loadMissingSnapshots(conversationId, page.items)
      } catch (error) {
        if (this.activeConversationId === conversationId) {
          this.conversationError = errorMessage(error)
        }
      }
    },

    /** 终态推进/删除后重新读取当前会话，保持 activeTaskId 与版本准确。 */
    async refreshActive(): Promise<void> {
      if (!this.activeConversationId) {
        return
      }
      const conversationId = this.activeConversationId
      try {
        const conversation = await getConversationApi(conversationId)
        const page = await listConversationTasksApi(conversationId)
        if (this.activeConversationId !== conversationId) {
          return
        }
        this.conversation = conversation
        this.taskHistory = page.items
        await this.loadMissingSnapshots(conversationId, page.items)
      } catch {
        // 会话可能刚被删除，保持现有视图
      }
    },

    /** 只补读缺失的目标快照，避免每次状态刷新都重复请求整段历史。 */
    async loadMissingSnapshots(conversationId: string, tasks: TaskResponse[]): Promise<void> {
      const knownIds = new Set(this.historySnapshots.map((snapshot) => snapshot.task.taskId))
      const missing = tasks.filter((task) => !knownIds.has(task.taskId))
      if (missing.length === 0) {
        return
      }
      const loaded = await Promise.allSettled(
        missing.map((task) => getTaskSnapshotApi(task.taskId)),
      )
      if (this.activeConversationId !== conversationId) {
        return
      }
      this.historySnapshots = [
        ...this.historySnapshots,
        ...loaded.flatMap((result) => result.status === 'fulfilled' ? [result.value] : []),
      ]
    },

    async createAndSelect(): Promise<string | null> {
      try {
        const conversation = await createConversationApi('新的对话')
        this.items.unshift(conversation)
        return conversation.conversationId
      } catch (error) {
        this.listError = errorMessage(error)
        return null
      }
    },

    async remove(conversation: ConversationResponse): Promise<boolean> {
      try {
        await deleteConversationApi(conversation.conversationId, {
          idempotencyKey: newIdempotencyKey(),
          ifMatch: String(conversation.version),
        })
      } catch (error) {
        this.listError = errorMessage(error)
        return false
      }
      this.items = this.items.filter(
        (item) => item.conversationId !== conversation.conversationId,
      )
      if (this.activeConversationId === conversation.conversationId) {
        this.activeConversationId = null
        this.conversation = null
        this.taskHistory = []
        this.historySnapshots = []
      }
      return true
    },
  },
})
