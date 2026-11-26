<script setup lang="ts">
import type { ChatAgentOption, ChatSession } from '../model/chatTypes'
import { computed, watch } from 'vue'
import { useChat } from '../composables/useChat'
import { useAuthStore } from '../../auth/stores/auth'
import ChatComposer from './ChatComposer.vue'
import ChatMessageList from './ChatMessageList.vue'
import ChatInteractionCard from './ChatInteractionCard.vue'
const props = defineProps<{
  convId?: string
  initialSnapshot?: ChatSession
  agents?: ChatAgentOption[]
  selectedAgentCode?: string
}>()
const emit = defineEmits<{
  created: [convId: string]
  'update:selectedAgentCode': [code: string]
}>()
const auth = useAuthStore()
const chat = useChat(auth.user!.userId, props.convId, {
  onCreated: id => emit('created', id),
  getAgentCode: () => props.selectedAgentCode,
})
const { messages, busy, inputs, error, connected, stopping, unresolvedInput, canResume, waiting, send, stop, resume, draft, scrollTop } = chat
const { interactions, answer, answering, answerDrafts, saveAnswerDraft, pendingAnswer } = chat
watch(() => props.initialSnapshot, snapshot => { if (snapshot) chat.apply(snapshot) }, { immediate: true })
const empty = computed(() => !messages.value.length)
</script>
<template>
  <section class="conversation-panel" :class="{ 'empty-conversation': empty }" aria-label="当前对话">
    <ChatMessageList v-if="!empty" :messages="messages" :initial-scroll-top="scrollTop" @scroll-position="scrollTop = $event" />
    <div v-if="empty" class="welcome">
      <picture><source media="(prefers-reduced-motion: reduce)" srcset="/brand/squad-icon-2d-motion-preview.png" /><img src="/brand/squad-icon-2d-motion.gif" width="112" height="112" alt="" /></picture>
      <h1>你好，{{ auth.user?.effectiveDisplayName }}</h1>
      <p>从一个问题开始，一起把事情做好</p>
      <div v-if="!convId && agents && agents.length" class="agent-selector" role="radiogroup" aria-label="选择对话智能体">
        <button
          v-for="item in agents"
          :key="item.agentCode"
          type="button"
          class="agent-chip"
          :class="{ 'is-active': selectedAgentCode === item.agentCode }"
          :title="item.description || item.displayName"
          @click="emit('update:selectedAgentCode', item.agentCode)"
        >
          <span class="agent-chip-name">{{ item.displayName }}</span>
          <span v-if="item.releaseNo != null" class="agent-chip-tag">#{{ item.releaseNo }}</span>
          <span v-else-if="item.builtIn" class="agent-chip-tag">内置</span>
          <span v-if="item.standalone" class="agent-chip-mode">独立对话</span>
        </button>
      </div>
    </div>
    <footer class="composer-area">
      <ChatInteractionCard v-for="interaction in interactions" :key="interaction.requestId"
        :interaction="interaction" :model-value="pendingAnswer?.requestId === interaction.requestId ? pendingAnswer.answer : answerDrafts[interaction.requestId] || ''"
        :submitting="answering" :disabled="busy || !waiting || Boolean(pendingAnswer && pendingAnswer.requestId !== interaction.requestId)"
        :locked="pendingAnswer?.requestId === interaction.requestId" :submit-answer="answer"
        @update:model-value="saveAnswerDraft(interaction.requestId, $event)" />
      <p v-if="convId && !connected" class="connection" role="status">正在连接会话，恢复执行状态…</p>
      <p v-for="input in inputs" :key="input.id" class="input-status">补充：{{ input.content }} · {{ input.label }}</p>
      <p v-if="canResume" class="connection">本轮已停止，可直接发送新消息 <button class="resume-turn" type="button" @click="resume">继续本轮</button></p>
      <p v-if="waiting && !interactions.length" class="connection">正在等待交互状态更新；若持续未出现问题，请刷新会话核对。</p>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <ChatComposer :model-value="draft" :busy="busy" :stopping="stopping" :disabled="unresolvedInput || waiting || Boolean(pendingAnswer)" :send-message="send" @update:model-value="chat.saveDraft" @stop="stop" />
    </footer>
  </section>
</template>
<style scoped>
.conversation-panel{position:relative;display:flex;flex-direction:column;flex:1;min-height:0;min-width:0}.composer-area{width:100%;max-width:900px;padding:12px 28px calc(24px + env(safe-area-inset-bottom));margin:0 auto;flex-shrink:0;background:var(--surface)}.connection,.input-status{font-size:12px;color:var(--text-secondary);margin:7px 0}.error{font-size:13px;color:var(--danger)}.resume-turn{margin-left:8px;padding:4px 10px;border:1px solid var(--input-border);border-radius:14px;background:#fff;color:var(--text-secondary);font:inherit}.welcome{text-align:center;max-width:800px;width:100%;margin:0 auto;padding:0 24px 28px}.welcome img{border-radius:23%;object-fit:contain;mix-blend-mode:multiply;display:block;margin:0 auto 20px}.welcome h1{font-size:30px;font-weight:500;line-height:1.4;margin:0 0 12px;overflow-wrap:anywhere}.welcome p{font-size:14px;color:var(--text-secondary);margin:0}.empty-conversation{padding-top:clamp(48px,20vh,220px);justify-content:flex-start;overflow:auto}.empty-conversation .composer-area{margin:0 auto}.composer-area :deep(.composer-footer span){color:var(--text-secondary)}
.agent-selector{display:flex;flex-wrap:wrap;justify-content:center;gap:8px;margin-top:20px}
.agent-chip{display:inline-flex;align-items:center;gap:6px;padding:6px 14px;border-radius:999px;border:1px solid #e2e8f0;background:#ffffff;color:#475569;font-size:13px;cursor:pointer;transition:all .15s ease}
.agent-chip:hover{border-color:#94a3b8;color:#0f172a}
.agent-chip.is-active{border-color:#2563eb;background:#eff6ff;color:#1d4ed8;font-weight:500}
.agent-chip-tag{font-size:11px;opacity:.75}
.agent-chip-mode{font-size:11px;padding:1px 6px;border-radius:999px;background:#f1f5f9;color:#64748b}
.agent-chip.is-active .agent-chip-mode{background:#dbeafe;color:#1d4ed8}
@media(max-width:767px){.composer-area{padding:10px 16px calc(16px + env(safe-area-inset-bottom))}.welcome h1{font-size:25px}.empty-conversation{padding-top:clamp(38px,14vh,120px)}}
</style>
