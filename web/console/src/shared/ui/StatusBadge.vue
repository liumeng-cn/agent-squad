<script setup lang="ts">
import { statusBadgeClass, statusText } from '../lib/taskStatus'

defineProps<{
  status: string
}>()
</script>

<template>
  <span class="badge" :class="statusBadgeClass(status)">{{ statusText(status) }}</span>
</template>
