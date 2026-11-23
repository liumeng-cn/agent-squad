<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { errorMessage } from '../../../shared/http/http'
import { useManagementStore } from '../stores/management'
import * as api from '../api/agentConfigApi'
import type {
  AgentDelegationDraft,
  AgentDetail,
  AgentDraft,
  AgentType,
  AgentVersion,
  SubagentOption,
  ToolOption,
} from '../model/agentConfig'
import {
  CODE_PATTERN,
  DELEGATION_NAME_PATTERN,
  DESCRIPTION_LIMIT,
  MAX_ITERS_LIMIT,
  SYSTEM_PROMPT_LIMIT,
  agentTypeDescriptions,
  agentTypeLabels,
  agentTypes,
  configStatusLabels,
  configStatusTones,
  formatTime,
  sanitizeIdentifier,
  uniqueIdentifier,
  versionLabel,
  type ChoiceOption,
  type EditorMode,
  type EditorTab,
} from '../model/agentConfigLabels'
import { delegationCard, duplicatedNames, toolCard } from '../model/agentBindings'
import { blockingChecks, type CheckItem } from '../model/publishCheck'
import { useAsyncAction, useSnapshot, useUnsavedGuard } from '../composables/useEditorState'
import { confirmManagement } from '../components/confirmManagement'
import ManagementShell from '../components/ManagementShell.vue'
import ManagementCard from '../components/ManagementCard.vue'
import ConfigEditorLayout from '../components/agent-config/ConfigEditorLayout.vue'
import ChoiceCards from '../components/agent-config/ChoiceCards.vue'
import DelegationCard from '../components/agent-config/DelegationCard.vue'
import PublishChecklist from '../components/agent-config/PublishChecklist.vue'
import SubagentPickerDialog from '../components/agent-config/SubagentPickerDialog.vue'
import ToolBindingCard from '../components/agent-config/ToolBindingCard.vue'
import ToolPickerDialog from '../components/agent-config/ToolPickerDialog.vue'
import UsageTable from '../components/agent-config/UsageTable.vue'
import VersionHistoryTable from '../components/agent-config/VersionHistoryTable.vue'

/**
 * Agent 编辑页：维护职责描述、系统提示词、运行参数，以及工具绑定和子 Agent 委派。
 * 只能绑定已发布的工具版本、委派已发布的子 Agent 版本；发布即切换为当前生效版本，可切回历史版本。
 */
interface AgentForm {
  description: string
  systemPrompt: string
  maxIters: number | null
  toolVersionIds: string[]
  subagents: AgentDelegationDraft[]
}

const route = useRoute()
const router = useRouter()
const access = useManagementStore()

const canEdit = computed(() => access.can('management:agent:edit'))
const canPublish = computed(() => access.can('management:agent:publish'))

const routeId = computed(() => String(route.params.id ?? ''))
const creating = computed(() => routeId.value === 'new')

const detail = ref<AgentDetail | null>(null)
const version = ref<AgentVersion | null>(null)
const mode = ref<EditorMode>('create')
const tab = ref<EditorTab>('config')
const loading = ref(false)
const loadError = ref('')

const entry = ref({ agentCode: '', agentName: '', agentType: 'MASTER' as AgentType })
const form = ref<AgentForm>(emptyForm())
const profileEditing = ref(false)
const profileName = ref('')

const toolOptions = ref<ToolOption[]>([])
const subagentOptions = ref<SubagentOption[]>([])
const optionsError = ref('')
const toolPicker = ref(false)
const subagentPicker = ref(false)

const { busy, error, notice, run } = useAsyncAction()
const draftState = computed(() => ({ entry: entry.value, form: form.value }))
const { markClean, changed } = useSnapshot(draftState)

function emptyForm(): AgentForm {
  return { description: '', systemPrompt: '', maxIters: null, toolVersionIds: [], subagents: [] }
}

function formFromVersion(source: AgentVersion): AgentForm {
  return {
    description: source.description,
    systemPrompt: source.systemPrompt,
    maxIters: source.maxIters,
    toolVersionIds: source.tools.map(tool => tool.toolVersionId),
    subagents: source.subagents.map(item => ({
      subagentVersionId: item.subagentVersionId,
      delegationName: item.delegationName,
      delegationDescription: item.delegationDescription ?? '',
    })),
  }
}

