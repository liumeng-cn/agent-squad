<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../../auth/stores/auth'
import { useConversationsStore } from '../stores/conversations'

const router = useRouter()
const auth = useAuthStore()
const conversations = useConversationsStore()
const expanded = ref(false)
const groups = computed(() => {
  const today = new Date(); today.setHours(0, 0, 0, 0)
  const buckets = new Map<string, typeof conversations.items>()
  for (const item of conversations.items) {
    const timestamp = Date.parse(/(?:Z|[+-]\d\d:\d\d)$/.test(item.lastActivityAt) ? item.lastActivityAt : `${item.lastActivityAt}Z`)
    const boundary = today.getTime()
    const label = timestamp >= boundary ? '今天' : timestamp >= boundary - 86400000 ? '昨天' : timestamp >= boundary - 6 * 86400000 ? '近 7 天' : '更早'
    if (!buckets.has(label)) buckets.set(label, [])
    buckets.get(label)!.push(item)
  }
  return [...buckets].map(([label, items]) => ({ label, items }))
})
onMounted(() => { if (!conversations.listLoaded) void conversations.refreshList() })
function select(id?: string) {
  expanded.value = false
  if (!id) conversations.startDraft()
  void router.push({ name: 'workspace', params: id ? { conversationId: id } : {} })
}
async function logout() {
  await auth.logout(); conversations.resetAll(); await router.push({ name: 'login' })
}
defineExpose({ openSidebar: () => { expanded.value = true } })
</script>

<template>
  <button v-if="expanded" class="sidebar-scrim" aria-label="关闭侧栏" @click="expanded = false"></button>
  <aside class="chat-sidebar" :class="{ 'is-open': expanded }" aria-label="对话历史" @keydown.esc="expanded = false">
    <div class="chat-brand"><span class="brand-mark" aria-hidden="true">✦</span><span>Agent Squad</span><button class="icon-button mobile-only" aria-label="关闭侧栏" @click="expanded = false">×</button></div>
    <button class="new-chat-button" @click="select()"><span aria-hidden="true">⊕</span> 开启新对话</button>
    <nav class="chat-history" aria-label="历史对话">
      <p v-if="conversations.listError" class="sidebar-error" role="alert">{{ conversations.listError }} <button class="text-button" @click="conversations.refreshList()">重新加载</button></p>
      <p v-if="conversations.listLoading && !conversations.items.length" class="history-placeholder">正在读取历史…</p>
      <p v-if="conversations.listLoaded && !conversations.items.length" class="history-placeholder">你的对话会保存在这里</p>
      <section v-for="group in groups" :key="group.label" class="history-group">
        <h2>{{ group.label }}</h2>
        <button v-for="item in group.items" :key="item.conversationId" class="history-item" :class="{ selected: item.conversationId === conversations.activeConversationId }" :title="item.title" :aria-current="item.conversationId === conversations.activeConversationId ? 'page' : undefined" @click="select(item.conversationId)">{{ item.title || '新对话' }}</button>
      </section>
      <button v-if="conversations.nextCursor" class="history-more" :disabled="conversations.listLoading" @click="conversations.loadMore()">{{ conversations.listLoading ? '加载中…' : '加载更早的对话' }}</button>
    </nav>
    <footer class="chat-account"><div class="account-avatar" aria-hidden="true">{{ auth.user?.username.slice(0, 1).toUpperCase() }}</div><span class="account-name">{{ auth.user?.username }}</span><details class="account-menu"><summary aria-label="账户选项">···</summary><div class="popup-menu"><router-link v-if="auth.user?.roles.includes('ADMIN')" :to="{ name: 'workers' }">能力设置</router-link><button @click="logout">退出登录</button></div></details></footer>
  </aside>
</template>
