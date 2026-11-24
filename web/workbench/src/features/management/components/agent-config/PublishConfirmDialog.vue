<script setup lang="ts">
import { ElAlert, ElButton, ElTable, ElTableColumn, ElTag } from 'element-plus'
import type { AgentPublishDependency, AgentPublishPreview } from '../../model/agentConfig'
import { versionLabel } from '../../model/agentConfigLabels'
import ManagementDialog from '../ManagementDialog.vue'

/**
 * Master 发布确认弹窗：展示全链路即将定格的配置版本清单与校验结果。
 */
defineProps<{
  preview: AgentPublishPreview
  busy: boolean
}>()
const emit = defineEmits<{ close: []; confirm: [] }>()

const kindLabels: Record<AgentPublishDependency['resourceKind'], string> = {
  MASTER: 'Master',
  SUB_AGENT: 'Sub',
  TOOL: '工具',
  MCP: 'MCP',
}
</script>

<template>
  <ManagementDialog
    :title="`发布 Master：${preview.agentName}`"
    wide
    :busy="busy"
    :dirty="false"
    @close="emit('close')"
  >
    <p class="management-help">
      发布后将定格下方全部组件的确切配置版本，生成新的不可变发布快照并立即生效；上游后续修改不影响本次发布。
      <span v-if="preview.currentReleaseNo != null">
        当前生效为 Release #{{ preview.currentReleaseNo }}，确认后将替换为 Release #{{ preview.currentReleaseNo + 1 }}。
      </span>
    </p>

    <ElAlert
      v-if="!preview.publishable"
      type="error"
      :closable="false"
      show-icon
      title="以下依赖或配置未满足发布条件，请修复后再发布"
      style="margin-bottom: 12px"
    >
      <ul style="margin: 4px 0 0; padding-left: 18px">
        <li v-for="(err, idx) in preview.errors" :key="idx">{{ err }}</li>
      </ul>
    </ElAlert>

    <div class="management-table-wrap">
      <ElTable :data="preview.dependencies" stripe style="width: 100%" empty-text="无依赖项">
        <!-- @vue-generic {AgentPublishDependency} -->
        <ElTableColumn label="类型" width="90">
          <template #default="{ row }: { row: AgentPublishDependency }">
            <ElTag class="management-tag" type="info">{{ kindLabels[row.resourceKind] }}</ElTag>
          </template>
        </ElTableColumn>
        <!-- @vue-generic {AgentPublishDependency} -->
        <ElTableColumn label="组件名称" min-width="180">
          <template #default="{ row }: { row: AgentPublishDependency }">
            <div>
              <span class="management-cell-title">{{ row.resourceName }}</span>
              <span v-if="row.displayAlias" class="management-muted">（{{ row.displayAlias }}）</span>
            </div>
          </template>
        </ElTableColumn>
        <!-- @vue-generic {AgentPublishDependency} -->
        <ElTableColumn label="定格版本" width="110">
          <template #default="{ row }: { row: AgentPublishDependency }">
            <ElTag v-if="row.versionNo != null" class="management-tag is-success" type="success">
              {{ versionLabel(row.versionNo) }}
            </ElTag>
            <span v-else class="management-muted">最新就绪</span>
          </template>
        </ElTableColumn>
        <!-- @vue-generic {AgentPublishDependency} -->
        <ElTableColumn label="归属" min-width="160" prop="scope" />
      </ElTable>
    </div>

    <template #actions>
      <ElButton
        type="primary"
        native-type="button"
        class="management-primary"
        :disabled="busy || !preview.publishable"
        :loading="busy"
        @click="emit('confirm')"
      >
        确认发布并定格
      </ElButton>
    </template>
  </ManagementDialog>
</template>
