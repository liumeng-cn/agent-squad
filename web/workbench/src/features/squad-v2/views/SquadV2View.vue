<script setup lang="ts">
import { useV2Auth } from '../auth'
import UserProfile from '../components/UserProfile.vue'
import AgentObservationLink from '../../../shared/ui/AgentObservationLink.vue'
import ChatComposer from '../components/ChatComposer.vue'
import ChatMessageList from '../components/ChatMessageList.vue'
import { useChat } from '../useChat'
const { messages, busy, inputs, error, connected, stopping, unresolvedInput, canResume, waiting, canClear, clear, send, stop, resume } = useChat(useV2Auth().user?.userId)
</script>

<template>
  <main class="chat-page">
    <header class="page-header"><span>Squad</span><span class="subtitle">一起把事情做好</span><button class="clear-session" type="button" aria-label="清空当前会话" :disabled="!canClear" title="清空页面并开启新会话" @click="clear">
        <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="m15 4 5 5-10 10H5l-3-3L15 4Zm-8 7 6 6M10 19h11" /></svg><span>清空</span>
      </button><UserProfile /><AgentObservationLink class="observation-link" /></header>
    <ChatMessageList :messages="messages" />
    <footer class="composer-area">
      <p v-if="!connected" class="connection" role="status">正在连接会话，恢复执行状态…</p>
      <p v-for="input in inputs" :key="input.id" class="input-status">补充：{{ input.content }} · {{ input.label }}</p>
      <p v-if="canResume" class="connection">本轮已停止，可直接发送新消息 <button class="resume-turn" type="button" @click="resume">继续本轮</button></p>
      <p v-if="waiting" class="connection">执行正在等待交互，补充消息不会自动代替确认操作。</p>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <ChatComposer :busy="busy" :stopping="stopping" :disabled="unresolvedInput || waiting" :send-message="send" @stop="stop" />
    </footer>
  </main>
</template>

<style scoped>
.connection, .input-status { font-size: 12px; color: #74796b; }
.resume-turn { margin-left: 8px; padding: 4px 10px; border: 1px solid #e5e7e1; border-radius: 14px; background: #f7f8f5; color: #656b5d; font: inherit; cursor: pointer; transition: background .15s; }
.resume-turn:hover { background: #ecefe7; }
.resume-turn:focus-visible { outline: 2px solid #858a7d; outline-offset: 2px; }
.error { font-size: 13px; color: #a6564d; }
.chat-page { position: relative; display: flex; flex-direction: column; height: 100dvh; min-height: 360px; background: #fff; color: #292c25; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', sans-serif; }
.page-header { display: flex; align-items: center; gap: 16px; flex-shrink: 0; height: 64px; padding: 0 32px; font-weight: 600; font-size: 17px; }
.clear-session { margin-left: auto; display: inline-flex; align-items: center; gap: 4px; padding: 5px; border: 0; background: transparent; color: #858a7d; font-size: 12px; cursor: pointer; }
.clear-session svg { width: 15px; height: 15px; stroke: currentColor; stroke-width: 1.6; stroke-linecap: round; stroke-linejoin: round; }
.clear-session:disabled { opacity: .4; cursor: default; }
.observation-link { color: #858a7d; font-size: 13px; font-weight: 400; text-decoration: none; }
.subtitle { font-size: 12px; color: #a4a79e; font-weight: 400; }
.composer-area { width: 100%; max-width: 860px; box-sizing: border-box; padding: 12px 28px 24px; margin: 0 auto; flex-shrink: 0; background: #fff; }
@media (max-width: 600px) { .page-header { height: 52px; padding: 0 18px; } .composer-area { padding: 10px 14px 16px; } }
</style>
