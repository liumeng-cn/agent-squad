<script setup lang="ts">
import { ElCard } from 'element-plus'
/**
 * 内容卡片：可选的标题、说明与右上角操作，正文放在默认插槽。
 */
defineProps<{ title?: string; description?: string; bodyless?: boolean }>()
</script>

<template>
  <ElCard class="management-card" shadow="never" :body-style="{ padding: '0' }">
    <template v-if="title || $slots.actions" #header
      ><header class="management-card-header">
        <div class="management-card-heading">
          <h2 v-if="title" class="management-card-title">{{ title }}</h2>
          <p v-if="description" class="management-card-desc">
            {{ description }}
          </p>
        </div>
        <div v-if="$slots.actions" class="management-card-actions">
          <slot name="actions" />
        </div></header
    ></template>
    <slot v-if="bodyless" />
    <div v-else class="management-card-body">
      <slot />
    </div>
  </ElCard>
</template>
