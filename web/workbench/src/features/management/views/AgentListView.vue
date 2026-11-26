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
  deleteAgent,
  disableAgent,
  enableAgent,
  listAgents,
  previewPublishAgent,
  publishAgent,
} from '../api/agentConfigApi'
import type { Agent, AgentPublishPreview, AgentType } from '../model/agentConfig'
import {
  agentTypeLabels,
  publishStateLabels,
  readinessLabels,
  versionLabel,
} from '../model/agentConfigLabels'
import { confirmManagement } from '../components/confirmManagement'
import ManagementShell from '../components/ManagementShell.vue'
import PublishConfirmDialog from '../components/agent-config/PublishConfirmDialog.vue'
import ReleaseInspectorDrawer from '../components/agent-config/ReleaseInspectorDrawer.vue'

/**
 * Agent 画廊列表（方案 3）：胶囊按 Master/Sub 切换 + 3 列清爽卡片；仅 Master 卡片显示“发布”按钮。
 */
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
const keyword = ref('')
const typeFilter = ref<'' | AgentType>('')
const publishPreview = ref<AgentPublishPreview | null>(null)
const publishTargetId = ref<string | null>(null)
const publishOpId = ref('')
const inspectingAgentId = ref('')
const inspectingReleaseId = ref<string | null>(null)

function openReleaseSnapshot(agent: Agent) {
  if (!agent.currentReleaseId) return
  inspectingAgentId.value = agent.agentId
  inspectingReleaseId.value = agent.currentReleaseId
}

const filtered = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  return agents.value.filter(item => {
    const matchType = !typeFilter.value || item.agentType === typeFilter.value
    const matchText =
      !q ||
      [item.agentName, item.displayAlias, item.agentCode, item.description].some(v =>
        (v || '').toLowerCase().includes(q),
      )
    return matchType && matchText
  })
})

function dotClass(agent: Agent) {
  if (agent.status !== 'ENABLED') return 'is-disabled'
  return agent.latestReadiness === 'READY' ? 'is-ready' : 'is-incomplete'
}

function dotTitle(agent: Agent) {
  if (agent.status !== 'ENABLED') return '已停用'
  if (agent.agentType === 'MASTER' && agent.publishState) {
    return `${readinessLabels[agent.latestReadiness]} · ${publishStateLabels[agent.publishState]}`
  }
  return readinessLabels[agent.latestReadiness]
}

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

async function openPublish(agent: Agent) {
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    publishPreview.value = await previewPublishAgent(agent.agentId)
    publishTargetId.value = agent.agentId
    publishOpId.value =
      typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
        ? crypto.randomUUID()
        : `pub_${Date.now()}_${Math.random().toString(36).slice(2, 10)}`
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    busy.value = false
  }
}

async function confirmPublish() {
  if (!publishPreview.value || !publishTargetId.value) return
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    const updated = await publishAgent(publishTargetId.value, {
      revision: publishPreview.value.revision,
      dependencyDigest: publishPreview.value.dependencyDigest,
      publishOperationId: publishOpId.value,
    })
    publishPreview.value = null
    publishTargetId.value = null
    notice.value = `“${updated.agentName}”已发布定格为 Release #${updated.currentReleaseNo}。`
    await load()
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    busy.value = false
  }
}

