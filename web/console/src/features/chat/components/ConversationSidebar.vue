<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../../auth/stores/auth'
import { useConversationsStore } from '../stores/conversations'

const router = useRouter()
const auth = useAuthStore()
const conversations = useConversationsStore()
const creating = ref(false)
const sidebarHidden = ref(false)

onMounted(() => {
  if (conversations.items.length === 0) {
    void conversations.loadMore()
  }
})

async function createConversation(): Promise<void> {
  if (creating.value) {
    return
  }
  creating.value = true
  const id = await conversations.createAndSelect()
  creating.value = false
  if (id) {
    sidebarHidden.value = true
    await router.push({ name: 'workspace', params: { conversationId: id } })
  }
}

function select(id: string): void {
  sidebarHidden.value = true
  void router.push({ name: 'workspace', params: { conversationId: id } })
}

async function logout(): Promise<void> {
  await auth.logout()
  await router.push({ name: 'login' })
}

defineExpose({ openSidebar: () => (sidebarHidden.value = false) })
</script>

<template>
  <aside class="sidebar" :class="{ 'hidden-narrow': sidebarHidden }">
    <div class="sidebar-header">
      <p class="sidebar-brand">小队</p>
      <button class="btn btn-primary btn-block" :disabled="creating" @click="createConversation">
        {{ creating ? '创建中…' : '新建对话' }}
      </button>
    </div>
    <div class="conversation-list">
      <p v-if="conversations.listError" class="form-error">{{ conversations.listError }}</p>
      <p v-if="!conversations.loading && conversations.items.length === 0" class="conversation-empty">
        还没有对话，先新建一个
      </p>
      <button
        v-for="item in conversations.items"
        :key="item.conversationId"
        class="conversation-item"
        :class="{ active: item.conversationId === conversations.activeConversationId }"
        @click="select(item.conversationId)"
      >
        <span class="conversation-title">{{ item.title }}</span>
      </button>
      <button
        v-if="conversations.nextCursor"
        class="conversation-item"
        :disabled="conversations.loading"
        @click="conversations.loadMore()"
      >
        <span class="conversation-title">{{ conversations.loading ? '加载中…' : '加载更多' }}</span>
      </button>
    </div>
    <div class="sidebar-footer">
      <span>{{ auth.user?.username }}</span>
      <button class="btn btn-sm" @click="logout">退出</button>
    </div>
  </aside>
</template>
