<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { errorMessage } from '../../../shared/http/http'
import { useManagementStore } from '../stores/management'
import { disableTool, enableTool, listTools } from '../api/agentConfigApi'
import type { Tool } from '../model/agentConfig'
import {
  configStatusLabels,
  configStatusTones,
  formatTime,
  toolTypeLabels,
  toolTypes,
  versionLabel,
} from '../model/agentConfigLabels'
import { useCatalogPage } from '../composables/useCatalogPage'
import { confirmManagement } from '../components/confirmManagement'
import ManagementShell from '../components/ManagementShell.vue'
import ManagementPageHeader from '../components/ManagementPageHeader.vue'
import ConfigFlow from '../components/ConfigFlow.vue'
import CatalogPagination from '../components/CatalogPagination.vue'

/**
 * 工具列表：配置流程的第二环。工具由配置生成实现，发布后的工具版本可被 Agent 绑定。
 */
const router = useRouter()
const access = useManagementStore()

const canQuery = computed(() => access.can('management:tool:query'))
const canEdit = computed(() => access.can('management:tool:edit'))
const canPublish = computed(() => access.can('management:tool:publish'))

const tools = ref<Tool[]>([])
const loading = ref(false)
const busy = ref(false)
const error = ref('')
const notice = ref('')

const { criteria, rows, total, page, size, pageCount, filtering, search, reset } = useCatalogPage(tools, tool => ({
  texts: [tool.toolName, tool.toolCode, tool.exposedName, tool.description],
  type: tool.toolType,
  status: tool.status,
}))

async function load() {
  loading.value = true
  error.value = ''
  try {
    tools.value = await listTools()
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    loading.value = false
  }
}

/** 启用或停用工具；停用时被启用 Agent 的当前版本绑定会被服务端拒绝并说明引用方。 */
async function toggleStatus(tool: Tool) {
  const disabling = tool.status === 'ENABLED'
  const message = disabling
    ? `停用“${tool.toolName}”？停用后 Agent 无法再绑定该工具；被启用 Agent 的生效版本绑定时无法停用。`
    : `启用“${tool.toolName}”？启用后其已发布版本可被 Agent 绑定。`
  if (!await confirmManagement(message, { danger: disabling, confirmText: disabling ? '停用' : '启用' })) {
    return
  }

  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    if (disabling) {
      await disableTool(tool.toolId, tool.revision)
    } else {
      await enableTool(tool.toolId, tool.revision)
    }
    notice.value = `“${tool.toolName}”已${disabling ? '停用' : '启用'}。`
    await load()
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    busy.value = false
  }
}

function open(tool: Tool) {
  void router.push(`/management/tools/${tool.toolId}`)
}

onMounted(() => {
  if (canQuery.value) {
    void load()
  }
})
</script>

