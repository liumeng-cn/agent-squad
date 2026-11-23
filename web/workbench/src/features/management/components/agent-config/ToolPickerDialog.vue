<script setup lang="ts">
import { computed, ref } from 'vue'
import type { ToolOption, ToolType } from '../../model/agentConfig'
import { toolTypeLabels, toolTypes, versionLabel } from '../../model/agentConfigLabels'
import ManagementDialog from '../ManagementDialog.vue'

/**
 * 添加工具：从已发布、启用且依赖可用的工具中多选，默认绑定每个工具的最新发布版本。
 * 已绑定的工具不再出现在列表中，换版本在工具卡片上操作。
 */
const props = defineProps<{ options: ToolOption[]; excludedToolIds: string[] }>()
const emit = defineEmits<{ close: []; add: [toolVersionIds: string[]] }>()

interface ToolChoice { latest: ToolOption; versionCount: number }

const keyword = ref('')
const type = ref<'' | ToolType>('')
const selected = ref<string[]>([])

/** 每个工具只展示一项，取最新发布版本。 */
const choices = computed<ToolChoice[]>(() => {
  const byTool = new Map<string, ToolOption[]>()
  for (const option of props.options) {
    const list = byTool.get(option.toolId) ?? []
    list.push(option)
    byTool.set(option.toolId, list)
  }

  const result: ToolChoice[] = []
  for (const [toolId, list] of byTool) {
    if (props.excludedToolIds.includes(toolId)) {
      continue
    }
    const sorted = [...list].sort((left, right) => right.versionNo - left.versionNo)
    const latest = sorted.find(option => option.latest) ?? sorted[0]!
    result.push({ latest, versionCount: list.length })
  }
  return result.sort((left, right) => left.latest.toolName.localeCompare(right.latest.toolName, 'zh-CN'))
})

const visible = computed(() => {
  const text = keyword.value.trim().toLowerCase()
  return choices.value.filter(choice => {
    const option = choice.latest
    const typeMatched = !type.value || option.toolType === type.value
    const keywordMatched = !text || [option.toolName, option.toolCode, option.exposedName, option.description]
      .some(value => value.toLowerCase().includes(text))
    return typeMatched && keywordMatched
  })
})

function toggle(toolId: string, checked: boolean) {
  selected.value = checked ? [...selected.value, toolId] : selected.value.filter(id => id !== toolId)
}

function confirm() {
  const versionIds = choices.value
    .filter(choice => selected.value.includes(choice.latest.toolId))
    .map(choice => choice.latest.toolVersionId)
  emit('add', versionIds)
}
</script>

<template>
  <ManagementDialog title="添加工具" wide :busy="false" :dirty="false" @close="emit('close')">
    <p class="management-help">只列出已发布且启用的工具，默认绑定最新发布版本；工具发布新版本后，Agent 需要在卡片上手动升级。</p>

    <div class="config-picker-toolbar">
      <input v-model="keyword" maxlength="120" placeholder="搜索名称、编码、模型工具名或描述" aria-label="搜索工具" />
      <select v-model="type" aria-label="工具类型">
        <option value="">全部类型</option>
        <option v-for="item in toolTypes" :key="item" :value="item">{{ toolTypeLabels[item] }}</option>
      </select>
    </div>

    <div v-if="!choices.length" class="management-empty">
      <p class="management-empty-title">暂无可添加的工具</p>
      <p class="management-empty-desc">工具需要发布且启用后才能被 Agent 绑定，已绑定的工具不会重复出现。</p>
      <div class="management-empty-actions">
        <RouterLink to="/management/tools">前往工具</RouterLink>
      </div>
    </div>
    <div v-else-if="!visible.length" class="management-empty is-compact">没有符合条件的工具</div>
    <div v-else class="config-picker-list">
      <label
        v-for="choice in visible"
        :key="choice.latest.toolId"
        class="config-picker-item"
        :class="{ 'is-selected': selected.includes(choice.latest.toolId) }"
      >
        <input
          type="checkbox"
          :checked="selected.includes(choice.latest.toolId)"
          @change="toggle(choice.latest.toolId, ($event.target as HTMLInputElement).checked)"
        />
        <span class="config-picker-main">
          <span class="config-picker-title">
            {{ choice.latest.toolName }}
            <span class="management-code">{{ choice.latest.exposedName }}</span>
          </span>
          <span class="config-picker-desc">{{ choice.latest.description || '未填写描述' }}</span>
        </span>
        <span class="management-tags">
          <span class="management-tag">{{ toolTypeLabels[choice.latest.toolType] }}</span>
          <span class="management-tag is-success">{{ versionLabel(choice.latest.versionNo) }}</span>
          <span v-if="choice.versionCount > 1" class="management-muted">共 {{ choice.versionCount }} 个可用版本</span>
        </span>
      </label>
    </div>

    <template #actions>
      <button type="button" class="management-primary" :disabled="!selected.length" @click="confirm">
        添加{{ selected.length ? ` ${selected.length} 个` : '' }}工具
      </button>
    </template>
  </ManagementDialog>
</template>
