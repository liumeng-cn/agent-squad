<script setup lang="ts" generic="T extends string">
import { ElRadioButton, ElRadioGroup } from 'element-plus'
import type { ChoiceOption } from '../../model/agentConfigLabels'

/**
 * 选择卡片：用卡片展示互斥选项及其说明，适合工具类型、Agent 类型和传输方式等需要解释的选择。
 */
const selected = defineModel<T>({ required: true })
defineProps<{
  options: ChoiceOption<T>[]
  name: string
  disabled?: boolean
  label: string
}>()
</script>

<template>
  <ElRadioGroup
    v-model="selected"
    :disabled="disabled"
    :aria-label="label"
    class="management-type-options"
  >
    <ElRadioButton
      v-for="option in options"
      :key="option.value"
      :value="option.value"
      :disabled="option.disabled"
      :name="name"
    >
      <span>{{ option.title }}</span><small>{{ option.description }}</small>
    </ElRadioButton>
  </ElRadioGroup>
</template>
