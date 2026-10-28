<script setup lang="ts">
import { ref, watch } from 'vue'
import { getOperation, listOperations, reconcileOperation, type Operation, type OperationDetail, type Artifact } from '../../../shared/api/taskDetailsApi'
import { ApiError, errorMessage } from '../../../shared/api/http'

const props = defineProps<{ taskId: string; cursor: string; artifacts: Artifact[] }>()
const emit = defineEmits<{ refresh: [] }>()
const items = ref<Operation[]>([])
const next = ref<string | undefined>()
const selected = ref<OperationDetail | null>(null)
const loading = ref(false)
const error = ref('')
const notice = ref('')
const note = ref('')
const evidence = ref<string[]>([])
const disposition = ref('UNRESOLVED')
const stateText: Record<string, string> = { PREPARED: '准备中', WAITING_APPROVAL: '等待确认', IN_FLIGHT: '处理中', UNKNOWN: '结果待核对', RECONCILING: '核对中', SUCCEEDED: '已完成', FAILED: '未完成', CANCELLED: '已取消' }
let revision = 0
async function load(append = false): Promise<void> {
  const current = ++revision
  try {
    const page = await listOperations(props.taskId, append ? next.value : undefined)
    if (current !== revision) return
    items.value = append ? [...items.value, ...page.items] : page.items
    next.value = page.nextCursor ?? undefined
  } catch (cause) { error.value = errorMessage(cause) }
}
async function inspect(id: string): Promise<void> {
  error.value = ''; selected.value = null
  try { selected.value = await getOperation(id) } catch (cause) { error.value = errorMessage(cause) }
}
async function reconcile(): Promise<void> {
  if (!selected.value?.canReconcile || loading.value) return
  loading.value = true; error.value = ''; notice.value = ''
  const id = selected.value.operation.operationId
  try {
    const result = await reconcileOperation(selected.value, disposition.value, evidence.value, note.value)
    await inspect(id); await load(); emit('refresh')
    notice.value = `核对结论已记录：${stateText[result.status] ?? '处理中'}`
  } catch (cause) {
    error.value = errorMessage(cause)
    if (cause instanceof ApiError && cause.status === 409) { await inspect(id); await load() }
  } finally { loading.value = false }
}
watch(() => [props.taskId, props.cursor], () => { void load() }, { immediate: true })
</script>

<template>
  <section class="detail-card">
    <h2>操作记录</h2>
    <p v-if="items.length === 0" class="detail-muted">暂无操作记录</p>
    <ul class="operation-list">
      <li v-for="operation in items" :key="operation.operationId">
        <span>{{ operation.effect === 'WRITE' ? '外部写入' : '信息处理' }} · {{ stateText[operation.status] ?? '处理中' }}</span>
        <button class="btn btn-sm" @click="inspect(operation.operationId)">查看详情</button>
      </li>
    </ul>
    <button v-if="next" class="btn" @click="load(true)">加载更多操作</button>
    <section v-if="selected" class="operation-detail" aria-label="操作详情">
      <div class="job-heading"><h3>操作详情</h3><button class="btn btn-sm" @click="selected = null">收起</button></div>
      <p>{{ stateText[selected.operation.status] ?? '处理中' }}</p>

      <h4>脱敏参数</h4>
      <pre class="job-result">{{ JSON.stringify(selected.argumentsPreview, null, 2) }}</pre>
      <p>{{ selected.evidenceRefs.length ? `已保存 ${selected.evidenceRefs.length} 项核对证据` : '暂无核对证据' }}</p>
      <form v-if="selected.canReconcile" @submit.prevent="reconcile">
        <h4>核对未知结果</h4>
        <label class="detail-field">结论<select v-model="disposition" :disabled="loading">
          <option value="UNRESOLVED">仍无法确认，保留未知状态</option>
          <option value="CONFIRMED_SUCCEEDED">证据确认已成功</option>
          <option value="CONFIRMED_NOT_EXECUTED">证据确认未执行</option>
        </select></label>
        <label class="detail-field">证据文件（至少一项）<select v-model="evidence" multiple required :disabled="loading">
          <option v-for="artifact in artifacts" :key="artifact.artifactId" :value="artifact.artifactId">{{ artifact.fileName }}</option>
        </select></label>
        <p v-if="artifacts.length === 0" class="detail-muted">此目标还没有可选证据文件。</p>
        <label class="detail-field">核对说明<textarea v-model="note" required maxlength="2000" :disabled="loading"></textarea></label>
        <button class="btn btn-primary" :disabled="loading || artifacts.length === 0">{{ loading ? '提交中…' : '提交核对结论' }}</button>
      </form>
    </section>
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <p v-if="notice" role="status">{{ notice }}</p>
  </section>
</template>
