<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import StatusBadge from '../../../shared/ui/StatusBadge.vue'
import { useRuntimeStore } from '../../chat/stores/runtime'
import InteractionCard from '../components/InteractionCard.vue'
import OperationPanel from '../components/OperationPanel.vue'
import { listArtifacts, listInteractions, type Artifact } from '../../../shared/api/taskDetailsApi'
import type { Interaction } from '../../../shared/api/types'
import { apiDownload, errorMessage } from '../../../shared/api/http'

const route = useRoute()
const runtime = useRuntimeStore()
const taskId = route.params.taskId as string
const artifacts = ref<Artifact[]>([])
const artifactError = ref('')
const downloading = ref<string | null>(null)
const interactions = ref<Interaction[]>([])
const interactionCursor = ref<string | undefined>()
const interactionError = ref('')
const interactionStatus: Record<string, string> = { RESPONDED: '已处理', EXPIRED: '已过期', INVALIDATED: '已失效', CANCELLED: '已取消' }
watch(() => runtime.snapshot?.cursor, () => { if (runtime.snapshot) void loadInteractions() })
async function loadInteractions(append = false): Promise<void> {
  try {
    const page = await listInteractions(taskId, append ? interactionCursor.value : undefined)
    interactions.value = append ? [...interactions.value, ...page.items] : page.items
    interactionCursor.value = page.nextCursor ?? undefined
  } catch (cause) { interactionError.value = errorMessage(cause) }
}
async function refreshDetails(): Promise<void> {
  await runtime.refreshSnapshot()
  await loadInteractions()
}
const eventLabels: Record<string, string> = {
  'task.accepted': '目标已接收', 'task.run_started': '协调处理中', 'task.run_ended': '本轮协调结束',
  'plan.published': '协作计划已建立', 'job.run_started': '子任务开始执行', 'job.run_ended': '子任务执行结束',
  'job.reused': '复用已验证的子任务结果', 'job.recovery_blocked': '子任务恢复受限',
  'interaction.requested': '等待补充信息或操作确认', 'interaction.resolved': '交互已处理',
  'operation.unknown': '操作结果待核对', 'task.recovery_blocked': '目标恢复受限',
  'answer.completed': '最终答案已生成', 'answer.replaced': '回答已更新', 'task.completed': '目标已收尾',
}
async function download(file: Artifact): Promise<void> {
  downloading.value = file.artifactId; artifactError.value = ''
  try {
    const blob = await apiDownload(`/api/v1/artifacts/${file.artifactId}/content`)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url; link.download = file.fileName; link.click()
    window.setTimeout(() => URL.revokeObjectURL(url), 1000)
  } catch (cause) { artifactError.value = errorMessage(cause) }
  finally { downloading.value = null }
}
const jobs = computed(() => runtime.snapshot?.jobs ?? [])
const finishedJobs = computed(() => jobs.value.filter((job) =>
  ['SUCCEEDED', 'PARTIAL', 'FAILED', 'CANCELLED', 'SUPERSEDED'].includes(job.status)).length)

function resultText(result: unknown): string {
  return JSON.stringify(result, null, 2)
}

