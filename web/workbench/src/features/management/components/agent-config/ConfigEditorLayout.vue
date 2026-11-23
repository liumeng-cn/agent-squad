<script setup lang="ts">
import { computed } from 'vue'
import type { EditorTab } from '../../model/agentConfigLabels'

/**
 * 配置编辑页框架：返回链接、标题与标签、页头操作、元信息和“配置 / 版本记录 / 引用关系”页签。
 * 配置页签采用主栏加侧栏布局，侧栏放版本信息与发布检查。
 */
const tab = defineModel<EditorTab>('tab', { required: true })
const props = defineProps<{
  backPath: string
  backLabel: string
  title: string
  showTabs: boolean
  versionCount: number
  usageCount: number
  usageLabel: string
}>()

const tabs = computed(() => [
  { value: 'config' as const, label: '配置' },
  { value: 'versions' as const, label: '版本记录', count: props.versionCount },
  { value: 'usages' as const, label: props.usageLabel, count: props.usageCount },
])
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

    <div v-if="showTabs" class="management-tabs" role="tablist" aria-label="编辑页内容">
      <button
        v-for="item in tabs"
        :key="item.value"
        type="button"
        role="tab"
        class="management-tab"
        :aria-selected="tab === item.value"
        @click="tab = item.value"
      >
        {{ item.label }}
        <span v-if="item.count !== undefined" class="management-tab-count">{{ item.count }}</span>
      </button>
    </div>

    <div v-if="tab === 'config'" class="config-editor-grid">
      <div class="config-editor-main">
        <slot name="main" />
      </div>
      <aside class="config-editor-aside">
        <slot name="aside" />
      </aside>
    </div>
    <section v-else-if="tab === 'versions'" class="management-card management-table-card">
      <slot name="versions" />
    </section>
    <section v-else class="management-card">
      <slot name="usages" />
    </section>
  </div>
</template>
