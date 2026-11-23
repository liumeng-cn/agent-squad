<script setup lang="ts">
import { computed, ref } from 'vue'
import type { SubagentOption } from '../../model/agentConfig'
import { versionLabel } from '../../model/agentConfigLabels'
import ManagementDialog from '../ManagementDialog.vue'

/**
 * 添加子 Agent 委派：从启用子 Agent 的已发布版本中多选，默认使用子 Agent 当前生效的版本。
 * 当前 Agent 自身与已委派的子 Agent 不会出现在列表中。
 */
const props = defineProps<{ options: SubagentOption[]; excludedAgentIds: string[] }>()
const emit = defineEmits<{ close: []; add: [options: SubagentOption[]] }>()

interface SubagentChoice { preferred: SubagentOption; versionCount: number }

const keyword = ref('')
const selected = ref<string[]>([])

const choices = computed<SubagentChoice[]>(() => {
  const byAgent = new Map<string, SubagentOption[]>()
  for (const option of props.options) {
    const list = byAgent.get(option.agentId) ?? []
    list.push(option)
    byAgent.set(option.agentId, list)
  }

  const result: SubagentChoice[] = []
  for (const [agentId, list] of byAgent) {
    if (props.excludedAgentIds.includes(agentId)) {
      continue
    }
    const sorted = [...list].sort((left, right) => right.versionNo - left.versionNo)
    const preferred = sorted.find(option => option.current) ?? sorted[0]!
    result.push({ preferred, versionCount: list.length })
  }
  return result.sort((left, right) => left.preferred.agentName.localeCompare(right.preferred.agentName, 'zh-CN'))
})

const visible = computed(() => {
  const text = keyword.value.trim().toLowerCase()
  if (!text) {
    return choices.value
  }
  return choices.value.filter(choice => [choice.preferred.agentName, choice.preferred.agentCode, choice.preferred.description]
    .some(value => value.toLowerCase().includes(text)))
})

function toggle(agentId: string, checked: boolean) {
  selected.value = checked ? [...selected.value, agentId] : selected.value.filter(id => id !== agentId)
}

function confirm() {
  const picked = choices.value
    .filter(choice => selected.value.includes(choice.preferred.agentId))
    .map(choice => choice.preferred)
  emit('add', picked)
}
</script>

<template>
  <ManagementDialog title="添加子 Agent 委派" wide :busy="false" :dirty="false" @close="emit('close')">
    <p class="management-help">只列出启用子 Agent 的已发布版本，默认使用其当前生效版本。主 Agent 依据委派描述判断何时交给该子 Agent。</p>

    <div class="config-picker-toolbar">
      <input v-model="keyword" maxlength="120" placeholder="搜索名称、编码或职责描述" aria-label="搜索子 Agent" />
    </div>

    <div v-if="!choices.length" class="management-empty">
      <p class="management-empty-title">暂无可委派的子 Agent</p>
      <p class="management-empty-desc">子 Agent 需要发布且启用后才能被委派，已委派的子 Agent 不会重复出现。</p>
      <div class="management-empty-actions">
        <RouterLink to="/management/agents/new?type=SUB_AGENT">新增子 Agent</RouterLink>
      </div>
    </div>
    <div v-else-if="!visible.length" class="management-empty is-compact">没有符合条件的子 Agent</div>
    <div v-else class="config-picker-list">
      <label
        v-for="choice in visible"
        :key="choice.preferred.agentId"
        class="config-picker-item"
        :class="{ 'is-selected': selected.includes(choice.preferred.agentId) }"
      >
        <input
          type="checkbox"
          :checked="selected.includes(choice.preferred.agentId)"
          @change="toggle(choice.preferred.agentId, ($event.target as HTMLInputElement).checked)"
        />
        <span class="config-picker-main">
          <span class="config-picker-title">
            {{ choice.preferred.agentName }}
            <span class="management-code">{{ choice.preferred.agentCode }}</span>
          </span>
          <span class="config-picker-desc">{{ choice.preferred.description || '未填写职责描述' }}</span>
        </span>
        <span class="management-tags">
          <span class="management-tag is-success">{{ versionLabel(choice.preferred.versionNo) }}</span>
          <span v-if="choice.preferred.current" class="management-tag is-primary">当前生效</span>
          <span v-if="choice.versionCount > 1" class="management-muted">共 {{ choice.versionCount }} 个可用版本</span>
        </span>
      </label>
    </div>

    <template #actions>
      <button type="button" class="management-primary" :disabled="!selected.length" @click="confirm">
        添加{{ selected.length ? ` ${selected.length} 个` : '' }}子 Agent
      </button>
    </template>
  </ManagementDialog>
</template>
