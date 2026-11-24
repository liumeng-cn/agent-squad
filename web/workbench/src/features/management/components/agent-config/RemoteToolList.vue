<script setup lang="ts">
import { ElRadio, ElTag } from 'element-plus'
import type { McpRemoteTool } from '../../model/agentConfig'

/**
 * 远程工具列表：展示 MCP发现的工具；selectable 时可单选一个工具带出定义。
 */
const props = defineProps<{
  tools: McpRemoteTool[]
  selectable?: boolean
  selected?: string | null
  disabled?: boolean
}>()
const emit = defineEmits<{ select: [tool: McpRemoteTool] }>()

function paramCount(tool: McpRemoteTool): number {
  const properties = tool.inputSchema?.properties
  return properties && typeof properties === 'object'
    ? Object.keys(properties).length
    : 0
}

function choose(tool: McpRemoteTool) {
  if (props.selectable && !props.disabled) {
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
    <component
      :is="'div'"
      v-for="tool in tools"
      :key="tool.name"
      class="config-picker-item"
      @click="choose(tool)"
      :class="{
        'is-selected': selectable && selected === tool.name,
        'is-disabled': selectable && disabled,
      }"
      :role="selectable ? undefined : 'listitem'"
    >
      <ElRadio
        @click.stop
        :aria-label="tool.name"
        v-if="selectable"
        name="remote-tool"
        :model-value="selected ?? ''"
        :value="tool.name"
        :disabled="disabled"
        @change="choose(tool)"
      />
      <span v-else />
      <span class="config-picker-main">
        <span class="config-picker-title">
          <span class="management-code">{{ tool.name }}</span>
          <span v-if="tool.title" class="management-muted">{{
            tool.title
          }}</span>
        </span>
        <span class="config-picker-desc">{{
          tool.description || '服务未提供描述'
        }}</span>
      </span>
      <span class="management-tags">
        <ElTag class="management-tag" type="info"
          >{{ paramCount(tool) }} 个参数</ElTag
        >
        <ElTag
          v-if="tool.readOnly"
          class="management-tag is-success"
          type="success"
          >只读</ElTag
        >
      </span>
    </component>
  </div>
</template>
