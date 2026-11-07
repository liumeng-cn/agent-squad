<script setup lang="ts">
import { computed, ref } from 'vue'
import { activityHistory } from '../../../shared/api/chatApi'
import type { ExecutionActivity } from '../../../shared/api/activities'
import ActivityTimeline from './ActivityTimeline.vue'

const props = defineProps<{ conversationId: string; sourceMessageId: string; live: ExecutionActivity[] }>()
const items = ref<ExecutionActivity[]>([])
const cursor = ref<string | null>()
const loading = ref(false)
const error = ref(false)
const latest = computed(() => new Map(props.live.map(activity => [activity.activityId, activity])))
const displayed = computed(() => items.value.map(activity => {
  const update = latest.value.get(activity.activityId)
  return update && update.version > activity.version ? update : activity
}))

async function load() {
  if (loading.value || cursor.value === null) return
  loading.value = true
  error.value = false
  try {
    const page = await activityHistory(props.conversationId, props.sourceMessageId, cursor.value)
    const merged = new Map(items.value.map(activity => [activity.activityId, activity]))
    for (const activity of page.items) merged.set(activity.activityId, activity)
    items.value = [...merged.values()]
    cursor.value = page.nextCursor
  } catch {
    error.value = true
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section aria-label="处理历史">
    <ActivityTimeline v-if="displayed.length" :activities="displayed" :completed="false" />
    <p v-if="error" role="status">暂时无法读取处理历史</p>
    <p v-else-if="cursor === null && !displayed.length">这条消息没有处理历史</p>
    <button v-if="cursor !== null" class="text-button" type="button" :disabled="loading" @click="load">
      {{ loading ? '正在读取…' : error ? '重试读取' : cursor ? '加载更早的步骤' : '查看处理历史' }}
    </button>
  </section>
</template>