const agent = computed(() => detail.value?.agent ?? null)
const agentType = computed<AgentType>(() => agent.value?.agentType ?? entry.value.agentType)
const isMaster = computed(() => agentType.value === 'MASTER')
const editable = computed(() => canEdit.value && mode.value !== 'published')
const dirty = computed(() => editable.value && changed())
const profileDirty = computed(() => profileEditing.value && profileName.value !== agent.value?.agentName)
useUnsavedGuard(computed(() => dirty.value || profileDirty.value))

const typeOptions = computed<ChoiceOption<AgentType>[]>(() => agentTypes.map(value => ({
  value,
  title: agentTypeLabels[value],
  description: agentTypeDescriptions[value],
})))

// 绑定展示：合并已保存的绑定详情与当前可选项
const savedTools = computed(() => version.value?.tools ?? [])
const savedDelegations = computed(() => version.value?.subagents ?? [])
const toolCards = computed(() => form.value.toolVersionIds.map(id => toolCard(id, toolOptions.value, savedTools.value)))
const delegationCards = computed(() => form.value.subagents.map(item => delegationCard(item, subagentOptions.value, savedDelegations.value)))
const duplicatedToolNames = computed(() => duplicatedNames(toolCards.value.map(card => card.exposedName)))
const duplicatedDelegations = computed(() => duplicatedNames(form.value.subagents.map(item => item.delegationName)))
const boundToolIds = computed(() => toolCards.value.map(card => card.toolId).filter(Boolean))
const excludedAgentIds = computed(() => {
  const ids = delegationCards.value.map(card => card.agentId).filter(Boolean)
  if (agent.value) {
    ids.push(agent.value.agentId)
  }
  return ids
})

const maxItersValid = computed(() => {
  const value = form.value.maxIters
  return value == null || (Number.isInteger(value) && value >= 1 && value <= MAX_ITERS_LIMIT)
})
const promptLength = computed(() => form.value.systemPrompt.length)

const checks = computed<CheckItem[]>(() => {
  const items: CheckItem[] = []
  if (creating.value) {
    items.push({ label: '填写 Agent 编码和名称', passed: CODE_PATTERN.test(entry.value.agentCode) && entry.value.agentName.trim() !== '' })
  }
  items.push(
    { label: '系统提示词已填写', passed: form.value.systemPrompt.trim() !== '', level: 'publish', hint: '描述角色、任务边界和回答要求' },
    { label: '系统提示词不超过 20000 字', passed: promptLength.value <= SYSTEM_PROMPT_LIMIT },
    { label: '最大步数在 1～200 之间或留空', passed: maxItersValid.value },
  )
  if (!isMaster.value) {
    items.push({ label: '职责描述已填写', passed: form.value.description.trim() !== '', level: 'publish', hint: '主 Agent 依据职责描述决定是否委派' })
  }
  items.push(
    { label: '绑定的工具均可用', passed: toolCards.value.every(card => card.usable), level: 'publish', hint: '切换到可用版本或移除不可用的工具' },
    { label: '模型工具名不重复', passed: duplicatedToolNames.value.length === 0, hint: `重复：${duplicatedToolNames.value.join('、')}` },
  )
  if (isMaster.value) {
    items.push(
      { label: '委派的子 Agent 均可用', passed: delegationCards.value.every(card => card.usable), level: 'publish' },
      {
        label: '委派名称合法且不重复',
        passed: duplicatedDelegations.value.length === 0
          && form.value.subagents.every(item => DELEGATION_NAME_PATTERN.test(item.delegationName)),
      },
    )
  }
  if (!form.value.toolVersionIds.length && !form.value.subagents.length) {
    items.push({ label: '已绑定工具或子 Agent', passed: false, level: 'advice', hint: '没有工具时 Agent 只能直接回答' })
  }
  return items
})

const canSave = computed(() => editable.value && !busy.value && blockingChecks(checks.value, 'save').length === 0)
const canPublishDraft = computed(() => (
  canPublish.value && mode.value === 'draft' && !dirty.value && !busy.value
  && blockingChecks(checks.value, 'publish').length === 0 && agent.value?.status === 'ENABLED'
))
const canActivate = computed(() => (
  canPublish.value && mode.value === 'published' && !busy.value
  && version.value !== null && version.value.agentVersionId !== agent.value?.currentVersionId
  && agent.value?.status === 'ENABLED'
))

