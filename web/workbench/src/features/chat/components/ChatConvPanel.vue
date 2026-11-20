<script setup lang="ts">
import type { ChatSession } from '../model/chatTypes'
import { computed, watch } from 'vue'
import { useChat } from '../composables/useChat'
import { useAuthStore } from '../../auth/stores/auth'
import ChatComposer from './ChatComposer.vue'
import ChatMessageList from './ChatMessageList.vue'
const props = defineProps<{ convId: string; fresh: boolean; initialSnapshot?: ChatSession }>()
const emit = defineEmits<{ created: [convId: string] }>()
const auth = useAuthStore()
const chat = useChat(auth.user!.userId, props.convId, { fresh: props.fresh, sessionId: props.initialSnapshot?.sessionId, onCreated: id => emit('created', id) })
const { messages, busy, inputs, error, connected, stopping, unresolvedInput, canResume, waiting, send, stop, resume, draft, scrollTop, registered } = chat
watch(() => props.initialSnapshot, snapshot => { if (snapshot) chat.apply(snapshot) }, { immediate: true })
const empty = computed(() => !messages.value.length)
</script>
<template>
  <section class="conversation-panel" :class="{ 'empty-conversation': empty }" aria-label="当前对话">
    <ChatMessageList v-if="!empty" :messages="messages" :initial-scroll-top="scrollTop" @scroll-position="scrollTop = $event" />
    <div v-if="empty" class="welcome"><picture><source media="(prefers-reduced-motion: reduce)" srcset="/brand/squad-icon-2d-motion-preview.png" /><img src="/brand/squad-icon-2d-motion.gif" width="112" height="112" alt="" /></picture><h1>你好，{{ auth.user?.effectiveDisplayName }}</h1><p>从一个问题开始，一起把事情做好</p></div>
    <footer class="composer-area">
      <p v-if="registered && !connected" class="connection" role="status">正在连接会话，恢复执行状态…</p>
      <p v-for="input in inputs" :key="input.id" class="input-status">补充：{{ input.content }} · {{ input.label }}</p>
      <p v-if="canResume" class="connection">本轮已停止，可直接发送新消息 <button class="resume-turn" type="button" @click="resume">继续本轮</button></p>
      <p v-if="waiting" class="connection">执行正在等待交互，补充消息不会代替确认操作。</p>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <ChatComposer :model-value="draft" :busy="busy" :stopping="stopping" :disabled="unresolvedInput || waiting" :send-message="send" @update:model-value="chat.saveDraft" @stop="stop" />
    </footer>
  </section>
</template>
<style scoped>
.conversation-panel{position:relative;display:flex;flex-direction:column;flex:1;min-height:0;min-width:0}.composer-area{width:100%;max-width:900px;padding:12px 28px calc(24px + env(safe-area-inset-bottom));margin:0 auto;flex-shrink:0;background:var(--surface)}.connection,.input-status{font-size:12px;color:var(--text-secondary);margin:7px 0}.error{font-size:13px;color:var(--danger)}.resume-turn{margin-left:8px;padding:4px 10px;border:1px solid var(--input-border);border-radius:14px;background:#fff;color:var(--text-secondary);font:inherit}.welcome{text-align:center;max-width:800px;width:100%;margin:0 auto;padding:0 24px 28px}.welcome img{border-radius:23%;object-fit:contain;mix-blend-mode:multiply;display:block;margin:0 auto 20px}.welcome h1{font-size:30px;font-weight:500;line-height:1.4;margin:0 0 12px;overflow-wrap:anywhere}.welcome p{font-size:14px;color:var(--text-secondary);margin:0}.empty-conversation{padding-top:clamp(48px,20vh,220px);justify-content:flex-start;overflow:auto}.empty-conversation .composer-area{margin:0 auto}.composer-area :deep(.composer-footer span){color:var(--text-secondary)}@media(max-width:767px){.composer-area{padding:10px 16px calc(16px + env(safe-area-inset-bottom))}.welcome h1{font-size:25px}.empty-conversation{padding-top:clamp(38px,14vh,120px)}}
</style>
