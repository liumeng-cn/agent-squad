<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
const props = defineProps<{ modelValue?: string; busy: boolean; stopping: boolean; disabled: boolean; sendMessage: (text: string) => Promise<boolean> }>()
const emit = defineEmits<{ stop: []; 'update:modelValue': [value: string] }>()
const submitting = ref(false)
const localDraft = ref('')
const draft = computed({ get: () => props.modelValue ?? localDraft.value, set: value => { localDraft.value = value; emit('update:modelValue', value) } })
const input = ref<HTMLTextAreaElement>()
function resize() {
  if (!input.value) return
  input.value.style.height = 'auto'
  input.value.style.height = `${Math.min(input.value.scrollHeight, 180)}px`
}
async function submit() {
  if (!draft.value.trim() || submitting.value || props.disabled || props.stopping) return
  submitting.value = true
  const sent = draft.value
  try {
    if (await props.sendMessage(sent) && draft.value === sent) draft.value = ''
  } finally { submitting.value = false }
  await nextTick()
  resize()
  input.value?.focus()
}
function keydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing && event.keyCode !== 229) {
    event.preventDefault()
    void submit()
  }
}
</script>

<template>
  <form class="composer" @submit.prevent="submit()">
    <textarea ref="input" v-model="draft" aria-label="输入消息" placeholder="有问题，尽管问"
      rows="1" maxlength="20000" @input="resize" @keydown="keydown($event)" />
    <div class="composer-footer">
      <span>{{ stopping ? '正在停止，已生成内容会保留' : busy ? '运行中发送，将作为补充要求' : 'Enter 发送 · Shift + Enter 换行' }}</span>
      <div class="composer-actions">
      <button v-if="busy" type="button" aria-label="停止执行" :disabled="stopping" @click="emit('stop')">■</button>
      <button type="submit" :aria-label="busy ? '补充要求' : '发送消息'" :disabled="disabled || stopping || submitting || !draft.trim()">
        <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M12 19V5m-6 6 6-6 6 6" /></svg>
      </button>
      </div>
    </div>
  </form>
</template>

<style scoped>
.composer { padding: 20px 22px 14px; min-height: 144px; border: 1px solid #b8c2cf; border-radius: 24px; background: #fff; box-shadow: 0 8px 32px #22222208; }
.composer:focus-within { border-color: #315efb; box-shadow: 0 0 0 2px #315efb24, 0 8px 32px #22222208; }
textarea { display: block; width: 100%; resize: none; border: 0; outline: 0; background: transparent; font: inherit; line-height: 1.6; max-height: 180px; color: #202630; }
textarea::placeholder { color: #687587; }
.composer-footer { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin-top: 14px; }
.composer-actions { display: flex; align-items: center; gap: 8px; margin-left: auto; flex-shrink: 0; }
.composer-footer span { font-size: 12px; color: #687587; }
button { display: inline-flex; align-items: center; justify-content: center; padding: 0; width: 36px; height: 36px; flex-shrink: 0; border: 0; border-radius: 50%; background: #315efb; color: white; cursor: pointer; }
button svg { display: block; width: 20px; height: 20px; stroke: currentColor; stroke-width: 1.8; stroke-linecap: round; stroke-linejoin: round; }
button:disabled { background: #d4d5d0; cursor: default; }
</style>
