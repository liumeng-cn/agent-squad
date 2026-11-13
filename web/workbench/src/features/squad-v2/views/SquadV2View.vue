<script setup lang="ts">
import AgentObservationLink from '../../../shared/ui/AgentObservationLink.vue'
import ChatComposer from '../components/ChatComposer.vue'
import ChatMessageList from '../components/ChatMessageList.vue'
import { useChat } from '../useChat'
const { messages, busy, send } = useChat()
</script>

<template>
  <main class="chat-page">
    <header class="page-header"><span>Squad</span><span class="subtitle">一起把事情做好</span><AgentObservationLink class="observation-link" /></header>
    <ChatMessageList :messages="messages" />
    <footer class="composer-area"><ChatComposer :busy="busy" @send="send" /></footer>
  </main>
</template>

<style scoped>
.chat-page { position: relative; display: flex; flex-direction: column; height: 100dvh; min-height: 360px; background: #fff; color: #292c25; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', sans-serif; }
.page-header { display: flex; align-items: center; gap: 16px; flex-shrink: 0; height: 64px; padding: 0 32px; font-weight: 600; font-size: 17px; }
.observation-link { margin-left: auto; color: #858a7d; font-size: 13px; font-weight: 400; text-decoration: none; }
.subtitle { font-size: 12px; color: #a4a79e; font-weight: 400; }
.composer-area { width: 100%; max-width: 860px; box-sizing: border-box; padding: 12px 28px 24px; margin: 0 auto; flex-shrink: 0; background: #fff; }
@media (max-width: 600px) { .page-header { height: 52px; padding: 0 18px; } .composer-area { padding: 10px 14px 16px; } }
</style>
