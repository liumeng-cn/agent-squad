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
  ElSwitch,
  ElTag,
} from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { errorMessage } from '../../../shared/http/http'
import { useManagementStore } from '../stores/management'
import {
  createTool,
  getTool,
  getToolVersion,
  listBuiltinToolOptions,
  listMcpServerOptions,
  listRemoteTools,
  listToolCredentialOptions,
  saveTool,
  testRunTool,
} from '../api/agentConfigApi'
import type {
  BuiltinToolOption,
  HttpMethod,
  HttpToolSpec,
  JsonSchema,
  McpRemoteTool,
  McpServerOption,
  ToolDetail,
  ToolSave,
  ToolTestRun,
  ToolType,
  ToolVersion,
} from '../model/agentConfig'
import {
  bodyMethods,
  configStatusLabels,
  configStatusTones,
  formatTime,
  httpMethods,
  readinessLabels,
  readinessTones,
  TOOL_DESCRIPTION_LIMIT,
  toolTypeLabels,
  toolTypes,
  versionLabel,
} from '../model/agentConfigLabels'
import { tagType } from '../model/elementTheme'
import { fieldsFromSchema, schemaFromFields, type ParamField } from '../model/toolSchema'
import { parseCurlCommand } from '../model/curlParser'
import { useEditorState } from '../composables/useEditorState'
import ManagementShell from '../components/ManagementShell.vue'
import ManagementDialog from '../components/ManagementDialog.vue'
import SteppedEditorLayout, { type EditorStepItem } from '../components/agent-config/SteppedEditorLayout.vue'
import ConfigReadonlyCards from '../components/agent-config/ConfigReadonlyCards.vue'
import KeyValueEditor from '../components/agent-config/KeyValueEditor.vue'
import ParameterTable from '../components/agent-config/ParameterTable.vue'
import TestRunPanel from '../components/agent-config/TestRunPanel.vue'
import UsageTable from '../components/agent-config/UsageTable.vue'
import VersionHistoryTable from '../components/agent-config/VersionHistoryTable.vue'

/**
 * 工具分步聚焦编辑页（方案 3）：1. 基础信息 → 2. 实现与定义 → 3. 在线试运行 → 4. 引用与历史。
 */
const route = useRoute()
const router = useRouter()
const access = useManagementStore()

const canEdit = computed(() => access.can('management:tool:edit'))
const toolId = computed(() => {
  const raw = route.params.id ? String(route.params.id) : ''
  return raw && raw !== 'new' ? raw : null
})
const isCreate = computed(() => !toolId.value)

const step = ref('basic')
const readonlyView = ref(false)
const detail = ref<ToolDetail | null>(null)
const builtins = ref<BuiltinToolOption[]>([])
const mcpOptions = ref<McpServerOption[]>([])
const credentials = ref<string[]>([])
const remoteTools = ref<McpRemoteTool[]>([])
const discovering = ref(false)
const discoverError = ref('')
const loading = ref(false)
const busy = ref(false)
const error = ref('')
const notice = ref('')
const advancedOpen = ref<string[]>([])
const inspectedVersion = ref<ToolVersion | null>(null)
const showCurlDialog = ref(false)
const curlInput = ref('')
const curlError = ref('')
const showLlmPreview = ref(false)
const copiedContract = ref(false)

function defaultHttpSpec(): HttpToolSpec {
  return { method: 'GET', url: '', headers: [], bodyTemplate: null, credentialRef: null, timeoutMs: 15000, resultPointer: null }
}

const form = ref({
  toolName: '',
  displayAlias: '',
  toolType: 'HTTP' as ToolType,
  exposedName: '',
  description: '',
  readOnly: true,
  implementationKey: null as string | null,
  mcpServerId: null as string | null,
  remoteToolName: null as string | null,
  httpSpec: defaultHttpSpec(),
  inputSchema: { type: 'object', properties: {} } as JsonSchema,
  parameters: [] as ParamField[],
})

