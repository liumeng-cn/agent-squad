<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useConversationsStore } from '../stores/conversations'
import SafeMarkdown from '../../../shared/ui/SafeMarkdown.vue'
import InteractionCard from '../../tasks/components/InteractionCard.vue'
import OperationCard from './OperationCard.vue'
import TaskSteps from './TaskSteps.vue'
import { apiDownload, errorMessage } from '../../../shared/api/http'
import type { TaskDirectiveView, ExecutionFailure } from '../../../shared/api/chatApi'
import type { Artifact } from '../../../shared/api/taskDetailsApi'
import { statusText } from '../../../shared/lib/taskStatus'
const chat = useConversationsStore()
const scroller = ref<HTMLElement | null>(null)
const pinned = ref(true)
let resizeObserver: ResizeObserver | undefined
let scrollFrame: number | undefined
const downloadError = ref('')
const downloading = ref<string | null>(null)
const copied = ref<string | null>(null)
const allFiles = computed(() => [...new Map([...chat.messages.flatMap(message => message.artifacts), ...(chat.snapshot?.deliverables ?? [])].map(file => [file.artifactId, file])).values()])
// 失败属于执行反馈，不再把系统失败占位正文同时当作助手答案展示。
const visibleMessages = computed(() => chat.messages.filter(message => {
  if (message.role !== 'ASSISTANT' || !message.executionId) return true
  return !chat.snapshot?.failures.some(failure => failure.executionId === message.executionId)
    || !chat.executionHistory.some(execution => execution.executionId === message.executionId && execution.status === 'FAILED')
}))
// 预览与终稿共用同一回答节点，完成事件只替换内容，不拆掉整段Markdown再建一遍。
const threadMessages = computed(() => [
  ...visibleMessages.value.map(message => ({ ...message, isPreview: false,
    renderKey: message.role === 'ASSISTANT' && message.executionId ? `reply:${message.executionId}` : message.messageId })),
  ...Object.values(chat.previews).map(preview => ({ messageId: `preview:${preview.executionId}`, sequence: Number.MAX_SAFE_INTEGER,
    role: 'ASSISTANT' as const, text: preview.text, revision: preview.revision, completed: false,
    sourceMessageId: preview.sourceMessageId, executionId: preview.executionId, taskId: null, occurredAt: '', artifacts: [],
    isPreview: true, renderKey: `reply:${preview.executionId}` })),
])
function failureText(failure: ExecutionFailure) {
  if (failure.status === 'RETRYING' || failure.actor !== 'SYSTEM' || failure.category === 'EXTERNAL_UNKNOWN') return failure.safeMessage
  if (failure.taskId) return '这项工作未能完成，已保留你的要求和已完成内容。'
  return '回答服务暂时异常，未能完成这次回答。你的消息已保留。'
}
function failuresFor(messageId: string) {
  return (chat.snapshot?.failures ?? []).filter(failure => failure.sourceMessageId === messageId)
}
// 找不到来源消息的失败没有可依附位置，仍保留在线程末尾，避免被误认为已处理。
const orphanFailures = computed(() => (chat.snapshot?.failures ?? []).filter(failure =>
  !failure.sourceMessageId || !chat.messages.some(message => message.messageId === failure.sourceMessageId)))
