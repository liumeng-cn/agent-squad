<script setup lang="ts" generic="T extends string">
import type { ChoiceOption } from '../../model/agentConfigLabels'

/**
 * 选择卡片：用卡片展示互斥选项及其说明，适合工具类型、Agent 类型和传输方式等需要解释的选择。
 */
const selected = defineModel<T>({ required: true })
defineProps<{ options: ChoiceOption<T>[]; name: string; disabled?: boolean; label: string }>()
</script>

<template>
  <div class="management-choices" role="radiogroup" :aria-label="label">
    <label
      v-for="option in options"
      :key="option.value"
      class="management-choice"
      :class="{ 'is-selected': selected === option.value, 'is-disabled': disabled || option.disabled }"
    >
      <input
        v-model="selected"
        type="radio"
        :name="name"
        :value="option.value"
        :disabled="disabled || option.disabled"
      />
      <span class="management-choice-title">{{ option.title }}</span>
      <span class="management-choice-desc">{{ option.description }}</span>
    </label>
  </div>
</template>
