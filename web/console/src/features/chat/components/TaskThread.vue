<script setup lang="ts">
import { computed } from 'vue'
import type { TaskSnapshot } from '../../../shared/api/types'
import StatusBadge from '../../../shared/ui/StatusBadge.vue'
import { useRuntimeStore } from '../stores/runtime'
import { statusText } from '../../../shared/lib/taskStatus'

const runtime = useRuntimeStore()
const props = defineProps<{ history: TaskSnapshot[] }>()

const previousTasks = computed(() =>
  props.history
    .filter((snapshot) => snapshot.task.taskId !== runtime.taskId)
    .slice()
    .reverse(),
)

const EVENT_TEXT: Record<string, string> = {
  'task.accepted': '目标已受理',
  'task.run_started': '开始执行',
  'task.run_ended': '本轮执行结束',
  'answer.completed': '答案已生成',
  'task.completed': '目标已收尾',
}

const milestones = computed(() =>
  runtime.events
    .filter((event) => EVENT_TEXT[event.type])
    .map((event) => ({ sequence: event.sequence, text: EVENT_TEXT[event.type] })),
)

const progressText = computed(() => {
  if (runtime.status === 'CANCELLING') {
    return '正在停止并核对已发操作…'
  }
  if (runtime.status && !runtime.terminal) {
    return `${statusText(runtime.status)}${runtime.streaming ? '' : '（事件连接已断开，正在恢复…）'}`
  }
  return ''
})
</script>

<template>
  <div class="thread">
    <article v-for="snapshot in previousTasks" :key="snapshot.task.taskId" class="task-exchange">
      <div class="message-row message-row-user">
        <span class="message-label">你</span>
        <div class="bubble bubble-user">{{ snapshot.goalMessage }}</div>
      </div>
      <div class="message-row message-row-assistant">
        <span class="message-label">Agent Squad <StatusBadge :status="snapshot.task.status" /></span>
        <div v-if="snapshot.answer.completed" class="bubble bubble-assistant">
          {{ snapshot.answer.text }}
        </div>
        <p v-else class="result-placeholder">该目标尚无最终答案，可打开详情查看状态。</p>
        <router-link class="task-detail-link" :to="{ name: 'task', params: { taskId: snapshot.task.taskId } }">
          查看任务详情 ↗
        </router-link>
      </div>
    </article>
    <template v-if="runtime.taskId">
      <div v-if="runtime.goalMessage" class="message-row message-row-user">
        <span class="message-label">你</span>
        <div class="bubble bubble-user">{{ runtime.goalMessage }}</div>
      </div>
      <div v-if="progressText" class="progress-line">
        <StatusBadge :status="runtime.status ?? ''" />
        <span>{{ progressText }}</span>
      </div>
      <ul v-if="milestones.length" class="event-log">
        <li v-for="item in milestones" :key="item.sequence">{{ item.text }}</li>
      </ul>
      <div v-if="runtime.answer?.completed && runtime.answer.text" class="message-row message-row-assistant">
        <span class="message-label">Agent Squad <StatusBadge :status="runtime.status ?? ''" /></span>
        <div class="bubble bubble-assistant">{{ runtime.answer.text }}</div>
      </div>
      <p v-if="runtime.streamError" class="banner-warning" role="alert">{{ runtime.streamError }}</p>
    </template>
    <slot v-if="!runtime.taskId && previousTasks.length === 0" />
  </div>
</template>
