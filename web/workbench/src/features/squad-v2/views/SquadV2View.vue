<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import SafeMarkdown from '../../../shared/ui/SafeMarkdown.vue'
import SquadComposer from '../components/SquadComposer.vue'
import SquadEventTimeline from '../components/SquadEventTimeline.vue'
import { useSquadEventStream } from '../composables/useSquadEventStream'
import { useSquadExecution } from '../composables/useSquadExecution'

const execution = useSquadExecution()
const stream = useSquadEventStream(execution.applyEvent)
const lastPrompt = ref('')
const conversationScroller = ref<HTMLElement | null>(null)
const followLatest = ref(true)

const statusLabel = computed(() => {
  const labels = {
    IDLE: '等待开始',
    CONNECTING: '正在连接',
    STREAMING: '正在接收事件',
    COMPLETED: '事件流已结束',
    DISCONNECTED: '连接已断开',
    ERROR: '连接异常',
  } as const
  return labels[stream.status.value]
})

const showJumpToLatest = computed(() =>
  !followLatest.value && (execution.timeline.value.length > 0 || Boolean(execution.answerText.value)),
)

function onConversationScroll(): void {
  const element = conversationScroller.value
  if (!element) return
  followLatest.value = element.scrollHeight - element.scrollTop - element.clientHeight < 80
}

function scrollToLatest(smooth = false): void {
  const element = conversationScroller.value
  if (!element) return
  followLatest.value = true
  if (smooth) element.scrollTo({ top: element.scrollHeight, behavior: 'smooth' })
  else element.scrollTop = element.scrollHeight
}

watch(
  () => [execution.timeline.value.length, execution.answerText.value, stream.status.value],
  async () => {
    if (!followLatest.value) return
    await nextTick()
    scrollToLatest()
  },
  { flush: 'post' },
)

async function runTest(message: string): Promise<void> {
  stream.reset()
  execution.reset()
  lastPrompt.value = message
  followLatest.value = true
  await nextTick()
  await stream.start({ intervalMillis: 300 })
}

function disconnect(): void {
  // 断开浏览器连接不会取消服务端 Agent；这里只停止当前 SSE 接收。
  stream.disconnect()
}

onBeforeUnmount(() => {
  stream.disconnect()
})
</script>

<template>
  <main class="squad-v2-page">
    <header class="topbar">
      <div class="brand">
        <span class="brand-mark">S</span>
        <div>
          <strong>智能协作</strong>
          <small>Squad V2 · SSE 事件联调</small>
        </div>
      </div>
      <div class="run-status">
        <span class="status-dot" :class="{ live: stream.isBusy.value }"></span>
        {{ statusLabel }}
      </div>
    </header>

    <section ref="conversationScroller" class="conversation" @scroll="onConversationScroll">
      <div class="conversation-content">
        <div v-if="!lastPrompt && !execution.timeline.value.length && !execution.answerText.value" class="welcome">
          <div class="welcome-mark">✳</div>
          <h1>Squad 事件工作台</h1>
          <p>使用预设模拟事件验证 SSE 接收、Agent 归属、文本增量、工具调用和最终结果。</p>
        </div>

        <div v-else class="timeline-content">
          <article v-if="lastPrompt" class="user-message">
            <span class="avatar">U</span>
            <div>
              <small>测试输入</small>
              <p>{{ lastPrompt }}</p>
            </div>
          </article>

          <section class="execution-section">
            <header class="section-heading">
              <strong>执行事件</strong>
              <span>{{ execution.timeline.value.length }} 条事件 · {{ execution.agents.value.length }} 个 Agent</span>
            </header>
            <SquadEventTimeline
              :items="execution.timeline.value"
              :streaming="stream.isBusy.value"
            />
          </section>

          <section v-if="execution.answerText.value" class="answer-section">
            <header class="section-heading">
              <strong>主 Agent 回答</strong>
              <span>{{ execution.hasFinalAnswer.value ? '最终结果' : '流式输出' }}</span>
            </header>
            <SafeMarkdown
              :text="execution.answerText.value"
              :streaming="stream.isBusy.value && !execution.hasFinalAnswer.value"
            />
          </section>

          <p
            v-if="stream.status.value === 'COMPLETED' && execution.agents.value.some(agent => agent.status === 'RUNNING')"
            class="execution-note"
          >事件流已经结束，但仍有 Agent 没有收到 AGENT_END。连接结束不代表 Agent 执行完成。</p>

          <p
            v-if="stream.status.value === 'DISCONNECTED'"
            class="execution-note"
          >当前 SSE 连接已断开；这不会取消服务端 Agent 的执行。</p>
        </div>
      </div>

      <button
        v-if="showJumpToLatest"
        type="button"
        class="jump-latest"
        @click="scrollToLatest(true)"
      >回到最新 ↓</button>
    </section>

    <p v-if="stream.error.value" class="error-message">{{ stream.error.value }}</p>

    <SquadComposer
      :busy="stream.isBusy.value"
      @submit="runTest"
      @disconnect="disconnect"
    />
  </main>
