<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { errorMessage } from '../../../shared/http/http'
import { useManagementStore } from '../stores/management'
import * as api from '../api/agentConfigApi'
import type { McpProbe, McpServerDetail, McpServerDraft, McpServerVersion, McpTransport } from '../model/agentConfig'
import {
  CODE_PATTERN,
  DESCRIPTION_LIMIT,
  configStatusLabels,
  configStatusTones,
  formatTime,
  mcpTransportDescriptions,
  mcpTransportLabels,
  mcpTransports,
  versionLabel,
  type ChoiceOption,
  type EditorMode,
  type EditorTab,
} from '../model/agentConfigLabels'
import { placeholdersIn } from '../model/toolSchema'
import { blockingChecks, type CheckItem } from '../model/publishCheck'
import { useAsyncAction, useSnapshot, useUnsavedGuard } from '../composables/useEditorState'
import { confirmManagement } from '../components/confirmManagement'
import ManagementShell from '../components/ManagementShell.vue'
import ManagementCard from '../components/ManagementCard.vue'
import ConfigEditorLayout from '../components/agent-config/ConfigEditorLayout.vue'
import ChoiceCards from '../components/agent-config/ChoiceCards.vue'
import KeyValueEditor from '../components/agent-config/KeyValueEditor.vue'
import PublishChecklist from '../components/agent-config/PublishChecklist.vue'
import RemoteToolList from '../components/agent-config/RemoteToolList.vue'
import VersionHistoryTable from '../components/agent-config/VersionHistoryTable.vue'
import UsageTable from '../components/agent-config/UsageTable.vue'

/**
 * MCP 服务编辑页：维护服务基本信息与连接版本，支持测试连接发现远程工具。
 * 草稿可反复保存，发布后冻结；工具只能引用已发布且启用的服务版本。
 */
const route = useRoute()
const router = useRouter()
const access = useManagementStore()

const canEdit = computed(() => access.can('management:mcp:edit'))
const canPublish = computed(() => access.can('management:mcp:publish'))

const routeId = computed(() => String(route.params.id ?? ''))
const creating = computed(() => routeId.value === 'new')

const detail = ref<McpServerDetail | null>(null)
const version = ref<McpServerVersion | null>(null)
const mode = ref<EditorMode>('create')
const tab = ref<EditorTab>('config')
const loading = ref(false)
const loadError = ref('')
const credentials = ref<string[]>([])

const entry = ref({ serverCode: '', serverName: '', description: '' })
const form = ref<McpServerDraft>(emptyDraft())
const profileEditing = ref(false)
const profile = ref({ serverName: '', description: '' })

const probe = ref<McpProbe | null>(null)
const probeError = ref('')
const probing = ref(false)

const { busy, error, notice, run } = useAsyncAction()
const draftState = computed(() => ({ entry: entry.value, form: form.value }))
const { markClean, changed } = useSnapshot(draftState)

function emptyDraft(): McpServerDraft {
  return { transportType: 'STREAMABLE_HTTP', endpoint: '', credentialRef: null, headers: [], timeoutMs: 30000 }
}

function copyDraft(source: McpServerVersion): McpServerDraft {
  return {
    transportType: source.transportType,
    endpoint: source.endpoint,
    credentialRef: source.credentialRef,
    headers: source.headers.map(header => ({ ...header })),
    timeoutMs: source.timeoutMs,
  }
}

const server = computed(() => detail.value?.server ?? null)
const editable = computed(() => canEdit.value && mode.value !== 'published')
const dirty = computed(() => editable.value && changed())
const profileDirty = computed(() => profileEditing.value && (
  profile.value.serverName !== server.value?.serverName || profile.value.description !== server.value?.description
))
const leaveDirty = computed(() => dirty.value || profileDirty.value)
useUnsavedGuard(leaveDirty)

const transportOptions = computed<ChoiceOption<McpTransport>[]>(() => mcpTransports.map(value => ({
  value,
  title: mcpTransportLabels[value],
  description: mcpTransportDescriptions[value],
})))

