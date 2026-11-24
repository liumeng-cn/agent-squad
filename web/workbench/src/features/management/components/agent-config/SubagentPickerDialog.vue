<script setup lang="ts">
import { ElButton, ElCheckbox, ElInput, ElTag } from 'element-plus'
import { computed, ref } from 'vue'
import type { SubagentOption } from '../../model/agentConfig'
import { versionLabel } from '../../model/agentConfigLabels'
import ManagementDialog from '../ManagementDialog.vue'

/**
 * 添加 Sub 委派：仅列出未删除、启用且最新配置就绪的 Sub，按业务 ID 唯一选择。
 */
const props = defineProps<{
  options: SubagentOption[]
  excludedAgentIds: string[]
}>()
const emit = defineEmits<{ close: []; add: [options: SubagentOption[]] }>()

const keyword = ref('')
const selected = ref<string[]>([])

const choices = computed(() =>
  props.options
    .filter(option => !props.excludedAgentIds.includes(option.agentId))
    .sort((left, right) => left.agentName.localeCompare(right.agentName, 'zh-CN')),
)

const visible = computed(() => {
  const text = keyword.value.trim().toLowerCase()
  if (!text) {
    return choices.value
  }
  return choices.value.filter(option =>
    [option.agentName, option.displayAlias, option.agentCode, option.description].some(value =>
      (value || '').toLowerCase().includes(text),
    ),
  )
})

function toggle(agentId: string, checked: boolean) {
  selected.value = checked ? [...selected.value, agentId] : selected.value.filter(id => id !== agentId)
}

function confirm() {
  const picked = choices.value.filter(option => selected.value.includes(option.agentId))
  emit('add', picked)
}
</script>

<template>
  <ManagementDialog title="添加 Sub 委派" wide :busy="false" :dirty="false" @close="emit('close')">
    <p class="management-help">
      仅列出已启用且配置就绪的 Sub，默认关联其最新配置版本；发布 Master 时定格最终版本。
    </p>

    <div class="config-picker-toolbar">
      <ElInput v-model="keyword" maxlength="120" placeholder="搜索名称、别称、编码或职责描述" aria-label="搜索 Sub" />
    </div>

    <div v-if="!choices.length" class="management-empty">
      <p class="management-empty-title">暂无可委派的 Sub</p>
      <p class="management-empty-desc">Sub 需启用且职责描述与提示词就绪后才可被委派，已委派的 Sub 不会重复出现。</p>
      <div class="management-empty-actions">
        <RouterLink to="/management/agents/new?type=SUB_AGENT">新增 Sub</RouterLink>
      </div>
    </div>
    <div v-else-if="!visible.length" class="management-empty is-compact">没有符合条件的 Sub</div>
    <div v-else class="config-picker-list">
      <div
        v-for="option in visible"
        :key="option.agentId"
        class="config-picker-item"
        :class="{ 'is-selected': selected.includes(option.agentId) }"
        @click="toggle(option.agentId, !selected.includes(option.agentId))"
      >
        <ElCheckbox
          @click.stop
          :aria-label="option.agentName"
          :model-value="selected.includes(option.agentId)"
          @change="toggle(option.agentId, Boolean($event))"
        />
        <span class="config-picker-main">
          <span class="config-picker-title">
            {{ option.agentName }}
            <span v-if="option.displayAlias" class="management-muted">（{{ option.displayAlias }}）</span>
            <span class="management-code">{{ option.agentCode }}</span>
          </span>
          <span class="config-picker-desc">{{ option.description || '未填写职责描述' }}</span>
        </span>
        <span class="management-tags">
          <ElTag class="management-tag is-success" type="success">{{ versionLabel(option.latestVersionNo) }}</ElTag>
          <span class="management-muted">{{ option.toolCount }} 个工具</span>
        </span>
      </div>
    </div>

    <template #actions>
      <ElButton type="primary" native-type="button" class="management-primary" :disabled="!selected.length" @click="confirm">
        添加{{ selected.length ? ` ${selected.length} 个` : '' }}Sub
      </ElButton>
    </template>
  </ManagementDialog>
</template>
