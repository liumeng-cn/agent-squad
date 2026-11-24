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
import { deleteTool, disableTool, enableTool, listTools } from '../api/agentConfigApi'
import type { Tool, ToolType } from '../model/agentConfig'
import {
  readinessLabels,
  toolTypeLabels,
  toolTypes,
  versionLabel,
} from '../model/agentConfigLabels'
import { confirmManagement } from '../components/confirmManagement'
import ManagementShell from '../components/ManagementShell.vue'

/**
 * 工具画廊列表（方案 3）：胶囊按类型切换 + 3 列清爽卡片 + 收敛更多菜单。
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
const keyword = ref('')
const typeFilter = ref<'' | ToolType>('')

const filtered = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  return tools.value.filter(item => {
    const matchType = !typeFilter.value || item.toolType === typeFilter.value
    const matchText =
      !q ||
      [item.toolName, item.displayAlias, item.toolCode, item.exposedName, item.description].some(v =>
        (v || '').toLowerCase().includes(q),
      )
    return matchType && matchText
  })
})

function avatarClass(type: ToolType) {
  if (type === 'HTTP') return 'is-http'
  if (type === 'MCP') return 'is-mcp'
  return 'is-builtin'
}

function avatarText(type: ToolType) {
  if (type === 'HTTP') return 'API'
  if (type === 'MCP') return 'MCP'
  return '内置'
}

function dotClass(tool: Tool) {
  if (tool.status !== 'ENABLED') return 'is-disabled'
  return tool.latestReadiness === 'READY' ? 'is-ready' : 'is-incomplete'
}

function dotTitle(tool: Tool) {
  if (tool.status !== 'ENABLED') return '已停用'
  return readinessLabels[tool.latestReadiness]
}

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

async function toggleStatus(tool: Tool) {
  const disabling = tool.status === 'ENABLED'
  const message = disabling
    ? `停用“${tool.toolName}”？停用后不可再被 Agent 关联；被启用 Agent 或生效发布引用时无法停用。`
    : `启用“${tool.toolName}”？`
  if (!(await confirmManagement(message, { danger: disabling, confirmText: disabling ? '停用' : '启用' }))) {
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

async function removeTool(tool: Tool) {
  const message = `删除“${tool.toolName}”？删除后将从列表隐藏，已定格的历史快照不受影响；仍被引用时无法删除。`
  if (!(await confirmManagement(message, { danger: true, confirmText: '删除' }))) {
    return
  }
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    await deleteTool(tool.toolId, tool.revision)
    notice.value = `“${tool.toolName}”已删除。`
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
    <section v-if="!canQuery" class="management-card">
      <div class="management-empty">
        <p class="management-empty-title">暂无查询权限</p>
        <p class="management-empty-desc">当前角色尚未授予工具查询功能。</p>
      </div>
    </section>

    <template v-else>
      <div class="studio-toolbar">
        <div class="studio-pills" role="group" aria-label="工具类型筛选">
          <button type="button" class="studio-pill" :class="{ 'is-active': typeFilter === '' }" @click="typeFilter = ''">
            全部 {{ tools.length }}
          </button>
          <button
            v-for="item in toolTypes"
            :key="item"
            type="button"
            class="studio-pill"
            :class="{ 'is-active': typeFilter === item }"
            @click="typeFilter = item"
          >
            {{ toolTypeLabels[item] }}
          </button>
        </div>

        <div class="studio-toolbar-right">
          <ElInput v-model="keyword" clearable class="studio-search" placeholder="搜索工具名称、别称或编码" />
          <ElButton
            v-if="canEdit"
            type="primary"
            native-type="button"
            class="management-primary"
            @click="router.push('/management/tools/new')"
          >
            + 新增工具
          </ElButton>
        </div>
      </div>

      <ElAlert v-if="error" class="management-error" role="alert" type="error" :title="error" show-icon :closable="false" />
      <ElAlert v-if="notice" class="management-notice" role="status" type="success" :title="notice" show-icon :closable="false" />

      <div v-loading="loading">
        <div v-if="!filtered.length" class="management-card">
          <ElEmpty description="暂无符合条件的工具" :image-size="68" />
        </div>

        <div v-else class="studio-gallery">
          <article v-for="tool in filtered" :key="tool.toolId" class="studio-card" @click="open(tool)">
            <div>
              <div class="studio-card-top">
                <div class="studio-card-identity">
                  <span class="studio-avatar" :class="avatarClass(tool.toolType)">{{ avatarText(tool.toolType) }}</span>
                  <div class="studio-card-titles">
                    <div class="studio-card-name" :title="tool.toolName">
                      {{ tool.toolName }}
                      <span v-if="tool.displayAlias" class="studio-card-alias">· {{ tool.displayAlias }}</span>
                    </div>
                    <div class="studio-card-meta">
                      <span>{{ tool.exposedName || '未命名工具' }}</span>
                      <span>·</span>
                      <span>{{ versionLabel(tool.latestVersionNo) }}</span>
                    </div>
                  </div>
                </div>
                <span class="studio-dot" :class="dotClass(tool)" :title="dotTitle(tool)" />
              </div>

              <p class="studio-card-desc" style="margin-top: 12px">
                {{ tool.description || '尚未填写模型调用说明' }}
              </p>
              <div class="studio-card-code" style="margin-top: 6px">{{ tool.toolCode }}</div>
            </div>

            <footer class="studio-card-foot" @click.stop>
              <span class="studio-card-stats">
                <span>{{ dotTitle(tool) }}</span>
                <span>·</span>
                <span>引用 {{ tool.usageCount }}</span>
              </span>
              <div class="studio-card-actions">
                <ElButton link type="primary" native-type="button" @click="open(tool)">
                  {{ canEdit ? '配置 →' : '查看 →' }}
                </ElButton>
                <ElDropdown v-if="canPublish" trigger="click">
                  <button type="button" class="studio-more-btn" :disabled="busy" aria-label="更多操作">···</button>
                  <template #dropdown>
                    <ElDropdownMenu>
                      <ElDropdownItem @click="toggleStatus(tool)">
                        {{ tool.status === 'ENABLED' ? '停用工具' : '启用工具' }}
                      </ElDropdownItem>
                      <ElDropdownItem divided @click="removeTool(tool)">
                        <span style="color: #dc2626">删除工具</span>
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