/** 连接配置的校验结果，同时用于保存前拦截和发布检查。 */
const endpointValid = computed(() => {
  try {
    const url = new URL(form.value.endpoint.trim())
    return (url.protocol === 'http:' || url.protocol === 'https:') && !url.username && !url.password && !url.hash
  } catch {
    return false
  }
})
const headersValid = computed(() => form.value.headers.every(header => header.name.trim() !== ''))
const usesCredential = computed(() => placeholdersIn(...form.value.headers.map(header => header.value)).includes('credential'))
const credentialValid = computed(() => !usesCredential.value || Boolean(form.value.credentialRef))
const timeoutValid = computed(() => Number.isInteger(form.value.timeoutMs) && form.value.timeoutMs >= 1000 && form.value.timeoutMs <= 120000)
const entryValid = computed(() => !creating.value || (CODE_PATTERN.test(entry.value.serverCode) && entry.value.serverName.trim() !== ''))

const checks = computed<CheckItem[]>(() => {
  const items: CheckItem[] = []
  if (creating.value) {
    items.push({ label: '填写服务编码和名称', passed: entryValid.value, hint: '编码只能包含字母、数字、下划线和连字符' })
  }
  items.push(
    { label: '连接地址为 http/https', passed: endpointValid.value, hint: '不含用户信息和 # 片段，例如 https://mcp.example.com/mcp' },
    { label: '请求头名称完整', passed: headersValid.value, hint: '删除空行或补充名称' },
    { label: '使用 {{credential}} 时已选择凭据', passed: credentialValid.value, hint: '请求头引用了凭据占位符但未选择凭据' },
    { label: '超时在 1000～120000 毫秒之间', passed: timeoutValid.value },
    { label: '已测试连接', passed: Boolean(probe.value), level: 'advice', hint: '建议发布前测试连接，确认能发现远程工具' },
  )
  return items
})
const canSave = computed(() => editable.value && !busy.value && blockingChecks(checks.value, 'save').length === 0)
const canPublishDraft = computed(() => (
  canPublish.value && mode.value === 'draft' && !dirty.value && !busy.value
  && blockingChecks(checks.value, 'publish').length === 0 && server.value?.status === 'ENABLED'
))

const title = computed(() => (creating.value ? '新增 MCP 服务' : server.value?.serverName ?? 'MCP 服务'))
const modeTag = computed(() => {
  if (mode.value === 'create' || mode.value === 'newDraft') {
    return { text: '新草稿', tone: 'is-warning' }
  }
  const label = versionLabel(version.value?.versionNo)
  return mode.value === 'draft' ? { text: `${label} 草稿`, tone: 'is-warning' } : { text: `${label} 已发布`, tone: 'is-success' }
})

async function loadCredentials() {
  try {
    credentials.value = await api.listMcpCredentialOptions()
  } catch {
    credentials.value = []
  }
}

/** 读取服务详情并选择默认版本：有草稿进入草稿，否则查看最新发布版本，都没有时准备新草稿。 */
async function load(preferredVersionId?: string) {
  loading.value = true
  loadError.value = ''
  try {
    detail.value = await api.getMcpServer(routeId.value)
    const target = preferredVersionId
      ?? detail.value.server.draftVersionId
      ?? detail.value.server.publishedVersionId
    if (target) {
      await openVersion(target)
    } else {
      startDraft(null)
    }
  } catch (reason) {
    loadError.value = errorMessage(reason)
  } finally {
    loading.value = false
  }
}

async function openVersion(versionId: string) {
  const loaded = await api.getMcpServerVersion(routeId.value, versionId)
  version.value = loaded
  form.value = copyDraft(loaded)
  mode.value = loaded.status === 'DRAFT' ? 'draft' : 'published'
  probe.value = null
  probeError.value = ''
  markClean()
}

async function selectVersion(versionId: string) {
  if (dirty.value && !await confirmManagement('当前草稿有未保存的修改，切换版本后将丢失。确认切换？', { danger: true })) {
    return
  }
  await run(async () => {
    await openVersion(versionId)
    tab.value = 'config'
  })
}

