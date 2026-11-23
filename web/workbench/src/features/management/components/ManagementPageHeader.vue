<script setup lang="ts">
/**
 * 页头：标题、一句话说明和右侧主操作。
 */
defineProps<{ title: string; description?: string }>()
</script>

<template>
  <header class="management-page-header">
    <div class="management-page-heading">
      <h1 class="management-page-title">{{ title }}</h1>
      <p v-if="description" class="management-page-desc">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="management-page-actions">
      <slot name="actions" />
    </div>
  </header>
</template>
