<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { errorMessage } from '../../../shared/http/http'
import { useManagementStore } from '../stores/management'
import * as api from '../api/agentConfigApi'
import type {
  BuiltinToolOption,
  HttpMethod,
  HttpToolSpec,
  JsonSchema,
  McpRemoteTool,
  McpServerOption,
  ToolDetail,
  ToolDraft,
  ToolType,
  ToolVersion,
} from '../model/agentConfig'
import {
  CODE_PATTERN,
  EXPOSED_NAME_PATTERN,
  TOOL_DESCRIPTION_LIMIT,
  bodyMethods,
  configStatusLabels,
  configStatusTones,
  formatTime,
  httpMethods,
  toolTypeDescriptions,
  toolTypeLabels,
  toolTypes,
  versionLabel,
  type ChoiceOption,
  type EditorMode,
  type EditorTab,
} from '../model/agentConfigLabels'
import {
  CREDENTIAL_PLACEHOLDER,
  emptyObjectSchema,
  fieldsFromSchema,
  isValidBodyTemplate,
  placeholdersIn,
  schemaFromFields,
  validateFields,
  type ParamField,
} from '../model/toolSchema'
import { blockingChecks, type CheckItem } from '../model/publishCheck'
import { useAsyncAction, useSnapshot, useUnsavedGuard } from '../composables/useEditorState'
import { confirmManagement } from '../components/confirmManagement'
import ManagementShell from '../components/ManagementShell.vue'
import ManagementCard from '../components/ManagementCard.vue'
import ConfigEditorLayout from '../components/agent-config/ConfigEditorLayout.vue'
import ChoiceCards from '../components/agent-config/ChoiceCards.vue'
import KeyValueEditor from '../components/agent-config/KeyValueEditor.vue'
import ParameterTable from '../components/agent-config/ParameterTable.vue'
import PublishChecklist from '../components/agent-config/PublishChecklist.vue'
import RemoteToolList from '../components/agent-config/RemoteToolList.vue'
import TestRunPanel from '../components/agent-config/TestRunPanel.vue'
import UsageTable from '../components/agent-config/UsageTable.vue'
import VersionHistoryTable from '../components/agent-config/VersionHistoryTable.vue'

/**
 * 工具编辑页：按类型配置工具实现，并由配置生成模型可调用的工具。
 * 内置工具选择代码登记的实现；HTTP 工具用参数表格和请求模板生成接口调用；MCP 工具从已发布服务发现远程工具。
 */
interface ToolForm {
  exposedName: string
  description: string
  readOnly: boolean
  implementationKey: string | null
  mcpServerVersionId: string | null
  remoteToolName: string | null
  /** 内置与 MCP 工具的参数来自实现，原样保存 */
  inputSchema: JsonSchema
  /** HTTP 工具的参数表格 */
  fields: ParamField[]
  http: HttpToolSpec
}

const route = useRoute()
const router = useRouter()
const access = useManagementStore()

const canEdit = computed(() => access.can('management:tool:edit'))
const canPublish = computed(() => access.can('management:tool:publish'))

const routeId = computed(() => String(route.params.id ?? ''))
const creating = computed(() => routeId.value === 'new')

const detail = ref<ToolDetail | null>(null)
const version = ref<ToolVersion | null>(null)
const mode = ref<EditorMode>('create')
const tab = ref<EditorTab>('config')
const loading = ref(false)
const loadError = ref('')

const entry = ref({ toolCode: '', toolName: '', toolType: 'HTTP' as ToolType })
const form = ref<ToolForm>(emptyForm())
const profileEditing = ref(false)
const profileName = ref('')

const builtinOptions = ref<BuiltinToolOption[]>([])
const mcpOptions = ref<McpServerOption[]>([])
const credentials = ref<string[]>([])
const optionsError = ref('')
const remoteTools = ref<McpRemoteTool[]>([])
const discovering = ref(false)
const discoverError = ref('')

const { busy, error, notice, run } = useAsyncAction()
const draftState = computed(() => ({ entry: entry.value, form: form.value }))
const { markClean, changed } = useSnapshot(draftState)

function emptyHttpSpec(): HttpToolSpec {
  return { method: 'GET', url: '', headers: [], bodyTemplate: null, credentialRef: null, timeoutMs: 15000, resultPointer: null }
}

function emptyForm(): ToolForm {
  return {
    exposedName: '',
    description: '',
    readOnly: true,
    implementationKey: null,
    mcpServerVersionId: null,
    remoteToolName: null,
    inputSchema: emptyObjectSchema(),
    fields: [],
    http: emptyHttpSpec(),
  }
}