const { dirty, markClean, resetDirty } = useEditorState(() => JSON.stringify(form.value))
const title = computed(() => (isCreate.value ? '新增工具' : detail.value?.tool.toolName || '工具'))
const currentSchema = computed<JsonSchema>(() =>
  form.value.toolType === 'HTTP' ? schemaFromFields(form.value.parameters) : form.value.inputSchema,
)
const showsBody = computed(() => bodyMethods.includes(form.value.httpSpec.method))

/** AgentScope 注册到 Toolkit 后实际暴露给大模型的工具契约预览 */
const llmContractJson = computed(() =>
  JSON.stringify(
    {
      type: 'function',
      function: {
        name: form.value.exposedName.trim() || '<未填写模型工具名>',
        description: form.value.description.trim() || '<未填写调用说明>',
        parameters: currentSchema.value,
      },
    },
    null,
    2,
  ),
)

function applyCurlImport() {
  curlError.value = ''
  try {
    const parsed = parseCurlCommand(curlInput.value)
    form.value.httpSpec.method = parsed.method
    form.value.httpSpec.url = parsed.url
    form.value.httpSpec.headers = parsed.headers
    form.value.httpSpec.bodyTemplate = parsed.bodyTemplate
    if (parsed.inferredParams.length) {
      form.value.parameters = parsed.inferredParams
    }
    form.value.readOnly = parsed.method === 'GET'
    showCurlDialog.value = false
    curlInput.value = ''
    notice.value = '已从 cURL 自动解析填充请求方法、地址、请求头与参数，请核对后保存。'
  } catch (reason) {
    curlError.value = reason instanceof Error ? reason.message : String(reason)
  }
}

async function copyContract() {
  try {
    await navigator.clipboard.writeText(llmContractJson.value)
    copiedContract.value = true
    setTimeout(() => {
      copiedContract.value = false
    }, 1800)
  } catch {
    // 忽略无剪贴板权限环境
  }
}

const steps = computed<EditorStepItem[]>(() => [
  { key: 'basic', no: 1, label: '基础信息' },
  { key: 'impl', no: 2, label: '实现与定义' },
  { key: 'test', no: 3, label: '在线试运行' },
  { key: 'history', no: 4, label: '引用与历史', badge: detail.value?.versions.length, hidden: isCreate.value },
])

const stepTitle = computed(() => {
  if (step.value === 'basic') return '1. 基础信息'
  if (step.value === 'impl') return `2. 实现与模型定义（${toolTypeLabels[form.value.toolType]}）`
  if (step.value === 'test') return '3. 在线试运行'
  return '4. 引用关系与配置历史'
})

function applyDetail(data: ToolDetail) {
  detail.value = data
  const latest = data.latestVersion
  const schema = latest?.inputSchema ?? { type: 'object', properties: {} }
  form.value = {
    toolName: data.tool.toolName,
    displayAlias: data.tool.displayAlias || '',
    toolType: data.tool.toolType,
    exposedName: latest?.exposedName ?? '',
    description: latest?.description ?? '',
    readOnly: latest?.readOnly ?? true,
    implementationKey: latest?.implementationKey ?? null,
    mcpServerId: latest?.mcpServerId ?? null,
    remoteToolName: latest?.remoteToolName ?? null,
    httpSpec: latest?.httpSpec ? { ...latest.httpSpec, headers: [...(latest.httpSpec.headers ?? [])] } : defaultHttpSpec(),
    inputSchema: schema,
    parameters: fieldsFromSchema(schema),
  }
  markClean()
}

function selectBuiltin(key: string) {
  form.value.implementationKey = key
  const option = builtins.value.find(item => item.implementationKey === key)
  if (!option) return
  form.value.exposedName = option.exposedName
  form.value.description = option.description
  form.value.inputSchema = option.inputSchema
  form.value.parameters = fieldsFromSchema(option.inputSchema)
  form.value.readOnly = option.readOnly
}

function selectMcpServer(serverId: string | null) {
  form.value.mcpServerId = serverId
  form.value.remoteToolName = null
  remoteTools.value = []
  discoverError.value = ''
}