onMounted(async () => {
  await runtime.observe(taskId, { replayFromStart: true })
  try { artifacts.value = await listArtifacts(taskId) }
  catch (cause) { artifactError.value = errorMessage(cause) }
  await loadInteractions()
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
      <router-link v-if="runtime.task" :to="{ name: 'workspace', params: { conversationId: runtime.task.conversationId } }">← 返回对话</router-link>
      <h2>
        任务
        <StatusBadge v-if="runtime.status" :status="runtime.status" />
      </h2>
      <p v-if="runtime.task?.blockedReason" class="banner-warning" role="alert">
        受限原因：{{ runtime.task.blockedReason }}
      </p>
      <button
        v-if="runtime.resumable && !runtime.snapshot?.pending.length"
        class="btn btn-primary"
        :disabled="runtime.resuming"
        @click="runtime.resume()"
      >
        {{ runtime.resuming ? '恢复中…' : '恢复执行' }}
      </button>
      <button v-if="runtime.stoppable" class="btn btn-danger" :disabled="runtime.stopping" @click="runtime.stop()">
        {{ runtime.stopping ? '正在请求停止…' : '停止任务' }}
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

    <div v-if="runtime.answer?.text" class="detail-card">
      <h2>{{ runtime.answer.completed ? '最终答案' : '当前回答' }}</h2>
      <p style="white-space: pre-wrap; margin: 0">{{ runtime.answer.text }}</p>
    </div>

    <div v-if="runtime.snapshot?.pending.length" class="detail-card">
      <InteractionCard v-for="interaction in runtime.snapshot.pending" :key="interaction.interactionId"
        :interaction="interaction" @refresh="refreshDetails" />
    </div>

    <div v-if="runtime.task && runtime.task.planVersion > 0" class="detail-card">
      <h2>当前计划 · 第 {{ runtime.task.planVersion }} 版</h2>
      <p class="detail-muted">{{ finishedJobs }} / {{ jobs.length }} 个子任务已结束</p>
      <p v-if="jobs.length === 0" class="detail-muted">计划节点正在同步…</p>
      <ol v-else class="job-list">
        <li v-for="job in jobs" :key="job.jobId" class="job-card">
          <div class="job-heading">
            <strong>{{ job.nodeKey }}</strong>
            <StatusBadge :status="job.status" />
          </div>
          <p class="detail-muted">{{ job.workerKey }} · {{ job.workerVersion }}</p>
          <p class="mono detail-muted">Job {{ job.jobId }}</p>
          <p v-if="job.reusedFromJobId" class="detail-muted">复用已验证结果 · 来源 Job {{ job.reusedFromJobId }}</p>
          <pre v-if="job.status === 'SUCCEEDED' && job.result != null" class="job-result">{{ resultText(job.result) }}</pre>
        </li>
      </ol>
    </div>

    <OperationPanel v-if="runtime.snapshot" :task-id="taskId" :cursor="runtime.snapshot.cursor" :artifacts="artifacts" @refresh="runtime.refreshSnapshot()" />

    <section v-if="interactions.some(item => item.status !== 'OPEN') || interactionError" class="detail-card">
      <h2>交互记录</h2>
      <ul class="operation-list"><li v-for="item in interactions.filter(entry => entry.status !== 'OPEN')" :key="item.interactionId">
        {{ item.kind === 'APPROVAL' ? '操作确认' : '补充信息' }} · {{ interactionStatus[item.status] ?? item.status }} · {{ item.question }}
      </li></ul>
      <button v-if="interactionCursor" class="btn" @click="loadInteractions(true)">加载更多交互</button>
      <p v-if="interactionError" class="form-error" role="alert">{{ interactionError }}</p>
    </section>

    <section class="detail-card">
      <h2>附件与产物</h2>
      <p v-if="artifacts.length === 0" class="detail-muted">暂无已提交文件</p>
      <ul class="attachment-list">
        <li v-for="file in artifacts" :key="file.artifactId">
          <span>{{ file.fileName }} · {{ file.sizeBytes }} 字节</span>
          <button class="btn btn-sm" :disabled="downloading !== null" @click="download(file)">{{ downloading === file.artifactId ? '下载中…' : '下载' }}</button>
        </li>
      </ul>
      <p v-if="artifactError" class="form-error" role="alert">{{ artifactError }}</p>
    </section>

    <div class="detail-card">
      <h2>进度时间线</h2>
      <p v-if="runtime.events.length === 0" style="color: var(--text-secondary)">暂无事件</p>
      <ul v-else class="event-log" style="align-self: stretch">
        <li v-for="event in runtime.events" :key="event.sequence" class="mono">
          #{{ event.sequence }} {{ eventLabels[event.type] ?? '任务记录更新' }} · {{ event.occurredAt }}
        </li>
      </ul>
    </div>

    <p v-if="runtime.streamError" class="banner-warning" role="alert">{{ runtime.streamError }}</p>
  </main>
</template>
