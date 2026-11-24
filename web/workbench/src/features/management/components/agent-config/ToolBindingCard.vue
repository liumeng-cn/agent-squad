<script setup lang="ts">
import { ElButton, ElTag } from 'element-plus'
import type { ToolCardInfo } from '../../model/agentBindings'
import { toolTypeLabels, versionLabel } from '../../model/agentConfigLabels'

/**
 * 已绑定工具卡片：按业务 ID 关联最新配置版本，展示别称与可用性，不提供手工选版本号。
 */
defineProps<{
  info: ToolCardInfo
  duplicated: boolean
  readonly: boolean
}>()
const emit = defineEmits<{ remove: [] }>()
</script>

<template>
  <article class="config-binding" :class="{ 'is-invalid': !info.usable || duplicated }">
    <div class="config-binding-head">
      <div class="config-binding-title">
        <strong>{{ info.toolName }}</strong>
        <span v-if="info.displayAlias" class="management-muted">（{{ info.displayAlias }}）</span>
        <span class="management-code">{{ info.exposedName || '未命名' }}</span>
        <ElTag class="management-tag" type="info">{{ toolTypeLabels[info.toolType] }}</ElTag>
        <ElTag class="management-tag is-success" type="success">最新 {{ versionLabel(info.versionNo) }}</ElTag>
        <ElTag v-if="!info.usable" class="management-tag is-danger" type="danger">
          {{ info.unusableReason || '不可用' }}
        </ElTag>
        <ElTag v-if="duplicated" class="management-tag is-danger" type="danger">模型工具名冲突</ElTag>
      </div>
      <div class="config-binding-controls">
        <ElButton v-if="!readonly" type="danger" link native-type="button" @click="emit('remove')">移除</ElButton>
      </div>
    </div>
    <p class="config-binding-desc">{{ info.description || '暂无调用说明' }}</p>
  </article>
</template>
