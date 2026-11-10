<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import type { ExecutionActivity } from '../../../shared/api/activities'
const props = defineProps<{ activities: ExecutionActivity[]; completed: boolean }>()
const PAGE_SIZE = 20
const expanded = ref(!props.completed)
const windowSize = ref(PAGE_SIZE)
const scroller = ref<HTMLElement>()
// 用户停留在列表底部时，新活动到达后继续跟随；向上翻看时保持当前位置。
const stick = ref(true)
const labels = { PREPARATION: '准备资料', PLANNING: '安排任务', EXECUTION: '执行处理', VERIFICATION: '检查结果', DELIVERY: '汇总交付' } as const
const states = { QUEUED: '等待开始', RUNNING: '进行中', WAITING: '等待处理', SUCCEEDED: '已完成', FAILED: '未完成', CANCELLED: '已停止', UNKNOWN: '状态待核对' } as const
const waits = { CAPACITY: '等待执行空位', DEPENDENCY: '等待前一步完成', INPUT: '等待你补充信息', APPROVAL: '等待你确认', EXTERNAL_RESULT: '等待操作结果核实', RESUME_READY: '等待继续', RETRY_BACKOFF: '等待重试', EVENT_RECOVERY: '等待恢复核对' } as const
const ordered = computed(() => (Object.keys(labels) as (keyof typeof labels)[]).flatMap(key =>
  props.activities.filter(activity => activity.group === key)
    .sort((a, b) => (a.startedAt ?? a.endedAt ?? '').localeCompare(b.startedAt ?? b.endedAt ?? ''))
    .map(activity => ({ label: labels[key], activity })),
))
const shown = computed(() => {
  const runs: { label: string; items: ExecutionActivity[] }[] = []
  for (const { label, activity } of ordered.value.slice(Math.max(0, ordered.value.length - windowSize.value))) {
    const last = runs[runs.length - 1]
    if (last?.label === label) last.items.push(activity)
    else runs.push({ label, items: [activity] })
  }
  return runs
})
const earlier = computed(() => ordered.value.length - shown.value.reduce((count, group) => count + group.items.length, 0))
const summary = computed(() => {
  const current = props.activities.filter(a => ['RUNNING', 'WAITING', 'QUEUED'].includes(a.status))
  if (!current.length) return '处理过程'
  return [...new Set(current.map(a => a.status === 'WAITING' && a.waitReason ? waits[a.waitReason] : `${a.summary} · ${states[a.status]}`))].slice(0, 3).join('、')
})
function scrollToLatest() {
  void nextTick(() => {
    const element = scroller.value
    if (element && expanded.value) element.scrollTop = element.scrollHeight
  })
}
function scrolled(event: Event) {
  const element = event.currentTarget as HTMLElement
  stick.value = element.scrollHeight - element.scrollTop - element.clientHeight < 24
  if (element.scrollTop > 24 || !earlier.value) return
  // 加载更早一页后补回新增高度，视线停在原位置。
  const previousHeight = element.scrollHeight
  windowSize.value += PAGE_SIZE
  void nextTick(() => {
    if (scroller.value) scroller.value.scrollTop += scroller.value.scrollHeight - previousHeight
  })
}
function toggled(event: Event) {
  expanded.value = (event.currentTarget as HTMLDetailsElement).open
  if (expanded.value) scrollToLatest()
}
onMounted(() => { if (expanded.value) scrollToLatest() })
watch(() => props.completed, value => { expanded.value = !value; if (!value) scrollToLatest() })
watch(() => ordered.value.length, (length, previous) => {
  if (length <= previous) return
  if (stick.value) scrollToLatest()
  else windowSize.value = Math.min(length, windowSize.value + length - previous)
})
// 切换到另一批活动（如历史计划版本）时重新从最新一页开始。
watch(() => props.activities[0]?.activityId, () => {
  windowSize.value = PAGE_SIZE
  stick.value = true
  scrollToLatest()
})
</script>
<template>
  <details v-if="activities.length" class="task-steps activity-timeline" :open="expanded" aria-label="处理过程" @toggle="toggled">
    <summary>{{ summary }}<span>{{ activities.length }} 项活动</span></summary>
    <div ref="scroller" class="activity-timeline-scroll" @scroll="scrolled">
      <p v-if="earlier" class="activity-timeline-earlier">向上滚动查看更早的 {{ earlier }} 项</p>
      <template v-for="group in shown" :key="group.label">
        <p class="task-steps-note activity-timeline-group">{{ group.label }}</p>
        <ol>
          <li v-for="activity in group.items" :key="activity.activityId" :class="{ finished: activity.status === 'SUCCEEDED' }">
            <span class="task-step-dot" aria-hidden="true">{{ activity.status === 'SUCCEEDED' ? '✓' : '·' }}</span>
            <span class="task-step-title" :title="activity.summary">{{ activity.summary }}</span>
            <small v-if="activity.status !== 'SUCCEEDED'">{{ activity.status === 'WAITING' && activity.waitReason ? waits[activity.waitReason] : states[activity.status] }}</small>
          </li>
        </ol>
      </template>
    </div>
  </details>
</template>
