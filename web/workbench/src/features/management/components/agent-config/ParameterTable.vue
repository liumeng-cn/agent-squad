<script setup lang="ts">
import {
  ElButton,
  ElCheckbox,
  ElInput,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
} from 'element-plus'
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
const props = defineProps<{
  readOnly: boolean
  schema: JsonSchema
  emptyText?: string
}>()

const errors = computed(() =>
  props.readOnly ? [] : validateFields(fields.value),
)
const schemaText = computed(() => JSON.stringify(props.schema, null, 2))
const enumTypes: ParamType[] = ['string', 'number', 'integer']

function update(index: number, patch: Partial<ParamField>) {
  fields.value = fields.value.map((field, position) =>
    position === index ? { ...field, ...patch } : field,
  )
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
      <ElTable :data="fields" stripe style="width: 100%" empty-text="暂无数据">
        <!-- @vue-generic {ParamField} -->
        <ElTableColumn label="参数名" min-width="150">
          <template
            #default="{
              row: field,
              $index: index,
            }: {
              row: ParamField
              $index: number
            }"
            ><div>
              <template v-if="readOnly"
                ><span class="management-code">{{ field.name }}</span></template
              ><template v-else>
                <ElInput
                  :model-value="field.name"
                  maxlength="64"
                  placeholder="keyword"
                  :aria-label="`第 ${index + 1} 个参数名`"
                  @input="update(index, { name: $event.trim() })"
                />
              </template></div
          ></template>
        </ElTableColumn>
        <!-- @vue-generic {ParamField} -->
        <ElTableColumn label="类型" min-width="120">
          <template
            #default="{
              row: field,
              $index: index,
            }: {
              row: ParamField
              $index: number
            }"
            ><div>
              <template v-if="readOnly">{{
                paramTypeLabels[field.type]
              }}</template><template v-else>
                <ElSelect
                  :empty-values="[null, undefined]"
                  :model-value="field.type"
                  :aria-label="`第 ${index + 1} 个参数类型`"
                  @change="changeType(index, $event as ParamType)"
                >
                  <ElOption
                    v-for="type in paramTypes"
                    :key="type"
                    :value="type"
                    :label="paramTypeLabels[type]"
                  />
                </ElSelect>
              </template></div
          ></template>
        </ElTableColumn>
        <!-- @vue-generic {ParamField} -->
        <ElTableColumn label="必填" width="70">
          <template
            #default="{
              row: field,
              $index: index,
            }: {
              row: ParamField
              $index: number
            }"
            ><div>
              <template v-if="readOnly">{{
                field.required ? '是' : '否'
              }}</template><template v-else>
                <ElCheckbox
                  :model-value="field.required"
                  :aria-label="`第 ${index + 1} 个参数必填`"
                  @change="update(index, { required: Boolean($event) })"
                />
              </template></div
          ></template>
        </ElTableColumn>
        <!-- @vue-generic {ParamField} -->
        <ElTableColumn label="说明" min-width="150">
          <template
            #default="{
              row: field,
              $index: index,
            }: {
              row: ParamField
              $index: number
            }"
            ><div>
              <template v-if="readOnly">{{
                field.description || '—'
              }}</template><template v-else>
                <ElInput
                  :model-value="field.description"
                  maxlength="500"
                  placeholder="告诉模型这个参数的含义和格式"
                  :aria-label="`第 ${index + 1} 个参数说明`"
                  @input="update(index, { description: $event })"
                />
              </template></div
          ></template>
        </ElTableColumn>
        <!-- @vue-generic {ParamField} -->
        <ElTableColumn label="可选值" min-width="150">
          <template
            #default="{
              row: field,
              $index: index,
            }: {
              row: ParamField
              $index: number
            }"
            ><div>
              <template v-if="readOnly">{{
                field.enumValues || '—'
              }}</template><template v-else>
                <ElInput
                  :model-value="field.enumValues"
                  maxlength="500"
                  :disabled="!enumTypes.includes(field.type)"
                  placeholder="逗号分隔，可留空"
                  :aria-label="`第 ${index + 1} 个参数可选值`"
                  @input="update(index, { enumValues: $event })"
                />
              </template></div
          ></template>
        </ElTableColumn>
        <!-- @vue-generic {ParamField} -->
        <ElTableColumn label="操作" min-width="220" v-if="!readOnly">
          <template #default="{ $index: index }"
            ><div>
              <template v-if="readOnly"></template
              ><template v-else>
                <ElButton
                  link
                  type="danger"
                  plain
                  native-type="button"
                  class="management-link management-danger"
                  @click="remove(index)"
                  >删除</ElButton
                >
              </template>
            </div></template
          >
        </ElTableColumn>
      </ElTable>
    </div>

    <div v-if="!readOnly" class="management-inline">
      <ElButton
        native-type="button"
        class="management-btn-sm"
        :disabled="fields.length >= MAX_PARAMS"
        @click="add"
        >添加参数</ElButton
      >
      <span class="management-muted"
        >参数名以字母或下划线开头；“文本列表”的元素为文本。</span
      >
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
