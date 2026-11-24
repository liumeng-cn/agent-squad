<script setup lang="ts">
import { ElTable, ElTableColumn, ElTag } from 'element-plus'
import { tagType } from '../../model/elementTheme'
import type { ConfigUsage } from '../../model/agentConfig'
import { configStatusLabels, versionLabel } from '../../model/agentConfigLabels'

/**
 * 引用关系：展示引用当前资源的下游最新配置与 Master 生效发布快照。
 */
defineProps<{
  usages: ConfigUsage[]
  ownerLabel: string
  ownerPath: (usage: ConfigUsage) => string
  description: string
}>()
</script>

<template>
  <div class="management-card-body">
    <p class="config-note">{{ description }}</p>
    <div class="management-table-wrap">
      <ElTable :data="usages" stripe style="width: 100%" empty-text="暂无引用关系">
        <!-- @vue-generic {ConfigUsage} -->
        <ElTableColumn :label="ownerLabel" min-width="180">
          <template #default="{ row: usage }: { row: ConfigUsage }">
            <div class="management-cell-main">
              <RouterLink class="management-cell-title" :to="ownerPath(usage)">
                {{ usage.ownerName }}
              </RouterLink>
              <span class="management-cell-sub">{{ usage.ownerCode }}</span>
            </div>
          </template>
        </ElTableColumn>
        <!-- @vue-generic {ConfigUsage} -->
        <ElTableColumn label="引用来源" min-width="180">
          <template #default="{ row: usage }: { row: ConfigUsage }">
            <span class="management-tags">
              <ElTag class="management-tag" :type="usage.released ? 'success' : 'info'">
                {{ usage.released ? `生效发布 #${usage.versionNo}` : `最新配置 ${versionLabel(usage.versionNo)}` }}
              </ElTag>
              <ElTag v-if="usage.ownerStatus === 'DISABLED'" class="management-tag is-muted" type="info">
                引用方{{ configStatusLabels[usage.ownerStatus] }}
              </ElTag>
            </span>
          </template>
        </ElTableColumn>
        <!-- @vue-generic {ConfigUsage} -->
        <ElTableColumn label="保护状态" min-width="130">
          <template #default="{ row: usage }: { row: ConfigUsage }">
            <ElTag class="management-tag is-dot" :type="tagType(usage.active ? 'is-success' : 'is-muted')">
              {{ usage.active ? '生效引用中' : '非启用引用' }}
            </ElTag>
          </template>
        </ElTableColumn>
      </ElTable>
    </div>
  </div>
</template>