function formFromVersion(source: ToolVersion): ToolForm {
  return {
    exposedName: source.exposedName,
    description: source.description,
    readOnly: source.readOnly,
    implementationKey: source.implementationKey,
    mcpServerVersionId: source.mcpServerVersionId,
    remoteToolName: source.remoteToolName,
    inputSchema: source.inputSchema ?? emptyObjectSchema(),
    fields: fieldsFromSchema(source.inputSchema),
    http: source.httpSpec
      ? { ...source.httpSpec, headers: source.httpSpec.headers.map(header => ({ ...header })) }
      : emptyHttpSpec(),
  }
}

const tool = computed(() => detail.value?.tool ?? null)
const toolType = computed<ToolType>(() => tool.value?.toolType ?? entry.value.toolType)
const editable = computed(() => canEdit.value && mode.value !== 'published')
const dirty = computed(() => editable.value && changed())
const profileDirty = computed(() => profileEditing.value && profileName.value !== tool.value?.toolName)
useUnsavedGuard(computed(() => dirty.value || profileDirty.value))

const typeOptions = computed<ChoiceOption<ToolType>[]>(() => toolTypes.map(value => ({
  value,
  title: toolTypeLabels[value],
  description: toolTypeDescriptions[value],
})))

/** 当前生效的参数 Schema：HTTP 由表格生成，其余取实现带出的 Schema。 */
const effectiveSchema = computed<JsonSchema>(() => (
  toolType.value === 'HTTP' ? schemaFromFields(form.value.fields) : form.value.inputSchema
))
const displayFields = computed(() => (
  toolType.value === 'HTTP' ? form.value.fields.filter(field => field.name.trim()) : fieldsFromSchema(form.value.inputSchema)
))
const fieldErrors = computed(() => (toolType.value === 'HTTP' ? validateFields(form.value.fields) : []))

const selectedBuiltin = computed(() => builtinOptions.value.find(option => option.implementationKey === form.value.implementationKey))
const selectedMcp = computed(() => mcpOptions.value.find(option => option.mcpServerVersionId === form.value.mcpServerVersionId))
const mcpServerLabel = computed(() => {
  if (selectedMcp.value) {
    return `${selectedMcp.value.serverName} · ${versionLabel(selectedMcp.value.versionNo)}`
  }
  if (version.value?.mcpServerName) {
    return `${version.value.mcpServerName} · ${versionLabel(version.value.mcpServerVersionNo)}`
  }
  return form.value.mcpServerVersionId ? '原绑定的服务版本已不可用' : '—'
})

