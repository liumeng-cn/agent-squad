<script setup lang="ts">
import { toolTypeLabels, versionLabel } from '../../model/agentConfigLabels'
import type { ToolCardInfo } from '../../model/agentBindings'

/**
 * 已绑定工具卡片：展示工具定义、选择绑定版本，提示可升级或不可用。
 */
const props = defineProps<{ info: ToolCardInfo; readOnly: boolean; duplicated: boolean }>()
const emit = defineEmits<{ change: [toolVersionId: string]; remove: [] }>()

function upgrade() {
  const latest = props.info.versions.find(option => option.versionNo === props.info.latestVersionNo)
  if (latest) {
    emit('change', latest.toolVersionId)
  }
}
</script>

<template>
  <li class="config-binding" :class="{ 'is-unusable': !info.usable || duplicated }">
    <div class="config-binding-head">
      <div class="config-binding-title">
        <RouterLink v-if="info.toolId" :to="`/management/tools/${info.toolId}`">{{ info.toolName }}</RouterLink>
        <span v-else>{{ info.toolName }}</span>
        <span class="management-code">{{ info.exposedName }}</span>
        <span class="management-tag">{{ toolTypeLabels[info.toolType] }}</span>
      </div>
      <button v-if="!readOnly" type="button" class="management-link management-danger" @click="emit('remove')">移除</button>
    </div>
    <p v-if="info.description" class="config-binding-desc">{{ info.description }}</p>
    <div class="config-binding-fields">
      <label class="management-field">
        <span class="management-field-label">绑定版本</span>
        <select
          :value="info.toolVersionId"
          :disabled="readOnly"
          @change="emit('change', ($event.target as HTMLSelectElement).value)"
        >
          <option v-if="!info.versions.some(option => option.toolVersionId === info.toolVersionId)" :value="info.toolVersionId">
            {{ versionLabel(info.versionNo) }}（不可用）
          </option>
          <option v-for="option in info.versions" :key="option.toolVersionId" :value="option.toolVersionId">
            {{ versionLabel(option.versionNo) }}{{ option.latest ? '（最新）' : '' }}
          </option>
        </select>
      </label>
      <div v-if="!info.usable" class="config-binding-note is-danger">
        该版本已不可用（工具停用或依赖失效），请切换版本或移除，否则无法发布。
      </div>
      <div v-else-if="duplicated" class="config-binding-note is-danger">
        模型工具名与其他已绑定工具重复，同一 Agent 内不能重复。
      </div>
      <div
        v-else-if="info.latestVersionNo != null && info.versionNo != null && info.latestVersionNo > info.versionNo"
        class="config-binding-note is-warning"
      >
        可升级到 {{ versionLabel(info.latestVersionNo) }}
        <button v-if="!readOnly" type="button" class="management-link" @click="upgrade">升级</button>
      </div>
      <div v-else class="config-binding-note">已是最新发布版本</div>
    </div>
  </li>
</template>
