<script setup lang="ts">
import type { SquadTimelineItem } from '../model/squadExecution'
import SquadEventItem from './SquadEventItem.vue'

defineProps<{
  items: SquadTimelineItem[]
  streaming: boolean
}>()
</script>

<template>
  <section class="event-timeline" aria-label="Agent 执行事件">
    <div v-if="items.length" class="event-list">
      <SquadEventItem
        v-for="item in items"
        :key="item.id"
        :item="item"
        :streaming="streaming"
      />
    </div>
    <p v-else class="timeline-empty">
      {{ streaming ? '正在等待第一条事件…' : '执行事件会显示在这里。' }}
    </p>
  </section>
</template>

<style scoped>
.event-list{display:flex;flex-direction:column;gap:9px}
.timeline-empty{margin:0;padding:16px 12px;color:#aaa;font-size:12px;text-align:center}
</style>
