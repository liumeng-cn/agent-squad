<script setup lang="ts">
import { computed, ref } from 'vue'
import type { Interaction } from '../../../shared/api/types'
import { ApiError, errorMessage } from '../../../shared/api/http'
import { respondInteraction } from '../../../shared/api/taskDetailsApi'
import { newIdempotencyKey } from '../../../shared/lib/idempotency'

const props = defineProps<{ interaction: Interaction }>()
const emit = defineEmits<{ refresh: [] }>()
interface Field { type: string; title?: string; description?: string; enum?: (string | number | boolean)[]; minLength?: number; maxLength?: number; minimum?: number; maximum?: number }
const schema = computed(() => props.interaction.answerSchema as { properties?: Record<string, Field>; required?: string[] })
const fields = computed(() => Object.entries(schema.value?.properties ?? {}))
const values = ref<Record<string, string | boolean>>({})
const pending = ref(false)
const error = ref('')
let attempt: { payload: string; key: string } | null = null
const approval = computed(() => props.interaction.approvalContext as { resourceSummary?: string; toolKey?: string; effect?: string; argumentsPreview?: Record<string, unknown>; requestHash?: string } | null)
const approvalValid = computed(() => approval.value?.requestHash === props.interaction.requestHash)

async function submit(approve?: boolean): Promise<void> {
  if (pending.value) return
  error.value = ''
  const answer: Record<string, unknown> = { kind: props.interaction.kind }
  if (props.interaction.kind === 'APPROVAL') {
    if (!approvalValid.value) { error.value = '审批内容已变化，请刷新'; emit('refresh'); return }
    answer.approve = approve
    answer.confirmedRequestHash = props.interaction.requestHash
  } else {
    const value: Record<string, unknown> = {}
    for (const [key, field] of fields.value) {
      const raw = values.value[key]
      if (raw === undefined || raw === '') continue
      value[key] = field.enum ? field.enum[Number(raw)] : field.type === 'boolean' ? raw === true || raw === 'true'
        : ['number', 'integer'].includes(field.type) ? Number(raw) : raw
    }
    answer.value = value
  }
  const payload = JSON.stringify(answer)
  if (attempt?.payload !== payload) attempt = { payload, key: newIdempotencyKey() }
  pending.value = true
  try {
    await respondInteraction(props.interaction.interactionId, props.interaction.version, answer, attempt.key)
    emit('refresh')
  } catch (cause) {
    error.value = errorMessage(cause)
    if (cause instanceof ApiError && cause.status === 409) emit('refresh')
  } finally { pending.value = false }
}
</script>

<template>
  <section class="interaction-card">
    <h3>{{ interaction.kind === 'APPROVAL' ? '操作确认' : '需要补充信息' }}</h3>
    <p class="preserve-text">{{ interaction.question }}</p>
    <template v-if="interaction.kind === 'APPROVAL'">
      <p>{{ approval?.effect === 'WRITE' ? '此操作会向外部系统写入信息，请核对影响后确认。' : '此操作需要你的确认。' }}</p>
      <pre class="job-result">{{ JSON.stringify(approval?.argumentsPreview ?? {}, null, 2) }}</pre>
      <div class="detail-actions">
        <button class="btn btn-primary" :disabled="pending || !approvalValid" @click="submit(true)">确认执行</button>
        <button class="btn" :disabled="pending || !approvalValid" @click="submit(false)">拒绝执行</button>
      </div>
    </template>
    <form v-else @submit.prevent="submit()">
      <p v-if="fields.length === 0" class="banner-warning">表单定义不可用，请刷新任务详情。</p>
      <label v-for="[key, field] in fields" :key="key" class="detail-field">
        <span>{{ field.title || key }}{{ schema.required?.includes(key) ? ' *' : '' }}</span>
        <small v-if="field.description">{{ field.description }}</small>
        <select v-if="field.enum" v-model="values[key]" :required="schema.required?.includes(key)" :disabled="pending">
          <option value="">请选择</option>
          <option v-for="(option, index) in field.enum" :key="index" :value="String(index)">{{ option }}</option>
        </select>
        <select v-else-if="field.type === 'boolean'" v-model="values[key]" :required="schema.required?.includes(key)" :disabled="pending">
          <option value="">请选择</option><option value="true">是</option><option value="false">否</option>
        </select>
        <input v-else v-model="values[key]" :type="['number', 'integer'].includes(field.type) ? 'number' : 'text'"
          :step="field.type === 'integer' ? 1 : 'any'" :min="field.minimum" :max="field.maximum"
          :minlength="field.minLength" :maxlength="field.maxLength ?? 4000" :required="schema.required?.includes(key)" :disabled="pending" />
      </label>
      <button class="btn btn-primary" :disabled="pending || fields.length === 0">{{ pending ? '提交中…' : '提交补充信息' }}</button>
    </form>
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
  </section>
</template>
