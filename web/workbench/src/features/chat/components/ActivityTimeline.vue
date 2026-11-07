<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { ExecutionActivity } from '../../../shared/api/activities'
const props = defineProps<{ activities: ExecutionActivity[]; completed: boolean }>()
const expanded = ref(!props.completed)
watch(() => props.completed, value => { expanded.value = !value })
const labels = { PREPARATION: '准备资料', PLANNING: '安排任务', EXECUTION: '执行处理', VERIFICATION: '检查结果', DELIVERY: '汇总交付' } as const
const states = { QUEUED: '等待开始', RUNNING: '进行中', WAITING: '等待处理', SUCCEEDED: '已完成', FAILED: '未完成', CANCELLED: '已停止', UNKNOWN: '状态待核对' } as const
const waits = { CAPACITY: '等待执行空位', DEPENDENCY: '等待前一步完成', INPUT: '等待你补充信息', APPROVAL: '等待你确认', EXTERNAL_RESULT: '等待操作结果核实', RESUME_READY: '等待继续', RETRY_BACKOFF: '等待重试', EVENT_RECOVERY: '等待恢复核对' } as const
const groups = computed(() => Object.entries(labels).map(([key, label]) => ({ label,
  items: props.activities.filter(a => a.group === key).sort((a, b) => (a.startedAt ?? a.endedAt ?? '').localeCompare(b.startedAt ?? b.endedAt ?? '')),
})).filter(group => group.items.length))
const summary = computed(() => {
  const current = props.activities.filter(a => ['RUNNING', 'WAITING', 'QUEUED'].includes(a.status))
  if (!current.length) return '处理过程'
  return [...new Set(current.map(a => a.status === 'WAITING' && a.waitReason ? waits[a.waitReason] : `${a.summary} · ${states[a.status]}`))].slice(0, 3).join('、')
})
function toggled(event: Event) { expanded.value = (event.currentTarget as HTMLDetailsElement).open }
</script>
<template>
  <details v-if="activities.length" class="task-steps activity-timeline" :open="expanded" aria-label="处理过程" @toggle="toggled">
    <summary>{{ summary }}<span>{{ activities.length }} 项活动</span></summary>
    <template v-for="group in groups" :key="group.label">
      <p class="task-steps-note">{{ group.label }}</p>
      <ol>
        <li v-for="activity in group.items" :key="activity.activityId" :class="{ finished: activity.status === 'SUCCEEDED' }">
          <span class="task-step-dot" aria-hidden="true">{{ activity.status === 'SUCCEEDED' ? '✓' : '·' }}</span>
          <div><span class="task-step-title">{{ activity.summary }}</span><small>{{ activity.status === 'WAITING' && activity.waitReason ? waits[activity.waitReason] : states[activity.status] }}</small></div>
        </li>
      </ol>
    </template>
  </details>
</template>
