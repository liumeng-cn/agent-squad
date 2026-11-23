<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { errorMessage } from '../../../../shared/http/http'
import type { ToolTestRun } from '../../model/agentConfig'
import {
  buildTestArguments,
  initialTestValues,
  parseEnumValues,
  paramTypeLabels,
  type ParamField,
  type TestInputValue,
} from '../../model/toolSchema'

/**
 * 试运行面板：按参数定义生成输入框，使用当前未保存的工具定义调用一次并展示结果。
 * 试运行不写库，也不进入 Agent 会话。
 */
const props = defineProps<{
  fields: ParamField[]
  disabledReason: string | null
  execute: (args: Record<string, unknown>) => Promise<ToolTestRun>
}>()

const values = ref<Record<string, TestInputValue>>({})

function textValue(name: string): string {
  const value = values.value[name]
  return typeof value === 'string' ? value : ''
}

function setValue(name: string, value: TestInputValue) {
  values.value = { ...values.value, [name]: value }
}
const inputErrors = ref<string[]>([])
const result = ref<ToolTestRun | null>(null)
const failure = ref('')
const running = ref(false)

const fieldKey = computed(() => props.fields.map(field => `${field.name}:${field.type}`).join('|'))

watch(fieldKey, () => {
  values.value = initialTestValues(props.fields)
  inputErrors.value = []
}, { immediate: true })

function enumOptions(field: ParamField): (string | number)[] {
  return parseEnumValues(field)
}

function isJsonField(field: ParamField): boolean {
  return field.type === 'array' || field.type === 'object'
}

async function runTest() {
  const built = buildTestArguments(props.fields, values.value)
  inputErrors.value = built.errors
  if (built.errors.length) {
    return
  }

  running.value = true
  failure.value = ''
  result.value = null
  try {
    result.value = await props.execute(built.arguments)
  } catch (reason) {
    failure.value = errorMessage(reason)
  } finally {
    running.value = false
  }
}
</script>

<template>
  <div class="management-form">
    <p v-if="disabledReason" class="management-muted">{{ disabledReason }}</p>

    <div v-if="fields.length" class="management-form-grid">
      <label
        v-for="field in fields"
        :key="field.name"
        class="management-field"
        :class="{ 'is-full': isJsonField(field) }"
      >
        <span class="management-field-label" :class="{ 'management-required': field.required }">
          <span class="management-code">{{ field.name }}</span>
          <span class="management-muted">{{ paramTypeLabels[field.type] }}</span>
        </span>

        <select
          v-if="enumOptions(field).length"
          :value="textValue(field.name)"
          :disabled="running"
          @change="setValue(field.name, ($event.target as HTMLSelectElement).value)"
        >
          <option value="">{{ field.required ? '请选择' : '不传' }}</option>
          <option v-for="option in enumOptions(field)" :key="String(option)" :value="String(option)">{{ option }}</option>
        </select>
        <span v-else-if="field.type === 'boolean'" class="management-switch">
          <input
            type="checkbox"
            :checked="values[field.name] === true"
            :disabled="running"
            @change="setValue(field.name, ($event.target as HTMLInputElement).checked)"
          />
          <span>{{ values[field.name] ? 'true' : 'false' }}</span>
        </span>
        <textarea
          v-else-if="isJsonField(field)"
          :value="textValue(field.name)"
          rows="3"
          :disabled="running"
          :placeholder="field.type === 'array' ? '[&quot;a&quot;, &quot;b&quot;]' : '{&quot;key&quot;: &quot;value&quot;}'"
          @input="setValue(field.name, ($event.target as HTMLTextAreaElement).value)"
        />
        <input
          v-else
          :value="textValue(field.name)"
          :inputmode="field.type === 'string' ? 'text' : 'decimal'"
          :disabled="running"
          @input="setValue(field.name, ($event.target as HTMLInputElement).value)"
        />
        <span v-if="field.description" class="management-field-hint">{{ field.description }}</span>
      </label>
    </div>
    <p v-else class="management-muted">该工具没有参数，可直接运行。</p>

    <div v-if="inputErrors.length" class="management-error" role="alert">
      <ul class="management-message-list">
        <li v-for="message in inputErrors" :key="message">{{ message }}</li>
      </ul>
    </div>

    <div class="management-inline">
      <button type="button" class="management-primary" :disabled="running || Boolean(disabledReason)" @click="runTest">
        {{ running ? '运行中…' : '运行' }}
      </button>
      <span class="management-muted">使用当前填写的定义运行，无需先保存。</span>
    </div>

    <p v-if="failure" class="management-error" role="alert">{{ failure }}</p>

    <div v-if="result" class="config-test-result" :class="result.success ? 'is-success' : 'is-fail'" role="status">
      <div class="config-test-head">
        <span class="management-tag" :class="result.success ? 'is-success' : 'is-danger'">
          {{ result.success ? '运行成功' : '运行失败' }}
        </span>
        <span>耗时 {{ result.durationMs }} ms</span>
        <span v-if="result.request">请求 <span class="management-code">{{ result.request }}</span></span>
      </div>
      <pre class="management-pre">{{ result.output || '（无输出）' }}</pre>
    </div>
  </div>
</template>