function directiveText(directive: TaskDirectiveView) {
  if (directive.status === 'APPLIED') return '已应用你的修改，后续按新要求处理'
  if (directive.status === 'REJECTED') return `这次修改未应用：${directive.reasonMessage || '请确认当前要求后再补充'}`
  const waiting = {
    NONE: '已收到修改，等待处理',
    WAITING_SAFE_POINT: '已收到修改，等待合适的处理时机',
    WAITING_OPERATION: '已收到修改，正在等待原操作结果，尚未应用',
    WAITING_USER: '已收到修改，请先处理待确认事项，尚未应用',
    WAITING_COORDINATOR: '已收到修改，等待当前处理结束后确认',
    COORDINATING: '修改已排队，等待确认如何应用',
  }
  return waiting[directive.waitReason]
}
function originalText(failure: ExecutionFailure) {
  return chat.messages.find(message => message.messageId === failure.sourceMessageId && message.role === 'USER')?.text
}
const working = computed(() => chat.snapshot?.activeTask)
const stepTasks = computed(() => {
  const tasks = new Map(chat.taskHistory.map(task => [task.taskId, { taskId: task.taskId, status: task.status, sourceMessageId: task.sourceMessageId }]))
  if (working.value) tasks.set(working.value.taskId, { taskId: working.value.taskId, status: working.value.status, sourceMessageId: tasks.get(working.value.taskId)?.sourceMessageId ?? null })
  return [...tasks.values()].map(task => {
    const source = visibleMessages.value.find(message => message.messageId === task.sourceMessageId)
    const answer = visibleMessages.value.find(message => message.role === 'ASSISTANT' && message.taskId === task.taskId)
    return { ...task, afterMessageId: source?.messageId, beforeMessageId: source ? undefined : answer?.messageId,
      answerCompleted: !!answer?.completed }
  })
})
const workText = computed(() => {
  if (working.value?.status === 'RECOVERY_BLOCKED') return '这项工作暂未完成，已保留当前内容'
  return statusText(working.value?.status ?? '')
})
function onScroll() { const node = scroller.value; if (node) pinned.value = node.scrollHeight - node.scrollTop - node.clientHeight < 110 }
function followOutput() {
  if (!pinned.value || scrollFrame !== undefined || typeof requestAnimationFrame === 'undefined') return
  scrollFrame = requestAnimationFrame(() => {
    scrollFrame = undefined
    if (pinned.value && scroller.value) scroller.value.scrollTop = scroller.value.scrollHeight
  })
}
onMounted(() => {
  const content = scroller.value?.querySelector('.chat-messages')
  if (content && typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(followOutput)
    resizeObserver.observe(content)
  }
})
onBeforeUnmount(() => { resizeObserver?.disconnect(); if (scrollFrame !== undefined) cancelAnimationFrame(scrollFrame) })
watch(() => chat.activeConversationId, () => { pinned.value = true; downloadError.value = '' })
watch(() => [chat.messages.length, chat.snapshot?.cursor, chat.loading], async () => { await nextTick(); followOutput() })
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
      <template v-for="message in threadMessages" :key="message.renderKey">
      <TaskSteps v-for="task in stepTasks.filter(item => item.beforeMessageId === message.messageId)" :key="task.taskId" :task-id="task.taskId" :status="task.status" :answer-completed="task.answerCompleted" :revision="chat.snapshot?.cursor ?? ''" />
      <article class="chat-message" :class="message.role === 'USER' ? 'from-user' : 'from-assistant'" :aria-label="message.isPreview ? '正在生成回答' : undefined">
        <template v-if="message.role === 'USER'">
          <div class="user-message-text">{{ message.text }}</div>
          <small v-for="directive in chat.directiveHistory.filter(item => item.sourceMessageId === message.messageId)" :key="directive.directiveId" class="stopped-note" role="status">{{ directiveText(directive) }}</small>
          <small v-if="chat.executionHistory.some(item => item.sourceMessageId === message.messageId && item.status === 'CANCELLED')" class="stopped-note">回答已停止</small>
          <small v-if="chat.taskHistory.some(item => item.sourceMessageId === message.messageId && item.status === 'CANCELLED')" class="stopped-note">这项工作已停止</small>
          <div v-if="message.artifacts.length" class="message-files"><button v-for="file in message.artifacts" :key="file.artifactId" class="message-file" :disabled="downloading === file.artifactId" @click="download(file)"><span aria-hidden="true">▤</span>{{ file.fileName }}<span class="file-download" aria-hidden="true">↓</span></button></div>
          <section v-for="failure in failuresFor(message.messageId)" :key="failure.failureId" class="chat-service-feedback" role="status">
            <p>{{ failureText(failure) }}</p>
            <button v-if="failure.allowedActions.includes('RETRY') && failure.executionId && failure.expectedVersion !== null" class="text-button" :disabled="!!chat.controlling" @click="chat.retry(failure.executionId, failure.expectedVersion)">{{ chat.controlling === failure.executionId ? '正在重试…' : '重新尝试' }}</button>
            <button v-if="failure.status !== 'RETRYING' && originalText(failure)" class="text-button" @click="copy(failure.failureId, originalText(failure)!)">{{ copied === failure.failureId ? '已复制' : '复制原消息' }}</button>
          </section>
        </template>
        <template v-else>
          <span class="assistant-avatar" aria-hidden="true">✦</span><div class="assistant-body"><SafeMarkdown :text="message.text" :streaming="message.isPreview" /><small v-if="message.isPreview" class="stopped-note">{{ message.text ? '正在生成…' : '正在思考…' }}</small><button v-else class="copy-answer-button" :aria-label="copied === message.messageId ? '已复制回答' : '复制回答'" @click="copy(message.messageId, message.text)">{{ copied === message.messageId ? '已复制' : '复制' }}</button></div>
        </template>
      </article>
      <TaskSteps v-for="task in stepTasks.filter(item => item.afterMessageId === message.messageId)" :key="task.taskId" :task-id="task.taskId" :status="task.status" :answer-completed="task.answerCompleted" :revision="chat.snapshot?.cursor ?? ''" />
      </template>
      <div v-for="execution in chat.snapshot?.activeExecutions ?? []" :key="execution.executionId" class="answer-state" role="status"><span class="state-indicator" :class="{ running: ['RUNNING', 'RECOVERING'].includes(execution.status) }"></span>{{ answerStatus(execution.status) }}</div>
      <div v-if="working" class="active-work-bar" role="status"><div><span class="work-mark" aria-hidden="true">✦</span><span>{{ workText }}</span><small v-if="!['CANCELLING', 'RECONCILING'].includes(working.status)">你可以继续在这里补充或提问</small></div><button v-if="working.status !== 'CANCELLING'" class="text-button" :disabled="!!chat.controlling" @click="chat.stop('task', working.taskId, working.version)">停止工作</button></div>
      <InteractionCard v-for="interaction in chat.snapshot?.interactions ?? []" :key="`${interaction.interactionId}:${interaction.version}`" :interaction="interaction" @refresh="chat.refreshSnapshot()" />
      <TaskSteps v-for="task in stepTasks.filter(item => !item.afterMessageId && !item.beforeMessageId)" :key="task.taskId" :task-id="task.taskId" :status="task.status" :answer-completed="task.answerCompleted" :revision="chat.snapshot?.cursor ?? ''" />
      <section v-for="failure in orphanFailures" :key="failure.failureId" class="chat-service-feedback" role="status">
        <p>{{ failureText(failure) }}</p>
        <button v-if="failure.allowedActions.includes('RETRY') && failure.executionId && failure.expectedVersion !== null" class="text-button" :disabled="!!chat.controlling" @click="chat.retry(failure.executionId, failure.expectedVersion)">{{ chat.controlling === failure.executionId ? '正在重试…' : '重新尝试' }}</button>
        <button v-if="failure.status !== 'RETRYING' && originalText(failure)" class="text-button" @click="copy(failure.failureId, originalText(failure)!)">{{ copied === failure.failureId ? '已复制' : '复制原消息' }}</button>
      </section>
      <OperationCard v-for="id in chat.snapshot?.operationIds ?? []" :key="id" :operation-id="id" :revision="chat.snapshot?.cursor ?? ''" :artifacts="allFiles" @refresh="chat.refreshSnapshot()" />
      <section v-if="chat.snapshot?.deliverables.length" class="delivery-files" aria-label="已交付文件"><h2>文件已就绪</h2><button v-for="file in chat.snapshot.deliverables" :key="file.artifactId" class="message-file" :disabled="downloading === file.artifactId" @click="download(file)"><span aria-hidden="true">▤</span>{{ file.fileName }}<span class="file-download" aria-hidden="true">↓</span></button></section>
      <p v-if="downloadError" class="chat-inline-error" role="alert">{{ downloadError }}</p>
      <p v-if="chat.actionError" class="chat-inline-error" role="alert">{{ chat.actionError }}</p>
      <p v-if="chat.error && (chat.connection !== 'offline' || chat.unavailable)" class="chat-inline-error" role="alert">{{ chat.error }} <button v-if="!chat.unavailable" class="text-button" @click="chat.refreshSnapshot()">重新读取</button></p>
      <div v-if="chat.activeConversationId && chat.connection === 'offline' && !chat.unavailable" class="connection-notice" role="status">连接暂时中断，后台可能仍在处理。正在重新连接…</div>
    </div>
  </div>
</template>
