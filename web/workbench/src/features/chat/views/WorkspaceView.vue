<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ConversationSidebar from '../components/ConversationSidebar.vue'
import ChatComposer from '../components/ChatComposer.vue'
import MessageThread from '../components/MessageThread.vue'
import { useConversationsStore } from '../stores/conversations'
import type { MessageReceipt } from '../../../shared/api/chatApi'
const route = useRoute()
const router = useRouter()
const chat = useConversationsStore()
const sidebar = ref<InstanceType<typeof ConversationSidebar> | null>(null)
const editingTitle = ref(false)
const title = ref('')
const savingTitle = ref(false)
const titleInput = ref<HTMLInputElement | null>(null)
const conversationId = computed(() => typeof route.params.conversationId === 'string' && route.params.conversationId ? route.params.conversationId : null)
const empty = computed(() => !conversationId.value && !chat.messages.length)
watch(conversationId, id => { editingTitle.value = false; if (id) void chat.open(id); else chat.startDraft() }, { immediate: true })
async function accepted(receipt: MessageReceipt) {
  if (!conversationId.value) await router.replace({ name: 'workspace', params: { conversationId: receipt.conversationId } })
}
async function editTitle() { title.value = chat.conversation?.title ?? ''; editingTitle.value = true; await nextTick(); titleInput.value?.focus(); titleInput.value?.select() }
async function rename() {
  if (savingTitle.value || !title.value.trim()) return
  savingTitle.value = true
  try { await chat.rename(title.value); editingTitle.value = false } catch { /* 由会话内提示解释冲突。 */ }
  finally { savingTitle.value = false }
}
async function remove() {
  if (!window.confirm('删除这个对话？对话中的处理也会停止，历史中将不再显示。')) return
  if (await chat.remove()) await router.replace({ name: 'workspace' })
}
onBeforeUnmount(() => { chat.dispose() })
</script>
<template>
  <div class="chat-workspace">
    <ConversationSidebar ref="sidebar" />
    <main class="chat-main" :class="{ 'is-empty': empty }">
      <header class="chat-header"><button class="icon-button sidebar-toggle mobile-only" aria-label="打开对话历史" @click="sidebar?.openSidebar()"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true"><rect x="4" y="4" width="16" height="16" rx="3"/><path d="M10 4v16"/></svg></button><form v-if="editingTitle" class="title-editor" @submit.prevent="rename"><input ref="titleInput" v-model="title" maxlength="200" aria-label="对话标题" :disabled="savingTitle" @keydown.esc="editingTitle = false" /><button class="text-button" :disabled="savingTitle || !title.trim()">保存</button><button class="text-button" type="button" @click="editingTitle = false">取消</button></form><h1 v-else-if="chat.conversation">{{ chat.conversation.title }}</h1><span class="spacer"></span><details v-if="chat.conversation && !editingTitle" class="conversation-menu"><summary class="icon-button" aria-label="对话选项">···</summary><div class="popup-menu"><button @click="editTitle">修改标题</button><button class="danger-text" @click="remove">删除对话</button></div></details></header>
      <div v-if="empty" class="chat-welcome"><div class="welcome-heading"><span aria-hidden="true">✦</span><h2>今天想一起完成什么？</h2></div><ChatComposer :key="`draft:${chat.draftRevision}`" :conversation-id="null" :draft-revision="chat.draftRevision" @accepted="accepted" /></div>
      <template v-else><MessageThread /><ChatComposer :key="conversationId ?? 'draft'" :conversation-id="conversationId" :draft-revision="chat.draftRevision" @accepted="accepted" /></template>
    </main>
  </div>
</template>
