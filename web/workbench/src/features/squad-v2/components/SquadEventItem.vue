<script setup lang="ts">
import { computed } from 'vue'
import SafeMarkdown from '../../../shared/ui/SafeMarkdown.vue'
import type { SquadTimelineItem } from '../model/squadExecution'
import {
  agentDisplayName,
  eventSummary,
  eventLabel,
  formatEventTime,
  formatJson,
  timelineItemLabel,
  timelineItemSummary,
} from '../model/squadEventPresenter'

const props = defineProps<{
  item: SquadTimelineItem
  streaming: boolean
}>()

const sender = computed(() => agentDisplayName(props.item.agent))
const label = computed(() => timelineItemLabel(props.item))
const summary = computed(() => timelineItemSummary(props.item))
const time = computed(() => formatEventTime(props.item.timestamp))
const rawEventSummary = computed(() => props.item.kind === 'event' ? eventSummary(props.item.event) : '')
const rawEventLabel = computed(() => props.item.kind === 'event' ? eventLabel(props.item.event.eventType) : '')
</script>

<template>
  <article class="event-item" :class="'event-' + item.kind">
    <header class="event-header">
      <span class="event-sender">{{ sender }}</span>
      <span class="event-type">{{ label }}</span>
      <time v-if="time">{{ time }}</time>
    </header>

    <template v-if="item.kind === 'block'">
      <SafeMarkdown
        v-if="item.blockType === 'TEXT' || item.blockType === 'THINKING'"
        :text="item.text || '等待内容…'"
        :streaming="streaming && !item.completed"
      />
      <pre v-else class="event-code">{{ item.text || '等待数据…' }}</pre>
    </template>

    <template v-else-if="item.kind === 'tool'">
      <p class="event-summary">{{ summary }}</p>
      <details v-if="item.inputText || item.outputText" class="event-details">
        <summary>查看工具参数与返回</summary>
        <div v-if="item.inputText" class="event-detail-block">
          <small>工具参数</small>
          <pre>{{ item.inputText }}</pre>
        </div>
        <div v-if="item.outputText" class="event-detail-block">
          <small>工具结果</small>
          <pre>{{ item.outputText }}</pre>
        </div>
      </details>
      <span class="tool-status">{{ item.status === 'COMPLETED' ? '已完成' : item.status === 'RETURNING' ? '返回中' : item.status === 'READY' ? '等待结果' : '准备中' }}</span>
    </template>

    <template v-else>
      <p class="event-summary">{{ rawEventSummary }}</p>
      <details class="event-details">
        <summary>查看原始事件（{{ rawEventLabel }}）</summary>
        <pre>{{ formatJson(item.event) }}</pre>
      </details>
    </template>
  </article>
</template>

<style scoped>
.event-item{min-width:0;padding:12px 13px;border:1px solid var(--border,#e9e9e9);border-radius:11px;background:var(--surface,#fff)}
.event-header{display:flex;align-items:center;gap:8px;min-width:0;margin-bottom:8px}
.event-sender{flex:none;max-width:160px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:11px;font-weight:650;color:var(--text-primary,#333)}
.event-type{min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;padding:3px 7px;border-radius:5px;background:var(--surface-muted,#f5f5f5);color:var(--text-secondary,#777);font-size:10px}
.event-header time{margin-left:auto;flex:none;color:#aaa;font-size:10px;font-variant-numeric:tabular-nums}
.event-summary{margin:0;color:var(--text-secondary,#666);font-size:12px;line-height:1.65;white-space:pre-wrap;overflow-wrap:anywhere}
.event-item :deep(.answer-markdown){font-size:12px;line-height:1.7;color:var(--text-primary,#444);overflow-wrap:anywhere}
.event-item :deep(.answer-markdown p){margin:3px 0}
.event-code,.event-details pre{max-width:100%;overflow:auto;margin:0;padding:9px 10px;border-radius:7px;background:var(--surface-muted,#f7f7f7);color:var(--text-secondary,#555);font:11px/1.6 ui-monospace,SFMono-Regular,Menlo,monospace;white-space:pre-wrap;overflow-wrap:anywhere}
.event-details{margin-top:8px;color:#888;font-size:10px}
.event-details summary{cursor:pointer;user-select:none}
.event-details pre{margin-top:7px}
.event-detail-block{margin-top:8px}
.event-detail-block small{display:block;margin-bottom:4px;color:#999}
.tool-status{display:inline-block;margin-top:8px;padding:3px 7px;border-radius:5px;background:#f5f5f5;color:#777;font-size:10px}
@media(max-width:600px){.event-item{padding:10px}.event-sender{max-width:105px}.event-header{gap:5px}}
</style>