/** 基于已发布版本（或空白）准备新草稿，保存后才创建版本。 */
function startDraft(source: McpServerVersion | null) {
  form.value = source ? copyDraft(source) : emptyDraft()
  version.value = null
  mode.value = 'newDraft'
  probe.value = null
  markClean()
}

/** 放弃尚未保存的新草稿，回到默认版本。 */
async function discardDraft() {
  if (dirty.value && !await confirmManagement('放弃新草稿中尚未保存的修改？', { danger: true, confirmText: '放弃' })) {
    return
  }
  await load()
}

async function editDraft() {
  const draftId = server.value?.draftVersionId
  if (draftId) {
    await selectVersion(draftId)
  }
}

function payload(): McpServerDraft {
  return {
    revision: mode.value === 'draft' ? version.value?.revision : undefined,
    transportType: form.value.transportType,
    endpoint: form.value.endpoint.trim(),
    credentialRef: form.value.credentialRef || null,
    headers: form.value.headers.map(header => ({ name: header.name.trim(), value: header.value })),
    timeoutMs: form.value.timeoutMs,
  }
}

/** 保存：新建资源时同时创建目录和 v1 草稿；已有资源新建或更新草稿。 */
async function save() {
  if (!canSave.value) {
    return
  }
  if (creating.value) {
    const created = await run(() => api.createMcpServer({
      serverCode: entry.value.serverCode.trim(),
      serverName: entry.value.serverName.trim(),
      description: entry.value.description.trim(),
      draft: payload(),
    }))
    if (created) {
      markClean()
      await router.replace(`/management/mcp-servers/${created.server.mcpServerId}`)
      notice.value = 'MCP 服务已创建，草稿 v1 已保存，测试连接无误后即可发布。'
    }
    return
  }

  const saved = await run(async () => {
    const result = mode.value === 'draft' && version.value
      ? await api.saveMcpServerVersion(routeId.value, version.value.mcpServerVersionId, payload())
      : await api.createMcpServerVersion(routeId.value, payload())
    detail.value = await api.getMcpServer(routeId.value)
    return result
  }, '草稿已保存。')
  if (saved) {
    version.value = saved
    form.value = copyDraft(saved)
    mode.value = 'draft'
    markClean()
  }
}

async function publish() {
  const target = version.value
  if (!target || !canPublishDraft.value) {
    return
  }
  const message = `发布 ${versionLabel(target.versionNo)}？发布后该版本冻结不可修改，工具即可引用。`
  if (!await confirmManagement(message, { confirmText: '发布' })) {
    return
  }
  await run(async () => {
    await api.publishMcpServerVersion(routeId.value, target.mcpServerVersionId, target.revision)
    await load(target.mcpServerVersionId)
  }, `${versionLabel(target.versionNo)} 已发布。`)
}

async function testConnection() {
  probing.value = true
  probeError.value = ''
  probe.value = null
  try {
    probe.value = await api.probeMcpServer(payload())
  } catch (reason) {
    probeError.value = errorMessage(reason)
  } finally {
    probing.value = false
  }
}

function startProfileEdit() {
  profile.value = { serverName: server.value?.serverName ?? '', description: server.value?.description ?? '' }
  profileEditing.value = true
}

async function saveProfile() {
  const current = server.value
  if (!current || !profile.value.serverName.trim()) {
    return
  }
  const updated = await run(() => api.updateMcpServer(current.mcpServerId, {
    serverName: profile.value.serverName.trim(),
    description: profile.value.description.trim(),
    revision: current.revision,
  }), '基本信息已更新。')
  if (updated && detail.value) {
    detail.value = { ...detail.value, server: updated }
    profileEditing.value = false
  }
}