const title = computed(() => (creating.value ? `新增${agentTypeLabels[entry.value.agentType]}` : agent.value?.agentName ?? 'Agent'))
const isCurrentVersion = computed(() => Boolean(version.value) && version.value?.agentVersionId === agent.value?.currentVersionId)
const modeTag = computed(() => {
  if (mode.value === 'create' || mode.value === 'newDraft') {
    return { text: '新草稿', tone: 'is-warning' }
  }
  const label = versionLabel(version.value?.versionNo)
  if (mode.value === 'draft') {
    return { text: `${label} 草稿`, tone: 'is-warning' }
  }
  return isCurrentVersion.value ? { text: `${label} 当前生效`, tone: 'is-primary' } : { text: `${label} 已发布`, tone: 'is-success' }
})

function toDraft(): AgentDraft {
  return {
    revision: mode.value === 'draft' ? version.value?.revision : undefined,
    description: form.value.description.trim(),
    systemPrompt: form.value.systemPrompt,
    maxIters: form.value.maxIters || null,
    toolVersionIds: [...form.value.toolVersionIds],
    subagents: isMaster.value
      ? form.value.subagents.map(item => ({
          subagentVersionId: item.subagentVersionId,
          delegationName: item.delegationName.trim(),
          delegationDescription: item.delegationDescription.trim(),
        }))
      : [],
  }
}

async function loadOptions() {
  optionsError.value = ''
  const [tools, subagents] = await Promise.allSettled([api.listAgentToolOptions(), api.listSubagentOptions()])
  toolOptions.value = tools.status === 'fulfilled' ? tools.value : []
  subagentOptions.value = subagents.status === 'fulfilled' ? subagents.value : []
  if (tools.status === 'rejected' || subagents.status === 'rejected') {
    optionsError.value = '可绑定的工具或子 Agent 加载失败，可刷新页面重试。'
  }
}

