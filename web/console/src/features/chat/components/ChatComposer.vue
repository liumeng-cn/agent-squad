<script setup lang="ts">
import { shouldSendOnEnter } from '../../../shared/lib/chatKeyboard'
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useConversationsStore } from '../stores/conversations'
import { uploadArtifact, type Artifact } from '../../../shared/api/taskDetailsApi'
import { ApiError, errorMessage } from '../../../shared/api/http'
import { newIdempotencyKey } from '../../../shared/lib/idempotency'
import type { MessageReceipt } from '../../../shared/api/chatApi'

const props = defineProps<{ conversationId: string | null; draftRevision: number }>()
const emit = defineEmits<{ accepted: [receipt: MessageReceipt] }>()
const chat = useConversationsStore()
interface Upload { id: string; file: File; key: string; status: 'uploading' | 'ready' | 'failed'; artifact?: Artifact; error?: string }
const draft = ref('')
const uploads = ref<Upload[]>([])
const error = ref('')
const uncertain = ref(false)
const composing = ref(false)
const input = ref<HTMLInputElement | null>(null)
const textarea = ref<HTMLTextAreaElement | null>(null)
let generation = 0
let attempt: { text: string; files: string[]; key: string } | null = null
const blocked = computed(() => chat.submitting || chat.unavailable || uploads.value.some(item => item.status !== 'ready'))
const canSend = computed(() => !blocked.value && !!draft.value.trim() && draft.value.length <= 20000)
const activeAnswer = computed(() => chat.latestExecution)
const canStop = computed(() => activeAnswer.value?.allowedActions.includes('STOP'))
watch(() => [props.conversationId, props.draftRevision], () => { generation++; draft.value = ''; uploads.value = []; error.value = ''; uncertain.value = false; attempt = null; void nextTick(resize) })
function resize() { if (textarea.value) { textarea.value.style.height = 'auto'; textarea.value.style.height = `${Math.min(240, textarea.value.scrollHeight)}px` } }
async function upload(id: string) {
  const current = generation
  const item = uploads.value.find(file => file.id === id)
  if (!item) return
  item.status = 'uploading'; item.error = ''
  try {
    const artifact = await uploadArtifact(item.file, item.key)
    if (current !== generation || !uploads.value.some(file => file.id === id)) return
    item.artifact = artifact; item.status = 'ready'
  } catch (cause) {
    if (current === generation && uploads.value.some(file => file.id === id)) { item.status = 'failed'; item.error = errorMessage(cause) }
  }
}
async function selectFiles(event: Event) {
  const files = [...((event.target as HTMLInputElement).files ?? [])]
  if (input.value) input.value.value = ''
  error.value = ''
  if (files.length + uploads.value.length > 20) { error.value = '一次最多添加 20 个附件'; return }
  const valid: string[] = []
  for (const file of files) {
    if (file.size > 10 * 1024 * 1024 || !/\.(txt|md|json|csv)$/i.test(file.name)) { error.value = '支持 txt、md、json、csv，每个文件最大 10 MiB'; continue }
    const item: Upload = { id: newIdempotencyKey(), file, key: newIdempotencyKey(), status: 'uploading' }
    uploads.value.push(item); valid.push(item.id)
  }
  for (const id of valid) await upload(id)
}
async function send() {
  if (!canSend.value) return
  const current = generation
  error.value = ''
  const text = draft.value.trim()
  const files = uploads.value.flatMap(item => item.artifact ? [item.artifact.artifactId] : [])
  if (!attempt || attempt.text !== text || JSON.stringify(attempt.files) !== JSON.stringify(files)) attempt = { text, files, key: newIdempotencyKey() }
  try {
    const receipt = await chat.send(attempt.text, attempt.files, attempt.key)
    if (current !== generation) return
    draft.value = ''; uploads.value = []; uncertain.value = false; attempt = null
    resize(); emit('accepted', receipt)
  } catch (cause) {
    if (current !== generation) return
    uncertain.value = cause instanceof ApiError && (cause.status === 0 || cause.status >= 500)
    error.value = uncertain.value ? '暂时还未确认是否收到。内容已保留，请点击发送按钮确认，重复确认不会重复发送。' : errorMessage(cause)
  }
}
function enter(event: KeyboardEvent) {
  if (!shouldSendOnEnter(event, composing.value)) return
  event.preventDefault(); void send()
}
onBeforeUnmount(() => { generation++ })
</script>

<template>
  <div class="chat-composer-area">
    <p v-if="error" class="composer-error" role="alert">{{ error }}</p>
    <div class="chat-composer" :class="{ 'is-sending': chat.submitting }">
      <ul v-if="uploads.length" class="composer-files">
        <li v-for="item in uploads" :key="item.id" class="composer-file" :class="{ failed: item.status === 'failed' }">
          <span class="file-icon" aria-hidden="true">▤</span><div><strong :title="item.file.name">{{ item.file.name }}</strong><small>{{ item.status === 'uploading' ? '正在上传…' : item.status === 'failed' ? item.error || '上传失败' : `${Math.max(1, Math.ceil(item.file.size / 1024))} KB · 已就绪` }}</small></div>
          <button v-if="item.status === 'failed'" class="text-button" :disabled="chat.submitting || uncertain" @click="upload(item.id)">重试</button>
          <button class="icon-button file-remove" :aria-label="`移除附件 ${item.file.name}`" :disabled="chat.submitting || uncertain" @click="uploads = uploads.filter(file => file.id !== item.id)">×</button>
        </li>
      </ul>
      <textarea ref="textarea" v-model="draft" class="chat-textarea" rows="2" maxlength="20000" aria-label="发送消息" placeholder="发消息，或一起完成一项工作…" :disabled="chat.submitting || chat.unavailable" :readonly="uncertain" @input="resize" @keydown.enter="enter" @compositionstart="composing = true" @compositionend="composing = false"></textarea>
      <div class="chat-composer-toolbar">
        <input ref="input" class="visually-hidden" type="file" accept=".txt,.md,.json,.csv" multiple tabindex="-1" aria-hidden="true" @change="selectFiles" />
        <button class="icon-button attach-button" type="button" title="添加附件" aria-label="添加附件" :disabled="chat.submitting || uncertain || chat.unavailable || uploads.length >= 20" @click="input?.click()"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true"><path d="m8 12 6-6a3 3 0 0 1 4 4l-8 8a5 5 0 0 1-7-7l8-8"/><path d="m7 13 7-7"/></svg></button>
        <span v-if="draft.length > 18000" class="composer-count">{{ draft.length }}/20000</span>
        <span v-if="chat.submitting" class="composer-sending" role="status">正在确认接收…</span>
        <span class="spacer"></span>
        <button v-if="canStop && activeAnswer" class="stop-answer-button" :disabled="!!chat.controlling" @click="chat.stop('answer', activeAnswer.executionId, activeAnswer.version)"><span class="stop-square" aria-hidden="true"></span>停止回答</button>
        <button class="send-message-button" :aria-label="uncertain ? '确认发送结果' : '发送消息'" :title="uncertain ? '确认发送结果' : '发送消息'" :disabled="!canSend" @click="send"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M12 19V5m-6 6 6-6 6 6"/></svg></button>
      </div>
    </div>
    <p class="chat-input-note">{{ uploads.some(item => item.status === 'uploading') ? '附件就绪后即可发送' : 'Enter 发送 · Shift + Enter 换行' }}</p>
  </div>
</template>