<template>
  <ManagementShell>
    <ManagementPageHeader
      title="工具目录"
      description="工具由配置自动生成：选择内置实现、按参数和请求模板生成 HTTP 接口调用，或引用 MCP 服务的远程工具。"
    >
      <template #actions>
        <RouterLink v-if="canEdit" to="/management/tools/new" custom v-slot="{ navigate }">
          <button type="button" class="management-primary" @click="navigate">新增工具</button>
        </RouterLink>
      </template>
    </ManagementPageHeader>

    <ConfigFlow current="tools" />

    <section v-if="!canQuery" class="management-card">
      <div class="management-empty">
        <p class="management-empty-title">暂无查询权限</p>
        <p class="management-empty-desc">当前角色尚未授予工具查询功能。</p>
      </div>
    </section>

    <template v-else>
      <form class="management-card management-filter" @submit.prevent="search">
        <label class="management-filter-field is-wide">
          关键字
          <input v-model="criteria.keyword" maxlength="120" placeholder="名称、编码或模型工具名" />
        </label>
        <label class="management-filter-field">
          类型
          <select v-model="criteria.type">
            <option value="">全部</option>
            <option v-for="type in toolTypes" :key="type" :value="type">{{ toolTypeLabels[type] }}</option>
          </select>
        </label>
        <label class="management-filter-field">
          状态
          <select v-model="criteria.status">
            <option value="">全部</option>
            <option value="ENABLED">启用</option>
            <option value="DISABLED">停用</option>
          </select>
        </label>
        <div class="management-filter-actions">
          <button type="submit" class="management-primary" :disabled="loading">查询</button>
          <button type="button" :disabled="loading" @click="reset">重置</button>
        </div>
      </form>

      <section class="management-card management-table-card" :aria-busy="loading || busy">
        <p v-if="error" class="management-error" role="alert">
          {{ error }}
          <button type="button" class="management-link" :disabled="loading" @click="load">重新加载</button>
        </p>
        <p v-if="notice" class="management-notice" role="status">{{ notice }}</p>

        <div class="management-table-wrap">
          <table class="management-table">
            <thead>
              <tr>
                <th>工具</th>
                <th>类型</th>
                <th>模型工具名</th>
                <th>最新发布</th>
                <th>引用 Agent</th>
                <th>状态</th>
                <th>更新时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="tool in rows" :key="tool.toolId">
                <td>
                  <div class="management-cell-main">
                    <RouterLink class="management-cell-title" :to="`/management/tools/${tool.toolId}`">
                      {{ tool.toolName }}
                    </RouterLink>
                    <span class="management-cell-sub">{{ tool.toolCode }}</span>
                  </div>
                </td>
                <td><span class="management-tag">{{ toolTypeLabels[tool.toolType] }}</span></td>
                <td>
                  <span v-if="tool.exposedName" class="management-code">{{ tool.exposedName }}</span>
                  <span v-else class="management-muted">—</span>
                </td>
                <td>
                  <span class="management-tags">
                    <span v-if="tool.publishedVersionNo" class="management-tag is-success">
                      {{ versionLabel(tool.publishedVersionNo) }}
                    </span>
                    <span v-else class="management-tag is-muted">未发布</span>
                    <span v-if="tool.draftVersionId" class="management-tag is-warning">有草稿</span>
                  </span>
                </td>
                <td>{{ tool.usageCount }}</td>
                <td>
                  <span class="management-tag is-dot" :class="configStatusTones[tool.status]">
                    {{ configStatusLabels[tool.status] }}
                  </span>
                </td>
                <td class="management-cell-nowrap">{{ formatTime(tool.lastUpdateTime) }}</td>
                <td class="management-actions">
                  <button type="button" :disabled="busy" @click="open(tool)">{{ canEdit ? '编辑' : '查看' }}</button>
                  <button
                    v-if="canPublish"
                    type="button"
                    :class="{ 'management-danger': tool.status === 'ENABLED' }"
                    :disabled="busy"
                    @click="toggleStatus(tool)"
                  >
                    {{ tool.status === 'ENABLED' ? '停用' : '启用' }}
                  </button>
                </td>
              </tr>
              <tr v-if="loading && !rows.length">
                <td colspan="8"><div class="management-loading" role="status">正在加载…</div></td>
              </tr>
              <tr v-else-if="!rows.length && !error">
                <td colspan="8">
                  <div v-if="filtering" class="management-empty is-compact">没有符合条件的工具</div>
                  <div v-else class="management-empty">
                    <p class="management-empty-title">还没有工具</p>
                    <p class="management-empty-desc">
                      内置工具和 HTTP 接口工具可直接创建；MCP 工具需要先发布 MCP 服务。工具发布后才能被 Agent 绑定。
                    </p>
                    <div class="management-empty-actions">
                      <RouterLink v-if="canEdit" to="/management/tools/new" custom v-slot="{ navigate }">
                        <button type="button" class="management-primary" @click="navigate">新增工具</button>
                      </RouterLink>
                      <RouterLink to="/management/mcp-servers" custom v-slot="{ navigate }">
                        <button type="button" @click="navigate">前往 MCP 服务</button>
                      </RouterLink>
                    </div>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <CatalogPagination v-model:page="page" v-model:size="size" :total="total" :page-count="pageCount" :busy="loading" />
      </section>
    </template>
  </ManagementShell>
</template>
