<script setup lang="ts">
import { computed, nextTick, onMounted, onBeforeUnmount, ref } from 'vue'
import { useConvsStore } from '../stores/convs'
import { groupSessions } from '../model/sessionGroups'
import type { ConvSummary } from '../model/conv'
import { ApiError, errorMessage } from '../../../shared/http/http'
import { readConv } from '../api/convApi'
import ChatIcon from './ChatIcon.vue'
import UserProfile from '../../user/components/UserProfile.vue'
defineProps<{ selectedId?: string; collapsed: boolean }>()
const emit = defineEmits<{ select: [id: string]; create: []; toggle: []; removed: [id: string] }>()
const sessions = useConvsStore()
const groups = computed(() => groupSessions(sessions.items))
const menu = ref<string>()
const removing = ref<ConvSummary>()
const acting = ref(false)
const actionError = ref('')
const editing = ref<ConvSummary>()
const title = ref('')
const error = ref('')
const conflict = ref(false)
const saving = ref(false)
const field = ref<HTMLInputElement>()
function dismissMenu(event: MouseEvent) {
  if (!(event.target instanceof Element) || !event.target.closest('.row-actions')) {
    menu.value = undefined
  }
}
function dismissMenuOnEscape(event: KeyboardEvent) {
  if (event.key === 'Escape') menu.value = undefined
}
onMounted(() => {
  document.addEventListener('click', dismissMenu, true)
  document.addEventListener('keydown', dismissMenuOnEscape)
})
onBeforeUnmount(() => {
  document.removeEventListener('click', dismissMenu, true)
  document.removeEventListener('keydown', dismissMenuOnEscape)
})
async function rename(row: ConvSummary) { menu.value = undefined; editing.value = { ...row }; title.value = row.title; error.value = ''; conflict.value = false; await nextTick(); field.value?.focus(); field.value?.select() }
async function save() {
  if (!editing.value || saving.value) return
  saving.value = true; error.value = ''; conflict.value = false
  try { await sessions.rename(editing.value.convId, title.value.trim(), editing.value.version); editing.value = undefined }
  catch (reason) {
    error.value = errorMessage(reason)
    conflict.value = reason instanceof ApiError && reason.code === 409
  }
  finally { saving.value = false }
}
async function refreshTitle() {
  if (!editing.value) return
  saving.value = true; error.value = ''
  try {
    const latest = await readConv(editing.value.convId)
    editing.value.version = latest.version
    conflict.value = false
  } catch (reason) { error.value = errorMessage(reason) }
  finally { saving.value = false }
}
async function archive(row: ConvSummary) {
  if (acting.value) return
  menu.value = undefined
  acting.value = true
  actionError.value = ''
  try {
    await sessions.archive(row.convId)
    emit('removed', row.convId)
  } catch (reason) {
    actionError.value = errorMessage(reason)
  } finally {
    acting.value = false
  }
}
function confirmDelete(row: ConvSummary) {
  menu.value = undefined
  actionError.value = ''
  removing.value = row
}
async function remove() {
  if (!removing.value || acting.value) return
  acting.value = true
  actionError.value = ''
  try {
    const id = removing.value.convId
    await sessions.remove(id)
    removing.value = undefined
    emit('removed', id)
  } catch (reason) {
    actionError.value = errorMessage(reason)
  } finally {
    acting.value = false
  }
}
</script>
<template>
  <aside class="chat-sidebar" :class="{ collapsed }" aria-label="会话导航">
    <div class="sidebar-brand"><img class="sidebar-logo" :src="collapsed ? '/brand/squad-symbol.svg' : '/brand/squad-logo.svg'" alt="Squad" :width="collapsed ? 36 : 156" :height="collapsed ? 36 : 64" /><button v-if="!collapsed" type="button" class="icon-button" aria-label="折叠会话侧栏" @click="emit('toggle')"><ChatIcon name="sidebar" /></button></div>
    <button v-if="collapsed" type="button" class="icon-button expand" aria-label="展开会话侧栏" @click="emit('toggle')"><ChatIcon name="sidebar" /></button>
    <button type="button" class="new-chat" :aria-label="collapsed ? '开启新对话' : undefined" @click="emit('create')"><ChatIcon name="new" /><span v-if="!collapsed">开启新对话</span></button>
    <nav v-if="!collapsed" class="history" aria-label="历史会话">
      <section v-for="group in groups" :key="group.label"><h2>{{ group.label }}</h2>
        <div v-for="item in group.sessions" :key="item.convId" class="session-row" :class="{ selected: item.convId === selectedId }">
          <button type="button" class="session-select" :aria-current="item.convId === selectedId ? 'page' : undefined" :title="item.title" @click="emit('select', item.convId)"><span>{{ item.title }}</span>

          </button><div class="row-actions" @keydown.esc="menu = undefined">
            <button type="button" class="rename-button" :aria-label="`管理 ${item.title}`" :aria-expanded="menu === item.convId" :disabled="acting" @click="menu = menu === item.convId ? undefined : item.convId">···</button>
            <div v-if="menu === item.convId" class="action-menu" aria-label="对话操作">
              <button type="button" @click="rename(item)">重命名</button>
              <button type="button" @click="archive(item)">归档</button>
              <button type="button" class="danger" @click="confirmDelete(item)">删除</button>
            </div>
          </div>
        </div>
      </section>
      <p v-if="!sessions.items.length && !sessions.loading && !sessions.error" class="empty-history">还没有历史对话</p>
      <p v-if="actionError && !removing" class="list-error" role="alert">{{ actionError }}</p>
      <p v-if="sessions.error" class="list-error" role="alert">{{ sessions.error }}<button type="button" @click="sessions.load()">重试</button></p>
      <button v-if="sessions.hasMore" class="load-more" type="button" :disabled="sessions.loading" @click="sessions.load(true)">加载更多</button><p v-if="sessions.loading" class="empty-history" role="status">正在加载…</p>
    </nav>
    <footer class="sidebar-user"><UserProfile :compact="collapsed" /></footer>
  </aside>
  <Teleport to="body">
    <div v-if="removing" class="rename-backdrop" @click.self="!acting && (removing = undefined)">
      <form role="dialog" aria-modal="true" aria-labelledby="delete-title" class="rename-panel" @submit.prevent="remove" @keydown.esc="!acting && (removing = undefined)">
        <h2 id="delete-title">删除对话</h2>
        <div class="delete-description">确定删除「{{ removing.title }}」吗？删除后将从对话列表移除。</div>
        <p v-if="actionError" role="alert">{{ actionError }}</p>
        <div><button type="button" :disabled="acting" @click="removing = undefined">取消</button><button class="delete-confirm" :disabled="acting">{{ acting ? '删除中…' : '删除' }}</button></div>
      </form>
    </div>
  </Teleport>
  <Teleport to="body"><div v-if="editing" class="rename-backdrop" @click.self="!saving && (editing = undefined)"><form role="dialog" aria-modal="true" aria-labelledby="rename-title" class="rename-panel" @submit.prevent="save" @keydown.esc="!saving && (editing = undefined)">
    <h2 id="rename-title">重命名会话</h2><label>会话名称<input ref="field" v-model="title" maxlength="120" required /></label><p v-if="error" role="alert">{{ error }}</p><button v-if="conflict" type="button" :disabled="saving" class="primary" @click="refreshTitle">刷新标题版本，保留输入</button>
    <div><button type="button" :disabled="saving" @click="editing = undefined">取消</button><button class="primary" :disabled="saving || !title.trim()">{{ saving ? '保存中…' : '保存' }}</button></div>
  </form></div></Teleport>
