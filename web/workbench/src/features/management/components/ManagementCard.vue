<script setup lang="ts">
/**
 * 内容卡片：可选的标题、说明与右上角操作，正文放在默认插槽。
 */
defineProps<{ title?: string; description?: string; bodyless?: boolean }>()
</script>

<template>
  <section class="management-card">
    <header v-if="title || $slots.actions" class="management-card-header">
      <div class="management-card-heading">
        <h2 v-if="title" class="management-card-title">{{ title }}</h2>
        <p v-if="description" class="management-card-desc">{{ description }}</p>
      </div>
      <div v-if="$slots.actions" class="management-card-actions">
        <slot name="actions" />
      </div>
    </header>
    <slot v-if="bodyless" />
    <div v-else class="management-card-body">
      <slot />
    </div>
  </section>
</template>