/** 读取 Agent 详情并选择默认版本：有草稿进入草稿，否则查看当前生效版本，都没有时准备新草稿。 */
async function load(preferredVersionId?: string) {
  loading.value = true
  loadError.value = ''
  try {
    detail.value = await api.getAgent(routeId.value)
    const target = preferredVersionId ?? detail.value.agent.draftVersionId ?? detail.value.agent.currentVersionId
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
  const loaded = await api.getAgentVersion(routeId.value, versionId)
  version.value = loaded
  form.value = formFromVersion(loaded)
  mode.value = loaded.status === 'DRAFT' ? 'draft' : 'published'
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

/** 基于已发布版本（或空白）准备新草稿；保留来源版本的绑定详情，便于展示已失效的绑定。 */
function startDraft(source: AgentVersion | null) {
  form.value = source ? formFromVersion(source) : emptyForm()
  mode.value = 'newDraft'
  if (!source) {
    version.value = null
  }
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
  const draftId = agent.value?.draftVersionId
  if (draftId) {
    await selectVersion(draftId)
  }
}

function addTools(toolVersionIds: string[]) {
  form.value.toolVersionIds = [...form.value.toolVersionIds, ...toolVersionIds]
  toolPicker.value = false
}

function changeToolVersion(index: number, toolVersionId: string) {
  form.value.toolVersionIds = form.value.toolVersionIds.map((id, position) => (position === index ? toolVersionId : id))
}

function removeTool(index: number) {
  form.value.toolVersionIds = form.value.toolVersionIds.filter((_, position) => position !== index)
}

/** 添加委派：委派名称默认取子 Agent 编码，冲突时追加序号；委派描述默认取子 Agent 的职责描述。 */
function addSubagents(options: SubagentOption[]) {
  const names = form.value.subagents.map(item => item.delegationName)
  const added: AgentDelegationDraft[] = []
  for (const option of options) {
    const name = uniqueIdentifier(sanitizeIdentifier(option.agentCode, 80), names, 80)
    names.push(name)
    added.push({ subagentVersionId: option.subagentVersionId, delegationName: name, delegationDescription: option.description })
  }
  form.value.subagents = [...form.value.subagents, ...added]
  subagentPicker.value = false
}

function updateDelegation(index: number, value: AgentDelegationDraft) {
  form.value.subagents = form.value.subagents.map((item, position) => (position === index ? value : item))
}

function removeDelegation(index: number) {
  form.value.subagents = form.value.subagents.filter((_, position) => position !== index)
}

async function save() {
  if (!canSave.value) {
    return
  }
  if (creating.value) {
    const created = await run(() => api.createAgent({
      agentCode: entry.value.agentCode.trim(),
      agentName: entry.value.agentName.trim(),
      agentType: entry.value.agentType,
      draft: toDraft(),
    }))
    if (created) {
      markClean()
      await router.replace(`/management/agents/${created.agent.agentId}`)
      notice.value = 'Agent 已创建，草稿 v1 已保存。检查无误后即可发布。'
    }
    return
  }

  const saved = await run(async () => {
    const result = mode.value === 'draft' && version.value
      ? await api.saveAgentVersion(routeId.value, version.value.agentVersionId, toDraft())
      : await api.createAgentVersion(routeId.value, toDraft())
    detail.value = await api.getAgent(routeId.value)
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
  const message = `发布 ${versionLabel(target.versionNo)} 并设为当前生效版本？发布后该版本冻结不可修改。`
  if (!await confirmManagement(message, { confirmText: '发布' })) {
    return
  }
  await run(async () => {
    await api.publishAgentVersion(routeId.value, target.agentVersionId, target.revision)
    await Promise.all([load(target.agentVersionId), loadOptions()])
  }, `${versionLabel(target.versionNo)} 已发布并生效。`)
}

async function activate() {
  const target = version.value
  const current = agent.value
  if (!target || !current || !canActivate.value) {
    return
  }
  const message = `将 ${versionLabel(target.versionNo)} 设为当前生效版本？新会话将使用该版本的配置。`
  if (!await confirmManagement(message, { confirmText: '设为生效' })) {
    return
  }
  await run(async () => {
    await api.activateAgentVersion(current.agentId, target.agentVersionId, current.revision)
    await Promise.all([load(target.agentVersionId), loadOptions()])
  }, `已切换到 ${versionLabel(target.versionNo)}。`)
}

function startProfileEdit() {
  profileName.value = agent.value?.agentName ?? ''
  profileEditing.value = true
}

async function saveProfile() {
  const current = agent.value
  if (!current || !profileName.value.trim()) {
    return
  }
  const updated = await run(() => api.updateAgent(current.agentId, { agentName: profileName.value.trim(), revision: current.revision }), '名称已更新。')
  if (updated && detail.value) {
    detail.value = { ...detail.value, agent: updated }
    profileEditing.value = false
  }
}

async function toggleStatus() {
  const current = agent.value
  if (!current) {
    return
  }
  const disabling = current.status === 'ENABLED'
  const impact = current.agentType === 'SUB_AGENT' ? '被启用主 Agent 的生效版本委派时无法停用。' : '停用后不再对用户提供服务。'
  const message = disabling ? `停用“${current.agentName}”？${impact}` : `启用“${current.agentName}”？`
  if (!await confirmManagement(message, { danger: disabling, confirmText: disabling ? '停用' : '启用' })) {
    return
  }
  const updated = await run(() => (disabling
    ? api.disableAgent(current.agentId, current.revision)
    : api.enableAgent(current.agentId, current.revision)), `Agent 已${disabling ? '停用' : '启用'}。`)
  if (updated && detail.value) {
    detail.value = { ...detail.value, agent: updated }
  }
}

function initialType(): AgentType {
  return route.query.type === 'SUB_AGENT' ? 'SUB_AGENT' : 'MASTER'
}

async function init() {
  detail.value = null
  version.value = null
  tab.value = 'config'
  profileEditing.value = false
  error.value = ''
  if (creating.value) {
    entry.value = { agentCode: '', agentName: '', agentType: initialType() }
    form.value = emptyForm()
    mode.value = 'create'
    markClean()
    return
  }
  await load()
}

/** 新建时切换为子 Agent 会清空委派，避免保存无效配置。 */
watch(() => entry.value.agentType, type => {
  if (type === 'SUB_AGENT' && form.value.subagents.length) {
    form.value.subagents = []
  }
})

watch(routeId, () => {
  void init()
})

onMounted(() => {
  void loadOptions()
  void init()
})
</script>

<template>
  <ManagementShell :current="creating ? '新增' : agent?.agentName">
    <div v-if="loading && !detail" class="management-card management-loading" role="status">正在加载…</div>
    <div v-else-if="loadError" class="management-card">
      <div class="management-empty">
        <p class="management-empty-title">加载失败</p>
        <p class="management-empty-desc">{{ loadError }}</p>
        <div class="management-empty-actions">
          <button type="button" @click="load()">重新加载</button>
          <RouterLink to="/management/agents">返回列表</RouterLink>
        </div>
      </div>
    </div>

    <ConfigEditorLayout
      v-else
      v-model:tab="tab"
      :back-path="`/management/agents?type=${agentType}`"
      back-label="Agent 配置"
      :title="title"
      :show-tabs="!creating"
      :version-count="detail?.versions.length ?? 0"
      :usage-count="detail?.usages.length ?? 0"
      :usage-label="isMaster ? '引用关系' : '被委派'"
    >
      <template #tags>
        <span class="management-tag" :class="{ 'is-primary': isMaster }">{{ agentTypeLabels[agentType] }}</span>
        <span v-if="agent" class="management-tag is-dot" :class="configStatusTones[agent.status]">
          {{ configStatusLabels[agent.status] }}
        </span>
        <span class="management-tag" :class="modeTag.tone">{{ modeTag.text }}</span>
      </template>

      <template #actions>
        <button v-if="agent && canPublish" type="button" :class="{ 'management-danger': agent.status === 'ENABLED' }" :disabled="busy" @click="toggleStatus">
          {{ agent.status === 'ENABLED' ? '停用' : '启用' }}
        </button>
        <template v-if="mode === 'published'">
          <button v-if="canActivate" type="button" :disabled="busy" @click="activate">设为生效版本</button>
          <template v-if="canEdit">
            <button v-if="agent?.draftVersionId" type="button" :disabled="busy" @click="editDraft">编辑草稿</button>
            <button v-else type="button" :disabled="busy" @click="startDraft(version)">基于此版本修改</button>
          </template>
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
          发布并生效
        </button>
      </template>

      <template v-if="agent" #meta>
        <span>编码 <span class="management-code">{{ agent.agentCode }}</span></span>
        <span>当前生效 {{ versionLabel(agent.currentVersionNo) }}</span>
        <span>更新于 {{ formatTime(agent.lastUpdateTime) }}</span>
      </template>

      <template #messages>
        <p v-if="error" class="management-error" role="alert">{{ error }}</p>
        <p v-if="notice" class="management-notice" role="status">{{ notice }}</p>
        <p v-if="optionsError" class="management-warning">{{ optionsError }}</p>
        <p v-if="mode === 'published'" class="management-info">
          正在查看已发布的 {{ versionLabel(version?.versionNo) }}{{ isCurrentVersion ? '（当前生效）' : '' }}，已发布版本不可修改。{{ canEdit ? '如需调整，请基于此版本创建新草稿。' : '' }}
        </p>
        <p v-if="agent?.status === 'DISABLED'" class="management-warning">Agent 已停用：草稿不能发布，也不能切换生效版本。</p>
      </template>

      <template #main>
        <ManagementCard v-if="creating" title="Agent 类型" description="类型创建后不可修改。建议先配置子 Agent，再由主 Agent 委派。">
          <ChoiceCards v-model="entry.agentType" :options="typeOptions" name="agent-type" label="Agent 类型" />
        </ManagementCard>

        <ManagementCard title="基本信息" description="编码与类型创建后不可修改；职责描述随版本保存。">
          <template v-if="agent && canEdit && !profileEditing" #actions>
            <button type="button" class="management-btn-sm" :disabled="busy" @click="startProfileEdit">编辑名称</button>
          </template>

          <div class="management-form-grid">
            <template v-if="creating">
              <label class="management-field">
                <span class="management-field-label management-required">Agent 编码</span>
                <input
                  v-model.trim="entry.agentCode"
                  maxlength="80"
                  placeholder="例如 order_assistant"
                  :aria-invalid="entry.agentCode !== '' && !CODE_PATTERN.test(entry.agentCode)"
                />
                <span class="management-field-hint">字母、数字、下划线或连字符，创建后不可修改；子 Agent 的编码也是默认委派名称。</span>
              </label>
              <label class="management-field">
                <span class="management-field-label management-required">Agent 名称</span>
                <input v-model="entry.agentName" maxlength="120" placeholder="例如 订单助手" />
              </label>
            </template>
            <template v-else-if="profileEditing">
              <label class="management-field">
                <span class="management-field-label management-required">Agent 名称</span>
                <input v-model="profileName" maxlength="120" />
              </label>
              <div class="management-field">
                <span class="management-field-label">操作</span>
                <div class="management-inline">
                  <button type="button" class="management-primary management-btn-sm" :disabled="busy || !profileName.trim()" @click="saveProfile">保存名称</button>
                  <button type="button" class="management-btn-sm" :disabled="busy" @click="profileEditing = false">取消</button>
                </div>
              </div>
            </template>
            <template v-else>
              <div class="management-field">
                <span class="management-field-label">Agent 名称</span>
                <div class="management-field-value">{{ agent?.agentName }}</div>
              </div>
              <div class="management-field">
                <span class="management-field-label">Agent 编码</span>
                <div class="management-field-value"><span class="management-code">{{ agent?.agentCode }}</span></div>
              </div>
            </template>

            <label class="management-field is-full">
              <span class="management-field-label" :class="{ 'management-required': !isMaster }">职责描述</span>
              <textarea
                v-model="form.description"
                rows="2"
                :maxlength="DESCRIPTION_LIMIT"
                :readonly="!editable"
                :placeholder="isMaster ? '说明该 Agent 面向的用户和业务范围' : '说明擅长处理的任务，例如：查询订单状态并解释物流异常'"
              />
              <span class="management-field-hint">
                {{ isMaster ? '用于管理端展示，便于区分不同主 Agent。' : '主 Agent 依据此描述决定是否委派，发布前必须填写。' }}
              </span>
            </label>
          </div>
        </ManagementCard>

        <ManagementCard title="系统提示词" description="定义 Agent 的角色、任务边界与回答要求；平台会在其后追加通用交互约定。">
          <div class="management-field">
            <textarea
              v-model="form.systemPrompt"
              class="config-prompt"
              :readonly="!editable"
              :maxlength="SYSTEM_PROMPT_LIMIT"
              aria-label="系统提示词"
              placeholder="例如：你是订单助手，负责解答用户关于订单、支付和物流的问题。回答时先确认订单号……"
            />
            <span class="config-counter" :class="{ 'is-over': promptLength > SYSTEM_PROMPT_LIMIT }">
              {{ promptLength }} / {{ SYSTEM_PROMPT_LIMIT }}
            </span>
          </div>
        </ManagementCard>

        <ManagementCard title="工具" description="绑定已发布的工具版本；工具发布新版本后不会自动升级，需要在这里手动切换。">
          <template v-if="editable" #actions>
            <button type="button" class="management-btn-sm" :disabled="busy" @click="toolPicker = true">添加工具</button>
          </template>
          <ul v-if="toolCards.length" class="config-bindings">
            <ToolBindingCard
              v-for="(card, index) in toolCards"
              :key="`${card.toolId}-${index}`"
              :info="card"
              :read-only="!editable"
              :duplicated="duplicatedToolNames.includes(card.exposedName)"
              @change="changeToolVersion(index, $event)"
              @remove="removeTool(index)"
            />
          </ul>
          <div v-else class="management-empty is-compact">
            <p>尚未绑定工具{{ editable ? '，点击“添加工具”从已发布的工具中选择' : '' }}。</p>
            <RouterLink v-if="editable && !toolOptions.length" to="/management/tools">还没有已发布的工具？前往工具页面</RouterLink>
          </div>
        </ManagementCard>

        <ManagementCard
          v-if="isMaster"
          title="子 Agent 委派"
          description="主 Agent 根据委派描述判断何时把任务交给子 Agent；只能委派启用子 Agent 的已发布版本。"
        >
          <template v-if="editable" #actions>
            <button type="button" class="management-btn-sm" :disabled="busy" @click="subagentPicker = true">添加子 Agent</button>
          </template>
          <ul v-if="delegationCards.length" class="config-bindings">
            <DelegationCard
              v-for="(card, index) in delegationCards"
              :key="`${card.agentId}-${index}`"
              :model-value="form.subagents[index]!"
              :info="card"
              :read-only="!editable"
              :duplicated="duplicatedDelegations.includes(form.subagents[index]!.delegationName)"
              @update:model-value="updateDelegation(index, $event)"
              @remove="removeDelegation(index)"
            />
          </ul>
          <div v-else class="management-empty is-compact">
            <p>尚未委派子 Agent{{ editable ? '，点击“添加子 Agent”选择' : '' }}。</p>
            <RouterLink v-if="editable && !subagentOptions.length" to="/management/agents/new?type=SUB_AGENT">
              还没有已发布的子 Agent？新增子 Agent
            </RouterLink>
          </div>
        </ManagementCard>

        <ManagementCard title="运行参数">
          <div class="management-form-grid">
            <label class="management-field">
              <span class="management-field-label">最大步数</span>
              <input
                v-model.number="form.maxIters"
                type="number"
                min="1"
                :max="MAX_ITERS_LIMIT"
                :readonly="!editable"
                placeholder="平台默认"
                :aria-invalid="!maxItersValid"
              />
              <span class="management-field-hint">单轮对话中推理与工具调用的最大步数，1～200，留空使用平台默认值。</span>
            </label>
          </div>
        </ManagementCard>
      </template>

      <template #aside>
        <ManagementCard title="版本信息">
          <dl class="config-meta">
            <dt>当前查看</dt>
            <dd>{{ modeTag.text }}</dd>
            <template v-if="version && mode !== 'newDraft'">
              <dt>修订号</dt>
              <dd>{{ version.revision }}</dd>
              <dt>发布时间</dt>
              <dd>{{ formatTime(version.publishedAt) }}</dd>
              <dt>更新时间</dt>
              <dd>{{ formatTime(version.lastUpdateTime) }}</dd>
            </template>
            <template v-if="agent">
              <dt>当前生效</dt>
              <dd>{{ versionLabel(agent.currentVersionNo) }}</dd>
            </template>
            <dt>工具</dt>
            <dd>{{ form.toolVersionIds.length }}</dd>
            <template v-if="isMaster">
              <dt>子 Agent</dt>
              <dd>{{ form.subagents.length }}</dd>
            </template>
          </dl>
        </ManagementCard>
        <ManagementCard v-if="editable" title="发布检查">
          <PublishChecklist :items="checks" />
          <p v-if="mode === 'draft' && dirty" class="config-note">有未保存的修改，保存草稿后才能发布。</p>
          <p v-else-if="mode !== 'draft'" class="config-note">保存草稿后可发布，发布即成为当前生效版本。</p>
        </ManagementCard>
      </template>

      <template #versions>
        <VersionHistoryTable
          :versions="detail?.versions ?? []"
          :selected-id="mode === 'newDraft' ? null : version?.agentVersionId ?? null"
          current-label="当前生效"
          :busy="busy"
          @select="selectVersion"
        />
      </template>

      <template #usages>
        <UsageTable
          :usages="detail?.usages ?? []"
          owner-label="主 Agent"
          :owner-path="usage => `/management/agents/${usage.ownerId}`"
          :description="isMaster
            ? '主 Agent 不会被其他 Agent 委派，这里通常为空。'
            : '列出委派该子 Agent 的主 Agent 版本。被启用主 Agent 的当前生效版本委派时为生效引用，此时子 Agent 不能停用。'"
        />
      </template>
    </ConfigEditorLayout>

    <ToolPickerDialog v-if="toolPicker" :options="toolOptions" :excluded-tool-ids="boundToolIds" @close="toolPicker = false" @add="addTools" />
    <SubagentPickerDialog
      v-if="subagentPicker"
      :options="subagentOptions"
      :excluded-agent-ids="excludedAgentIds"
      @close="subagentPicker = false"
      @add="addSubagents"
    />
  </ManagementShell>
</template>
