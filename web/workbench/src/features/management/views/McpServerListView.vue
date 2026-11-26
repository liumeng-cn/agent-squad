<script setup lang="ts">
import {
  vLoading,
  ElAlert,
  ElButton,
  ElDropdown,
  ElDropdownItem,
  ElDropdownMenu,
  ElEmpty,
  ElInput,
} from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { errorMessage } from '../../../shared/http/http'
import { useManagementStore } from '../stores/management'
import {
  deleteMcpServer,
  disableMcpServer,
  enableMcpServer,
  listMcpServers,
} from '../api/agentConfigApi'
import type { ConfigStatus, McpServer } from '../model/agentConfig'
import {
  mcpTransportLabels,
  readinessLabels,
  versionLabel,
} from '../model/agentConfigLabels'
import { confirmManagement } from '../components/confirmManagement'
import ManagementShell from '../components/ManagementShell.vue'

/**
 * MCP 服务画廊列表（方案 3）：清爽 3 列卡片 + 胶囊筛选 + 收敛操作菜单。
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
const keyword = ref('')
const statusFilter = ref<'' | ConfigStatus>('')

const filtered = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  return servers.value.filter(item => {
    const matchStatus = !statusFilter.value || item.status === statusFilter.value
    const matchText =
      !q ||
      [item.serverName, item.serverCode, item.description, item.endpoint].some(v =>
        (v || '').toLowerCase().includes(q),
      )
    return matchStatus && matchText
  })
})

function dotClass(server: McpServer) {
  if (server.status !== 'ENABLED') return 'is-disabled'
  return server.latestReadiness === 'READY' ? 'is-ready' : 'is-incomplete'
}

function dotTitle(server: McpServer) {
  if (server.status !== 'ENABLED') return '已停用'
  return readinessLabels[server.latestReadiness]
}

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

async function toggleStatus(server: McpServer) {
  const disabling = server.status === 'ENABLED'
  const message = disabling
    ? `停用“${server.serverName}”？停用后不可再被工具关联；被启用工具或生效发布引用时无法停用。`
    : `启用“${server.serverName}”？`
  if (!(await confirmManagement(message, { danger: disabling, confirmText: disabling ? '停用' : '启用' }))) {
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

async function removeServer(server: McpServer) {
  const message = `删除“${server.serverName}”？删除后将从列表隐藏，已定格的历史快照不受影响；仍被引用时无法删除。`
  if (!(await confirmManagement(message, { danger: true, confirmText: '删除' }))) {
    return
  }
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    await deleteMcpServer(server.mcpServerId, server.revision)
    notice.value = `“${server.serverName}”已删除。`
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
    <section v-if="!canQuery" class="management-card">
      <div class="management-empty">
        <p class="management-empty-title">暂无查询权限</p>
        <p class="management-empty-desc">当前角色尚未授予 MCP 服务查询功能。</p>
      </div>
    </section>

    <template v-else>
      <header class="mp-ph">
        <div>
          <div class="mp-ph-eyebrow">Agent 配置 · 外部服务接入</div>
          <h1 class="mp-ph-title">MCP 服务</h1>
        </div>
      </header>

      <div class="studio-toolbar">
        <div class="studio-pills" role="group" aria-label="状态筛选">
          <button type="button" class="studio-pill" :class="{ 'is-active': statusFilter === '' }" @click="statusFilter = ''">
            全部 {{ servers.length }}
          </button>
          <button type="button" class="studio-pill" :class="{ 'is-active': statusFilter === 'ENABLED' }" @click="statusFilter = 'ENABLED'">
            启用
          </button>
          <button type="button" class="studio-pill" :class="{ 'is-active': statusFilter === 'DISABLED' }" @click="statusFilter = 'DISABLED'">
            停用
          </button>
        </div>

        <div class="studio-toolbar-right">
          <ElInput v-model="keyword" clearable class="studio-search" placeholder="搜索 MCP 名称、编码或地址" />
          <ElButton
            v-if="canEdit"
            type="primary"
            native-type="button"
            class="management-primary"
            @click="router.push('/management/mcp-servers/new')"
          >
            + 新增 MCP
          </ElButton>
        </div>
      </div>

      <ElAlert v-if="error" class="management-error" role="alert" type="error" :title="error" show-icon :closable="false" />
      <ElAlert v-if="notice" class="management-notice" role="status" type="success" :title="notice" show-icon :closable="false" />

      <div v-loading="loading">
        <div v-if="!filtered.length" class="management-card">
          <ElEmpty description="暂无符合条件的 MCP 服务" :image-size="68" />
        </div>

        <div v-else class="studio-gallery">
          <article v-for="server in filtered" :key="server.mcpServerId" class="studio-card" @click="open(server)">
            <div>
              <div class="studio-card-top">
                <div class="studio-card-identity">
                  <span class="studio-avatar is-mcp">MCP</span>
                  <div class="studio-card-titles">
                    <div class="studio-card-name" :title="server.serverName">{{ server.serverName }}</div>
                    <div class="studio-card-meta">
                      <span>{{ server.transportType ? mcpTransportLabels[server.transportType] : '未配置传输' }}</span>
                      <span>·</span>
                      <span>{{ versionLabel(server.latestVersionNo) }}</span>
                    </div>
                  </div>
                </div>
                <span class="studio-dot" :class="dotClass(server)" :title="dotTitle(server)" />
              </div>

              <p class="studio-card-desc" style="margin-top: 12px">
                {{ server.description || server.endpoint || '尚未填写服务说明或连接地址' }}
              </p>
              <div class="studio-card-code" style="margin-top: 6px">{{ server.serverCode }}</div>
            </div>

            <footer class="studio-card-foot" @click.stop>
              <span class="studio-card-stats">
                <span>{{ dotTitle(server) }}</span>
                <span>·</span>
                <span>引用 {{ server.usageCount }}</span>
              </span>
              <div class="studio-card-actions">
                <ElButton link type="primary" native-type="button" @click="open(server)">
                  {{ canEdit ? '配置 →' : '查看 →' }}
                </ElButton>
                <ElDropdown v-if="canPublish" trigger="click">
                  <button type="button" class="studio-more-btn" :disabled="busy" aria-label="更多操作">···</button>
                  <template #dropdown>
                    <ElDropdownMenu>
                      <ElDropdownItem @click="toggleStatus(server)">
                        {{ server.status === 'ENABLED' ? '停用服务' : '启用服务' }}
                      </ElDropdownItem>
                      <ElDropdownItem divided @click="removeServer(server)">
                        <span style="color: #dc2626">删除服务</span>
                      </ElDropdownItem>
                    </ElDropdownMenu>
                  </template>
                </ElDropdown>
              </div>
            </footer>
          </article>
        </div>
      </div>
    </template>
  </ManagementShell>
</template>
