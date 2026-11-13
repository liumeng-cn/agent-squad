<script setup lang="ts">
import { nextTick, ref } from 'vue'
defineProps<{ busy: boolean }>()
const emit = defineEmits<{ send: [text: string] }>()
const draft = ref('')
const input = ref<HTMLTextAreaElement>()
function resize() {
  if (!input.value) return
  input.value.style.height = 'auto'
  input.value.style.height = `${Math.min(input.value.scrollHeight, 180)}px`
}
async function submit() {
  if (!draft.value.trim()) return
  emit('send', draft.value)
  draft.value = ''
  await nextTick()
  resize()
  input.value?.focus()
}
function keydown(event: KeyboardEvent, busy: boolean) {
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing && event.keyCode !== 229) {
    event.preventDefault()
    if (!busy) submit()
  }
}
</script>

<template>
  <form class="composer" @submit.prevent="!busy && submit()">
    <textarea ref="input" v-model="draft" aria-label="输入消息" placeholder="有问题，尽管问"
      rows="1" maxlength="20000" @input="resize" @keydown="keydown($event, busy)" />
    <div class="composer-footer">
      <span>{{ busy ? '正在回复，你可以先准备下一条消息' : 'Enter 发送 · Shift + Enter 换行' }}</span>
      <button type="submit" aria-label="发送消息" :disabled="busy || !draft.trim()">
        <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M12 19V5m-6 6 6-6 6 6" /></svg>
      </button>
    </div>
  </form>
</template>

<style scoped>
.composer { padding: 20px 22px 14px; border: 1px solid #e9e9e6; border-radius: 24px; background: #fff; box-shadow: 0 8px 32px #22222208; }
textarea { display: block; width: 100%; resize: none; border: 0; outline: 0; background: transparent; font: inherit; line-height: 1.6; max-height: 180px; color: #272824; }
textarea::placeholder { color: #a3a39d; }
.composer-footer { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin-top: 14px; }
.composer-footer span { font-size: 12px; color: #a0a098; }
button { display: inline-flex; align-items: center; justify-content: center; padding: 0; width: 36px; height: 36px; flex-shrink: 0; border: 0; border-radius: 50%; background: #30332e; color: white; cursor: pointer; }
button svg { display: block; width: 20px; height: 20px; stroke: currentColor; stroke-width: 1.8; stroke-linecap: round; stroke-linejoin: round; }
button:disabled { background: #d4d5d0; cursor: default; }
</style>
