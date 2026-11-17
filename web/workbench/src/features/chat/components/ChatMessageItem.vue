<script setup lang="ts">
import SafeMarkdown from '../../../shared/components/SafeMarkdown.vue'
import ChatExecution from './ChatExecution.vue'
import ChatThinking from './ChatThinking.vue'
import type { ChatMessage } from '../model/chatTypes'
import { inputStatusDescriptions } from '../model/chatTypes'
defineProps<{ message: ChatMessage; now: number }>()
</script>

<template>
  <article :class="['message', message.role]">
    <div v-if="message.role === 'user'" class="user-bubble">{{ message.content }}<small v-if="message.inputStatus && message.inputStatus !== 'APPLIED'" class="input-state">{{ inputStatusDescriptions[message.inputStatus] }}</small></div>
    <template v-else>
      <div class="agent-name">Squad</div>
      <ChatExecution :message="message" :now="now" />
      <ChatThinking :message="message" />
      <div class="answer" aria-live="off" :aria-busy="message.status === 'RUNNING'">
        <SafeMarkdown v-if="message.content" :text="message.content" :streaming="message.status === 'RUNNING'" />
        <span v-else-if="message.status === 'RUNNING'" class="waiting">正在准备回复…</span>
      </div>
      <p v-if="message.error" class="error" role="status">{{ message.error }}</p>
    </template>
  </article>
</template>

<style scoped>
.message { margin-bottom: 44px; }
.user { display: flex; justify-content: flex-end; }
.user-bubble { max-width: 82%; padding: 12px 18px; border-radius: 16px; background: #f3f3f0; white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.7; }
.input-state { display: block; margin-top: 6px; color: #56614f; font-size: 11px; }
.agent-name { color: #394232; font-size: 16px; font-weight: 600; letter-spacing: -.3px; }
.answer { font-size: 16px; line-height: 1.85; color: #20251e; overflow-wrap: anywhere; }
.answer :deep(pre) { overflow-x: auto; }
.answer :deep(table) { display: block; overflow-x: auto; }
.waiting { color: #68715f; font-size: 14px; }
.error { padding: 12px 0; color: #963f35; font-size: 14px; }
</style>
