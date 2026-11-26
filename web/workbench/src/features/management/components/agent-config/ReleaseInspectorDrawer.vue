<script setup lang="ts">
import { ElAlert, ElDrawer, ElTag, vLoading } from 'element-plus'
import { computed, ref, watch } from 'vue'
import { errorMessage } from '../../../../shared/http/http'
import {
  getAgentRelease,
  getAgentReleaseSubagent,
  getAgentReleaseTool,
} from '../../api/agentConfigApi'
import type {
  AgentReleaseDetail,
  AgentReleaseSubagentDetail,
  AgentReleaseToolDetail,
} from '../../model/agentConfig'
import { formatTime, toolTypeLabels, versionLabel } from '../../model/agentConfigLabels'
import ConfigReadonlyCards from './ConfigReadonlyCards.vue'

/**
 * 发布快照树形只读浏览器：用“左侧完整定格依赖树 + 右侧单画布详情”取代弹窗套弹窗。
 */
const props = defineProps<{
  agentId: string
  releaseId: string | null
}>()

const emit = defineEmits<{ close: [] }>()

type NodeSelection =
  | { kind: 'master' }
  | { kind: 'sub'; subagentId: string }
  | { kind: 'tool'; toolId: string }

const loading = ref(false)
const nodeLoading = ref(false)
const error = ref('')
const release = ref<AgentReleaseDetail | null>(null)
const selected = ref<NodeSelection>({ kind: 'master' })

const subCache = ref<Record<string, AgentReleaseSubagentDetail>>({})
const toolCache = ref<Record<string, AgentReleaseToolDetail>>({})

const isOpen = computed(() => Boolean(props.releaseId))
const isStandalone = computed(
  () => Boolean(release.value && release.value.tools.length === 0 && release.value.subagents.length === 0),
)

const activeSub = computed(() =>
  selected.value.kind === 'sub' ? subCache.value[selected.value.subagentId] ?? null : null,
)
const activeTool = computed(() =>
  selected.value.kind === 'tool' ? toolCache.value[selected.value.toolId] ?? null : null,
)

async function loadRelease() {
  if (!props.releaseId) {
    release.value = null
    return
  }
  loading.value = true
  error.value = ''
  selected.value = { kind: 'master' }
  subCache.value = {}
  toolCache.value = {}
  try {
    release.value = await getAgentRelease(props.agentId, props.releaseId)
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    loading.value = false
  }
}

async function selectSub(subagentId: string) {
  selected.value = { kind: 'sub', subagentId }
  if (!props.releaseId || subCache.value[subagentId]) return
  nodeLoading.value = true
  error.value = ''
  try {
    const detail = await getAgentReleaseSubagent(props.agentId, props.releaseId, subagentId)
    subCache.value = { ...subCache.value, [subagentId]: detail }
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    nodeLoading.value = false
  }
}

async function selectTool(toolId: string) {
  selected.value = { kind: 'tool', toolId }
  if (!props.releaseId || toolCache.value[toolId]) return
  nodeLoading.value = true
  error.value = ''
  try {
    const detail = await getAgentReleaseTool(props.agentId, props.releaseId, toolId)
    toolCache.value = { ...toolCache.value, [toolId]: detail }
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    nodeLoading.value = false
  }
}

watch(
  () => [props.agentId, props.releaseId],
  () => {
    void loadRelease()
  },
  { immediate: true },
)
</script>

