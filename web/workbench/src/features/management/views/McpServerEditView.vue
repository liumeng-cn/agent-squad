<script setup lang="ts">
import {
  ElAlert,
  ElButton,
  ElCollapse,
  ElCollapseItem,
  ElDrawer,
  ElInput,
  ElInputNumber,
  ElOption,
  ElRadioButton,
  ElRadioGroup,
  ElSelect,
  ElTag,
} from 'element-plus'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { errorMessage } from '../../../shared/http/http'
import { useManagementStore } from '../stores/management'
import {
  createMcpServer,
  createTool,
  getMcpServer,
  getMcpServerVersion,
  listMcpCredentialOptions,
  listTools,
  probeMcpServer,
  saveMcpServer,
} from '../api/agentConfigApi'
import type {
  KeyValue,
  McpProbe,
  McpServerDetail,
  McpServerVersion,
  McpTransport,
} from '../model/agentConfig'
import {
  configStatusLabels,
  configStatusTones,
  DESCRIPTION_LIMIT,
  formatTime,
  mcpTransportLabels,
  mcpTransports,
  readinessLabels,
  readinessTones,
  versionLabel,
} from '../model/agentConfigLabels'
import { tagType } from '../model/elementTheme'
import { useEditorState } from '../composables/useEditorState'
import ManagementShell from '../components/ManagementShell.vue'
import SteppedEditorLayout, { type EditorStepItem } from '../components/agent-config/SteppedEditorLayout.vue'
import ConfigReadonlyCards from '../components/agent-config/ConfigReadonlyCards.vue'
import KeyValueEditor from '../components/agent-config/KeyValueEditor.vue'
import RemoteToolList from '../components/agent-config/RemoteToolList.vue'
import UsageTable from '../components/agent-config/UsageTable.vue'
import VersionHistoryTable from '../components/agent-config/VersionHistoryTable.vue'

/**
 * MCP 分步聚焦编辑页（方案 3）：1. 基础信息 → 2. 连接与探测 → 3. 引用与历史。
 */
const route = useRoute()
const router = useRouter()
const access = useManagementStore()

const canEdit = computed(() => access.can('management:mcp:edit'))
const canCreateTool = computed(() => access.can('management:tool:edit'))
const mcpServerId = computed(() => {
  const raw = route.params.id ? String(route.params.id) : ''
  return raw && raw !== 'new' ? raw : null
})
const isCreate = computed(() => !mcpServerId.value)

const step = ref('basic')
const readonlyView = ref(false)
const detail = ref<McpServerDetail | null>(null)
const credentials = ref<string[]>([])
const loading = ref(false)
const busy = ref(false)
const probing = ref(false)
const importingTools = ref(false)
const error = ref('')
const notice = ref('')
const probeResult = ref<McpProbe | null>(null)
const probeError = ref('')
const selectedRemoteNames = ref<string[]>([])
const importedRemoteNames = ref<string[]>([])
const advancedOpen = ref<string[]>([])
const inspectedVersion = ref<McpServerVersion | null>(null)

const form = ref({
  serverName: '',
  description: '',
  transportType: 'STREAMABLE_HTTP' as McpTransport,
  endpoint: '',
  credentialRef: null as string | null,
  headers: [] as KeyValue[],
  timeoutMs: 30000,
})

const { dirty, markClean, resetDirty } = useEditorState(() => JSON.stringify(form.value))

const canBatchImport = computed(
  () =>
    canCreateTool.value &&
    !isCreate.value &&
    !dirty.value &&
    detail.value?.server.status === 'ENABLED' &&
    detail.value?.server.latestReadiness === 'READY',
)
const title = computed(() => (isCreate.value ? '新增 MCP 服务' : detail.value?.server.serverName || 'MCP 服务'))

const steps = computed<EditorStepItem[]>(() => [
  { key: 'basic', no: 1, label: '基础信息' },
  { key: 'connect', no: 2, label: '连接与探测' },
  { key: 'history', no: 3, label: '引用与历史', badge: detail.value?.versions.length, hidden: isCreate.value },
])

const stepTitle = computed(() => {
  if (step.value === 'basic') return '1. 基础信息'
  if (step.value === 'connect') return '2. 连接与实时探测'
  return '3. 引用关系与配置历史'
})

watch(
  () => [form.value.transportType, form.value.endpoint, form.value.credentialRef, JSON.stringify(form.value.headers), form.value.timeoutMs],
  () => {
    probeResult.value = null
    probeError.value = ''
  },
)

