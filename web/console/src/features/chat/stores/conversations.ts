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
import type { ConversationResponse, TaskResponse } from '../../../shared/api/types'

interface ConversationsState {
  items: ConversationResponse[]
  nextCursor: string | null
  loading: boolean
  listError: string | null
  activeConversationId: string | null
  conversation: ConversationResponse | null
  conversationError: string | null
  taskHistory: TaskResponse[]
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
      try {
        this.conversation = await getConversationApi(conversationId)
        const page = await listConversationTasksApi(conversationId)
        this.taskHistory = page.items
      } catch (error) {
        this.conversationError = errorMessage(error)
      }
    },

    /** 终态推进/删除后重新读取当前会话，保持 activeTaskId 与版本准确。 */
    async refreshActive(): Promise<void> {
      if (!this.activeConversationId) {
        return
      }
      try {
        this.conversation = await getConversationApi(this.activeConversationId)
        const page = await listConversationTasksApi(this.activeConversationId)
        this.taskHistory = page.items
      } catch {
        // 会话可能刚被删除，保持现有视图
      }
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
      }
      return true
    },
  },
})
