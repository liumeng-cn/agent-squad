<script setup lang="ts">
import { computed } from 'vue'
import type { JsonSchema } from '../../model/agentConfig'
import {
  MAX_PARAMS,
  emptyParamField,
  paramTypeLabels,
  paramTypes,
  validateFields,
  type ParamField,
  type ParamType,
} from '../../model/toolSchema'

/**
 * 参数表格：HTTP 工具在这里定义模型可传入的参数，保存时生成 JSON Schema；
 * 内置与 MCP 工具的参数来自实现或远程服务，只读展示。
 */
const fields = defineModel<ParamField[]>({ required: true })
const props = defineProps<{ readOnly: boolean; schema: JsonSchema; emptyText?: string }>()

const errors = computed(() => (props.readOnly ? [] : validateFields(fields.value)))
const schemaText = computed(() => JSON.stringify(props.schema, null, 2))
const enumTypes: ParamType[] = ['string', 'number', 'integer']

function update(index: number, patch: Partial<ParamField>) {
  fields.value = fields.value.map((field, position) => (position === index ? { ...field, ...patch } : field))
}

function changeType(index: number, type: ParamType) {
  const patch: Partial<ParamField> = { type }
  if (!enumTypes.includes(type)) {
    patch.enumValues = ''
  }
  update(index, patch)
}

function add() {
  fields.value = [...fields.value, emptyParamField()]
}

function remove(index: number) {
  fields.value = fields.value.filter((_, position) => position !== index)
}
</script>

<template>
  <div class="management-form">
    <div class="management-table-wrap">
      <table class="management-table config-param-table">
        <thead>
          <tr>
            <th class="is-name">参数名</th>
            <th class="is-type">类型</th>
            <th class="is-check">必填</th>
            <th>说明</th>
            <th>可选值</th>
            <th v-if="!readOnly" class="is-action">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(field, index) in fields" :key="index">
            <template v-if="readOnly">
              <td><span class="management-code">{{ field.name }}</span></td>
              <td>{{ paramTypeLabels[field.type] }}</td>
              <td class="is-check">{{ field.required ? '是' : '否' }}</td>
              <td>{{ field.description || '—' }}</td>
              <td>{{ field.enumValues || '—' }}</td>
            </template>
            <template v-else>
              <td>
                <input
                  :value="field.name"
                  maxlength="64"
                  placeholder="keyword"
                  :aria-label="`第 ${index + 1} 个参数名`"
                  @input="update(index, { name: ($event.target as HTMLInputElement).value.trim() })"
                />
              </td>
              <td>
                <select
                  :value="field.type"
                  :aria-label="`第 ${index + 1} 个参数类型`"
                  @change="changeType(index, ($event.target as HTMLSelectElement).value as ParamType)"
                >
                  <option v-for="type in paramTypes" :key="type" :value="type">{{ paramTypeLabels[type] }}</option>
                </select>
              </td>
              <td class="is-check">
                <input
                  type="checkbox"
                  :checked="field.required"
                  :aria-label="`第 ${index + 1} 个参数必填`"
                  @change="update(index, { required: ($event.target as HTMLInputElement).checked })"
                />
              </td>
              <td>
                <input
                  :value="field.description"
                  maxlength="500"
                  placeholder="告诉模型这个参数的含义和格式"
                  :aria-label="`第 ${index + 1} 个参数说明`"
                  @input="update(index, { description: ($event.target as HTMLInputElement).value })"
                />
              </td>
              <td>
                <input
                  :value="field.enumValues"
                  maxlength="500"
                  :disabled="!enumTypes.includes(field.type)"
                  placeholder="逗号分隔，可留空"
                  :aria-label="`第 ${index + 1} 个参数可选值`"
                  @input="update(index, { enumValues: ($event.target as HTMLInputElement).value })"
                />
              </td>
              <td class="is-action">
                <button type="button" class="management-link management-danger" @click="remove(index)">删除</button>
              </td>
            </template>
          </tr>
          <tr v-if="!fields.length">
            <td :colspan="readOnly ? 5 : 6">
              <div class="management-empty is-compact">{{ emptyText ?? '暂无参数' }}</div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="!readOnly" class="management-inline">
      <button type="button" class="management-btn-sm" :disabled="fields.length >= MAX_PARAMS" @click="add">添加参数</button>
      <span class="management-muted">参数名以字母或下划线开头；“文本列表”的元素为文本。</span>
    </div>

    <div v-if="errors.length" class="management-error" role="alert">
      <ul class="management-message-list">
        <li v-for="message in errors" :key="message">{{ message }}</li>
      </ul>
    </div>

    <details class="config-schema">
      <summary>查看生成的 JSON Schema</summary>
      <pre class="management-pre">{{ schemaText }}</pre>
    </details>
  </div>
</template>