</template>

<style scoped>
.squad-v2-page{height:100vh;height:100dvh;min-height:560px;display:flex;flex-direction:column;background:var(--surface,#fff);color:var(--text-primary,#202124)}
.topbar{height:60px;flex:none;border-bottom:1px solid var(--border,#e9e9e9);display:flex;align-items:center;justify-content:space-between;padding:0 28px}
.brand{display:flex;align-items:center;gap:10px}
.brand-mark{width:29px;height:29px;border-radius:9px;background:#242424;color:#fff;display:grid;place-items:center;font-size:13px;font-weight:700}
.brand strong{display:block;font-size:13px;font-weight:650}
.brand small{display:block;color:#999;font-size:10px;margin-top:2px}
.run-status{display:flex;align-items:center;gap:8px;color:#888;font-size:11px}
.status-dot{width:7px;height:7px;border-radius:50%;background:#aaa}
.status-dot.live{background:#6d9d83;box-shadow:0 0 0 3px #6d9d8318}
.conversation{position:relative;width:min(900px,calc(100% - 40px));margin:0 auto;flex:1;min-height:0;overflow:auto;padding:26px 0 24px;box-sizing:border-box;overscroll-behavior:contain;scrollbar-gutter:stable}
.conversation-content{min-height:100%;display:flex;flex-direction:column}
.welcome{min-height:100%;display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center;padding:30px;box-sizing:border-box}
.welcome-mark{font-size:31px;width:58px;height:58px;border-radius:19px;background:#f2f2f2;display:grid;place-items:center}
.welcome h1{font-size:24px;font-weight:600;margin:19px 0 8px;letter-spacing:-.4px}
.welcome p{max-width:540px;color:#858585;font-size:13px;line-height:1.8}
.timeline-content{display:flex;flex-direction:column;gap:17px}
.user-message{display:flex;justify-content:flex-end;gap:10px;margin-bottom:2px}
.user-message>div{background:#f3f3f3;padding:10px 14px;border-radius:15px;max-width:72%}
.user-message small{display:block;font-size:10px;color:#888}
.user-message p{font-size:13px;line-height:1.6;margin:4px 0 0;white-space:pre-wrap;overflow-wrap:anywhere}
.avatar{width:27px;height:27px;display:grid;place-items:center;border-radius:50%;font-size:11px;flex:none;background:#dedede}
.execution-section,.answer-section{display:flex;flex-direction:column;gap:10px}
.section-heading{display:flex;align-items:center;justify-content:space-between;gap:10px}
.section-heading strong{font-size:13px;font-weight:650;color:var(--text-primary,#444)}
.section-heading span{font-size:10px;color:#999}
.answer-section{padding:13px 14px;border:1px solid var(--border,#e9e9e9);border-radius:11px;background:var(--surface,#fff)}
.answer-section :deep(.answer-markdown){font-size:14px;line-height:1.75;overflow-wrap:anywhere}
.answer-section :deep(.answer-markdown p){margin:4px 0}
.execution-note{margin:0;padding:9px 11px;border-radius:8px;background:#faf7ed;color:#897849;font-size:11px;line-height:1.6}
.jump-latest{position:sticky;bottom:12px;align-self:center;display:flex;align-items:center;gap:7px;margin:10px auto 0;border:1px solid #e8e8e8;border-radius:18px;padding:7px 12px;background:#ffffffed;color:#777;font-size:11px;box-shadow:0 2px 10px #0000000a;cursor:pointer;backdrop-filter:blur(8px)}
.jump-latest:hover{color:#444;border-color:#dcdcdc}
.error-message{color:#b42318;text-align:center;font-size:12px;margin:0 20px 8px}
@media(max-width:600px){.topbar{padding:0 16px}.conversation{width:calc(100% - 28px);padding-top:18px}.section-heading{align-items:flex-start}.section-heading span{white-space:nowrap}}
</style>
