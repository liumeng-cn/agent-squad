<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import StatusBadge from '../../../shared/ui/StatusBadge.vue'
import ConversationSidebar from '../components/ConversationSidebar.vue'
import TaskComposer from '../components/TaskComposer.vue'
import TaskThread from '../components/TaskThread.vue'
import { useConversationsStore } from '../stores/conversations'
import { useRuntimeStore } from '../stores/runtime'
import { isTerminal } from '../../../shared/lib/taskStatus'

const route = useRoute()
const router = useRouter()
const conversations = useConversationsStore()
const runtime = useRuntimeStore()
const sidebar = ref<InstanceType<typeof ConversationSidebar> | null>(null)

const conversationId = computed(() => {
  const id = route.params.conversationId
  return typeof id === 'string' && id ? id : null
})

const hasActiveGoal = computed(() => {
  const taskId = conversations.conversation?.activeTaskId
  return taskId !== null && taskId !== undefined && taskId !== ''
})

const composerBlocked = computed(() => {
  if (!conversationId.value) {
    return true
  }
  if (hasActiveGoal.value && !runtime.terminal) {
    return true
  }
  return false
})

const blockHint = computed(() => {
  if (!conversationId.value) {
    return '请先新建或选择一个对话'
  }
  return '当前有未完成目标：请先停止它，或新建对话后再提新目标'
})

watch(
  conversationId,
  async (id) => {
    runtime.dispose()
    if (!id) {
      conversations.activeConversationId = null
      conversations.conversation = null
      conversations.taskHistory = []
      return
    }
    await conversations.open(id)
    const activeTaskId = conversations.conversation?.activeTaskId
    if (activeTaskId) {
      await runtime.observe(activeTaskId)
    }
  },
  { immediate: true },
)

// 目标进入终态或新目标受理后，同步会话的 activeTaskId，决定输入区是否解锁。
watch(
  () => [runtime.taskId, runtime.status],
  async () => {
    if (!conversationId.value) {
      return
    }
    const status = runtime.status
    if (runtime.taskId && (!status || isTerminal(status))) {
      await conversations.refreshActive()
    }
  },
)

async function removeConversation(): Promise<void> {
  const conversation = conversations.conversation
  if (!conversation) {
    return
  }
  if (!window.confirm(`确定删除对话「${conversation.title}」吗？删除后不可恢复。`)) {
    return
  }
  const removed = await conversations.remove(conversation)
  if (removed) {
    runtime.dispose()
    await router.replace({ name: 'workspace' })
  }
}

onBeforeUnmount(() => {
  runtime.dispose()
})
</script>

<template>
  <div class="workspace">
    <ConversationSidebar ref="sidebar" />
    <section class="workspace-main">
      <header class="workspace-header">
        <button class="btn btn-sm" @click="sidebar?.openSidebar()">会话</button>
        <h1 class="workspace-title">{{ conversations.conversation?.title ?? '工作台' }}</h1>
        <StatusBadge v-if="runtime.status" :status="runtime.status" />
        <span class="spacer"></span>
        <router-link
          v-if="runtime.taskId"
          class="btn btn-sm"
          :to="{ name: 'task', params: { taskId: runtime.taskId } }"
        >
          查看详情
        </router-link>
        <button
          v-if="conversations.conversation"
          class="btn btn-danger btn-sm"
          :disabled="conversations.conversation.activeTaskId !== null && !runtime.terminal"
          @click="removeConversation"
        >
          删除对话
        </button>
      </header>
      <p v-if="conversations.conversationError" class="form-error" style="margin: 12px 20px 0">
        {{ conversations.conversationError }}
      </p>
      <TaskThread>
        <div class="thread-empty">
          <p>在下方输入你的目标，发送后平台会受理并执行。</p>
          <p>数据仅本人可见；自动使用已授权的技能。</p>
        </div>
      </TaskThread>
      <TaskComposer
        v-if="conversationId"
        :conversation-id="conversationId"
        :blocked="composerBlocked"
        :block-hint="blockHint"
      />
      <div v-else class="composer-area">
        <p class="composer-hint">请从左侧选择或新建一个对话</p>
      </div>
    </section>
  </div>
</template>