function applyDetail(data: McpServerDetail) {
  detail.value = data
  const latest = data.latestVersion
  form.value = {
    serverName: data.server.serverName,
    description: data.server.description || '',
    transportType: latest?.transportType ?? 'STREAMABLE_HTTP',
    endpoint: latest?.endpoint ?? '',
    credentialRef: latest?.credentialRef ?? null,
    headers: latest?.headers ? latest.headers.map(item => ({ ...item })) : [],
    timeoutMs: latest?.timeoutMs ?? 30000,
  }
  markClean()
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    credentials.value = await listMcpCredentialOptions()
    if (mcpServerId.value) {
      applyDetail(await getMcpServer(mcpServerId.value))
      if (!canEdit.value) readonlyView.value = true
    } else {
      resetDirty()
    }
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!form.value.serverName.trim()) {
    step.value = 'basic'
    error.value = '请先填写 MCP 名称'
    return
  }
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    const payload = {
      revision: detail.value?.server.revision,
      serverName: form.value.serverName.trim(),
      description: form.value.description.trim(),
      transportType: form.value.transportType,
      endpoint: form.value.endpoint.trim(),
      credentialRef: form.value.credentialRef || null,
      headers: form.value.headers.filter(h => h.name.trim()),
      timeoutMs: form.value.timeoutMs || 30000,
    }
    if (isCreate.value) {
      const created = await createMcpServer(payload)
      applyDetail(created)
      notice.value = `MCP 服务已创建（编码 ${created.server.serverCode}，配置版本 v${created.server.latestVersionNo}）。`
      await router.replace(`/management/mcp-servers/${created.server.mcpServerId}`)
    } else {
      const saved = await saveMcpServer(mcpServerId.value!, payload)
      applyDetail(saved)
      notice.value = `已保存为新配置版本 v${saved.server.latestVersionNo}（${readinessLabels[saved.server.latestReadiness]}）。`
    }
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    busy.value = false
  }
}

function safeExposedName(raw: string): string {
  const cleaned = raw.trim().replace(/[^A-Za-z0-9_]/g, '_').replace(/^[^A-Za-z_]+/, 't_').slice(0, 64)
  const fallback = cleaned || 'mcp_tool'
  return fallback === 'ask_user' ? 'mcp_ask_user' : fallback
}

async function refreshImportedMarkers(tools: McpProbe['tools']) {
  if (!access.can('management:tool:query')) return
  try {
    const allTools = await listTools()
    const mcpExposedSet = new Set(
      allTools.filter(t => t.toolType === 'MCP').map(t => t.exposedName),
    )
    importedRemoteNames.value = tools
      .filter(rt => mcpExposedSet.has(rt.name) || mcpExposedSet.has(safeExposedName(rt.name)))
      .map(rt => rt.name)
  } catch {
    // 忽略标记辅助查询失败
  }
}

function toggleRemoteSelection(name: string) {
  if (selectedRemoteNames.value.includes(name)) {
    selectedRemoteNames.value = selectedRemoteNames.value.filter(item => item !== name)
  } else {
    selectedRemoteNames.value = [...selectedRemoteNames.value, name]
  }
}

function selectAllUnimported() {
  if (!probeResult.value) return
  selectedRemoteNames.value = probeResult.value.tools
    .map(t => t.name)
    .filter(name => !importedRemoteNames.value.includes(name))
}

async function batchImportRemoteTools() {
  if (!mcpServerId.value || !probeResult.value || !selectedRemoteNames.value.length) return
  importingTools.value = true
  error.value = ''
  notice.value = ''
  const pickedTools = probeResult.value.tools.filter(t => selectedRemoteNames.value.includes(t.name))
  let createdCount = 0
  try {
    for (const remote of pickedTools) {
      const exposed = safeExposedName(remote.name)
      const displayTitle = remote.title?.trim() || remote.name
      await createTool({
        toolName: `${form.value.serverName.trim() || 'MCP'} · ${displayTitle}`.slice(0, 120),
        displayAlias: displayTitle.slice(0, 60),
        toolType: 'MCP',
        exposedName: exposed,
        description: (remote.description?.trim() || `调用远程 MCP 工具 ${remote.name}`).slice(0, 2000),
        inputSchema: remote.inputSchema ?? { type: 'object', properties: {} },
        readOnly: remote.readOnly,
        implementationKey: null,
        mcpServerId: mcpServerId.value,
        remoteToolName: remote.name,
        httpSpec: null,
      })
      createdCount++
      importedRemoteNames.value = [...importedRemoteNames.value, remote.name]
    }
    selectedRemoteNames.value = []
    detail.value = await getMcpServer(mcpServerId.value)
    notice.value = `已成功将 ${createdCount} 个远程工具批量生成为本平台 MCP 工具。`
  } catch (reason) {
    error.value = `已导入 ${createdCount} 个工具，后续中断：${errorMessage(reason)}`
  } finally {
    importingTools.value = false
  }
}

