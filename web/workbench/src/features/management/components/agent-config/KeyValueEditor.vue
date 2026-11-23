<script setup lang="ts">
import type { KeyValue } from '../../model/agentConfig'

/**
 * 键值编辑器：用于请求头等名称/值成对的配置，支持增删行和数量上限。
 */
const rows = defineModel<KeyValue[]>({ required: true })
const props = withDefaults(defineProps<{
  readOnly?: boolean
  max?: number
  namePlaceholder?: string
  valuePlaceholder?: string
  addLabel?: string
  emptyText?: string
}>(), {
  readOnly: false,
  max: 20,
  namePlaceholder: '名称',
  valuePlaceholder: '值',
  addLabel: '添加',
  emptyText: '暂未配置',
})

function add() {
  rows.value = [...rows.value, { name: '', value: '' }]
}

function remove(index: number) {
  rows.value = rows.value.filter((_, position) => position !== index)
}

function update(index: number, key: keyof KeyValue, value: string) {
  rows.value = rows.value.map((row, position) => (position === index ? { ...row, [key]: value } : row))
}
</script>

<template>
  <div class="management-kv">
    <p v-if="!rows.length" class="management-kv-empty">{{ emptyText }}</p>
    <div v-for="(row, index) in rows" :key="index" class="management-kv-row">
      <input
        :value="row.name"
        :placeholder="namePlaceholder"
        :readonly="readOnly"
        :aria-label="`第 ${index + 1} 行名称`"
        maxlength="128"
        @input="update(index, 'name', ($event.target as HTMLInputElement).value)"
      />
      <input
        :value="row.value"
        :placeholder="valuePlaceholder"
        :readonly="readOnly"
        :aria-label="`第 ${index + 1} 行值`"
        maxlength="2048"
        @input="update(index, 'value', ($event.target as HTMLInputElement).value)"
      />
      <button v-if="!readOnly" type="button" class="management-link management-danger" @click="remove(index)">删除</button>
    </div>
    <div v-if="!readOnly">
      <button type="button" class="management-btn-sm" :disabled="rows.length >= props.max" @click="add">
        {{ addLabel }}
      </button>
    </div>
  </div>
</template>