// HTTP 请求模板相关的校验
const bodyAllowed = computed(() => bodyMethods.includes(form.value.http.method))
const urlValid = computed(() => {
  const url = form.value.http.url.trim()
  const match = /^(https?):\/\/([^/?#]+)/i.exec(url)
  return Boolean(match) && !match![2]!.includes('{{') && !url.includes('#')
})
const httpPlaceholders = computed(() => placeholdersIn(
  form.value.http.url,
  ...form.value.http.headers.map(header => header.value),
  bodyAllowed.value ? form.value.http.bodyTemplate : null,
))
const paramNames = computed(() => form.value.fields.map(field => field.name.trim()).filter(Boolean))
const undefinedPlaceholders = computed(() => httpPlaceholders.value
  .filter(name => name !== CREDENTIAL_PLACEHOLDER && !paramNames.value.includes(name)))
const usesCredential = computed(() => httpPlaceholders.value.includes(CREDENTIAL_PLACEHOLDER))
const bodyValid = computed(() => !bodyAllowed.value || !form.value.http.bodyTemplate?.trim() || isValidBodyTemplate(form.value.http.bodyTemplate))
const pointerValid = computed(() => !form.value.http.resultPointer || form.value.http.resultPointer.startsWith('/'))
const timeoutValid = computed(() => {
  const value = form.value.http.timeoutMs
  return Number.isInteger(value) && value >= 1000 && value <= 60000
})
const forbiddenHeaders = ['host', 'content-length', 'connection', 'transfer-encoding']
const headersValid = computed(() => form.value.http.headers.every(header => (
  header.name.trim() !== '' && !forbiddenHeaders.includes(header.name.trim().toLowerCase())
)))

const implementationReady = computed(() => {
  if (toolType.value === 'BUILTIN') {
    return Boolean(form.value.implementationKey)
  }
  if (toolType.value === 'MCP') {
    return Boolean(form.value.mcpServerVersionId && form.value.remoteToolName)
  }
  return urlValid.value && headersValid.value && bodyValid.value && pointerValid.value && timeoutValid.value
    && (!usesCredential.value || Boolean(form.value.http.credentialRef))
})

const checks = computed<CheckItem[]>(() => {
  const items: CheckItem[] = []
  if (creating.value) {
    items.push({ label: '填写工具编码和名称', passed: CODE_PATTERN.test(entry.value.toolCode) && entry.value.toolName.trim() !== '' })
  }
  items.push({ label: '模型工具名格式正确', passed: EXPOSED_NAME_PATTERN.test(form.value.exposedName), hint: '字母、数字、下划线或连字符，最多 64 位' })
  if (toolType.value === 'BUILTIN') {
    items.push({ label: '已选择内置实现', passed: Boolean(form.value.implementationKey) })
  } else if (toolType.value === 'MCP') {
    items.push(
      { label: '已选择 MCP 服务版本', passed: Boolean(form.value.mcpServerVersionId) },
      { label: '已选择远程工具', passed: Boolean(form.value.remoteToolName), hint: '点击“发现工具”后选择一个远程工具' },
    )
  } else {
    items.push(
      { label: '请求地址为 http/https 且主机不含占位符', passed: urlValid.value },
      { label: '参数定义有效', passed: fieldErrors.value.length === 0 },
      { label: '模板占位符均已定义为参数', passed: undefinedPlaceholders.value.length === 0, hint: `未定义：${undefinedPlaceholders.value.join('、')}` },
      { label: '使用 {{credential}} 时已选择凭据', passed: !usesCredential.value || Boolean(form.value.http.credentialRef) },
      { label: '请求头与请求体模板有效', passed: headersValid.value && bodyValid.value, hint: '请求头名称不能为空或使用受限名称；请求体替换占位符后须为合法 JSON' },
      { label: '超时与结果路径有效', passed: timeoutValid.value && pointerValid.value, hint: '超时 1000～60000 毫秒；结果路径以 / 开头' },
    )
  }
  items.push({ label: '描述已填写', passed: form.value.description.trim() !== '', hint: '发布前必须填写，模型根据描述判断何时调用', level: 'publish' })
  return items
})

/** 保存草稿允许描述为空，发布时必须填写；其余检查项同时约束保存与发布。 */
const canSave = computed(() => editable.value && !busy.value && blockingChecks(checks.value, 'save').length === 0)
const canPublishDraft = computed(() => (
  canPublish.value && mode.value === 'draft' && !dirty.value && !busy.value
  && blockingChecks(checks.value, 'publish').length === 0 && tool.value?.status === 'ENABLED'
))

const testDisabledReason = computed(() => {
  if (!canEdit.value) {
    return '试运行需要工具编辑权限。'
  }
  if (!implementationReady.value) {
    return '请先完成实现配置。'
  }
  if (fieldErrors.value.length) {
    return '请先修正参数定义。'
  }
  return null
})

const title = computed(() => (creating.value ? '新增工具' : tool.value?.toolName ?? '工具'))
const modeTag = computed(() => {
  if (mode.value === 'create' || mode.value === 'newDraft') {
    return { text: '新草稿', tone: 'is-warning' }
  }
  const label = versionLabel(version.value?.versionNo)
  return mode.value === 'draft' ? { text: `${label} 草稿`, tone: 'is-warning' } : { text: `${label} 已发布`, tone: 'is-success' }
})

/** 按工具类型组装草稿：只填写当前类型的实现字段，其余置空。 */
function toDraft(): ToolDraft {
  const type = toolType.value
  const http = form.value.http
  return {
    revision: mode.value === 'draft' ? version.value?.revision : undefined,
    exposedName: form.value.exposedName.trim(),
    description: form.value.description.trim(),
    inputSchema: effectiveSchema.value,
    readOnly: form.value.readOnly,
    implementationKey: type === 'BUILTIN' ? form.value.implementationKey : null,
    mcpServerVersionId: type === 'MCP' ? form.value.mcpServerVersionId : null,
    remoteToolName: type === 'MCP' ? form.value.remoteToolName : null,
    httpSpec: type === 'HTTP'
      ? {
          method: http.method,
          url: http.url.trim(),
          headers: http.headers.map(header => ({ name: header.name.trim(), value: header.value })),
          bodyTemplate: bodyAllowed.value && http.bodyTemplate?.trim() ? http.bodyTemplate : null,
          credentialRef: http.credentialRef || null,
          timeoutMs: http.timeoutMs,
          resultPointer: http.resultPointer?.trim() || null,
        }
      : null,
  }
}

async function loadOptions() {
  optionsError.value = ''
  const [builtin, mcp, credential] = await Promise.allSettled([
    api.listBuiltinToolOptions(),
    api.listMcpServerOptions(),
    api.listToolCredentialOptions(),
  ])
  builtinOptions.value = builtin.status === 'fulfilled' ? builtin.value : []
  mcpOptions.value = mcp.status === 'fulfilled' ? mcp.value : []
  credentials.value = credential.status === 'fulfilled' ? credential.value : []
  if ([builtin, mcp, credential].some(result => result.status === 'rejected')) {
    optionsError.value = '部分选项加载失败，可刷新页面重试。'
  }
}

/** 读取工具详情并选择默认版本：有草稿进入草稿，否则查看最新发布版本，都没有时准备新草稿。 */
async function load(preferredVersionId?: string) {
  loading.value = true
  loadError.value = ''
  try {
    detail.value = await api.getTool(routeId.value)
    const target = preferredVersionId ?? detail.value.tool.draftVersionId ?? detail.value.tool.publishedVersionId
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
  const loaded = await api.getToolVersion(routeId.value, versionId)
  version.value = loaded
  form.value = formFromVersion(loaded)
  mode.value = loaded.status === 'DRAFT' ? 'draft' : 'published'
  remoteTools.value = []
  discoverError.value = ''
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

function startDraft(source: ToolVersion | null) {
  form.value = source ? formFromVersion(source) : emptyForm()
  version.value = null
  mode.value = 'newDraft'
  remoteTools.value = []
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
  const draftId = tool.value?.draftVersionId
  if (draftId) {
    await selectVersion(draftId)
  }
}

/** 切换工具类型时清空上一种类型的实现配置，避免混入无效字段。 */
watch(() => entry.value.toolType, () => {
  if (!creating.value) {
    return
  }
  const name = form.value.exposedName
  const description = form.value.description
  form.value = { ...emptyForm(), exposedName: name, description }
  remoteTools.value = []
})

function applyBuiltin(implementationKey: string) {
  const option = builtinOptions.value.find(item => item.implementationKey === implementationKey)
  form.value.implementationKey = implementationKey
  if (!option) {
    return
  }
  form.value.exposedName = option.exposedName
  form.value.description = option.description
  form.value.inputSchema = option.inputSchema ?? emptyObjectSchema()
  form.value.readOnly = option.readOnly
}

function changeMcpServer(versionId: string) {
  form.value.mcpServerVersionId = versionId || null
  form.value.remoteToolName = null
  remoteTools.value = []
  discoverError.value = ''
}

async function discover() {
  const versionId = form.value.mcpServerVersionId
  if (!versionId) {
    return
  }
  discovering.value = true
  discoverError.value = ''
  try {
    remoteTools.value = await api.listRemoteTools(versionId)
    if (!remoteTools.value.length) {
      discoverError.value = '该服务版本没有返回任何工具。'
    }
  } catch (reason) {
    discoverError.value = errorMessage(reason)
  } finally {
    discovering.value = false
  }
}

/** 选择远程工具后带出远程定义；模型工具名默认取远程工具名并转换为合法格式。 */
function applyRemoteTool(remote: McpRemoteTool) {
  form.value.remoteToolName = remote.name
  form.value.exposedName = remote.name.replace(/[^A-Za-z0-9_-]/g, '_').slice(0, 64)
  form.value.description = remote.description ?? ''
  form.value.inputSchema = remote.inputSchema ?? emptyObjectSchema()
  form.value.readOnly = remote.readOnly
}

async function save() {
  if (!canSave.value) {
    return
  }
  if (creating.value) {
    const created = await run(() => api.createTool({
      toolCode: entry.value.toolCode.trim(),
      toolName: entry.value.toolName.trim(),
      toolType: entry.value.toolType,
      draft: toDraft(),
    }))
    if (created) {
      markClean()
      await router.replace(`/management/tools/${created.tool.toolId}`)
      notice.value = '工具已创建，草稿 v1 已保存。试运行无误后即可发布。'
    }
    return
  }

  const saved = await run(async () => {
    const result = mode.value === 'draft' && version.value
      ? await api.saveToolVersion(routeId.value, version.value.toolVersionId, toDraft())
      : await api.createToolVersion(routeId.value, toDraft())
    detail.value = await api.getTool(routeId.value)
    return result
  }, '草稿已保存。')
  if (saved) {
    version.value = saved
    form.value = formFromVersion(saved)
    mode.value = 'draft'
    markClean()
  }
}

async function publish() {
  const target = version.value
  if (!target || !canPublishDraft.value) {
    return
  }
  const message = `发布 ${versionLabel(target.versionNo)}？发布后该版本冻结不可修改，Agent 即可绑定。已绑定旧版本的 Agent 需要手动升级。`
  if (!await confirmManagement(message, { confirmText: '发布' })) {
    return
  }
  await run(async () => {
    await api.publishToolVersion(routeId.value, target.toolVersionId, target.revision)
    await load(target.toolVersionId)
  }, `${versionLabel(target.versionNo)} 已发布。`)
}

function executeTest(args: Record<string, unknown>) {
  return api.testRunTool({ toolType: toolType.value, draft: toDraft(), arguments: args })
}

function startProfileEdit() {
  profileName.value = tool.value?.toolName ?? ''
  profileEditing.value = true
}

async function saveProfile() {
  const current = tool.value
  if (!current || !profileName.value.trim()) {
    return
  }
  const updated = await run(() => api.updateTool(current.toolId, { toolName: profileName.value.trim(), revision: current.revision }), '工具名称已更新。')
  if (updated && detail.value) {
    detail.value = { ...detail.value, tool: updated }
    profileEditing.value = false
  }
}

async function toggleStatus() {
  const current = tool.value
  if (!current) {
    return
  }
  const disabling = current.status === 'ENABLED'
  const message = disabling
    ? `停用“${current.toolName}”？被启用 Agent 的生效版本绑定时无法停用。`
    : `启用“${current.toolName}”？`
  if (!await confirmManagement(message, { danger: disabling, confirmText: disabling ? '停用' : '启用' })) {
    return
  }
  const updated = await run(() => (disabling
    ? api.disableTool(current.toolId, current.revision)
    : api.enableTool(current.toolId, current.revision)), `工具已${disabling ? '停用' : '启用'}。`)
  if (updated && detail.value) {
    detail.value = { ...detail.value, tool: updated }
  }
}

function setMethod(method: HttpMethod) {
  form.value.http.method = method
}

async function init() {
  detail.value = null
  version.value = null
  tab.value = 'config'
  profileEditing.value = false
  remoteTools.value = []
  error.value = ''
  if (creating.value) {
    entry.value = { toolCode: '', toolName: '', toolType: 'HTTP' }
    form.value = emptyForm()
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
  void loadOptions()
  void init()
})
</script>

<template>
  <ManagementShell :current="creating ? '新增' : tool?.toolName">
    <div v-if="loading && !detail" class="management-card management-loading" role="status">正在加载…</div>
    <div v-else-if="loadError" class="management-card">
      <div class="management-empty">
        <p class="management-empty-title">加载失败</p>
        <p class="management-empty-desc">{{ loadError }}</p>
        <div class="management-empty-actions">
          <button type="button" @click="load()">重新加载</button>
          <RouterLink to="/management/tools">返回列表</RouterLink>
        </div>
      </div>
    </div>

    <ConfigEditorLayout
      v-else
      v-model:tab="tab"
      back-path="/management/tools"
      back-label="工具目录"
      :title="title"
      :show-tabs="!creating"
      :version-count="detail?.versions.length ?? 0"
      :usage-count="detail?.usages.length ?? 0"
      usage-label="引用 Agent"
    >
      <template #tags>
        <span class="management-tag">{{ toolTypeLabels[toolType] }}</span>
        <span v-if="tool" class="management-tag is-dot" :class="configStatusTones[tool.status]">
          {{ configStatusLabels[tool.status] }}
        </span>
        <span class="management-tag" :class="modeTag.tone">{{ modeTag.text }}</span>
      </template>

      <template #actions>
        <button v-if="tool && canPublish" type="button" :class="{ 'management-danger': tool.status === 'ENABLED' }" :disabled="busy" @click="toggleStatus">
          {{ tool.status === 'ENABLED' ? '停用' : '启用' }}
        </button>
        <template v-if="mode === 'published' && canEdit">
          <button v-if="tool?.draftVersionId" type="button" :disabled="busy" @click="editDraft">编辑草稿</button>
          <button v-else type="button" :disabled="busy" @click="startDraft(version)">基于此版本修改</button>
        </template>
        <button v-if="mode === 'newDraft' && detail?.versions.length" type="button" :disabled="busy" @click="discardDraft">放弃新草稿</button>
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

      <template v-if="tool" #meta>
        <span>编码 <span class="management-code">{{ tool.toolCode }}</span></span>
        <span>最新发布 {{ versionLabel(tool.publishedVersionNo) }}</span>
        <span>更新于 {{ formatTime(tool.lastUpdateTime) }}</span>
      </template>

      <template #messages>
        <p v-if="error" class="management-error" role="alert">{{ error }}</p>
        <p v-if="notice" class="management-notice" role="status">{{ notice }}</p>
        <p v-if="optionsError" class="management-warning">{{ optionsError }}</p>
        <p v-if="mode === 'published'" class="management-info">
          正在查看已发布的 {{ versionLabel(version?.versionNo) }}，已发布版本不可修改。{{ canEdit ? '如需调整，请基于此版本创建新草稿。' : '' }}
        </p>
        <p v-if="tool?.status === 'DISABLED'" class="management-warning">工具已停用：Agent 无法绑定，草稿也不能发布。</p>
      </template>

      <template #main>
        <ManagementCard v-if="creating" title="工具类型" description="类型决定工具的生成方式，创建后不可修改。">
          <ChoiceCards v-model="entry.toolType" :options="typeOptions" name="tool-type" label="工具类型" />
        </ManagementCard>

        <ManagementCard title="基本信息" description="编码与类型创建后不可修改，名称用于管理端展示。">
          <template v-if="tool && canEdit && !profileEditing" #actions>
            <button type="button" class="management-btn-sm" :disabled="busy" @click="startProfileEdit">编辑</button>
          </template>
          <div v-if="creating" class="management-form-grid">
            <label class="management-field">
              <span class="management-field-label management-required">工具编码</span>
              <input
                v-model.trim="entry.toolCode"
                maxlength="80"
                placeholder="例如 order_query"
                :aria-invalid="entry.toolCode !== '' && !CODE_PATTERN.test(entry.toolCode)"
              />
              <span class="management-field-hint">字母、数字、下划线或连字符，创建后不可修改。</span>
            </label>
            <label class="management-field">
              <span class="management-field-label management-required">工具名称</span>
              <input v-model="entry.toolName" maxlength="120" placeholder="例如 订单查询" />
            </label>
          </div>
          <div v-else-if="profileEditing" class="management-form-grid">
            <label class="management-field">
              <span class="management-field-label management-required">工具名称</span>
              <input v-model="profileName" maxlength="120" />
            </label>
            <div class="management-field">
              <span class="management-field-label">工具编码</span>
              <div class="management-field-value"><span class="management-code">{{ tool?.toolCode }}</span></div>
            </div>
            <div class="management-inline is-full">
              <button type="button" class="management-primary management-btn-sm" :disabled="busy || !profileName.trim()" @click="saveProfile">保存名称</button>
              <button type="button" class="management-btn-sm" :disabled="busy" @click="profileEditing = false">取消</button>
            </div>
          </div>
          <dl v-else class="management-form-grid">
            <div class="management-field">
              <dt class="management-field-label">工具名称</dt>
              <dd class="management-field-value">{{ tool?.toolName }}</dd>
            </div>
            <div class="management-field">
              <dt class="management-field-label">工具编码</dt>
              <dd class="management-field-value"><span class="management-code">{{ tool?.toolCode }}</span></dd>
            </div>
          </dl>
        </ManagementCard>

        <fieldset class="config-fieldset" :disabled="!editable || busy">
          <ManagementCard title="实现配置" :description="toolTypeDescriptions[toolType]">
            <template v-if="toolType === 'BUILTIN'">
              <label class="management-field">
                <span class="management-field-label management-required">内置实现</span>
                <select :value="form.implementationKey ?? ''" @change="applyBuiltin(($event.target as HTMLSelectElement).value)">
                  <option value="" disabled>选择平台登记的实现</option>
                  <option
                    v-if="form.implementationKey && !selectedBuiltin"
                    :value="form.implementationKey"
                  >
                    {{ form.implementationKey }}（实现已下线）
                  </option>
                  <option v-for="option in builtinOptions" :key="option.implementationKey" :value="option.implementationKey">
                    {{ option.exposedName }} · {{ option.implementationKey }}
                  </option>
                </select>
                <span class="management-field-hint">选择后带出名称、描述和参数；参数与只读标记由实现决定。</span>
              </label>
              <p v-if="!builtinOptions.length" class="management-muted">暂无可用的内置实现。</p>
            </template>

            <template v-else-if="toolType === 'MCP'">
              <p v-if="!mcpOptions.length && !form.mcpServerVersionId" class="management-warning">
                暂无可用的 MCP 服务版本。请先
                <RouterLink to="/management/mcp-servers">配置并发布 MCP 服务</RouterLink>
                ，再回来发现远程工具。
              </p>
              <div class="management-form-grid">
                <label class="management-field">
                  <span class="management-field-label management-required">MCP 服务版本</span>
                  <select :value="form.mcpServerVersionId ?? ''" @change="changeMcpServer(($event.target as HTMLSelectElement).value)">
                    <option value="" disabled>选择已发布的服务版本</option>
                    <option v-if="form.mcpServerVersionId && !selectedMcp" :value="form.mcpServerVersionId">{{ mcpServerLabel }}</option>
                    <option v-for="option in mcpOptions" :key="option.mcpServerVersionId" :value="option.mcpServerVersionId">
                      {{ option.serverName }} · {{ versionLabel(option.versionNo) }}{{ option.latest ? '（最新）' : '' }}
                    </option>
                  </select>
                </label>
                <div class="management-field">
                  <span class="management-field-label management-required">远程工具</span>
                  <div class="management-input-group">
                    <input :value="form.remoteToolName ?? ''" readonly placeholder="发现后选择" aria-label="远程工具名" />
                    <button type="button" :disabled="!form.mcpServerVersionId || discovering || !editable" @click="discover">
                      {{ discovering ? '发现中…' : '发现工具' }}
                    </button>
                  </div>
                </div>
              </div>
              <p v-if="discoverError" class="management-error" role="alert">{{ discoverError }}</p>
              <RemoteToolList
                v-if="remoteTools.length"
                :tools="remoteTools"
                selectable
                :selected="form.remoteToolName"
                :disabled="!editable"
                @select="applyRemoteTool"
              />
            </template>

            <template v-else>
              <div class="management-field">
                <span class="management-field-label management-required">请求</span>
                <div class="management-input-group">
                  <select :value="form.http.method" aria-label="请求方法" @change="setMethod(($event.target as HTMLSelectElement).value as HttpMethod)">
                    <option v-for="method in httpMethods" :key="method" :value="method">{{ method }}</option>
                  </select>
                  <input
                    v-model.trim="form.http.url"
                    maxlength="2048"
                    placeholder="https://api.example.com/orders/{{orderId}}"
                    aria-label="请求地址"
                    :aria-invalid="form.http.url !== '' && !urlValid"
                  />
                </div>
                <span class="management-field-hint">
                  路径和查询中可用 <code v-pre>{{参数名}}</code> 引用参数（自动 URL 编码），<code v-pre>{{credential}}</code> 引用凭据；主机部分不能使用占位符。
                  未在地址中使用的参数：GET/DELETE 追加为查询参数，POST/PUT/PATCH 组成 JSON 请求体。
                </span>
              </div>

              <div class="management-field">
                <span class="management-field-label">请求头</span>
                <KeyValueEditor
                  v-model="form.http.headers"
                  :read-only="!editable"
                  name-placeholder="Authorization"
                  value-placeholder="Bearer {{credential}}"
                  add-label="添加请求头"
                  empty-text="未配置请求头，默认发送 Accept: application/json"
                />
              </div>

              <label v-if="bodyAllowed" class="management-field">
                <span class="management-field-label">请求体模板</span>
                <textarea
                  :value="form.http.bodyTemplate ?? ''"
                  rows="5"
                  class="config-code-input"
                  placeholder='{"q": {{keyword}}, "size": {{size}}}'
                  :aria-invalid="!bodyValid"
                  @input="form.http.bodyTemplate = ($event.target as HTMLTextAreaElement).value"
                />
                <span class="management-field-hint">
                  可留空（留空时由参数组成 JSON 请求体）。<code v-pre>{{参数名}}</code> 替换为参数的 JSON 值，字符串会自动加引号，因此写 <code v-pre>{"q": {{keyword}}}</code> 而不是 <code v-pre>{"q": "{{keyword}}"}</code>。
                </span>
                <span v-if="!bodyValid" class="management-field-error">占位符替换为 null 后不是合法 JSON。</span>
              </label>

              <div class="management-form-grid">
                <label class="management-field">
                  <span class="management-field-label" :class="{ 'management-required': usesCredential }">凭据</span>
                  <select v-model="form.http.credentialRef">
                    <option :value="null">不使用凭据</option>
                    <option
                      v-if="form.http.credentialRef && !credentials.includes(form.http.credentialRef)"
                      :value="form.http.credentialRef"
                    >
                      {{ form.http.credentialRef }}（未配置）
                    </option>
                    <option v-for="item in credentials" :key="item" :value="item">{{ item }}</option>
                  </select>
                  <span class="management-field-hint">替换模板中的 <code v-pre>{{credential}}</code>，凭据值不会回显。</span>
                </label>
                <label class="management-field">
                  <span class="management-field-label management-required">超时（毫秒）</span>
                  <input v-model.number="form.http.timeoutMs" type="number" min="1000" max="60000" step="1000" :aria-invalid="!timeoutValid" />
                  <span class="management-field-hint">1000～60000，默认 15000。</span>
                </label>
                <label class="management-field is-full">
                  <span class="management-field-label">结果路径</span>
                  <input
                    :value="form.http.resultPointer ?? ''"
                    maxlength="255"
                    placeholder="/data/items"
                    :aria-invalid="!pointerValid"
                    @input="form.http.resultPointer = ($event.target as HTMLInputElement).value"
                  />
                  <span class="management-field-hint">JSON Pointer，从 JSON 响应中提取返回给模型的部分；留空返回完整响应。</span>
                </label>
              </div>

              <p v-if="undefinedPlaceholders.length" class="management-warning">
                模板引用了未定义的参数：{{ undefinedPlaceholders.join('、') }}。请在下方参数中添加，或修改模板。
              </p>
              <p v-if="usesCredential && !form.http.credentialRef" class="management-warning">
                模板使用了 <code v-pre>{{credential}}</code>，请选择凭据。
              </p>
            </template>
          </ManagementCard>

          <ManagementCard title="模型可见定义" description="模型根据名称、描述和参数决定何时调用以及如何传参。">
            <div class="management-form-grid">
              <label class="management-field">
                <span class="management-field-label management-required">模型工具名</span>
                <input
                  v-model.trim="form.exposedName"
                  maxlength="64"
                  placeholder="例如 query_order"
                  :aria-invalid="form.exposedName !== '' && !EXPOSED_NAME_PATTERN.test(form.exposedName)"
                />
                <span class="management-field-hint">字母、数字、下划线或连字符；同一 Agent 内不能重复。</span>
              </label>
              <div class="management-field">
                <span class="management-field-label">只读</span>
                <label class="management-switch">
                  <input v-model="form.readOnly" type="checkbox" :disabled="toolType !== 'HTTP' || !editable" />
                  <span>{{ form.readOnly ? '只读，不修改外部数据' : '会修改外部数据' }}</span>
                </label>
                <span class="management-field-hint">{{ toolType === 'HTTP' ? '有写操作的接口请关闭。' : '由实现决定。' }}</span>
              </div>
              <label class="management-field is-full">
                <span class="management-field-label management-required">描述</span>
                <textarea
                  v-model="form.description"
                  rows="4"
                  :maxlength="TOOL_DESCRIPTION_LIMIT"
                  placeholder="模型据此判断何时调用：说明工具能做什么、适用场景和返回内容"
                />
                <span class="management-field-hint">模型据此判断何时调用。保存草稿时可暂不填写，发布前必须填写。</span>
              </label>
            </div>
          </ManagementCard>

          <ManagementCard
            title="参数"
            :description="toolType === 'HTTP' ? '定义模型调用时需要传入的参数，保存时生成 JSON Schema。' : '参数来自实现或远程服务，只读展示。'"
          >
            <ParameterTable
              v-if="toolType === 'HTTP'"
              v-model="form.fields"
              :read-only="!editable"
              :schema="effectiveSchema"
              empty-text="暂无参数，点击“添加参数”定义模型可传入的值"
            />
            <ParameterTable
              v-else
              :model-value="displayFields"
              read-only
              :schema="effectiveSchema"
              :empty-text="toolType === 'MCP' ? '选择远程工具后带出参数' : '选择内置实现后带出参数'"
            />
          </ManagementCard>
        </fieldset>

        <ManagementCard title="试运行" description="用当前填写的定义调用一次工具，结果不写库，也不进入 Agent 会话。">
          <TestRunPanel :fields="displayFields" :disabled-reason="testDisabledReason" :execute="executeTest" />
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
            <template v-if="toolType === 'MCP'">
              <dt>MCP 服务</dt>
              <dd>{{ mcpServerLabel }}</dd>
            </template>
            <template v-if="tool">
              <dt>最新发布</dt>
              <dd>{{ versionLabel(tool.publishedVersionNo) }}</dd>
              <dt>引用 Agent</dt>
              <dd>{{ tool.usageCount }}</dd>
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
          :selected-id="version?.toolVersionId ?? null"
          current-label="最新发布"
          :busy="busy"
          @select="selectVersion"
        />
      </template>

      <template #usages>
        <UsageTable
          :usages="detail?.usages ?? []"
          owner-label="Agent"
          :owner-path="usage => `/management/agents/${usage.ownerId}`"
          description="列出绑定该工具版本的 Agent 版本。被启用 Agent 的当前生效版本绑定时为生效引用，此时工具不能停用。"
        />
      </template>
    </ConfigEditorLayout>
  </ManagementShell>
</template>
