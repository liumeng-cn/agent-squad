<script setup lang="ts">
import { computed } from 'vue'
import { useAuthStore } from '../../features/auth/stores/auth'
const auth = useAuthStore()
const url = computed(() => {
  try {
    const target = new URL(import.meta.env.VITE_OBSERVATION_URL || 'http://127.0.0.1:13001')
    return ['http:', 'https:'].includes(target.protocol) && !target.username && !target.password ? target.href : ''
  } catch { return '' }
})
</script>

<template>
  <a v-if="auth.user?.roles.includes('ADMIN') && url" :href="url" target="_blank" rel="noopener noreferrer"
    title="在新标签页打开 Langfuse">Agent 观测 ↗</a>
</template>