async function toggleStatus(agent: Agent) {
  const disabling = agent.status === 'ENABLED'
  const impact =
    agent.agentType === 'SUB_AGENT'
      ? '停用后 Master 无法再委派它；被启用 Master 或生效发布引用时无法停用。'
      : '停用后该 Master 无法发布新快照。'
  const message = disabling ? `停用“${agent.agentName}”？${impact}` : `启用“${agent.agentName}”？`
  if (!(await confirmManagement(message, { danger: disabling, confirmText: disabling ? '停用' : '启用' }))) {
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

async function removeAgent(agent: Agent) {
  const message = `删除“${agent.agentName}”？删除后将从列表隐藏，历史配置与已定格快照不受影响；仍被引用时无法删除。`
  if (!(await confirmManagement(message, { danger: true, confirmText: '删除' }))) {
    return
  }
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    await deleteAgent(agent.agentId, agent.revision)
    notice.value = `“${agent.agentName}”已删除。`
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
    <section v-if="!canQuery" class="management-card">
      <div class="management-empty">
        <p class="management-empty-title">暂无查询权限</p>
        <p class="management-empty-desc">当前角色尚未授予 Agent 查询功能。</p>
      </div>
    </section>

    <template v-else>
      <header class="mp-ph">
        <div>
          <div class="mp-ph-eyebrow">Agent 配置 · 编排与发布</div>
          <h1 class="mp-ph-title">智能体（Master & Sub）</h1>
        </div>
      </header>

      <div class="studio-toolbar">
        <div class="studio-pills" role="group" aria-label="Agent 类型筛选">
          <button type="button" class="studio-pill" :class="{ 'is-active': typeFilter === '' }" @click="typeFilter = ''">
            全部 {{ agents.length }}
          </button>
          <button type="button" class="studio-pill" :class="{ 'is-active': typeFilter === 'MASTER' }" @click="typeFilter = 'MASTER'">
            Master
          </button>
          <button type="button" class="studio-pill" :class="{ 'is-active': typeFilter === 'SUB_AGENT' }" @click="typeFilter = 'SUB_AGENT'">
            Sub
          </button>
        </div>

        <div class="studio-toolbar-right">
          <ElInput v-model="keyword" clearable class="studio-search" placeholder="搜索 Agent 名称、别称或编码" />
          <ElButton
            v-if="canEdit"
            type="primary"
            native-type="button"
            class="management-primary"
            @click="router.push('/management/agents/new')"
          >
            + 创建 Agent
          </ElButton>
        </div>
      </div>

      <ElAlert v-if="error" class="management-error" role="alert" type="error" :title="error" show-icon :closable="false" />
      <ElAlert v-if="notice" class="management-notice" role="status" type="success" :title="notice" show-icon :closable="false" />

      <div v-loading="loading">
        <div v-if="!filtered.length" class="management-card">
          <ElEmpty description="暂无符合条件的 Agent" :image-size="68" />
        </div>

        <div v-else class="studio-gallery">
          <article v-for="agent in filtered" :key="agent.agentId" class="studio-card" @click="open(agent)">
            <div>
              <div class="studio-card-top">
                <div class="studio-card-identity">
                  <span class="studio-avatar" :class="{ 'is-master': agent.agentType === 'MASTER' }">
                    {{ agent.agentType === 'MASTER' ? 'M' : 'S' }}
                  </span>
                  <div class="studio-card-titles">
                    <div class="studio-card-name" :title="agent.agentName">
                      {{ agent.agentName }}
                      <span v-if="agent.displayAlias" class="studio-card-alias">· {{ agent.displayAlias }}</span>
                    </div>
                    <div class="studio-card-meta">
                      <span>{{ agentTypeLabels[agent.agentType] }}</span>
                      <span>·</span>
                      <span>{{ versionLabel(agent.latestVersionNo) }}</span>
                      <template v-if="agent.agentType === 'MASTER' && agent.currentReleaseNo != null">
                        <span>·</span>
                        <span>#{{ agent.currentReleaseNo }}</span>
                      </template>
                    </div>
                  </div>
                </div>
                <span class="studio-dot" :class="dotClass(agent)" :title="dotTitle(agent)" />
              </div>

              <p class="studio-card-desc" style="margin-top: 12px">
                {{ agent.description || '尚未填写职责描述' }}
              </p>
              <div class="studio-card-code" style="margin-top: 6px">{{ agent.agentCode }}</div>
            </div>

            <footer class="studio-card-foot" @click.stop>
              <span class="studio-card-stats">
                <template v-if="agent.agentType === 'MASTER' && agent.toolCount === 0 && agent.subagentCount === 0">
                  <span>独立对话</span>
                </template>
                <template v-else>
                  <span>{{ agent.toolCount }} 工具</span>
                  <template v-if="agent.agentType === 'MASTER'">
                    <span>·</span>
                    <span>{{ agent.subagentCount }} Sub</span>
                  </template>
                  <template v-else>
                    <span>·</span>
                    <span>被委派 {{ agent.usageCount }}</span>
                  </template>
                </template>
                <template v-if="agent.agentType === 'MASTER' && agent.publishState === 'MODIFIED'">
                  <span>·</span>
                  <span style="color: #b45309">待更新</span>
                </template>
              </span>

              <div class="studio-card-actions">
                <button
                  v-if="agent.agentType === 'MASTER' && agent.currentReleaseId"
                  type="button"
                  class="studio-release-link"
                  title="查看当前生效发布快照"
                  @click="openReleaseSnapshot(agent)"
                >
                  Release #{{ agent.currentReleaseNo }}
                </button>
                <button
                  v-if="canPublish && agent.agentType === 'MASTER'"
                  type="button"
                  class="studio-pub-chip"
                  :disabled="busy || agent.status !== 'ENABLED'"
                  @click="openPublish(agent)"
                >
                  发布
                </button>
                <ElButton link type="primary" native-type="button" @click="open(agent)">
                  {{ canEdit ? '配置 →' : '查看 →' }}
                </ElButton>
                <ElDropdown v-if="canPublish" trigger="click">
                  <button type="button" class="studio-more-btn" :disabled="busy" aria-label="更多操作">···</button>
                  <template #dropdown>
                    <ElDropdownMenu>
                      <ElDropdownItem @click="toggleStatus(agent)">
                        {{ agent.status === 'ENABLED' ? '停用 Agent' : '启用 Agent' }}
                      </ElDropdownItem>
                      <ElDropdownItem divided @click="removeAgent(agent)">
                        <span style="color: #dc2626">删除 Agent</span>
                      </ElDropdownItem>
                    </ElDropdownMenu>
                  </template>
                </ElDropdown>
              </div>
            </footer>
          </article>
        </div>
      </div>

      <PublishConfirmDialog
        v-if="publishPreview"
        :preview="publishPreview"
        :busy="busy"
        @close="publishPreview = null"
        @confirm="confirmPublish"
      />

      <ReleaseInspectorDrawer
        v-if="inspectingReleaseId"
        :agent-id="inspectingAgentId"
        :release-id="inspectingReleaseId"
        @close="inspectingReleaseId = null"
      />
    </template>
  </ManagementShell>
</template>
