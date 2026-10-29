<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getTaskApi } from '../../../shared/api/tasksApi'
import { errorMessage } from '../../../shared/api/http'
const route = useRoute()
const router = useRouter()
const error = ref('')
onMounted(async () => {
  try { const task = await getTaskApi(String(route.params.taskId)); await router.replace({ name: 'workspace', params: { conversationId: task.conversationId } }) }
  catch (cause) { error.value = errorMessage(cause) }
})
</script>
<template><main class="legacy-link-state"><p v-if="error" role="alert">{{ error }}</p><p v-else role="status">正在打开所属对话…</p><router-link :to="{ name: 'workspace' }">返回对话</router-link></main></template>
