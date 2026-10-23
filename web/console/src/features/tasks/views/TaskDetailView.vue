<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import StatusBadge from '../../../shared/ui/StatusBadge.vue'
import { useRuntimeStore } from '../../chat/stores/runtime'

const route = useRoute()
const runtime = useRuntimeStore()
const taskId = route.params.taskId as string

onMounted(async () => {
  await runtime.observe(taskId, { replayFromStart: true })
})

// 返回工作台时保留订阅交给工作台视图接管；离开应用其他页面才释放。
onBeforeUnmount(() => {
  if (route.name !== 'workspace') {
    runtime.dispose()
  }
})
</script>

<template>
  <main class="detail-page">
    <div class="detail-card">
      <h2>
        任务
        <StatusBadge v-if="runtime.status" :status="runtime.status" />
      </h2>
      <p v-if="runtime.task?.blockedReason" class="banner-warning" role="alert">
        受限原因：{{ runtime.task.blockedReason }}
      </p>
      <button
        v-if="runtime.resumable"
        class="btn btn-primary"
        :disabled="runtime.resuming"
        @click="runtime.resume()"
      >
        {{ runtime.resuming ? '恢复中…' : '恢复执行' }}
      </button>
      <dl v-if="runtime.task" class="meta-grid">
        <div>
          <dt>任务 ID</dt>
          <dd class="mono">{{ runtime.task.taskId }}</dd>
        </div>
        <div>
          <dt>会话 ID</dt>
          <dd class="mono">{{ runtime.task.conversationId }}</dd>
        </div>
        <div>
          <dt>版本</dt>
          <dd>{{ runtime.task.version }}</dd>
        </div>
        <div>
          <dt>计划版本</dt>
          <dd>{{ runtime.task.planVersion }}</dd>
        </div>
        <div>
          <dt>答案版本</dt>
          <dd>{{ runtime.task.answerRevision }}</dd>
        </div>
        <div>
          <dt>创建时间</dt>
          <dd>{{ runtime.task.createdAt ?? '—' }}</dd>
        </div>
        <div>
          <dt>更新时间</dt>
          <dd>{{ runtime.task.updatedAt ?? '—' }}</dd>
        </div>
      </dl>
    </div>

    <div v-if="runtime.goalMessage" class="detail-card">
      <h2>目标</h2>
      <p style="white-space: pre-wrap; margin: 0">{{ runtime.goalMessage }}</p>
    </div>

    <div v-if="runtime.answer?.completed" class="detail-card">
      <h2>最终答案</h2>
      <p style="white-space: pre-wrap; margin: 0">{{ runtime.answer.text }}</p>
    </div>

    <div class="detail-card">
      <h2>公开事件</h2>
      <p v-if="runtime.events.length === 0" style="color: var(--text-secondary)">暂无事件</p>
      <ul v-else class="event-log" style="align-self: stretch">
        <li v-for="event in runtime.events" :key="event.sequence" class="mono">
          #{{ event.sequence }} {{ event.type }} · {{ event.occurredAt }}
        </li>
      </ul>
    </div>

    <p v-if="runtime.streamError" class="banner-warning" role="alert">{{ runtime.streamError }}</p>
  </main>
</template>
