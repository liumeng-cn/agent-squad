<script setup lang="ts">
import { ElCheckbox, ElRadio, ElTag } from 'element-plus'
import type { McpRemoteTool } from '../../model/agentConfig'

/**
 * 远程工具列表：支持单选（在工具页绑定一个远程工具）或多选（在 MCP 页批量勾选一键导入为平台工具）。
 */
const props = defineProps<{
  tools: McpRemoteTool[]
  selectable?: boolean
  selected?: string | null
  multiSelectable?: boolean
  selectedNames?: string[]
  importedNames?: string[]
  disabled?: boolean
}>()
const emit = defineEmits<{
  select: [tool: McpRemoteTool]
  toggle: [name: string]
}>()

function paramCount(tool: McpRemoteTool): number {
  const properties = tool.inputSchema?.properties
  return properties && typeof properties === 'object'
    ? Object.keys(properties).length
    : 0
}

function isImported(name: string): boolean {
  return Boolean(props.importedNames?.includes(name))
}

function isChecked(name: string): boolean {
  return Boolean(props.selectedNames?.includes(name))
}

function choose(tool: McpRemoteTool) {
  if (props.disabled) return
  if (props.multiSelectable) {
    if (!isImported(tool.name)) {
      emit('toggle', tool.name)
    }
    return
  }
  if (props.selectable) {
    emit('select', tool)
  }
}
</script>

<template>
  <div
    class="config-picker-list"
    :role="selectable ? 'radiogroup' : 'list'"
    aria-label="远程工具"
  >
    <div
      v-for="tool in tools"
      :key="tool.name"
      class="config-picker-item"
      :class="{
        'is-selected':
          (selectable && selected === tool.name) ||
          (multiSelectable && isChecked(tool.name)),
        'is-disabled':
          ((selectable || multiSelectable) && disabled) ||
          (multiSelectable && isImported(tool.name)),
      }"
      :role="selectable ? undefined : 'listitem'"
      @click="choose(tool)"
    >
      <ElCheckbox
        v-if="multiSelectable"
        :model-value="isChecked(tool.name) || isImported(tool.name)"
        :disabled="disabled || isImported(tool.name)"
        :aria-label="tool.name"
        @click.stop
        @change="choose(tool)"
      />
      <ElRadio
        v-else-if="selectable"
        name="remote-tool"
        :aria-label="tool.name"
        :model-value="selected ?? ''"
        :value="tool.name"
        :disabled="disabled"
        @click.stop
        @change="choose(tool)"
      />
      <span v-else />

      <span class="config-picker-main">
        <span class="config-picker-title">
          <span class="management-code">{{ tool.name }}</span>
          <span v-if="tool.title" class="management-muted">{{ tool.title }}</span>
        </span>
        <span class="config-picker-desc">{{ tool.description || '服务未提供描述' }}</span>
      </span>

      <span class="management-tags">
        <ElTag v-if="isImported(tool.name)" size="small" type="success">已导入</ElTag>
        <ElTag class="management-tag" size="small" type="info">{{ paramCount(tool) }} 个参数</ElTag>
        <ElTag v-if="tool.readOnly" class="management-tag is-success" size="small" type="success">只读</ElTag>
      </span>
    </div>
  </div>
</template>
