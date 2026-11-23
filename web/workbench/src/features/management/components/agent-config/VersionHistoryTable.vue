<script setup lang="ts">
import type { VersionSummary } from '../../model/agentConfig'
import { formatTime, versionLabel } from '../../model/agentConfigLabels'

/**
 * 版本记录：按版本号倒序展示状态与时间，点击查看切换到对应版本。
 * currentLabel 区分 Agent 的“当前生效”与工具、MCP 的“最新发布”。
 */
defineProps<{
  versions: VersionSummary[]
  selectedId: string | null
  currentLabel: string
  busy: boolean
}>()
const emit = defineEmits<{ select: [versionId: string] }>()
</script>

<template>
  <div class="management-table-wrap">
    <table class="management-table">
      <thead>
        <tr>
          <th>版本</th>
          <th>状态</th>
          <th>发布时间</th>
          <th>更新时间</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="version in versions" :key="version.versionId">
          <td>
            <span class="management-cell-title">{{ versionLabel(version.versionNo) }}</span>
          </td>
          <td>
            <span class="management-tags">
              <span v-if="version.status === 'DRAFT'" class="management-tag is-warning">草稿</span>
              <span v-else class="management-tag is-success">已发布</span>
              <span v-if="version.current" class="management-tag is-primary">{{ currentLabel }}</span>
            </span>
          </td>
          <td class="management-cell-nowrap">{{ formatTime(version.publishedAt) }}</td>
          <td class="management-cell-nowrap">{{ formatTime(version.lastUpdateTime) }}</td>
          <td class="management-actions">
            <span v-if="version.versionId === selectedId" class="management-muted">正在查看</span>
            <button v-else type="button" :disabled="busy" @click="emit('select', version.versionId)">查看</button>
          </td>
        </tr>
        <tr v-if="!versions.length">
          <td colspan="5">
            <div class="management-empty is-compact">暂无版本</div>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
