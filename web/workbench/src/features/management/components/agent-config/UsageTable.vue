<script setup lang="ts">
import type { ConfigUsage } from '../../model/agentConfig'
import { configStatusLabels, versionLabel, versionStatusLabels } from '../../model/agentConfigLabels'

/**
 * 引用关系：哪些下游资源的哪个版本引用了当前资源。生效引用存在时当前资源不能停用。
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
      <table class="management-table">
        <thead>
          <tr>
            <th>{{ ownerLabel }}</th>
            <th>引用方版本</th>
            <th>引用的版本</th>
            <th>是否生效</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="usage in usages" :key="`${usage.ownerId}-${usage.versionNo}`">
            <td>
              <div class="management-cell-main">
                <RouterLink class="management-cell-title" :to="ownerPath(usage)">{{ usage.ownerName }}</RouterLink>
                <span class="management-cell-sub">{{ usage.ownerCode }}</span>
              </div>
            </td>
            <td>
              <span class="management-tags">
                {{ versionLabel(usage.versionNo) }}
                <span class="management-tag" :class="usage.versionStatus === 'DRAFT' ? 'is-warning' : 'is-success'">
                  {{ versionStatusLabels[usage.versionStatus] }}
                </span>
                <span v-if="usage.ownerStatus === 'DISABLED'" class="management-tag is-muted">
                  引用方{{ configStatusLabels[usage.ownerStatus] }}
                </span>
              </span>
            </td>
            <td>{{ versionLabel(usage.referencedVersionNo) }}</td>
            <td>
              <span class="management-tag is-dot" :class="usage.active ? 'is-success' : 'is-muted'">
                {{ usage.active ? '生效中' : '未生效' }}
              </span>
            </td>
          </tr>
          <tr v-if="!usages.length">
            <td colspan="4">
              <div class="management-empty is-compact">暂无引用</div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