async function runProbe() {
  if (!form.value.endpoint.trim()) {
    probeError.value = '请先填写连接地址再测试连接'
    return
  }
  probing.value = true
  probeError.value = ''
  probeResult.value = null
  selectedRemoteNames.value = []
  try {
    const probed = await probeMcpServer({
      transportType: form.value.transportType,
      endpoint: form.value.endpoint.trim(),
      credentialRef: form.value.credentialRef || null,
      headers: form.value.headers.filter(h => h.name.trim()),
      timeoutMs: form.value.timeoutMs || 30000,
    })
    probeResult.value = probed
    await refreshImportedMarkers(probed.tools)
  } catch (reason) {
    probeError.value = errorMessage(reason)
  } finally {
    probing.value = false
  }
}

async function inspectVersion(versionId: string) {
  if (!mcpServerId.value) return
  busy.value = true
  try {
    inspectedVersion.value = await getMcpServerVersion(mcpServerId.value, versionId)
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    busy.value = false
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <ManagementShell>
    <SteppedEditorLayout
      v-model:step="step"
      v-model:readonly-view="readonlyView"
      back-path="/management/mcp-servers"
      back-label="MCP 列表"
      :title="title"
      :code="detail?.server.serverCode"
      :subtitle="detail ? `最近更新：${formatTime(detail.server.lastUpdateTime)}` : '编码格式：MCP-yyyyMMdd-001'"
      :steps="steps"
      :can-save="canEdit"
      :can-toggle-readonly="!isCreate && Boolean(detail?.latestVersion)"
      :saving="busy || loading"
      :save-label="dirty ? '保存新版本' : '保存'"
      @save="save"
    >
      <template #readonly>
        <ConfigReadonlyCards
          v-if="detail?.latestVersion"
          :mcp="{ server: detail.server, version: detail.latestVersion }"
        />
      </template>
      <template #status>
        <ElTag v-if="detail" class="management-tag" :class="configStatusTones[detail.server.status]" :type="tagType(configStatusTones[detail.server.status])">
          {{ configStatusLabels[detail.server.status] }}
        </ElTag>
        <ElTag v-if="detail" class="management-tag" :class="readinessTones[detail.server.latestReadiness]" :type="tagType(readinessTones[detail.server.latestReadiness])">
          {{ versionLabel(detail.server.latestVersionNo) }} · {{ readinessLabels[detail.server.latestReadiness] }}
        </ElTag>
      </template>

      <template #step-title>{{ stepTitle }}</template>

      <template #messages>
        <ElAlert v-if="error" type="error" :title="error" show-icon :closable="false" />
        <ElAlert v-if="notice" type="success" :title="notice" show-icon :closable="false" />
      </template>

      <!-- 步骤 1：基础信息 -->
      <div v-if="step === 'basic'" class="management-form-grid">
        <label class="management-field">
          <span class="management-field-label">MCP 名称 <span class="management-required" /></span>
          <ElInput v-model="form.serverName" :disabled="!canEdit" maxlength="120" placeholder="如：订单中心 MCP" />
        </label>
        <label class="management-field">
          <span class="management-field-label">组件编码（自动生成）</span>
          <ElInput :model-value="detail?.server.serverCode || '保存后自动生成（MCP-yyyyMMdd-001）'" disabled />
        </label>
        <label class="management-field is-full">
          <span class="management-field-label">说明</span>
          <ElInput v-model="form.description" :disabled="!canEdit" type="textarea" :rows="3" :maxlength="DESCRIPTION_LIMIT" placeholder="说明该 MCP 服务的业务用途" />
        </label>
      </div>

      <!-- 步骤 2：连接与探测 -->
      <div v-else-if="step === 'connect'" class="studio-canvas-body">
        <div class="management-form-grid">
          <label class="management-field">
            <span class="management-field-label">传输方式</span>
            <ElRadioGroup v-model="form.transportType" :disabled="!canEdit">
              <ElRadioButton v-for="item in mcpTransports" :key="item" :value="item">
                {{ mcpTransportLabels[item] }}
              </ElRadioButton>
            </ElRadioGroup>
          </label>
          <label class="management-field">
            <span class="management-field-label">凭据引用</span>
            <ElSelect v-model="form.credentialRef" :disabled="!canEdit" clearable filterable placeholder="不使用凭据">
              <ElOption v-for="item in credentials" :key="item" :value="item" :label="item" />
            </ElSelect>
            <span v-if="!credentials.length" class="management-field-hint">
              密钥托管于服务端配置项 <code>agentsquad.v2.credentials</code>，前端不保存明文。
            </span>
          </label>
          <label class="management-field is-full">
            <span class="management-field-label">连接地址（http / https）</span>
            <ElInput v-model="form.endpoint" :disabled="!canEdit" maxlength="2048" placeholder="https://example.com/mcp" />
          </label>
          <div class="management-field is-full">
            <span class="management-field-label">附加请求头（值可用 &#123;&#123;credential&#125;&#125; 引用凭据）</span>
            <KeyValueEditor v-model="form.headers" :read-only="!canEdit" />
          </div>
        </div>

        <div class="studio-subblock">
          <div class="studio-subblock-head">
            <span class="studio-subblock-title">实时连通性探测与工具批量导入</span>
            <div class="management-actions">
              <ElButton
                v-if="probeResult?.tools.length && canBatchImport"
                native-type="button"
                @click="selectAllUnimported"
              >
                全选未导入
              </ElButton>
              <ElButton
                v-if="probeResult?.tools.length && canBatchImport"
                type="primary"
                native-type="button"
                :loading="importingTools"
                :disabled="!selectedRemoteNames.length"
                @click="batchImportRemoteTools"
              >
                一键导入为工具（{{ selectedRemoteNames.length }}）
              </ElButton>
              <ElButton native-type="button" :loading="probing" :disabled="busy" @click="runProbe">测试连接并发现工具</ElButton>
            </div>
          </div>
          <span v-if="probeResult" class="management-muted">
            连接成功（{{ probeResult.durationMs }} ms），发现 {{ probeResult.tools.length }} 个远程工具。
            <template v-if="!canBatchImport && canCreateTool">
              请先保存当前改动并确保该 MCP 处于“启用 + 已就绪”状态，即可勾选远程工具一键批量生成平台工具。
            </template>
          </span>
          <ElAlert v-if="probeError" type="error" :title="probeError" show-icon :closable="false" />
          <RemoteToolList
            v-if="probeResult"
            :tools="probeResult.tools"
            :multi-selectable="canBatchImport"
            :selected-names="selectedRemoteNames"
            :imported-names="importedRemoteNames"
            :disabled="importingTools"
            @toggle="toggleRemoteSelection"
          />
        </div>

        <ElCollapse v-model="advancedOpen">
          <ElCollapseItem title="高级设置（超时时间）" name="advanced">
            <label class="management-field" style="max-width: 260px">
              <span class="management-field-label">超时时间（毫秒，1000～120000）</span>
              <ElInputNumber v-model="form.timeoutMs" :disabled="!canEdit" :min="1000" :max="120000" :step="1000" />
            </label>
          </ElCollapseItem>
        </ElCollapse>
      </div>

      <!-- 步骤 3：引用与历史 -->
      <div v-else class="studio-canvas-body">
        <div class="studio-subblock">
          <span class="studio-subblock-title">配置历史（{{ detail?.versions.length ?? 0 }}）</span>
          <VersionHistoryTable
            :versions="detail?.versions ?? []"
            :latest-version-id="detail?.server.latestVersionId ?? null"
            :busy="busy"
            @inspect="inspectVersion"
          />
        </div>
        <div class="studio-subblock">
          <span class="studio-subblock-title">下游引用关系（{{ detail?.usages.length ?? 0 }}）</span>
          <UsageTable
            :usages="detail?.usages ?? []"
            owner-label="引用方"
            :owner-path="usage => (usage.released ? `/management/agents/${usage.ownerId}` : `/management/tools/${usage.ownerId}`)"
            description="展示最新配置或 Master 当前生效发布引用该 MCP 的资源。"
          />
        </div>
      </div>
    </SteppedEditorLayout>

    <ElDrawer
      :model-value="Boolean(inspectedVersion)"
      :title="inspectedVersion ? `配置版本详情 · ${versionLabel(inspectedVersion.versionNo)}` : ''"
      size="min(720px, 92vw)"
      append-to-body
      destroy-on-close
      @close="inspectedVersion = null"
    >
      <ConfigReadonlyCards
        v-if="inspectedVersion"
        :mcp="{ server: detail?.server, version: inspectedVersion }"
      />
    </ElDrawer>
  </ManagementShell>
</template>
