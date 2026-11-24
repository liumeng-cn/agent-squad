<script setup lang="ts">
/** 管理列表工具栏：类型切换在上，新增操作紧邻表格。 */
</script>

<template>
  <div class="management-table-toolbar">
    <div v-if="$slots.tabs" class="management-table-toolbar-tabs"><slot name="tabs" /></div>
    <div class="management-table-toolbar-actions"><slot /></div>
  </div>
</template>
