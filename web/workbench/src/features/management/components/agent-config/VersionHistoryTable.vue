<script setup lang="ts">
import { ElButton, ElTable, ElTableColumn, ElTag } from 'element-plus'
import type { VersionSummary } from '../../model/agentConfig'
import { formatTime, readinessLabels, readinessTones, versionLabel } from '../../model/agentConfigLabels'
import { tagType } from '../../model/elementTheme'

/**
 * 配置历史版本表：每次保存新增一条不可变配置版本，仅提供只读查看。
 */
defineProps<{
  versions: VersionSummary[]
  latestVersionId: string | null
  busy: boolean
}>()
const emit = defineEmits<{ inspect: [versionId: string] }>()
</script>

<template>
  <div class="management-table-wrap">
    <ElTable :data="versions" stripe style="width: 100%" empty-text="暂无配置历史">
      <!-- @vue-generic {VersionSummary} -->
      <ElTableColumn label="配置版本" min-width="140">
        <template #default="{ row }: { row: VersionSummary }">
          <span class="management-tags">
            <span class="management-cell-title">{{ versionLabel(row.versionNo) }}</span>
            <ElTag v-if="row.versionId === latestVersionId" class="management-tag is-primary" type="primary">最新配置</ElTag>
          </span>
        </template>
      </ElTableColumn>
      <!-- @vue-generic {VersionSummary} -->
      <ElTableColumn label="就绪状态" min-width="120">
        <template #default="{ row }: { row: VersionSummary }">
          <ElTag class="management-tag" :class="readinessTones[row.readiness]" :type="tagType(readinessTones[row.readiness])">
            {{ readinessLabels[row.readiness] }}
          </ElTag>
        </template>
      </ElTableColumn>
      <!-- @vue-generic {VersionSummary} -->
      <ElTableColumn label="保存时间" min-width="180">
        <template #default="{ row }: { row: VersionSummary }">
          <span class="management-cell-nowrap">{{ formatTime(row.lastUpdateTime) }}</span>
        </template>
      </ElTableColumn>
      <!-- @vue-generic {VersionSummary} -->
      <ElTableColumn label="操作" width="120" fixed="right">
        <template #default="{ row }: { row: VersionSummary }">
          <div class="management-actions">
            <ElButton link type="primary" native-type="button" :disabled="busy" @click="emit('inspect', row.versionId)">
              只读查看
            </ElButton>
          </div>
        </template>
      </ElTableColumn>
    </ElTable>
  </div>
</template>
