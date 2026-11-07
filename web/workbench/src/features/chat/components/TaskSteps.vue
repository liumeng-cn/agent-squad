<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import ActivityTimeline from './ActivityTimeline.vue'
import type { ExecutionActivity } from '../../../shared/api/activities'
import { getTaskJobs } from '../../../shared/api/tasksApi'
import type { AcceptanceCheckView, WorkerJob, WorkerJobStatus } from '../../../shared/api/types'
import { statusText } from '../../../shared/lib/taskStatus'
const props = defineProps<{ taskId: string; status: string; revision: string; answerCompleted?: boolean; activities?: ExecutionActivity[] }>()
const expanded = ref(true)
const jobs = ref<WorkerJob[]>([])
const checks = ref<AcceptanceCheckView[]>([])
const verdictLabels = { PASS: '符合要求', FAIL: '未达到要求', UNKNOWN: '尚未确认' } as const
const verifierLabels = { DETERMINISTIC: '程序检查', MODEL_REVIEW: '模型检查', USER_REVIEW: '用户确认' } as const
const error = ref(false)
const loading = ref(false)
const selectedVersion = ref<number | undefined>()
const shownVersion = ref(0)
const latestVersion = ref(0)
const overallStatus = computed(() => ({
  SUCCEEDED: '整体已完成', PARTIAL: '整体只完成了一部分', FAILED: '整体未完成', CANCELLED: '整体已停止',
}[props.status] ?? `整体${statusText(props.status)}`))
const shownActivities = computed(() => (props.activities ?? []).filter(activity =>
  activity.planVersion === shownVersion.value || (shownVersion.value === latestVersion.value && activity.planVersion === 0)))
let requestVersion = 0
const labels: Record<WorkerJobStatus, string> = {
  BLOCKED: '等待前一步完成', READY: '等待开始', RUNNING: '正在执行',
  WAITING_INPUT: '需要你补充信息', WAITING_APPROVAL: '等待你确认', RECOVERING: '正在重试',
  RECOVERY_BLOCKED: '遇到问题，需要重新安排', RECONCILING: '正在核对结果', CANCELLING: '正在停止',
  SUCCEEDED: '已完成', PARTIAL: '只完成了一部分', FAILED: '未完成', CANCELLED: '已停止',
  SKIPPED: '前一步失败，这一步未执行', SUPERSEDED: '已改为新的安排',
}
const completed = computed(() => jobs.value.filter(job => job.status === 'SUCCEEDED').length)
const running = computed(() => jobs.value.filter(job => job.status === 'RUNNING' || job.status === 'RECOVERING').length)
// 终态且没有任何步骤与检查时不再占用对话空间；运行中与有内容的历史任务保留展示。
const terminal = computed(() => ['SUCCEEDED', 'PARTIAL', 'FAILED', 'CANCELLED'].includes(props.status))
watch(() => [props.taskId, props.answerCompleted ?? terminal.value] as const, () => {
  expanded.value = !(props.answerCompleted ?? terminal.value)
}, { immediate: true })
function toggled(event: Event) { expanded.value = (event.currentTarget as HTMLDetailsElement).open }
const visible = computed(() => loading.value || error.value || !terminal.value || !!jobs.value.length || !!checks.value.length || !!props.activities?.length)
async function load() {
  const current = ++requestVersion
  const taskId = props.taskId
  loading.value = true
  error.value = false
  try {
    const page = await getTaskJobs(taskId, selectedVersion.value)
    if (current === requestVersion && taskId === props.taskId) {
      jobs.value = page.items
      checks.value = page.acceptanceChecks ?? []
      shownVersion.value = page.planVersion ?? page.items[0]?.planVersion ?? 0
      latestVersion.value = page.currentPlanVersion ?? shownVersion.value
    }
  } catch { if (current === requestVersion) error.value = true }
  finally { if (current === requestVersion) loading.value = false }
}
function selectVersion(version?: number) {
  selectedVersion.value = version
  void load()
}
watch(() => props.taskId, () => { jobs.value = []; checks.value = []; selectedVersion.value = undefined; shownVersion.value = 0; latestVersion.value = 0 })
watch(() => [props.taskId, props.status, props.revision], () => { void load() }, { immediate: true })
onBeforeUnmount(() => { requestVersion++ })
</script>
<template>
  <details v-if="visible" class="task-steps" :open="expanded" aria-label="工作步骤" @toggle="toggled">
    <summary>处理步骤<span>{{ overallStatus }}</span></summary>
    <p v-if="shownVersion" class="task-steps-note">
      {{ shownVersion < latestVersion ? '过去的安排' : '当前安排' }} · 第 {{ shownVersion }} 版
      <button v-if="shownVersion > 1" class="text-button" type="button" :disabled="loading" @click="selectVersion(shownVersion - 1)">查看上一版</button>
      <button v-if="selectedVersion !== undefined" class="text-button" type="button" :disabled="loading" @click="selectVersion()">返回当前安排</button>
    </p>
    <p v-if="jobs.length" class="task-steps-note">本版步骤：{{ completed }}/{{ jobs.length }} 项已完成</p>
    <p v-if="error" class="task-steps-note">暂时无法读取进度 <button class="text-button" type="button" @click="load">重新读取</button></p>
    <p v-else-if="!jobs.length" class="task-steps-note">{{ loading ? '正在读取进度…' : (['SUCCEEDED', 'PARTIAL', 'FAILED', 'CANCELLED'].includes(status) ? '这次工作没有拆分步骤' : '正在安排工作…') }}</p>
    <p v-if="running > 1" class="task-steps-note">有 {{ running }} 项工作同时进行</p>
    <ActivityTimeline :activities="shownActivities.filter(a => !a.jobId)" :completed="answerCompleted ?? terminal" />
    <ol>
      <li v-for="job in jobs" :key="job.jobId" :class="{ finished: job.status === 'SUCCEEDED' }">
        <span class="task-step-dot" aria-hidden="true">{{ job.status === 'SUCCEEDED' ? '✓' : '·' }}</span>
        <div><span class="task-step-title">{{ job.title || '处理这项工作' }}</span><small><span v-if="job.required === false">可选工作 · </span>{{ labels[job.status] }}<span v-if="job.reusedFromJobId"> · 沿用已有结果</span></small><ActivityTimeline :activities="shownActivities.filter(a => a.jobId === job.jobId)" :completed="['SUCCEEDED', 'FAILED', 'CANCELLED', 'SKIPPED', 'SUPERSEDED'].includes(job.status)" /></div>
      </li>
    </ol>
    <template v-if="checks.length">
      <p class="task-steps-note">结果检查</p>
      <ol>
        <li v-for="check in checks" :key="check.criterionId" :class="{ finished: check.verdict === 'PASS' }">
          <span class="task-step-dot" aria-hidden="true">{{ check.verdict === 'PASS' ? '✓' : '·' }}</span>
          <div><span class="task-step-title">{{ check.text }}</span><small>{{ verifierLabels[check.verificationType] }} · {{ verdictLabels[check.verdict] }}</small></div>
        </li>
      </ol>
    </template>
  </details>
</template>
