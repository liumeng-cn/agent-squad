<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ApiError, errorMessage } from '../../../shared/api/http'
import { createMemory, deleteMemory, listMemories, updateMemory, type Memory } from '../../../shared/api/memoriesApi'

const items = ref<Memory[]>([])
const nextCursor = ref<string | null>(null)
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const notice = ref('')
const affectedScopes = ref(0)
const key = ref('')
const content = ref('')
const expiresAt = ref('')
const editing = ref<Memory | null>(null)

onMounted(() => { void load() })

async function load(cursor?: string): Promise<void> {
  if (loading.value) return
  loading.value = true
  try {
    const page = await listMemories(cursor)
    items.value = cursor ? [...items.value, ...page.items] : page.items
    nextCursor.value = page.nextCursor ?? null
    affectedScopes.value = page.affectedScopes
  } catch (cause) { error.value = errorMessage(cause) }
  finally { loading.value = false }
}

function edit(memory: Memory): void {
  editing.value = memory
  key.value = memory.key
  content.value = memory.content
  const timestamp = memory.expiresAt ? new Date(memory.expiresAt) : null
  expiresAt.value = timestamp && !Number.isNaN(timestamp.getTime())
    ? new Date(timestamp.getTime() - timestamp.getTimezoneOffset() * 60000).toISOString().slice(0, 16) : ''
  error.value = ''
}

function clear(): void {
  editing.value = null
  key.value = ''; content.value = ''; expiresAt.value = ''
}

async function save(): Promise<void> {
  if (saving.value || !key.value.trim() || !content.value.trim()) return
  saving.value = true; error.value = ''; notice.value = ''
  try {
    const expiration = expiresAt.value ? new Date(expiresAt.value).toISOString() : null
    const result = editing.value
      ? await updateMemory(editing.value, content.value, expiration)
      : await createMemory({ key: key.value.trim(), content: content.value, expiresAt: expiration })
    notice.value = result.impact
    clear()
    await load()
  } catch (cause) {
    error.value = errorMessage(cause)
    if (cause instanceof ApiError && cause.status === 409) {
      error.value = '记忆版本或状态已变化。你的草稿已保留，请重新选择最新记录后核对保存。'
      await load()
    }
  } finally { saving.value = false }
}

async function remove(memory: Memory): Promise<void> {
  if (saving.value || !window.confirm(`删除“${memory.key}”？已使用旧内容的执行会被阻塞，需要从有效资料重新发起。`)) return
  saving.value = true; error.value = ''; notice.value = ''
  try {
    const result = await deleteMemory(memory)
    notice.value = result.impact
    if (editing.value?.memoryId === memory.memoryId) clear()
    await load()
  } catch (cause) {
    error.value = errorMessage(cause)
    if (cause instanceof ApiError && cause.status === 409) await load()
  } finally { saving.value = false }
}
</script>

<template>
  <main class="memory-settings">
    <header><router-link :to="{ name: 'workspace' }">← 返回对话</router-link><h1>个人记忆</h1><p>只保存你明确授权的长期信息。普通聊天不会自动变成记忆。</p></header>
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <p v-if="notice" class="memory-notice" role="status">{{ notice }}</p>
    <p v-if="affectedScopes" class="memory-notice">存在 {{ affectedScopes }} 个旧执行上下文受限。新对话使用当前有效记忆，旧会话中的已发请求不能撤回。</p>
    <section class="memory-editor" aria-label="显式记忆编辑">
      <h2>{{ editing ? '纠正记忆' : '明确记住一条信息' }}</h2>
      <form @submit.prevent="save">
        <label class="field"><span>记忆键</span><input v-model="key" class="input" required maxlength="100" :disabled="saving || !!editing" placeholder="例如：回复语言" /></label>
        <label class="field"><span>内容</span><textarea v-model="content" class="input" required maxlength="8000" rows="4" :disabled="saving" placeholder="例如：优先使用中文回复我"></textarea></label>
        <label class="field"><span>有效期（可选，本地时间）</span><input v-model="expiresAt" class="input" type="datetime-local" :disabled="saving" /></label>
        <div class="memory-actions"><button class="btn btn-primary" :disabled="saving || !key.trim() || !content.trim()">{{ saving ? '保存中…' : editing ? '确认纠正' : '确认记住' }}</button><button v-if="editing" class="btn" type="button" :disabled="saving" @click="clear">取消编辑</button></div>
      </form>
    </section>
    <section aria-label="已有记忆"><h2>我的记忆</h2><p v-if="loading && !items.length" role="status">正在读取…</p><p v-else-if="!items.length">还没有记忆。你可以在这里明确创建，也可以在聊天中要求记住。</p>
      <article v-for="memory in items" :key="memory.memoryId" class="memory-record"><div class="memory-record-heading"><h3>{{ memory.key }}</h3><span>{{ memory.status === 'ACTIVE' ? '有效' : memory.status === 'EXPIRED' ? '已过期' : '已删除' }}</span></div><p v-if="memory.status !== 'DELETED'" class="memory-content">{{ memory.content }}</p><p v-else class="memory-deleted">正文已删除，保留删除标记以阻止旧内容恢复。</p><small v-if="memory.expiresAt">有效期：{{ new Date(memory.expiresAt).toLocaleString() }}</small><div v-if="memory.status !== 'DELETED'" class="memory-actions"><button class="btn btn-sm" :disabled="saving" @click="edit(memory)">纠正</button><button class="btn btn-sm btn-danger" :disabled="saving" @click="remove(memory)">删除</button></div></article>
      <button v-if="nextCursor" class="btn" :disabled="loading" @click="load(nextCursor)">{{ loading ? '读取中…' : '加载更早的记忆' }}</button>
    </section>
  </main>
</template>

<style scoped>
.memory-settings { max-width: 880px; margin: 0 auto; padding: 28px 24px 60px; }
header p, .memory-deleted { color: var(--text-secondary); }
h1 { margin-bottom: 4px; } h2 { font-size: 19px; } h3 { margin: 0; font-size: 16px; }
.memory-editor, .memory-record { background: var(--surface); border: 1px solid var(--border); border-radius: 12px; padding: 20px; margin: 18px 0; }
.memory-actions, .memory-record-heading { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
.memory-record-heading { justify-content: space-between; }
.memory-record-heading span, small { color: var(--text-secondary); font-size: 13px; }
.memory-content { white-space: pre-wrap; overflow-wrap: anywhere; }
.memory-notice { padding: 12px 16px; background: var(--accent-soft); border-radius: 8px; }
.memory-record .memory-actions { margin-top: 12px; }
@media (max-width: 600px) { .memory-settings { padding: 18px 14px 40px; } .memory-editor, .memory-record { padding: 15px; } }
</style>