<template>
  <ElDrawer
    :model-value="isOpen"
    size="min(1020px, 94vw)"
    append-to-body
    destroy-on-close
    class="release-inspector-drawer"
    @close="emit('close')"
  >
    <template #header>
      <div class="release-inspector-head">
        <div class="management-tags">
          <strong class="release-inspector-title">
            {{ release ? `Release #${release.releaseNo} 定格快照` : '加载发布快照...' }}
          </strong>
          <ElTag v-if="release?.current" size="small" type="success">当前生效</ElTag>
          <ElTag v-else-if="release" size="small" type="info">历史快照</ElTag>
          <ElTag v-if="isStandalone" size="small" type="primary">独立对话模式</ElTag>
        </div>
        <span v-if="release" class="management-muted">
          发布时间：{{ formatTime(release.publishedAt) }} · 指纹 {{ release.dependencyDigest.slice(0, 12) }}
        </span>
      </div>
    </template>

    <ElAlert v-if="error" type="error" :title="error" show-icon :closable="false" style="margin-bottom: 12px" />

    <div v-loading="loading" class="release-inspector-shell">
      <template v-if="release">
        <!-- 左侧定格依赖树 -->
        <aside class="release-tree-rail" aria-label="定格组件树">
          <div class="release-tree-group-label">主智能体</div>
          <button
            type="button"
            class="release-tree-node"
            :class="{ 'is-active': selected.kind === 'master' }"
            @click="selected = { kind: 'master' }"
          >
            <span class="release-node-badge is-master">M</span>
            <span class="release-node-main">
              <span class="release-node-name">{{ release.masterName }}</span>
              <span class="release-node-meta">{{ versionLabel(release.masterVersionNo) }}</span>
            </span>
          </button>

          <div class="release-tree-group-label">直接工具（{{ release.tools.length }}）</div>
          <div v-if="!release.tools.length" class="release-tree-empty">未挂载直接工具</div>
          <button
            v-for="t in release.tools"
            :key="`direct-${t.toolId}`"
            type="button"
            class="release-tree-node"
            :class="{ 'is-active': selected.kind === 'tool' && selected.toolId === t.toolId }"
            @click="selectTool(t.toolId)"
          >
            <span class="release-node-badge is-tool">T</span>
            <span class="release-node-main">
              <span class="release-node-name">{{ t.displayAlias || t.toolName }}</span>
              <span class="release-node-meta">{{ toolTypeLabels[t.toolType] }} · {{ versionLabel(t.versionNo) }}</span>
            </span>
          </button>

          <div class="release-tree-group-label">Sub 委派（{{ release.subagents.length }}）</div>
          <div v-if="!release.subagents.length" class="release-tree-empty">未委派子 Agent</div>
          <div v-for="s in release.subagents" :key="s.subagentId" class="release-sub-branch">
            <button
              type="button"
              class="release-tree-node"
              :class="{ 'is-active': selected.kind === 'sub' && selected.subagentId === s.subagentId }"
              @click="selectSub(s.subagentId)"
            >
              <span class="release-node-badge is-sub">S</span>
              <span class="release-node-main">
                <span class="release-node-name">{{ s.displayAlias || s.subagentName }}</span>
                <span class="release-node-meta">{{ s.delegationName }} · {{ versionLabel(s.versionNo) }}</span>
              </span>
            </button>

            <!-- 展开该 Sub 下定格的工具节点 -->
            <div v-if="subCache[s.subagentId]?.tools.length" class="release-sub-children">
              <button
                v-for="st in subCache[s.subagentId]!.tools"
                :key="`sub-${s.subagentId}-${st.toolId}`"
                type="button"
                class="release-tree-node is-child"
                :class="{ 'is-active': selected.kind === 'tool' && selected.toolId === st.toolId }"
                @click="selectTool(st.toolId)"
              >
                <span class="release-node-badge is-tool">T</span>
                <span class="release-node-main">
                  <span class="release-node-name">{{ st.displayAlias || st.toolName }}</span>
                  <span class="release-node-meta">{{ versionLabel(st.versionNo) }}</span>
                </span>
              </button>
            </div>
          </div>
        </aside>

        <!-- 右侧单层详情画布 -->
        <section v-loading="nodeLoading" class="release-detail-pane">
          <!-- Master 定格详情 -->
          <template v-if="selected.kind === 'master'">
            <ConfigReadonlyCards
              :agent="{
                agentType: 'MASTER',
                version: {
                  agentVersionId: release.masterVersionId,
                  agentId: release.masterAgentId,
                  versionNo: release.masterVersionNo,
                  readiness: 'READY',
                  description: release.description,
                  promptSections: release.promptSections,
                  systemPrompt: release.systemPrompt,
                  maxIters: release.maxIters,
                  lastUpdateTime: release.publishedAt,
                  tools: release.tools,
                  subagents: release.subagents,
                },
              }"
            />
          </template>

          <!-- Sub 定格详情 -->
          <template v-else-if="selected.kind === 'sub' && activeSub">
            <div class="ro-prose-box" style="margin-bottom: 14px">
              <div class="ro-meta-grid">
                <div class="ro-meta-item">
                  <span class="ro-meta-label">Sub 组件名 / 编码</span>
                  <span class="ro-meta-value is-strong">{{ activeSub.subagentName }}（{{ activeSub.subagentCode }}）</span>
                </div>
                <div class="ro-meta-item">
                  <span class="ro-meta-label">定格委派名 / 展示别称</span>
                  <span class="ro-meta-value">
                    <span class="management-code">{{ activeSub.delegationName }}</span>
                    · {{ activeSub.displayAlias || activeSub.subagentName }}
                  </span>
                </div>
              </div>
              <div v-if="activeSub.delegationDescription" style="margin-top: 8px">
                <div class="ro-sub-label">Master 委派触发说明</div>
                <p class="ro-prose-text">{{ activeSub.delegationDescription }}</p>
              </div>
            </div>

            <ConfigReadonlyCards
              :agent="{
                agentType: 'SUB_AGENT',
                version: {
                  agentVersionId: activeSub.subagentVersionId,
                  agentId: activeSub.subagentId,
                  versionNo: activeSub.versionNo,
                  readiness: 'READY',
                  description: activeSub.description,
                  promptSections: activeSub.promptSections,
                  systemPrompt: activeSub.systemPrompt,
                  maxIters: activeSub.maxIters,
                  lastUpdateTime: activeSub.publishedAt,
                  tools: activeSub.tools,
                  subagents: [],
                },
              }"
            />
          </template>

          <!-- Tool（含定格 MCP）详情 -->
          <template v-else-if="selected.kind === 'tool' && activeTool">
            <ConfigReadonlyCards
              :tool="{
                version: activeTool.toolVersion,
              }"
            />
            <div v-if="activeTool.mcpVersion" style="margin-top: 16px">
              <div class="ro-sub-label" style="margin-bottom: 8px">
                同步定格的上游 MCP 服务：{{ activeTool.mcpServerName }}（{{ versionLabel(activeTool.mcpVersion.versionNo) }}）
              </div>
              <ConfigReadonlyCards :mcp="{ version: activeTool.mcpVersion }" />
            </div>
          </template>
        </section>
      </template>
    </div>
  </ElDrawer>
</template>