</template>
<style scoped>
.chat-sidebar{display:flex;flex-direction:column;width:272px;flex-shrink:0;height:100dvh;background:var(--bg);border-right:1px solid #eef0f4;padding:24px 14px 14px}.sidebar-brand{display:flex;align-items:center;gap:10px;min-height:40px;padding:0 8px;margin-bottom:28px}.sidebar-logo{object-fit:contain;mix-blend-mode:multiply;flex-shrink:0}.sidebar-brand strong{font-size:21px;font-weight:600;letter-spacing:-.5px}.icon-button{display:grid;place-items:center;border:0;background:transparent;color:var(--text-secondary);width:40px;height:40px;border-radius:10px;flex-shrink:0;font-size:22px}.sidebar-brand .icon-button{margin-left:auto}.icon-button:hover{background:var(--hover)}.new-chat{display:flex;align-items:center;justify-content:center;gap:10px;min-height:44px;background:#fff;border:1px solid #eef0f4;border-radius:24px;box-shadow:0 2px 5px #18243b08;color:var(--text);font-size:14px;flex-shrink:0}.new-chat>span:first-child{font-size:22px}.new-chat:hover{background:#fdfdff;border-color:#dce1ef;box-shadow:0 3px 9px #18243b0b}.history{overflow:auto;flex:1;min-height:0;padding:16px 0 24px;scrollbar-width:thin}.history h2{font-size:12px;color:var(--text-secondary);font-weight:400;margin:24px 10px 9px}.session-row{display:flex;align-items:center;border-radius:10px;min-width:0;margin:3px 0}.session-row:hover{background:var(--hover)}.session-row.selected{background:var(--accent-soft)}.session-select{display:flex;align-items:center;gap:7px;flex:1;min-width:0;background:transparent;border:0;color:var(--text);text-align:left;min-height:43px;padding:10px;font-size:14px}.session-select>span{overflow:hidden;white-space:nowrap;text-overflow:ellipsis;flex:1}.session-select small{font-size:11px;color:var(--text-secondary);white-space:nowrap}.rename-button{border:0;background:transparent;color:var(--text-secondary);padding:8px;font-size:18px;opacity:0}.session-row:hover .rename-button,.rename-button:focus-visible,.selected .rename-button{opacity:1}.sidebar-user{margin-top:auto;padding-top:12px}.collapsed{width:64px;padding:16px 8px}.collapsed .sidebar-brand{padding:0;justify-content:center;margin-bottom:12px}.collapsed .new-chat{margin-top:12px;min-height:44px}.collapsed .expand{margin:0 auto}.collapsed .sidebar-user{padding-top:8px}.empty-history,.list-error{font-size:12px;color:var(--text-secondary);margin:20px 10px}.list-error{color:var(--danger)}.list-error button,.load-more{border:0;background:transparent;color:var(--accent);font-size:12px;padding:8px}.load-more{width:100%;text-align:center}.rename-backdrop{position:fixed;inset:0;z-index:100;background:#20263045;display:grid;place-items:center;padding:16px}.rename-panel{width:400px;max-width:100%;background:#fff;border-radius:20px;padding:26px}.rename-panel h2{font-size:20px;font-weight:500;margin:0 0 24px}.rename-panel label{font-size:13px}.rename-panel input{display:block;width:100%;margin-top:9px;padding:12px;border-radius:12px;border:1px solid var(--input-border);font:inherit}.rename-panel p{font-size:13px;color:var(--danger)}.rename-panel>div{display:flex;justify-content:flex-end;gap:10px;margin-top:24px}.rename-panel button{border:1px solid var(--input-border);border-radius:10px;padding:9px 16px;background:#fff;font-size:14px}.rename-panel .primary{color:#fff;background:var(--accent);border-color:var(--accent)}
.row-actions{position:relative;flex-shrink:0}.action-menu{position:absolute;right:0;top:34px;z-index:5;min-width:124px;padding:5px;background:var(--surface,#fff);border:1px solid var(--input-border);border-radius:12px;box-shadow:0 8px 24px #18243b14}.action-menu button{display:block;width:100%;padding:9px 12px;border:0;border-radius:8px;text-align:left;background:transparent;color:var(--text);font:inherit;font-size:13px}.action-menu button:hover{background:var(--hover)}.action-menu .danger{color:var(--danger)}.delete-description{display:block!important;margin:0!important;font-size:14px;line-height:1.7;color:var(--text-secondary);overflow-wrap:anywhere}.rename-panel .delete-confirm{background:var(--danger);border-color:var(--danger);color:#fff}.rename-panel button:disabled{opacity:.5}.session-row:focus-within .rename-button{opacity:1}@media(hover:none){.rename-button{opacity:1}}
</style>
