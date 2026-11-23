<script setup lang="ts">
import type { CheckItem } from '../../model/publishCheck'

/**
 * 发布检查清单：逐项展示是否满足发布前置条件，与服务端发布校验规则保持一致。
 */
defineProps<{ items: CheckItem[] }>()

function stateClass(item: CheckItem): string {
  if (item.passed) {
    return 'is-ok'
  }
  return item.level === 'advice' ? 'is-advice' : 'is-fail'
}
</script>

<template>
  <ul class="config-checklist">
    <li v-for="item in items" :key="item.label" class="config-check" :class="stateClass(item)">
      <span class="config-check-icon" aria-hidden="true" />
      <span>
        {{ item.label }}
        <span class="management-sr-only">{{ item.passed ? '已满足' : '未满足' }}</span>
        <span v-if="!item.passed && item.hint" class="config-check-hint">{{ item.hint }}</span>
      </span>
    </li>
  </ul>
</template>
