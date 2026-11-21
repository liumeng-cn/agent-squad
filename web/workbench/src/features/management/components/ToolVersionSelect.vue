<script setup lang="ts">
import { computed } from 'vue'

const selected = defineModel<string[]>({ required: true })
const props = defineProps<{
  options: { value: string; label: string }[]
  readOnly: boolean
}>()

const labels = computed(() => new Map(props.options.map(option => [option.value, option.label])))
const unavailable = computed(() => selected.value.filter(value => !labels.value.has(value)))
const summary = computed(() => selected.value.length
  ? selected.value.map(value => labels.value.get(value) ?? `不可用版本 ${value}`).join('、')
  : '选择已发布的工具版本')
</script>

<template>
  <div class="catalog-tool-select">
    <span id="tool-version-select-label">授权工具</span>
    <details class="catalog-multiselect">
      <summary aria-labelledby="tool-version-select-label tool-version-select-summary">
        <span id="tool-version-select-summary" :title="summary">{{ summary }}</span>
      </summary>
      <div class="catalog-multiselect-options" role="group" aria-labelledby="tool-version-select-label">
        <label v-for="option in options" :key="option.value">
          <input v-model="selected" type="checkbox" :value="option.value" :disabled="readOnly" />
          {{ option.label }}
        </label>
        <label v-for="value in unavailable" :key="value">
          <input v-model="selected" type="checkbox" :value="value" :disabled="readOnly" />
          原绑定已不可用 · {{ value }}
        </label>
        <p v-if="!options.length && !unavailable.length" class="management-muted">暂无已发布工具。</p>
      </div>
    </details>
  </div>
</template>