async function toggleStatus() {
  const current = server.value
  if (!current) {
    return
  }
  const disabling = current.status === 'ENABLED'
  const message = disabling
    ? `停用“${current.serverName}”？被启用工具的已发布版本引用时无法停用。`
    : `启用“${current.serverName}”？`
  if (!await confirmManagement(message, { danger: disabling, confirmText: disabling ? '停用' : '启用' })) {
    return
  }
  const updated = await run(() => (disabling
    ? api.disableMcpServer(current.mcpServerId, current.revision)
    : api.enableMcpServer(current.mcpServerId, current.revision)), `服务已${disabling ? '停用' : '启用'}。`)
  if (updated && detail.value) {
    detail.value = { ...detail.value, server: updated }
  }
}

/** 进入页面或切换到其他服务时重置状态；新建保存后地址从 new 切换为服务 ID 并重新加载。 */
async function init() {
  detail.value = null
  version.value = null
  tab.value = 'config'
  profileEditing.value = false
  probe.value = null
  probeError.value = ''
  error.value = ''
  if (creating.value) {
    entry.value = { serverCode: '', serverName: '', description: '' }
    form.value = emptyDraft()
    mode.value = 'create'
    markClean()
    return
  }
  await load()
}

watch(routeId, () => {
  void init()
})

onMounted(() => {
  void loadCredentials()
  void init()
})
</script>