async function discoverRemote() {
  if (!form.value.mcpServerId) {
    discoverError.value = '请先选择已就绪的 MCP 服务'
    return
  }
  discovering.value = true
  discoverError.value = ''
  try {
    remoteTools.value = await listRemoteTools(form.value.mcpServerId)
  } catch (reason) {
    discoverError.value = errorMessage(reason)
  } finally {
    discovering.value = false
  }
}

function selectRemoteTool(name: string) {
  form.value.remoteToolName = name
  const picked = remoteTools.value.find(item => item.name === name)
  if (!picked) return
  if (!form.value.exposedName.trim()) form.value.exposedName = picked.name
  if (!form.value.description.trim()) form.value.description = picked.description || ''
  form.value.inputSchema = picked.inputSchema ?? { type: 'object', properties: {} }
  form.value.parameters = fieldsFromSchema(form.value.inputSchema)
  form.value.readOnly = picked.readOnly
}

function changeHttpMethod(method: HttpMethod) {
  form.value.httpSpec.method = method
  if (!bodyMethods.includes(method)) form.value.httpSpec.bodyTemplate = null
}

function buildSavePayload(): ToolSave {
  return {
    revision: detail.value?.tool.revision,
    toolName: form.value.toolName.trim(),
    displayAlias: form.value.displayAlias.trim(),
    toolType: form.value.toolType,
    exposedName: form.value.exposedName.trim(),
    description: form.value.description.trim(),
    inputSchema: currentSchema.value,
    readOnly: form.value.readOnly,
    implementationKey: form.value.toolType === 'BUILTIN' ? form.value.implementationKey : null,
    mcpServerId: form.value.toolType === 'MCP' ? form.value.mcpServerId : null,
    remoteToolName: form.value.toolType === 'MCP' ? form.value.remoteToolName : null,
    httpSpec:
      form.value.toolType === 'HTTP'
        ? { ...form.value.httpSpec, url: form.value.httpSpec.url.trim(), headers: form.value.httpSpec.headers.filter(h => h.name.trim()) }
        : null,
  }
}

async function save() {
  if (!form.value.toolName.trim()) {
    step.value = 'basic'
    error.value = '请先填写工具名称'
    return
  }
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    const payload = buildSavePayload()
    if (isCreate.value) {
      const created = await createTool(payload)
      applyDetail(created)
      notice.value = `工具已创建（编码 ${created.tool.toolCode}，配置版本 v${created.tool.latestVersionNo}）。`
      await router.replace(`/management/tools/${created.tool.toolId}`)
    } else {
      const saved = await saveTool(toolId.value!, payload)
      applyDetail(saved)
      notice.value = `已保存为新配置版本 v${saved.tool.latestVersionNo}（${readinessLabels[saved.tool.latestReadiness]}）。`
    }
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    busy.value = false
  }
}

async function runTest(args: Record<string, unknown>): Promise<ToolTestRun> {
  return testRunTool({
    toolType: form.value.toolType,
    definition: buildSavePayload(),
    arguments: args,
  })
}

