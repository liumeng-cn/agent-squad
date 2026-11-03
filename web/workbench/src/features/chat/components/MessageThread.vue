<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useConversationsStore } from '../stores/conversations'
import SafeMarkdown from '../../../shared/ui/SafeMarkdown.vue'
import InteractionCard from '../../tasks/components/InteractionCard.vue'
import OperationCard from './OperationCard.vue'
import { apiDownload, errorMessage } from '../../../shared/api/http'
import type { Artifact } from '../../../shared/api/taskDetailsApi'
import { statusText } from '../../../shared/lib/taskStatus'
const chat = useConversationsStore()
const scroller = ref<HTMLElement | null>(null)
const pinned = ref(true)
const downloadError = ref('')
const downloading = ref<string | null>(null)
const copied = ref<string | null>(null)
const allFiles = computed(() => [...new Map([...chat.messages.flatMap(message => message.artifacts), ...(chat.snapshot?.deliverables ?? [])].map(file => [file.artifactId, file])).values()])
const working = computed(() => chat.snapshot?.activeTask)
const workText = computed(() => {
  if (working.value?.status === 'RECOVERY_BLOCKED') return '这项工作暂未完成，已保留当前内容'
  return statusText(working.value?.status ?? '')
})
function onScroll() { const node = scroller.value; if (node) pinned.value = node.scrollHeight - node.scrollTop - node.clientHeight < 110 }
watch(() => chat.activeConversationId, () => { pinned.value = true; downloadError.value = '' })
watch(() => [chat.messages.length, chat.snapshot?.cursor, chat.loading], async () => { await nextTick(); if (pinned.value && scroller.value) scroller.value.scrollTop = scroller.value.scrollHeight })
async function older() {
  const node = scroller.value; const height = node?.scrollHeight ?? 0
  pinned.value = false; await chat.loadOlder(); await nextTick()
  if (node) node.scrollTop += node.scrollHeight - height
}
async function download(file: Artifact) {
  downloading.value = file.artifactId; downloadError.value = ''
  try {
    const blob = await apiDownload(`/api/v1/artifacts/${encodeURIComponent(file.artifactId)}/content`)
    const url = URL.createObjectURL(blob); const anchor = document.createElement('a')
    anchor.href = url; anchor.download = file.fileName; anchor.click(); setTimeout(() => URL.revokeObjectURL(url), 1000)
  } catch (cause) { downloadError.value = errorMessage(cause) }
  finally { downloading.value = null }
}
async function copy(id: string, text: string) {
  try { await navigator.clipboard.writeText(text); copied.value = id; setTimeout(() => { if (copied.value === id) copied.value = null }, 1800) }
  catch { downloadError.value = '未能复制，请选中文本复制' }
}
function answerStatus(status: string) {
  return ({ QUEUED: '已收到，等待开始', RUNNING: '正在回复…', RECOVERING: '正在恢复回答…', WAITING_INPUT: '等待你补充信息', WAITING_APPROVAL: '等待你确认', RECONCILING: '正在核对外部结果', CANCELLING: '正在停止回答…' } as Record<string, string>)[status] ?? '正在处理…'
}
</script>
<template>
  <div ref="scroller" class="chat-scroll" tabindex="0" aria-label="对话内容" @scroll="onScroll">
    <div class="chat-messages">
      <button v-if="chat.nextBeforeSeq" class="load-history-button" :disabled="chat.loadingHistory" @click="older">{{ chat.loadingHistory ? '正在读取…' : '查看更早的消息' }}</button>
      <p v-if="chat.loading" class="chat-loading" role="status">正在读取对话…</p>
      <article v-for="message in chat.messages" :key="message.messageId" class="chat-message" :class="message.role === 'USER' ? 'from-user' : 'from-assistant'">
        <template v-if="message.role === 'USER'">
          <div class="user-message-text">{{ message.text }}</div>
          <small v-if="chat.executionHistory.some(item => item.sourceMessageId === message.messageId && item.status === 'CANCELLED')" class="stopped-note">回答已停止</small>
          <small v-if="chat.taskHistory.some(item => item.sourceMessageId === message.messageId && item.status === 'CANCELLED')" class="stopped-note">这项工作已停止</small>
          <div v-if="message.artifacts.length" class="message-files"><button v-for="file in message.artifacts" :key="file.artifactId" class="message-file" :disabled="downloading === file.artifactId" @click="download(file)"><span aria-hidden="true">▤</span>{{ file.fileName }}<span class="file-download" aria-hidden="true">↓</span></button></div>
        </template>
        <template v-else>
          <span class="assistant-avatar" aria-hidden="true">✦</span><div class="assistant-body"><SafeMarkdown :text="message.text" /><button class="copy-answer-button" :aria-label="copied === message.messageId ? '已复制回答' : '复制回答'" @click="copy(message.messageId, message.text)">{{ copied === message.messageId ? '已复制' : '复制' }}</button></div>
        </template>
      </article>
      <div v-for="execution in chat.snapshot?.activeExecutions ?? []" :key="execution.executionId" class="answer-state" role="status"><span class="state-indicator" :class="{ running: ['RUNNING', 'RECOVERING'].includes(execution.status) }"></span>{{ answerStatus(execution.status) }}</div>
      <div v-if="working" class="active-work-bar" role="status"><div><span class="work-mark" aria-hidden="true">✦</span><span>{{ workText }}</span><small v-if="!['CANCELLING', 'RECONCILING'].includes(working.status)">你可以继续在这里补充或提问</small></div><button v-if="working.status !== 'CANCELLING'" class="text-button" :disabled="!!chat.controlling" @click="chat.stop('task', working.taskId, working.version)">停止工作</button></div>
      <InteractionCard v-for="interaction in chat.snapshot?.interactions ?? []" :key="`${interaction.interactionId}:${interaction.version}`" :interaction="interaction" @refresh="chat.refreshSnapshot()" />
      <section v-for="failure in chat.snapshot?.failures ?? []" :key="failure.failureId" class="conversation-notice" :class="{ 'is-recovering': failure.status === 'RETRYING' }" role="status"><p>{{ failure.safeMessage }}</p><button v-if="failure.allowedActions.includes('RETRY') && failure.executionId && failure.expectedVersion !== null" class="text-button" :disabled="!!chat.controlling" @click="chat.retry(failure.executionId, failure.expectedVersion)">重新回答</button><small v-if="failure.status !== 'RETRYING' && !failure.allowedActions.length && failure.category !== 'EXTERNAL_UNKNOWN'">内容已保留，你可以继续交流。<span class="diagnostic-reference">问题编号 {{ failure.diagnosticId.slice(0, 8) }}</span></small></section>
      <OperationCard v-for="id in chat.snapshot?.operationIds ?? []" :key="id" :operation-id="id" :revision="chat.snapshot?.cursor ?? ''" :artifacts="allFiles" @refresh="chat.refreshSnapshot()" />
      <section v-if="chat.snapshot?.deliverables.length" class="delivery-files" aria-label="已交付文件"><h2>文件已就绪</h2><button v-for="file in chat.snapshot.deliverables" :key="file.artifactId" class="message-file" :disabled="downloading === file.artifactId" @click="download(file)"><span aria-hidden="true">▤</span>{{ file.fileName }}<span class="file-download" aria-hidden="true">↓</span></button></section>
      <p v-if="downloadError" class="chat-inline-error" role="alert">{{ downloadError }}</p>
      <p v-if="chat.actionError" class="chat-inline-error" role="alert">{{ chat.actionError }}</p>
      <p v-if="chat.error" class="chat-inline-error" role="alert">{{ chat.error }} <button v-if="!chat.unavailable" class="text-button" @click="chat.refreshSnapshot()">重新读取</button></p>
      <div v-if="chat.activeConversationId && chat.connection === 'offline' && !chat.unavailable" class="connection-notice" role="status">连接暂时中断，后台可能仍在处理。正在重新连接…</div>
    </div>
  </div>
</template>
