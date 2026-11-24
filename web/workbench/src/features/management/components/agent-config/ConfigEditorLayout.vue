<script setup lang="ts">
import { ElTabPane, ElTabs } from 'element-plus'
import { computed } from 'vue'
import type { EditorTab } from '../../model/agentConfigLabels'

/**
 * 配置编辑页单栏清爽框架：返回链接、标题与标签、页头操作、元信息以及页签切换。
 */
const tab = defineModel<EditorTab>('tab', { required: true })
const props = withDefaults(
  defineProps<{
    backPath: string
    backLabel: string
    title: string
    showTabs: boolean
    versionCount: number
    releaseCount?: number
    showReleases?: boolean
    usageCount?: number
    usageLabel?: string
    showUsages?: boolean
  }>(),
  {
    releaseCount: 0,
    showReleases: false,
    usageCount: 0,
    usageLabel: '引用关系',
    showUsages: true,
  },
)

const tabs = computed(() => {
  const items: Array<{ value: EditorTab; label: string; count?: number }> = [
    { value: 'config', label: '配置' },
  ]
  if (props.showReleases) {
    items.push({ value: 'releases', label: '发布历史', count: props.releaseCount })
  }
  if (props.showUsages) {
    items.push({ value: 'usages', label: props.usageLabel, count: props.usageCount })
  }
  items.push({ value: 'versions', label: '配置历史', count: props.versionCount })
  return items
})
</script>

<template>
  <div class="config-editor">
    <header class="config-editor-header">
      <RouterLink class="config-back" :to="backPath">← 返回{{ backLabel }}</RouterLink>
      <div class="config-editor-title-row">
        <div class="config-editor-heading">
          <h1 class="config-editor-title">{{ title }}</h1>
          <slot name="tags" />
        </div>
        <div v-if="$slots.actions" class="config-editor-actions">
          <slot name="actions" />
        </div>
      </div>
      <div v-if="$slots.meta" class="config-editor-meta">
        <slot name="meta" />
      </div>
    </header>

    <slot name="messages" />

    <ElTabs
      v-if="showTabs"
      v-model="tab"
      class="management-editor-tabs"
      aria-label="编辑页内容"
    >
      <ElTabPane
        v-for="item in tabs"
        :key="item.value"
        :name="item.value"
        :label="item.count === undefined ? item.label : `${item.label} ${item.count}`"
      />
    </ElTabs>

    <div v-if="tab === 'config'" class="config-editor-single">
      <slot name="main" />
    </div>
    <section v-else-if="tab === 'releases'" class="management-card management-table-card">
      <slot name="releases" />
    </section>
    <section v-else-if="tab === 'versions'" class="management-card management-table-card">
      <slot name="versions" />
    </section>
    <section v-else class="management-card">
      <slot name="usages" />
    </section>
  </div>
</template>