<template>
  <ManagementShell :current="creating ? '新增' : server?.serverName">
    <div v-if="loading && !detail" class="management-card management-loading" role="status">正在加载…</div>
    <div v-else-if="loadError" class="management-card">
      <div class="management-empty">
        <p class="management-empty-title">加载失败</p>
        <p class="management-empty-desc">{{ loadError }}</p>
        <div class="management-empty-actions">
          <button type="button" @click="load()">重新加载</button>
          <RouterLink to="/management/mcp-servers">返回列表</RouterLink>
        </div>
      </div>
    </div>

    <ConfigEditorLayout
      v-else
      v-model:tab="tab"
      back-path="/management/mcp-servers"
      back-label="MCP 服务"
      :title="title"
      :show-tabs="!creating"
      :version-count="detail?.versions.length ?? 0"
      :usage-count="detail?.usages.length ?? 0"
      usage-label="引用工具"
    >
      <template #tags>
        <span v-if="server" class="management-tag is-dot" :class="configStatusTones[server.status]">
          {{ configStatusLabels[server.status] }}
        </span>
        <span class="management-tag" :class="modeTag.tone">{{ modeTag.text }}</span>
      </template>

      <template #actions>
        <button v-if="server && canPublish" type="button" :class="{ 'management-danger': server.status === 'ENABLED' }" :disabled="busy" @click="toggleStatus">
          {{ server.status === 'ENABLED' ? '停用' : '启用' }}
        </button>
        <template v-if="mode === 'published' && canEdit">
          <button v-if="server?.draftVersionId" type="button" :disabled="busy" @click="editDraft">编辑草稿</button>
          <button v-else type="button" :disabled="busy" @click="startDraft(version)">基于此版本修改</button>
        </template>
        <button v-if="mode === 'newDraft' && detail?.versions.length" type="button" :disabled="busy" @click="discardDraft">
          放弃新草稿
        </button>
        <button v-if="editable" type="button" :class="{ 'management-primary': !canPublishDraft }" :disabled="!canSave" @click="save">
          {{ creating ? '创建并保存草稿' : '保存草稿' }}
        </button>
        <button
          v-if="canPublish && mode === 'draft'"
          type="button"
          :class="{ 'management-primary': canPublishDraft }"
          :disabled="!canPublishDraft"
          @click="publish"
        >
          发布
        </button>
      </template>

      <template v-if="server" #meta>
        <span>编码 <span class="management-code">{{ server.serverCode }}</span></span>
        <span>最新发布 {{ versionLabel(server.publishedVersionNo) }}</span>
        <span>更新于 {{ formatTime(server.lastUpdateTime) }}</span>
      </template>

      <template #messages>
        <p v-if="error" class="management-error" role="alert">{{ error }}</p>
        <p v-if="notice" class="management-notice" role="status">{{ notice }}</p>
        <p v-if="mode === 'published'" class="management-info">
          正在查看已发布的 {{ versionLabel(version?.versionNo) }}，已发布版本不可修改。{{ canEdit ? '如需调整，请基于此版本创建新草稿。' : '' }}
        </p>
        <p v-if="server?.status === 'DISABLED'" class="management-warning">服务已停用：工具无法引用其版本，草稿也不能发布。</p>
      </template>

      <template #main>
        <ManagementCard title="基本信息" description="编码创建后不可修改，名称与说明用于管理端展示。">
          <template v-if="server && canEdit && !profileEditing" #actions>
            <button type="button" class="management-btn-sm" :disabled="busy" @click="startProfileEdit">编辑</button>
          </template>

          <div v-if="creating" class="management-form-grid">
            <label class="management-field">
              <span class="management-field-label management-required">服务编码</span>
              <input
                v-model.trim="entry.serverCode"
                maxlength="80"
                placeholder="例如 order-mcp"
                :aria-invalid="entry.serverCode !== '' && !CODE_PATTERN.test(entry.serverCode)"
              />
              <span class="management-field-hint">字母、数字、下划线或连字符，创建后不可修改。</span>
            </label>
            <label class="management-field">
              <span class="management-field-label management-required">服务名称</span>
              <input v-model="entry.serverName" maxlength="120" placeholder="例如 订单中心 MCP" />
            </label>
            <label class="management-field is-full">
              <span class="management-field-label">说明</span>
              <textarea v-model="entry.description" rows="2" :maxlength="DESCRIPTION_LIMIT" placeholder="服务提供的能力、负责团队等" />
            </label>
          </div>

          <div v-else-if="profileEditing" class="management-form-grid">
            <label class="management-field">
              <span class="management-field-label management-required">服务名称</span>
              <input v-model="profile.serverName" maxlength="120" />
            </label>
            <div class="management-field">
              <span class="management-field-label">服务编码</span>
              <div class="management-field-value"><span class="management-code">{{ server?.serverCode }}</span></div>
            </div>
            <label class="management-field is-full">
              <span class="management-field-label">说明</span>
              <textarea v-model="profile.description" rows="2" :maxlength="DESCRIPTION_LIMIT" />
            </label>
            <div class="management-inline is-full">
              <button type="button" class="management-primary management-btn-sm" :disabled="busy || !profile.serverName.trim()" @click="saveProfile">
                保存基本信息
              </button>
              <button type="button" class="management-btn-sm" :disabled="busy" @click="profileEditing = false">取消</button>
            </div>
          </div>

          <dl v-else class="management-form-grid">
            <div class="management-field">
              <dt class="management-field-label">服务名称</dt>
              <dd class="management-field-value">{{ server?.serverName }}</dd>
            </div>
            <div class="management-field">
              <dt class="management-field-label">服务编码</dt>
              <dd class="management-field-value"><span class="management-code">{{ server?.serverCode }}</span></dd>
            </div>
            <div class="management-field is-full">
              <dt class="management-field-label">说明</dt>
              <dd class="management-field-value">{{ server?.description || '—' }}</dd>
            </div>
          </dl>
        </ManagementCard>

        <ManagementCard title="连接配置" description="保存为版本草稿；发布后该版本冻结，工具按版本固定引用。">
          <fieldset class="config-fieldset" :disabled="!editable || busy">
            <div class="management-field">
              <span class="management-field-label">传输方式</span>
              <ChoiceCards v-model="form.transportType" :options="transportOptions" name="mcp-transport" label="传输方式" :disabled="!editable" />
            </div>
            <label class="management-field">
              <span class="management-field-label management-required">连接地址</span>
              <input
                v-model.trim="form.endpoint"
                type="url"
                maxlength="2048"
                placeholder="https://mcp.example.com/mcp"
                :aria-invalid="form.endpoint !== '' && !endpointValid"
              />
              <span class="management-field-hint">完整的 http/https 地址；SSE 传输填写事件流地址。</span>
            </label>
            <div class="management-form-grid">
              <label class="management-field">
                <span class="management-field-label">凭据</span>
                <select v-model="form.credentialRef">
                  <option :value="null">不使用凭据</option>
                  <option v-if="form.credentialRef && !credentials.includes(form.credentialRef)" :value="form.credentialRef">
                    {{ form.credentialRef }}（未配置）
                  </option>
                  <option v-for="item in credentials" :key="item" :value="item">{{ item }}</option>
                </select>
                <span class="management-field-hint">凭据值保存在服务端配置中，这里只引用名称。</span>
              </label>
              <label class="management-field">
                <span class="management-field-label management-required">超时（毫秒）</span>
                <input v-model.number="form.timeoutMs" type="number" min="1000" max="120000" step="1000" :aria-invalid="!timeoutValid" />
                <span class="management-field-hint">1000～120000，默认 30000。</span>
              </label>
            </div>
            <div class="management-field">
              <span class="management-field-label">请求头</span>
              <KeyValueEditor
                v-model="form.headers"
                :read-only="!editable"
                name-placeholder="Authorization"
                value-placeholder="Bearer {{credential}}"
                add-label="添加请求头"
                empty-text="未配置请求头"
              />
              <span class="management-field-hint">值中的 <code v-pre>{{credential}}</code> 在连接时替换为所选凭据，日志与回显中会遮蔽。</span>
            </div>
          </fieldset>
        </ManagementCard>

        <ManagementCard title="测试连接" description="使用当前填写的连接配置（无需保存）连接服务并列出可用工具。">
          <template #actions>
            <button type="button" class="management-btn-sm" :disabled="probing || !endpointValid || !credentialValid || !canEdit" @click="testConnection">
              {{ probing ? '连接中…' : '测试连接' }}
            </button>
          </template>
          <p v-if="!canEdit" class="management-muted">测试连接需要编辑权限。</p>
          <p v-if="probeError" class="management-error" role="alert">{{ probeError }}</p>
          <template v-if="probe">
            <p class="management-notice" role="status">连接成功，耗时 {{ probe.durationMs }} ms，发现 {{ probe.tools.length }} 个工具。</p>
            <RemoteToolList v-if="probe.tools.length" :tools="probe.tools" />
          </template>
          <p v-else-if="!probeError && canEdit" class="management-muted">填写连接地址后点击“测试连接”。</p>
        </ManagementCard>
      </template>

      <template #aside>
        <ManagementCard title="版本信息">
          <dl class="config-meta">
            <dt>当前查看</dt>
            <dd>{{ modeTag.text }}</dd>
            <template v-if="version">
              <dt>修订号</dt>
              <dd>{{ version.revision }}</dd>
              <dt>发布时间</dt>
              <dd>{{ formatTime(version.publishedAt) }}</dd>
              <dt>更新时间</dt>
              <dd>{{ formatTime(version.lastUpdateTime) }}</dd>
            </template>
            <template v-if="server">
              <dt>最新发布</dt>
              <dd>{{ versionLabel(server.publishedVersionNo) }}</dd>
              <dt>引用工具</dt>
              <dd>{{ server.usageCount }}</dd>
            </template>
          </dl>
        </ManagementCard>
        <ManagementCard v-if="editable" title="发布检查">
          <PublishChecklist :items="checks" />
          <p v-if="mode === 'draft' && dirty" class="config-note">有未保存的修改，保存草稿后才能发布。</p>
          <p v-else-if="mode !== 'draft'" class="config-note">保存草稿后可发布。</p>
        </ManagementCard>
      </template>

      <template #versions>
        <VersionHistoryTable
          :versions="detail?.versions ?? []"
          :selected-id="version?.mcpServerVersionId ?? null"
          current-label="最新发布"
          :busy="busy"
          @select="selectVersion"
        />
      </template>

      <template #usages>
        <UsageTable
          :usages="detail?.usages ?? []"
          owner-label="工具"
          :owner-path="usage => `/management/tools/${usage.ownerId}`"
          description="列出引用该服务版本的工具版本。被启用工具的已发布版本引用时为生效引用，此时服务不能停用。"
        />
      </template>
    </ConfigEditorLayout>
  </ManagementShell>
</template>