async function inspectVersion(versionId: string) {
  if (!toolId.value) return
  busy.value = true
  try {
    inspectedVersion.value = await getToolVersion(toolId.value, versionId)
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    busy.value = false
  }
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [builtinList, mcpList, credList] = await Promise.all([
      listBuiltinToolOptions(),
      listMcpServerOptions(),
      listToolCredentialOptions(),
    ])
    builtins.value = builtinList
    mcpOptions.value = mcpList
    credentials.value = credList
    if (toolId.value) {
      applyDetail(await getTool(toolId.value))
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

onMounted(() => {
  void load()
})
</script>

<template>
  <ManagementShell>
    <SteppedEditorLayout
      v-model:step="step"
      v-model:readonly-view="readonlyView"
      back-path="/management/tools"
      back-label="工具列表"
      :title="title"
      :code="detail?.tool.toolCode"
      :subtitle="detail ? `最近更新：${formatTime(detail.tool.lastUpdateTime)}` : '编码格式：TOOL-H-yyyyMMdd-001'"
      :steps="steps"
      :can-save="canEdit"
      :can-toggle-readonly="!isCreate && Boolean(detail?.latestVersion)"
      :saving="busy || loading"
      :save-label="dirty ? '保存新版本' : '保存'"
      @save="save"
    >
      <template #extra-actions>
        <ElButton
          v-if="canEdit && !readonlyView && step === 'impl' && form.toolType === 'HTTP'"
          native-type="button"
          @click="showCurlDialog = true"
        >
          从 cURL 导入
        </ElButton>
      </template>

      <template #readonly>
        <ConfigReadonlyCards
          v-if="detail?.latestVersion"
          :tool="{ summary: detail.tool, version: detail.latestVersion }"
        />
      </template>
      <template #status>
        <ElTag class="management-tag" type="info">{{ toolTypeLabels[form.toolType] }}</ElTag>
        <ElTag v-if="detail" class="management-tag" :class="configStatusTones[detail.tool.status]" :type="tagType(configStatusTones[detail.tool.status])">
          {{ configStatusLabels[detail.tool.status] }}
        </ElTag>
        <ElTag v-if="detail" class="management-tag" :class="readinessTones[detail.tool.latestReadiness]" :type="tagType(readinessTones[detail.tool.latestReadiness])">
          {{ versionLabel(detail.tool.latestVersionNo) }} · {{ readinessLabels[detail.tool.latestReadiness] }}
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
          <span class="management-field-label">工具名称 <span class="management-required" /></span>
          <ElInput v-model="form.toolName" :disabled="!canEdit" maxlength="120" placeholder="后台管理识别名，如：订单查询接口-生产" />
        </label>
        <label class="management-field">
          <span class="management-field-label">前端展示别称</span>
          <ElInput v-model="form.displayAlias" :disabled="!canEdit" maxlength="60" placeholder="聊天事件卡片展示，留空使用工具名称" />
        </label>
        <label class="management-field">
          <span class="management-field-label">工具类型</span>
          <ElRadioGroup v-model="form.toolType" :disabled="!isCreate || !canEdit">
            <ElRadioButton v-for="item in toolTypes" :key="item" :value="item">
              {{ toolTypeLabels[item] }}
            </ElRadioButton>
          </ElRadioGroup>
        </label>
        <label class="management-field">
          <span class="management-field-label">组件编码（自动生成）</span>
          <ElInput :model-value="detail?.tool.toolCode || '保存后自动生成（TOOL-*-yyyyMMdd-001）'" disabled />
        </label>
      </div>

      <!-- 步骤 2：实现与定义 -->
      <div v-else-if="step === 'impl'" class="studio-canvas-body">
        <div v-if="form.toolType === 'BUILTIN'" class="management-form-grid">
          <label class="management-field">
            <span class="management-field-label">内置实现</span>
            <ElSelect :model-value="form.implementationKey" :disabled="!canEdit" filterable placeholder="选择内置实现" @update:model-value="selectBuiltin(String($event))">
              <ElOption v-for="item in builtins" :key="item.implementationKey" :value="item.implementationKey" :label="`${item.exposedName}（${item.implementationKey}）`" />
            </ElSelect>
          </label>
          <label class="management-field">
            <span class="management-field-label">只读属性</span>
            <ElInput :model-value="form.readOnly ? '只读（由内置实现决定）' : '包含写操作（由内置实现决定）'" disabled />
          </label>
        </div>

        <div v-else-if="form.toolType === 'MCP'" class="management-form-grid">
          <label class="management-field">
            <span class="management-field-label">关联 MCP 服务（仅启用且就绪）</span>
            <ElSelect :model-value="form.mcpServerId" :disabled="!canEdit" clearable filterable placeholder="选择 MCP 服务" @update:model-value="selectMcpServer($event ? String($event) : null)">
              <ElOption v-for="item in mcpOptions" :key="item.mcpServerId" :value="item.mcpServerId" :label="`${item.serverName}（${item.serverCode}）`" />
            </ElSelect>
          </label>
          <div class="management-field">
            <span class="management-field-label">远程工具</span>
            <div class="management-actions">
              <ElSelect :model-value="form.remoteToolName" :disabled="!canEdit || !form.mcpServerId" filterable placeholder="先发现远程工具" style="flex: 1" @update:model-value="selectRemoteTool(String($event))">
                <ElOption v-for="item in remoteTools" :key="item.name" :value="item.name" :label="item.name" />
              </ElSelect>
              <ElButton native-type="button" :loading="discovering" :disabled="!form.mcpServerId" @click="discoverRemote">发现工具</ElButton>
            </div>
            <span v-if="discoverError" class="management-field-error">{{ discoverError }}</span>
          </div>
        </div>

        <div v-else class="management-form-grid">
          <label class="management-field">
            <span class="management-field-label">请求方法</span>
            <ElSelect :model-value="form.httpSpec.method" :disabled="!canEdit" @update:model-value="changeHttpMethod($event as HttpMethod)">
              <ElOption v-for="m in httpMethods" :key="m" :value="m" :label="m" />
            </ElSelect>
          </label>
          <label class="management-field">
            <span class="management-field-label">凭据引用</span>
            <ElSelect v-model="form.httpSpec.credentialRef" :disabled="!canEdit" clearable filterable placeholder="不使用凭据">
              <ElOption v-for="item in credentials" :key="item" :value="item" :label="item" />
            </ElSelect>
          </label>
          <label class="management-field is-full">
            <span class="management-field-label">请求地址模板（支持 &#123;&#123;参数名&#125;&#125;）</span>
            <ElInput v-model="form.httpSpec.url" :disabled="!canEdit" maxlength="2048" placeholder="https://api.example.com/orders/{{order_id}}" />
          </label>
          <div class="management-field is-full">
            <span class="management-field-label">请求参数（自动生成 JSON Schema）</span>
            <ParameterTable v-model="form.parameters" :read-only="!canEdit" :schema="currentSchema" />
          </div>
          <div class="management-field is-full">
            <span class="management-field-label">请求头</span>
            <KeyValueEditor v-model="form.httpSpec.headers" :read-only="!canEdit" />
          </div>
          <label v-if="showsBody" class="management-field is-full">
            <span class="management-field-label">请求体模板（JSON，选填）</span>
            <ElInput v-model="form.httpSpec.bodyTemplate" :disabled="!canEdit" type="textarea" :rows="3" placeholder='{"orderId": "{{order_id}}"}' />
          </label>
        </div>

        <div class="studio-subblock">
          <div class="studio-subblock-head">
            <span class="studio-subblock-title">模型可见定义</span>
            <button type="button" class="management-link-btn" @click="showLlmPreview = !showLlmPreview">
              {{ showLlmPreview ? '收起大模型视角契约' : '预览大模型视角 JSON' }}
            </button>
          </div>
          <div class="management-form-grid">
            <label class="management-field">
              <span class="management-field-label">模型工具名（1～64 位字母数字下划线）</span>
              <ElInput v-model="form.exposedName" :disabled="!canEdit" maxlength="64" placeholder="如 query_order_detail（禁止 ask_user）" />
            </label>
            <label v-if="form.toolType === 'HTTP'" class="management-field">
              <span class="management-field-label">只读属性</span>
              <ElSwitch v-model="form.readOnly" :disabled="!canEdit" active-text="只读工具" inactive-text="存在写操作" />
            </label>
            <label class="management-field is-full">
              <span class="management-field-label">模型调用说明</span>
              <ElInput v-model="form.description" :disabled="!canEdit" type="textarea" :rows="3" :maxlength="TOOL_DESCRIPTION_LIMIT" placeholder="向模型清晰描述该工具的用途与适用时机" />
            </label>
          </div>
          <div v-if="form.toolType !== 'HTTP'" class="management-field is-full">
            <span class="management-field-label">参数定义（只读，来自真实实现）</span>
            <ParameterTable v-model="form.parameters" :read-only="true" :schema="currentSchema" />
          </div>

          <div v-if="showLlmPreview" class="prompt-preview-box">
            <div class="studio-subblock-head" style="margin-bottom: 6px">
              <span class="prompt-preview-title" style="margin: 0">AgentScope 注册给大模型的 Function Schema 契约</span>
              <ElButton link type="primary" native-type="button" @click="copyContract">
                {{ copiedContract ? '已复制' : '复制 JSON' }}
              </ElButton>
            </div>
            <pre class="prompt-preview-content">{{ llmContractJson }}</pre>
          </div>
        </div>

        <ElCollapse v-if="form.toolType === 'HTTP'" v-model="advancedOpen">
          <ElCollapseItem title="高级设置（超时与结果提取）" name="advanced">
            <div class="management-form-grid">
              <label class="management-field">
                <span class="management-field-label">超时（毫秒，1000～60000）</span>
                <ElInputNumber v-model="form.httpSpec.timeoutMs" :disabled="!canEdit" :min="1000" :max="60000" :step="1000" />
              </label>
              <label class="management-field">
                <span class="management-field-label">结果提取路径（JSON Pointer）</span>
                <ElInput v-model="form.httpSpec.resultPointer" :disabled="!canEdit" maxlength="500" placeholder="/data/items" />
              </label>
            </div>
          </ElCollapseItem>
        </ElCollapse>
      </div>

      <!-- 步骤 3：在线试运行 -->
      <div v-else-if="step === 'test'">
        <TestRunPanel :fields="form.parameters" :disabled-reason="null" :execute="runTest" />
      </div>

      <!-- 步骤 4：引用与历史 -->
      <div v-else class="studio-canvas-body">
        <div class="studio-subblock">
          <span class="studio-subblock-title">配置历史（{{ detail?.versions.length ?? 0 }}）</span>
          <VersionHistoryTable
            :versions="detail?.versions ?? []"
            :latest-version-id="detail?.tool.latestVersionId ?? null"
            :busy="busy"
            @inspect="inspectVersion"
          />
        </div>
        <div class="studio-subblock">
          <span class="studio-subblock-title">下游引用关系（{{ detail?.usages.length ?? 0 }}）</span>
          <UsageTable
            :usages="detail?.usages ?? []"
            owner-label="引用方 Agent"
            :owner-path="usage => `/management/agents/${usage.ownerId}`"
            description="展示最新配置或 Master 生效发布引用该工具的 Agent。"
          />
        </div>
      </div>
    </SteppedEditorLayout>

    <ManagementDialog
      v-if="showCurlDialog"
      title="从 cURL 快速解析导入 HTTP 工具"
      wide
      :busy="false"
      :dirty="false"
      @close="showCurlDialog = false"
    >
      <p class="management-help">
        粘贴业务接口的 <code>curl</code> 命令（支持 <code>-X</code>、<code>-H</code>、<code>-d</code> / <code>--json</code>），系统将自动提取请求方法、URL、请求头，并将 JSON 请求体字段转为参数契约。
      </p>
      <ElAlert v-if="curlError" type="error" :title="curlError" show-icon :closable="false" />
      <ElInput
        v-model="curlInput"
        type="textarea"
        :rows="6"
        placeholder="curl -X POST 'https://api.example.com/orders/query' -H 'Content-Type: application/json' -d '{&quot;order_id&quot;: &quot;ORD1001&quot;}'"
      />
      <template #actions>
        <ElButton type="primary" native-type="button" class="management-primary" @click="applyCurlImport">
          解析并填入表单
        </ElButton>
      </template>
    </ManagementDialog>

    <ElDrawer
      :model-value="Boolean(inspectedVersion)"
      :title="inspectedVersion ? `配置版本详情 · ${versionLabel(inspectedVersion.versionNo)}` : ''"
      size="min(740px, 92vw)"
      append-to-body
      destroy-on-close
      @close="inspectedVersion = null"
    >
      <ConfigReadonlyCards
        v-if="inspectedVersion"
        :tool="{ summary: detail?.tool, version: inspectedVersion }"
      />
    </ElDrawer>
  </ManagementShell>
</template>
