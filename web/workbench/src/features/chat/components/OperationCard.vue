<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { getOperation, reconcileOperation, type OperationDetail, type Artifact } from '../../../shared/api/taskDetailsApi'
import { errorMessage } from '../../../shared/api/http'
const props = defineProps<{ operationId: string; revision: string; artifacts: Artifact[] }>()
const emit = defineEmits<{ refresh: [] }>()
const detail = ref<OperationDetail | null>(null)
const error = ref('')
const busy = ref(false)
const expanded = ref(false)
const disposition = ref('UNRESOLVED')
const evidence = ref<string[]>([])
const note = ref('')
let request = 0
async function load() {
  const current = ++request
  try { const result = await getOperation(props.operationId); if (current === request) detail.value = result }
  catch (cause) { if (current === request) error.value = errorMessage(cause) }
}
watch(() => [props.operationId, props.revision], () => { void load() }, { immediate: true })
async function submit() {
  if (!detail.value?.canReconcile || busy.value) return
  busy.value = true; error.value = ''
  try { await reconcileOperation(detail.value, disposition.value, evidence.value, note.value); await load(); emit('refresh') }
  catch (cause) { error.value = errorMessage(cause); await load(); emit('refresh') }
  finally { busy.value = false }
}
onBeforeUnmount(() => { request++ })
</script>
<template>
  <section class="conversation-notice operation-notice">
    <strong>正在核对一项外部操作的结果</strong>
    <p>结果确认前，不会重复执行这项操作。</p>
    <template v-if="detail?.canReconcile">
      <button class="text-button" @click="expanded = !expanded">{{ expanded ? '收起核对' : '核对结果' }}</button>
      <form v-if="expanded" class="inline-reconcile" @submit.prevent="submit">
        <p>请根据已核实的证据填写结论。</p>
        <pre>{{ JSON.stringify(detail.argumentsPreview, null, 2) }}</pre>
        <label>核对结论<select v-model="disposition" :disabled="busy"><option value="UNRESOLVED">仍无法确认，保留待核对状态</option><option value="CONFIRMED_SUCCEEDED">证据确认已成功</option><option value="CONFIRMED_NOT_EXECUTED">证据确认未执行</option></select></label>
        <label>证据文件<select v-model="evidence" multiple required :disabled="busy"><option v-for="file in artifacts" :key="file.artifactId" :value="file.artifactId">{{ file.fileName }}</option></select></label>
        <p v-if="!artifacts.length">请先在对话中添加证据文件。</p>
        <label>核对说明<textarea v-model="note" required maxlength="2000" :disabled="busy"></textarea></label>
        <button class="btn btn-primary" :disabled="busy || !evidence.length">{{ busy ? '提交中…' : '提交核对结论' }}</button>
      </form>
    </template>
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
  </section>
</template>
