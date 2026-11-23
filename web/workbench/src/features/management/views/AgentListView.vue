<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { errorMessage } from '../../../shared/http/http'
import { useManagementStore } from '../stores/management'
import { disableAgent, enableAgent, listAgents } from '../api/agentConfigApi'
import type { Agent, AgentType } from '../model/agentConfig'
import {
  agentTypeLabels,
  configStatusLabels,
  configStatusTones,
  formatTime,
  versionLabel,
  type FlowStep,
} from '../model/agentConfigLabels'
import { useCatalogPage } from '../composables/useCatalogPage'
import { confirmManagement } from '../components/confirmManagement'
import ManagementShell from '../components/ManagementShell.vue'
import ManagementPageHeader from '../components/ManagementPageHeader.vue'
import ConfigFlow from '../components/ConfigFlow.vue'
import CatalogPagination from '../components/CatalogPagination.vue'

/**
 * Agent 列表：配置流程的第三、四环。子 Agent 专注单一职责并由主 Agent 委派，主 Agent 面向用户。
 * 类型页签与地址参数 ?type= 同步，便于从配置流程条直接跳到对应类型。
 */
type TypeTab = '' | AgentType

const route = useRoute()
const router = useRouter()
const access = useManagementStore()

const canQuery = computed(() => access.can('management:agent:query'))
const canEdit = computed(() => access.can('management:agent:edit'))
const canPublish = computed(() => access.can('management:agent:publish'))

const agents = ref<Agent[]>([])
const loading = ref(false)
const busy = ref(false)
const error = ref('')
const notice = ref('')

const { criteria, applied, rows, total, page, size, pageCount, search, reset, applyType } = useCatalogPage(
  agents,
  agent => ({
    texts: [agent.agentName, agent.agentCode, agent.description],
    type: agent.agentType,
    status: agent.status,
  }),
)

const typeTabs: { value: TypeTab; label: string }[] = [
  { value: '', label: '全部' },
  { value: 'MASTER', label: agentTypeLabels.MASTER },
  { value: 'SUB_AGENT', label: agentTypeLabels.SUB_AGENT },
]

const routeType = computed<TypeTab>(() => {
  const value = route.query.type
  return value === 'MASTER' || value === 'SUB_AGENT' ? value : ''
})

const flowSteps = computed<FlowStep[]>(() => {
  if (routeType.value === 'MASTER') {
    return ['masters']
  }
  return routeType.value === 'SUB_AGENT' ? ['subagents'] : ['subagents', 'masters']
})

const tabCount = (type: TypeTab) => (type ? agents.value.filter(agent => agent.agentType === type).length : agents.value.length)

watch(routeType, type => applyType(type), { immediate: true })

function selectTab(type: TypeTab) {
  void router.replace({ query: type ? { ...route.query, type } : {} })
}

function resetFilters() {
  reset()
  applyType(routeType.value)
}

const createPath = computed(() => `/management/agents/new?type=${routeType.value || 'MASTER'}`)

async function load() {
  loading.value = true
  error.value = ''
  try {
    agents.value = await listAgents()
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    loading.value = false
  }
}

/** 启用或停用 Agent；子 Agent 被启用主 Agent 的生效版本委派时，服务端拒绝停用并说明引用方。 */
async function toggleStatus(agent: Agent) {
  const disabling = agent.status === 'ENABLED'
  const impact = agent.agentType === 'SUB_AGENT'
    ? '停用后主 Agent 无法再委派它；被启用主 Agent 的生效版本委派时无法停用。'
    : '停用后该 Agent 不再对用户提供服务。'
  const message = disabling ? `停用“${agent.agentName}”？${impact}` : `启用“${agent.agentName}”？`
  if (!await confirmManagement(message, { danger: disabling, confirmText: disabling ? '停用' : '启用' })) {
    return
  }

  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    if (disabling) {
      await disableAgent(agent.agentId, agent.revision)
    } else {
      await enableAgent(agent.agentId, agent.revision)
    }
    notice.value = `“${agent.agentName}”已${disabling ? '停用' : '启用'}。`
    await load()
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    busy.value = false
  }
}

