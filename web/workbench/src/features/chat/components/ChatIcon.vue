<script setup lang="ts">
defineProps<{ name: 'sidebar' | 'new' }>()
</script>
<template>
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.65" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
    <template v-if="name === 'sidebar'"><rect x="3" y="3.5" width="18" height="17" rx="4" /><path d="M9 4v16" /></template>
    <template v-else><path d="M20.5 11.5v.5a8.5 8.5 0 0 1-12.8 7.3L3 21l1.7-4.7A8.5 8.5 0 0 1 12 3.5h.5" /><path d="M18 2.5v7m-3.5-3.5h7" /></template>
  </svg>
</template>
<style scoped>svg{display:block;width:22px;height:22px;flex-shrink:0}</style>
