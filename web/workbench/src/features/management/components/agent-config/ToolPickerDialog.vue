<script setup lang="ts">
import { ElButton, ElCheckbox, ElInput, ElOption, ElSelect, ElTag } from 'element-plus'
import { computed, ref } from 'vue'
import type { ToolOption, ToolType } from '../../model/agentConfig'
import { toolTypeLabels, toolTypes, versionLabel } from '../../model/agentConfigLabels'
import ManagementDialog from '../ManagementDialog.vue'

/**
 * 添加工具：仅列出未删除、启用且最新配置就绪的工具，按工具业务 ID 唯一选择，默认使用最新版本。
 */
const props = defineProps<{
  options: ToolOption[]
  excludedToolIds: string[]
}>()
const emit = defineEmits<{ close: []; add: [toolIds: string[]] }>()

const keyword = ref('')
const type = ref<'' | ToolType>('')
const selected = ref<string[]>([])

const choices = computed(() =>
  props.options
    .filter(option => !props.excludedToolIds.includes(option.toolId))
    .sort((left, right) => left.toolName.localeCompare(right.toolName, 'zh-CN')),
)

const visible = computed(() => {
  const text = keyword.value.trim().toLowerCase()
  return choices.value.filter(option => {
    const typeMatched = !type.value || option.toolType === type.value
    const keywordMatched =
      !text ||
      [option.toolName, option.displayAlias, option.toolCode, option.exposedName, option.description].some(value =>
        (value || '').toLowerCase().includes(text),
      )
    return typeMatched && keywordMatched
  })
})

function toggle(toolId: string, checked: boolean) {
  selected.value = checked ? [...selected.value, toolId] : selected.value.filter(id => id !== toolId)
}

function confirm() {
  emit('add', [...selected.value])
}
</script>

<template>
  <ManagementDialog title="添加工具" wide :busy="false" :dirty="false" @close="emit('close')">
    <p class="management-help">
      仅列出已启用且配置就绪的工具，按资源默认关联最新配置版本；发布 Master 时定格最终版本。
    </p>

    <div class="config-picker-toolbar">
      <ElInput v-model="keyword" maxlength="120" placeholder="搜索名称、别称、编码、模型工具名或描述" aria-label="搜索工具" />
      <ElSelect :empty-values="[null, undefined]" v-model="type" aria-label="工具类型">
        <ElOption value="" label="全部类型" />
        <ElOption v-for="item in toolTypes" :key="item" :value="item" :label="toolTypeLabels[item]" />
      </ElSelect>
    </div>

    <div v-if="!choices.length" class="management-empty">
      <p class="management-empty-title">暂无可添加的工具</p>
      <p class="management-empty-desc">工具需启用且必填项配置就绪后才可关联，已关联的工具不会重复出现。</p>
      <div class="management-empty-actions">
        <RouterLink to="/management/tools">前往工具管理</RouterLink>
      </div>
    </div>
    <div v-else-if="!visible.length" class="management-empty is-compact">没有符合条件的工具</div>
    <div v-else class="config-picker-list">
      <div
        v-for="option in visible"
        :key="option.toolId"
        class="config-picker-item"
        :class="{ 'is-selected': selected.includes(option.toolId) }"
        @click="toggle(option.toolId, !selected.includes(option.toolId))"
      >
        <ElCheckbox
          @click.stop
          :aria-label="option.toolName"
          :model-value="selected.includes(option.toolId)"
          @change="toggle(option.toolId, Boolean($event))"
        />
        <span class="config-picker-main">
          <span class="config-picker-title">
            {{ option.toolName }}
            <span v-if="option.displayAlias" class="management-muted">（{{ option.displayAlias }}）</span>
            <span class="management-code">{{ option.exposedName }}</span>
          </span>
          <span class="config-picker-desc">{{ option.description || '未填写调用说明' }}</span>
        </span>
        <span class="management-tags">
          <ElTag class="management-tag" type="info">{{ toolTypeLabels[option.toolType] }}</ElTag>
          <ElTag class="management-tag is-success" type="success">{{ versionLabel(option.latestVersionNo) }}</ElTag>
        </span>
      </div>
    </div>

    <template #actions>
      <ElButton type="primary" native-type="button" class="management-primary" :disabled="!selected.length" @click="confirm">
        添加{{ selected.length ? ` ${selected.length} 个` : '' }}工具
      </ElButton>
    </template>
  </ManagementDialog>
</template>
