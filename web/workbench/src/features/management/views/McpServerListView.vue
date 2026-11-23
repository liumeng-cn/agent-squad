<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { errorMessage } from '../../../shared/http/http'
import { useManagementStore } from '../stores/management'
import { disableMcpServer, enableMcpServer, listMcpServers } from '../api/agentConfigApi'
import type { McpServer } from '../model/agentConfig'
import { configStatusLabels, configStatusTones, formatTime, versionLabel } from '../model/agentConfigLabels'
import { useCatalogPage } from '../composables/useCatalogPage'
import { confirmManagement } from '../components/confirmManagement'
import ManagementShell from '../components/ManagementShell.vue'
import ManagementPageHeader from '../components/ManagementPageHeader.vue'
import ConfigFlow from '../components/ConfigFlow.vue'
import CatalogPagination from '../components/CatalogPagination.vue'

/**
 * MCP 服务列表：配置流程的第一环，维护远程 MCP 服务连接；发布后的服务版本可被 MCP 工具引用。
 */
const router = useRouter()
const access = useManagementStore()

const canQuery = computed(() => access.can('management:mcp:query'))
const canEdit = computed(() => access.can('management:mcp:edit'))
const canPublish = computed(() => access.can('management:mcp:publish'))

const servers = ref<McpServer[]>([])
const loading = ref(false)
const busy = ref(false)
const error = ref('')
const notice = ref('')

const { criteria, rows, total, page, size, pageCount, filtering, search, reset } = useCatalogPage(servers, server => ({
  texts: [server.serverName, server.serverCode, server.description],
  status: server.status,
}))

async function load() {
  loading.value = true
  error.value = ''
  try {
    servers.value = await listMcpServers()
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    loading.value = false
  }
}

/** 启用或停用服务；停用时被启用工具的已发布版本引用会被服务端拒绝并说明引用方。 */
async function toggleStatus(server: McpServer) {
  const disabling = server.status === 'ENABLED'
  const message = disabling
    ? `停用“${server.serverName}”？停用后工具无法再绑定该服务；被启用工具引用时无法停用。`
    : `启用“${server.serverName}”？启用后其已发布版本可被工具引用。`
  if (!await confirmManagement(message, { danger: disabling, confirmText: disabling ? '停用' : '启用' })) {
    return
  }

  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    if (disabling) {
      await disableMcpServer(server.mcpServerId, server.revision)
    } else {
      await enableMcpServer(server.mcpServerId, server.revision)
    }
    notice.value = `“${server.serverName}”已${disabling ? '停用' : '启用'}。`
    await load()
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    busy.value = false
  }
}

function open(server: McpServer) {
  void router.push(`/management/mcp-servers/${server.mcpServerId}`)
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
      title="MCP 服务"
      description="登记远程 MCP 服务的连接方式。发布后的服务版本可在工具中发现并引用其远程工具。"
    >
      <template #actions>
        <RouterLink v-if="canEdit" to="/management/mcp-servers/new" custom v-slot="{ navigate }">
          <button type="button" class="management-primary" @click="navigate">新增 MCP 服务</button>
        </RouterLink>
      </template>
    </ManagementPageHeader>

    <ConfigFlow current="mcp" />

    <section v-if="!canQuery" class="management-card">
      <div class="management-empty">
        <p class="management-empty-title">暂无查询权限</p>
        <p class="management-empty-desc">当前角色尚未授予 MCP 服务查询功能。</p>
      </div>
    </section>

    <template v-else>
      <form class="management-card management-filter" @submit.prevent="search">
        <label class="management-filter-field is-wide">
          关键字
          <input v-model="criteria.keyword" maxlength="120" placeholder="名称、编码或说明" />
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
                <th>MCP 服务</th>
                <th>说明</th>
                <th>最新发布</th>
                <th>引用工具</th>
                <th>状态</th>
                <th>更新时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="server in rows" :key="server.mcpServerId">
                <td>
                  <div class="management-cell-main">
                    <RouterLink class="management-cell-title" :to="`/management/mcp-servers/${server.mcpServerId}`">
                      {{ server.serverName }}
                    </RouterLink>
                    <span class="management-cell-sub">{{ server.serverCode }}</span>
                  </div>
                </td>
                <td>
                  <div class="management-cell-ellipsis" :title="server.description">{{ server.description || '—' }}</div>
                </td>
                <td>
                  <span class="management-tags">
                    <span v-if="server.publishedVersionNo" class="management-tag is-success">
                      {{ versionLabel(server.publishedVersionNo) }}
                    </span>
                    <span v-else class="management-tag is-muted">未发布</span>
                    <span v-if="server.draftVersionId" class="management-tag is-warning">有草稿</span>
                  </span>
                </td>
                <td>{{ server.usageCount }}</td>
                <td>
                  <span class="management-tag is-dot" :class="configStatusTones[server.status]">
                    {{ configStatusLabels[server.status] }}
                  </span>
                </td>
                <td class="management-cell-nowrap">{{ formatTime(server.lastUpdateTime) }}</td>
                <td class="management-actions">
                  <button type="button" :disabled="busy" @click="open(server)">{{ canEdit ? '编辑' : '查看' }}</button>
                  <button
                    v-if="canPublish"
                    type="button"
                    :class="{ 'management-danger': server.status === 'ENABLED' }"
                    :disabled="busy"
                    @click="toggleStatus(server)"
                  >
                    {{ server.status === 'ENABLED' ? '停用' : '启用' }}
                  </button>
                </td>
              </tr>
              <tr v-if="loading && !rows.length">
                <td colspan="7"><div class="management-loading" role="status">正在加载…</div></td>
              </tr>
              <tr v-else-if="!rows.length && !error">
                <td colspan="7">
                  <div v-if="filtering" class="management-empty is-compact">没有符合条件的 MCP 服务</div>
                  <div v-else class="management-empty">
                    <p class="management-empty-title">还没有 MCP 服务</p>
                    <p class="management-empty-desc">
                      MCP 服务是可选环节：需要接入远程 MCP 工具时先在这里登记服务并发布，再到工具页面发现其远程工具。
                    </p>
                    <div class="management-empty-actions">
                      <RouterLink v-if="canEdit" to="/management/mcp-servers/new" custom v-slot="{ navigate }">
                        <button type="button" class="management-primary" @click="navigate">新增 MCP 服务</button>
                      </RouterLink>
                      <RouterLink to="/management/tools" custom v-slot="{ navigate }">
                        <button type="button" @click="navigate">直接配置工具</button>
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
