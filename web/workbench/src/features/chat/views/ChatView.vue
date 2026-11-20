<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useConvsStore } from '../stores/convs'
import type { ChatSession } from '../model/chatTypes'
import { readConv } from '../api/convApi'
import { readSession } from '../api/chatApi'
import { ApiError, errorMessage } from '../../../shared/http/http'
import ChatIcon from '../components/ChatIcon.vue'
import ChatSidebar from '../components/ChatSidebar.vue'
import ChatConvPanel from '../components/ChatConvPanel.vue'
const route = useRoute()
const router = useRouter()
const sessions = useConvsStore()
const draftSession = ref(crypto.randomUUID())
const routeId = computed(() => typeof route.params.convId === 'string' ? route.params.convId : undefined)
const activeId = computed(() => routeId.value || draftSession.value)
const selected = computed(() => sessions.items.find(item => item.convId === routeId.value))
const compact = ref(window.innerWidth < 1024 || localStorage.getItem('squad.sidebar.collapsed') === 'true')
const drawer = ref(false)
const narrow = ref(window.innerWidth < 1024)
const toggle = ref<HTMLButtonElement>()
const error = ref('')
const ready = ref(!routeId.value)
const initialSnapshot = ref<ChatSession>()
let selectionEpoch = 0
function resize() { narrow.value = window.innerWidth < 1024; if (!narrow.value) drawer.value = false }
async function closeDrawer() { drawer.value = false; await nextTick(); toggle.value?.focus() }
function toggleSidebar() {
  if (narrow.value) drawer.value = !drawer.value
  else { compact.value = !compact.value; localStorage.setItem('squad.sidebar.collapsed', String(compact.value)) }
}
function select(id: string) { void router.push({ name: 'chat-session', params: { convId: id } }); void closeDrawer() }
function create() { draftSession.value = crypto.randomUUID(); void router.push({ name: 'chat' }); void closeDrawer() }
function created(id: string) { void router.replace({ name: 'chat-session', params: { convId: id } }) }
function removed(id: string) { if (routeId.value === id) create() }
function keyboard(event: KeyboardEvent) { if (event.key === 'Escape' && drawer.value) void closeDrawer() }
watch(() => selected.value?.title, title => { document.title = `${title || (routeId.value ? '会话' : '新对话')} · Squad` }, { immediate: true })
watch(routeId, async id => {
  const epoch = ++selectionEpoch
  const generation = sessions.generation
  error.value = ''
  initialSnapshot.value = undefined
  if (!id) { ready.value = true; return }
  ready.value = id === draftSession.value
  try {
    const summary = await readConv(id)
    if (epoch !== selectionEpoch || generation !== sessions.generation) return
    sessions.upsert(summary)
    const snapshot = await readSession(summary.sessionId)
    if (epoch !== selectionEpoch || generation !== sessions.generation) return
    initialSnapshot.value = snapshot
    if (snapshot.summary) sessions.upsert(snapshot.summary)
    ready.value = true
  } catch (reason) {
    if (epoch !== selectionEpoch || generation !== sessions.generation) return
    error.value = errorMessage(reason)
    if (reason instanceof ApiError && reason.code === 404) await router.replace({ name: 'chat' })
  }
}, { immediate: true })
onMounted(() => { void sessions.load(); window.addEventListener('resize', resize); document.addEventListener('keydown', keyboard) })
onBeforeUnmount(() => { selectionEpoch++; window.removeEventListener('resize', resize); document.removeEventListener('keydown', keyboard) })
</script>
<template>
  <main class="chat-workspace" :class="{ 'drawer-open': drawer, 'sidebar-hidden': compact || narrow }">
    <button v-if="drawer" class="drawer-backdrop" aria-label="关闭会话侧栏" @click="closeDrawer" />
    <div class="sidebar-shell" :class="{ 'drawer-visible': drawer }"><ChatSidebar :selected-id="routeId" :collapsed="drawer ? false : compact" @toggle="toggleSidebar" @create="create" @select="select" @removed="removed" /></div>
    <div class="workspace-main">
      <header class="chat-header"><div v-if="compact || narrow" class="header-navigation"><img src="/brand/squad-symbol.svg" alt="Squad" width="36" height="36" /><div class="header-tools"><button ref="toggle" type="button" class="sidebar-toggle" aria-label="展开会话侧栏" :aria-expanded="drawer" @click="toggleSidebar"><ChatIcon name="sidebar" /></button><button type="button" class="sidebar-toggle" aria-label="开启新对话" @click="create"><ChatIcon name="new" /></button></div></div><span>{{ selected?.title || '新对话' }}</span></header>
      <p v-if="error" class="page-error" role="alert">{{ error }}</p>
      <ChatConvPanel v-if="ready" :key="activeId" :conv-id="activeId" :fresh="!routeId" :initial-snapshot="initialSnapshot" @created="created" />
    </div>
  </main>
</template>
<style scoped>
.chat-workspace{display:flex;width:100%;height:100dvh;min-height:360px;background:var(--surface);color:var(--text)}.workspace-main{display:flex;flex-direction:column;min-width:0;flex:1;min-height:0}.chat-header{height:72px;flex-shrink:0;display:flex;align-items:center;gap:14px;padding:0 28px;font-size:15px;color:var(--text);font-weight:500}.demo-label{margin-left:auto;font-size:12px;color:var(--text-secondary)}.chat-header>span{white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.sidebar-toggle{display:grid;place-items:center;width:40px;height:40px;border:0;background:none;color:var(--text-secondary);font-size:20px;border-radius:10px}.header-navigation>img{object-fit:contain;mix-blend-mode:multiply}.header-navigation{display:flex;align-items:center;gap:18px}.header-tools{display:flex;align-items:center;gap:2px;padding:3px 6px;border:1px solid #e8e9ee;border-radius:28px;box-shadow:0 3px 12px #18243b05}.sidebar-toggle:hover{background:#f1f3f8}.sidebar-hidden .sidebar-shell:not(.drawer-visible){display:none}.page-error{font-size:13px;color:var(--danger);padding:0 28px}.drawer-backdrop{position:fixed;inset:0;z-index:40;background:#20263045;border:0}.sidebar-shell{flex-shrink:0}.sidebar-shell.drawer-visible{position:fixed;left:0;top:0;z-index:41}.drawer-visible :deep(.chat-sidebar){width:min(300px,85vw)}@media(max-width:1023px){.sidebar-toggle{display:grid}.sidebar-shell:not(.drawer-visible) :deep(.chat-sidebar){width:64px}.chat-header{padding:0 16px}}@media(max-width:767px){.sidebar-shell:not(.drawer-visible){display:none}.chat-header{height:64px;padding:0 16px;gap:16px}.header-navigation>img{object-fit:contain;mix-blend-mode:multiply}.header-navigation{gap:12px}.demo-label{display:none}}
</style>
