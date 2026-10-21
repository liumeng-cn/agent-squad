<script setup lang="ts">
import { computed } from 'vue'
import StatusBadge from '../../../shared/ui/StatusBadge.vue'
import { useRuntimeStore } from '../stores/runtime'
import { statusText } from '../../../shared/lib/taskStatus'

const runtime = useRuntimeStore()

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
    <template v-if="runtime.taskId">
      <div v-if="runtime.goalMessage" class="bubble bubble-user">{{ runtime.goalMessage }}</div>
      <div v-if="progressText" class="progress-line">
        <StatusBadge :status="runtime.status ?? ''" />
        <span>{{ progressText }}</span>
      </div>
      <ul v-if="milestones.length" class="event-log">
        <li v-for="item in milestones" :key="item.sequence">{{ item.text }}</li>
      </ul>
      <div v-if="runtime.answer?.completed && runtime.answer.text" class="bubble bubble-assistant">
        {{ runtime.answer.text }}
      </div>
      <p v-if="runtime.streamError" class="banner-warning" role="alert">{{ runtime.streamError }}</p>
    </template>
    <slot v-else />
  </div>
</template>
