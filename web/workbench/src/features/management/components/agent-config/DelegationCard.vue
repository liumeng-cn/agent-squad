<script setup lang="ts">
import type { AgentDelegationDraft } from '../../model/agentConfig'
import { DELEGATION_NAME_PATTERN, DESCRIPTION_LIMIT, versionLabel } from '../../model/agentConfigLabels'
import type { DelegationCardInfo } from '../../model/agentBindings'

/**
 * 子 Agent 委派卡片：选择委派版本，填写主 Agent 内唯一的委派名称和委派描述。
 * 委派描述决定主 Agent 何时把任务交给该子 Agent，默认取子 Agent 的职责描述。
 */
const delegation = defineModel<AgentDelegationDraft>({ required: true })
const props = defineProps<{ info: DelegationCardInfo; readOnly: boolean; duplicated: boolean }>()
const emit = defineEmits<{ remove: [] }>()

function useCurrent() {
  const current = props.info.versions.find(option => option.current)
  if (current) {
    delegation.value = { ...delegation.value, subagentVersionId: current.subagentVersionId }
  }
}

function update(patch: Partial<AgentDelegationDraft>) {
  delegation.value = { ...delegation.value, ...patch }
}
</script>

<template>
  <li class="config-binding" :class="{ 'is-unusable': !info.usable }">
    <div class="config-binding-head">
      <div class="config-binding-title">
        <RouterLink v-if="info.agentId" :to="`/management/agents/${info.agentId}`">{{ info.agentName }}</RouterLink>
        <span v-else>{{ info.agentName }}</span>
        <span class="management-code">{{ info.agentCode }}</span>
      </div>
      <button v-if="!readOnly" type="button" class="management-link management-danger" @click="emit('remove')">移除</button>
    </div>
    <p v-if="info.description" class="config-binding-desc">职责：{{ info.description }}</p>

    <div class="config-binding-fields is-delegation">
      <label class="management-field">
        <span class="management-field-label">委派版本</span>
        <select
          :value="delegation.subagentVersionId"
          :disabled="readOnly"
          @change="update({ subagentVersionId: ($event.target as HTMLSelectElement).value })"
        >
          <option
            v-if="!info.versions.some(option => option.subagentVersionId === delegation.subagentVersionId)"
            :value="delegation.subagentVersionId"
          >
            {{ versionLabel(info.versionNo) }}（不可用）
          </option>
          <option v-for="option in info.versions" :key="option.subagentVersionId" :value="option.subagentVersionId">
            {{ versionLabel(option.versionNo) }}{{ option.current ? '（当前生效）' : '' }}
          </option>
        </select>
      </label>
      <label class="management-field">
        <span class="management-field-label management-required">委派名称</span>
        <input
          :value="delegation.delegationName"
          maxlength="80"
          :readonly="readOnly"
          :aria-invalid="!DELEGATION_NAME_PATTERN.test(delegation.delegationName) || duplicated"
          @input="update({ delegationName: ($event.target as HTMLInputElement).value.trim() })"
        />
      </label>
      <label class="management-field">
        <span class="management-field-label">委派描述</span>
        <input
          :value="delegation.delegationDescription"
          :maxlength="DESCRIPTION_LIMIT"
          :readonly="readOnly"
          placeholder="何时把任务交给该子 Agent"
          @input="update({ delegationDescription: ($event.target as HTMLInputElement).value })"
        />
      </label>
    </div>

    <p v-if="!info.usable" class="config-binding-note is-danger">该版本已不可用（子 Agent 停用或版本失效），请切换版本或移除，否则无法发布。</p>
    <p v-else-if="duplicated" class="config-binding-note is-danger">委派名称在当前主 Agent 内重复。</p>
    <p v-else-if="!DELEGATION_NAME_PATTERN.test(delegation.delegationName)" class="config-binding-note is-danger">
      委派名称只能包含字母、数字、下划线和连字符。
    </p>
    <p
      v-else-if="info.currentVersionNo != null && info.versionNo != null && info.currentVersionNo !== info.versionNo"
      class="config-binding-note is-warning"
    >
      子 Agent 当前生效版本为 {{ versionLabel(info.currentVersionNo) }}
      <button v-if="!readOnly" type="button" class="management-link" @click="useCurrent">切换到生效版本</button>
    </p>
  </li>
</template>
