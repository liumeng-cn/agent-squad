<script setup lang="ts">
import { ElButton, ElInput, ElTag } from 'element-plus'
import type { DelegationCardInfo } from '../../model/agentBindings'
import type { AgentDelegationDraft } from '../../model/agentConfig'
import { DESCRIPTION_LIMIT, versionLabel } from '../../model/agentConfigLabels'

/**
 * Master 委派卡片：可维护委派名称、前端展示别称覆盖与委派说明覆盖。
 */
defineProps<{
  delegation: AgentDelegationDraft
  info: DelegationCardInfo
  nameError: string
  readonly: boolean
}>()
const emit = defineEmits<{
  update: [next: AgentDelegationDraft]
  remove: []
}>()
</script>

<template>
  <article class="config-binding" :class="{ 'is-invalid': !info.usable || Boolean(nameError) }">
    <div class="config-binding-head">
      <div class="config-binding-title">
        <strong>{{ info.agentName }}</strong>
        <span class="management-code">{{ info.agentCode }}</span>
        <ElTag class="management-tag is-success" type="success">最新 {{ versionLabel(info.versionNo) }}</ElTag>
        <span class="management-muted">{{ info.toolCount }} 个工具</span>
        <ElTag v-if="!info.usable" class="management-tag is-danger" type="danger">
          {{ info.unusableReason || '不可用' }}
        </ElTag>
      </div>
      <div class="config-binding-controls">
        <ElButton v-if="!readonly" type="danger" link native-type="button" @click="emit('remove')">移除</ElButton>
      </div>
    </div>

    <div class="management-form-grid" style="margin-top: 8px">
      <label class="management-field">
        <span class="management-field-label">委派名称 <span class="management-required" /></span>
        <ElInput
          :model-value="delegation.delegationName"
          :disabled="readonly"
          maxlength="80"
          placeholder="如 order_specialist"
          @update:model-value="emit('update', { ...delegation, delegationName: String($event) })"
        />
        <span v-if="nameError" class="management-field-error">{{ nameError }}</span>
      </label>

      <label class="management-field">
        <span class="management-field-label">前端展示别称</span>
        <ElInput
          :model-value="delegation.displayAlias"
          :disabled="readonly"
          maxlength="60"
          :placeholder="info.agentDisplayAlias || info.agentName"
          @update:model-value="emit('update', { ...delegation, displayAlias: String($event) })"
        />
      </label>

      <label class="management-field is-full">
        <span class="management-field-label">委派说明（留空沿用 Sub 职责描述）</span>
        <ElInput
          :model-value="delegation.delegationDescription"
          :disabled="readonly"
          :maxlength="DESCRIPTION_LIMIT"
          :placeholder="info.description || '描述何时委派给该 Sub'"
          @update:model-value="emit('update', { ...delegation, delegationDescription: String($event) })"
        />
      </label>
    </div>
  </article>
</template>