function open(agent: Agent) {
  void router.push(`/management/agents/${agent.agentId}`)
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
      title="Agent 配置"
      description="先配置子 Agent，再由主 Agent 绑定工具并委派子 Agent。发布即生效，可随时切换回历史版本。"
    >
      <template #actions>
        <RouterLink v-if="canEdit" to="/management/agents/new?type=SUB_AGENT" custom v-slot="{ navigate }">
          <button type="button" @click="navigate">新增子 Agent</button>
        </RouterLink>
        <RouterLink v-if="canEdit" to="/management/agents/new?type=MASTER" custom v-slot="{ navigate }">
          <button type="button" class="management-primary" @click="navigate">新增主 Agent</button>
        </RouterLink>
      </template>
    </ManagementPageHeader>

    <ConfigFlow :current="flowSteps" />

    <section v-if="!canQuery" class="management-card">
      <div class="management-empty">
        <p class="management-empty-title">暂无查询权限</p>
        <p class="management-empty-desc">当前角色尚未授予 Agent 查询功能。</p>
      </div>
    </section>

    <template v-else>
      <div class="management-segmented" role="group" aria-label="Agent 类型">
        <button
          v-for="tab in typeTabs"
          :key="tab.value || 'all'"
          type="button"
          :aria-pressed="routeType === tab.value"
          @click="selectTab(tab.value)"
        >
          {{ tab.label }}
          <span class="management-tab-count">{{ tabCount(tab.value) }}</span>
        </button>
      </div>

      <form class="management-card management-filter" @submit.prevent="search">
        <label class="management-filter-field is-wide">
          关键字
          <input v-model="criteria.keyword" maxlength="120" placeholder="名称、编码或职责描述" />
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
          <button type="button" :disabled="loading" @click="resetFilters">重置</button>
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
                <th>Agent</th>
                <th>类型</th>
                <th>职责描述</th>
                <th>生效版本</th>
                <th>能力</th>
                <th>被委派</th>
                <th>状态</th>
                <th>更新时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="agent in rows" :key="agent.agentId">
                <td>
                  <div class="management-cell-main">
                    <RouterLink class="management-cell-title" :to="`/management/agents/${agent.agentId}`">
                      {{ agent.agentName }}
                    </RouterLink>
                    <span class="management-cell-sub">{{ agent.agentCode }}</span>
                  </div>
                </td>
                <td>
                  <span class="management-tag" :class="{ 'is-primary': agent.agentType === 'MASTER' }">
                    {{ agentTypeLabels[agent.agentType] }}
                  </span>
                </td>
                <td>
                  <div class="management-cell-ellipsis" :title="agent.description">{{ agent.description || '—' }}</div>
                </td>
                <td>
                  <span class="management-tags">
                    <span v-if="agent.currentVersionNo" class="management-tag is-success">
                      {{ versionLabel(agent.currentVersionNo) }}
                    </span>
                    <span v-else class="management-tag is-muted">未发布</span>
                    <span v-if="agent.draftVersionId" class="management-tag is-warning">有草稿</span>
                  </span>
                </td>
                <td class="management-cell-nowrap">
                  {{ agent.toolCount }} 个工具
                  <template v-if="agent.agentType === 'MASTER'"> · {{ agent.subagentCount }} 个子 Agent</template>
                </td>
                <td>{{ agent.agentType === 'SUB_AGENT' ? agent.usageCount : '—' }}</td>
                <td>
                  <span class="management-tag is-dot" :class="configStatusTones[agent.status]">
                    {{ configStatusLabels[agent.status] }}
                  </span>
                </td>
                <td class="management-cell-nowrap">{{ formatTime(agent.lastUpdateTime) }}</td>
                <td class="management-actions">
                  <button type="button" :disabled="busy" @click="open(agent)">{{ canEdit ? '编辑' : '查看' }}</button>
                  <button
                    v-if="canPublish"
                    type="button"
                    :class="{ 'management-danger': agent.status === 'ENABLED' }"
                    :disabled="busy"
                    @click="toggleStatus(agent)"
                  >
                    {{ agent.status === 'ENABLED' ? '停用' : '启用' }}
                  </button>
                </td>
              </tr>
              <tr v-if="loading && !rows.length">
                <td colspan="9"><div class="management-loading" role="status">正在加载…</div></td>
              </tr>
              <tr v-else-if="!rows.length && !error">
                <td colspan="9">
                  <div v-if="applied.keyword || applied.status" class="management-empty is-compact">没有符合条件的 Agent</div>
                  <div v-else class="management-empty">
                    <p class="management-empty-title">
                      还没有{{ routeType ? agentTypeLabels[routeType] : ' Agent' }}
                    </p>
                    <p class="management-empty-desc">
                      Agent 只能绑定已发布的工具、委派已发布的子 Agent。建议先发布需要的工具和子 Agent，再配置主 Agent。
                    </p>
                    <div class="management-empty-actions">
                      <RouterLink v-if="canEdit" :to="createPath" custom v-slot="{ navigate }">
                        <button type="button" class="management-primary" @click="navigate">
                          新增{{ agentTypeLabels[routeType || 'MASTER'] }}
                        </button>
                      </RouterLink>
                      <RouterLink to="/management/tools" custom v-slot="{ navigate }">
                        <button type="button" @click="navigate">前往工具</button>
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
