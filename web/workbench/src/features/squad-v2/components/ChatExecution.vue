<script setup lang="ts">
import { computed, ref } from 'vue'
import type { ChatMessage, ChatStatus } from '../chatTypes'
const props = defineProps<{ message: ChatMessage; now: number }>()
// undefined 表示用户尚未操作；自动展开运行中的过程，完成后收起。
const preference = ref<boolean>()
const expanded = computed(() => preference.value ?? props.message.status === 'RUNNING')
const labels: Record<ChatStatus, string> = {
  RUNNING: '执行中', COMPLETED: '已完成', FAILED: '失败', WAITING: '等待交互', INTERRUPTED: '已中断',
}
const duration = computed(() => Math.max(0, ((props.message.finishedAt ?? props.now) - props.message.startedAt) / 1000).toFixed(1))
const current = computed(() => [...props.message.activities].reverse().find(item => item.status === 'RUNNING'))
</script>

<template>
  <section class="execution" aria-label="执行过程">
    <button class="summary" :aria-expanded="expanded" @click="preference = !expanded">
      <span :class="['indicator', { running: message.status === 'RUNNING' }]" />
      <span>{{ message.status === 'RUNNING' ? (current?.title || '正在处理') : labels[message.status] }}</span>
      <span class="duration">{{ duration }} 秒</span>
      <span :class="['chevron', { expanded }]" aria-hidden="true">
        <svg viewBox="0 0 24 24" fill="none"><path d="m7 10 5 5 5-5" /></svg>
      </span>
    </button>
    <div v-if="expanded" class="activities">
      <p v-if="!message.activities.length" class="empty">{{ message.status === 'RUNNING' ? '已提交，等待 Agent 开始执行…' : '暂无可展示的过程事件' }}</p>
      <div v-for="activity in message.activities" :key="activity.id" class="activity">
        <span :class="['activity-dot', activity.status.toLowerCase()]" />
        <span class="title">{{ activity.title }}</span>
        <span class="state">{{ labels[activity.status] }}</span>
      </div>
    </div>
  </section>
</template>

<style scoped>
.execution { margin: 10px 0 20px; color: #83857d; font-size: 13px; }
.summary { display: flex; align-items: center; gap: 8px; min-height: 24px; line-height: 20px; padding: 0; background: none; border: 0; color: inherit; font: inherit; cursor: pointer; }
.indicator, .activity-dot { width: 6px; height: 6px; border-radius: 50%; background: #aaa; flex-shrink: 0; }
.running { background: #657c5a; animation: pulse 1.3s ease-in-out infinite; }
.duration { color: #a2a499; margin-left: 6px; font-variant-numeric: tabular-nums; }
.chevron { display: inline-flex; align-items: center; justify-content: center; width: 16px; height: 16px; flex: 0 0 16px; margin-left: 2px; transition: transform .16s ease; }
.chevron svg { display: block; width: 14px; height: 14px; stroke: currentColor; stroke-width: 1.7; stroke-linecap: round; stroke-linejoin: round; }
.chevron.expanded { transform: rotate(180deg); }
.activities { margin: 14px 0 0 3px; padding-left: 18px; border-left: 1px solid #e8e9e4; }
.activity { display: flex; align-items: center; line-height: 20px; gap: 10px; min-height: 32px; }
.title { overflow-wrap: anywhere; }
.state { margin-left: auto; color: #a2a499; white-space: nowrap; font-size: 12px; }
.completed { background: #84977b; }
.failed { background: #ba7770; }
.empty { padding: 4px 0; color: #a2a499; }
@keyframes pulse { 50% { opacity: .3; } }
@media (prefers-reduced-motion: reduce) { .running { animation: none; } .chevron { transition: none; } }
</style>
