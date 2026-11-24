<script setup lang="ts">
import {
  ElAlert,
  ElButton,
  ElCollapse,
  ElCollapseItem,
  ElInput,
  ElInputNumber,
  ElRadioButton,
  ElRadioGroup,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { errorMessage } from '../../../shared/http/http'
import { useManagementStore } from '../stores/management'
import {
  createAgent,
  getAgent,
  getAgentRelease,
  getAgentReleaseSubagent,
  getAgentReleaseTool,
  getAgentVersion,
  listAgentToolOptions,
  listSubagentOptions,
  saveAgent,
} from '../api/agentConfigApi'
import type {
  AgentDelegationDraft,
  AgentDetail,
  AgentReleaseDetail,
  AgentReleaseSubagentDetail,
  AgentReleaseSummary,
  AgentReleaseToolDetail,
  AgentType,
  AgentVersion,
  PromptSections,
  SubagentOption,
  ToolOption,
} from '../model/agentConfig'
import {
  agentTypeLabels,
  agentTypes,
  configStatusLabels,
  configStatusTones,
  DELEGATION_NAME_PATTERN,
  DESCRIPTION_LIMIT,
  emptyPromptSections,
  formatTime,
  MAX_ITERS_LIMIT,
  publishStateLabels,
  publishStateTones,
  readinessLabels,
  readinessTones,
  sanitizeIdentifier,
  uniqueIdentifier,
  versionLabel,
} from '../model/agentConfigLabels'
import { tagType } from '../model/elementTheme'
import { delegationCard, duplicatedNames, toolCard } from '../model/agentBindings'
import { useEditorState } from '../composables/useEditorState'
import { confirmManagement } from '../components/confirmManagement'
import ManagementShell from '../components/ManagementShell.vue'
import ManagementDialog from '../components/ManagementDialog.vue'
import SteppedEditorLayout, { type EditorStepItem } from '../components/agent-config/SteppedEditorLayout.vue'
import PromptSectionsEditor from '../components/agent-config/PromptSectionsEditor.vue'
import ToolBindingCard from '../components/agent-config/ToolBindingCard.vue'
import ToolPickerDialog from '../components/agent-config/ToolPickerDialog.vue'
import DelegationCard from '../components/agent-config/DelegationCard.vue'
import SubagentPickerDialog from '../components/agent-config/SubagentPickerDialog.vue'
import UsageTable from '../components/agent-config/UsageTable.vue'
import VersionHistoryTable from '../components/agent-config/VersionHistoryTable.vue'

/**
 * Agent 分步聚焦编辑页（方案 3）：1. 基础信息 → 2. 系统提示词 → 3. 工具与 Sub → 4. 版本与发布。
 */
const route = useRoute()
const router = useRouter()
const access = useManagementStore()

const canEdit = computed(() => access.can('management:agent:edit'))
const agentId = computed(() => {
  const raw = route.params.id ? String(route.params.id) : ''
  return raw && raw !== 'new' ? raw : null
})
const isCreate = computed(() => !agentId.value)

const step = ref('basic')
const detail = ref<AgentDetail | null>(null)
const toolOptions = ref<ToolOption[]>([])
const subagentOptions = ref<SubagentOption[]>([])
const loading = ref(false)
const busy = ref(false)
const error = ref('')
const notice = ref('')
const advancedOpen = ref<string[]>([])
const showToolPicker = ref(false)
const showSubPicker = ref(false)

const inspectedVersion = ref<AgentVersion | null>(null)
const inspectedRelease = ref<AgentReleaseDetail | null>(null)
const inspectedReleaseSub = ref<AgentReleaseSubagentDetail | null>(null)
const inspectedReleaseTool = ref<AgentReleaseToolDetail | null>(null)

const initialType: AgentType = route.query.type === 'SUB_AGENT' ? 'SUB_AGENT' : 'MASTER'
const form = ref({
  agentName: '',
  displayAlias: '',
  agentType: initialType as AgentType,
  description: '',
  promptSections: emptyPromptSections() as PromptSections,
  maxIters: null as number | null,
  toolIds: [] as string[],
  subagents: [] as AgentDelegationDraft[],
})

const { dirty, markClean, resetDirty } = useEditorState(() => JSON.stringify(form.value))

const title = computed(() => (isCreate.value ? '创建 Agent' : detail.value?.agent.agentName || 'Agent'))
const isMaster = computed(() => form.value.agentType === 'MASTER')

const steps = computed<EditorStepItem[]>(() => [
  { key: 'basic', no: 1, label: '基础信息' },
  { key: 'prompt', no: 2, label: '系统提示词' },
  {
    key: 'abilities',
    no: 3,
    label: isMaster.value ? '工具与 Sub' : '工具绑定',
    badge: form.value.toolIds.length + (isMaster.value ? form.value.subagents.length : 0) || '',
  },
  {
    key: 'history',
    no: 4,
    label: isMaster.value ? '版本与发布' : '引用与历史',
    badge: detail.value?.versions.length,
    hidden: isCreate.value,
  },
])

const stepTitle = computed(() => {
  if (step.value === 'basic') return '1. 基础信息'
  if (step.value === 'prompt') return '2. 结构化系统提示词'
  if (step.value === 'abilities') return isMaster.value ? '3. 工具绑定与 Sub 委派' : '3. 工具绑定'
  return isMaster.value ? '4. 发布快照与配置历史' : '4. 引用关系与配置历史'
})

const latestToolBindings = computed(() => detail.value?.latestVersion?.tools ?? [])
const latestDelegations = computed(() => detail.value?.latestVersion?.subagents ?? [])

const toolCards = computed(() =>
  form.value.toolIds.map(id => toolCard(id, toolOptions.value, latestToolBindings.value)),
)
const dupToolNames = computed(() =>
  duplicatedNames(toolCards.value.map(card => card.exposedName)),
)
const delegationCards = computed(() =>
  form.value.subagents.map(item => ({
    draft: item,
    info: delegationCard(item, subagentOptions.value, latestDelegations.value),
  })),
)

function delegationNameError(index: number): string {
  const name = form.value.subagents[index]?.delegationName.trim() ?? ''
  if (!name) return '请填写委派名称'
  if (!DELEGATION_NAME_PATTERN.test(name)) return '仅支持 1～80 位字母、数字、下划线或连字符'
  if (name === 'ask_user') return 'ask_user 是平台保留名称'
  if (toolCards.value.some(card => card.exposedName === name)) return '与已绑定工具的模型工具名冲突'
  const sameCount = form.value.subagents.filter(item => item.delegationName.trim() === name).length
  if (sameCount > 1) return '委派名称重复'
  return ''
}

function applyDetail(data: AgentDetail) {
  detail.value = data
  const latest = data.latestVersion
  form.value = {
    agentName: data.agent.agentName,
    displayAlias: data.agent.displayAlias || '',
    agentType: data.agent.agentType,
    description: latest?.description ?? '',
    promptSections: latest?.promptSections ? { ...emptyPromptSections(), ...latest.promptSections } : emptyPromptSections(),
    maxIters: latest?.maxIters ?? null,
    toolIds: latest?.tools ? latest.tools.map(t => t.toolId) : [],
    subagents: latest?.subagents
      ? latest.subagents.map(d => ({
          subagentId: d.subagentId,
          delegationName: d.delegationName,
          delegationDescription: d.delegationDescription || '',
          displayAlias: d.displayAlias || '',
        }))
      : [],
  }
  markClean()
}

async function switchAgentType(nextType: AgentType) {
  if (!isCreate.value || form.value.agentType === nextType) return
  if (nextType === 'SUB_AGENT' && form.value.subagents.length > 0) {
    const ok = await confirmManagement('切换为 Sub 将清空已配置的 Sub 委派，确定继续吗？', {
      danger: true,
      confirmText: '切换并清空',
    })
    if (!ok) return
    form.value.subagents = []
  }
  form.value.agentType = nextType
}

function addTools(ids: string[]) {
  const existing = new Set(form.value.toolIds)
  for (const id of ids) {
    if (!existing.has(id)) form.value.toolIds.push(id)
  }
  showToolPicker.value = false
}

function removeTool(toolId: string) {
  form.value.toolIds = form.value.toolIds.filter(id => id !== toolId)
}

function addSubagents(picked: SubagentOption[]) {
  const taken = [
    ...toolCards.value.map(c => c.exposedName),
    ...form.value.subagents.map(s => s.delegationName),
    'ask_user',
  ]
  for (const option of picked) {
    if (form.value.subagents.some(s => s.subagentId === option.agentId)) continue
    const baseName = sanitizeIdentifier(option.agentCode, 80)
    const delegationName = uniqueIdentifier(baseName, taken, 80)
    taken.push(delegationName)
    form.value.subagents.push({
      subagentId: option.agentId,
      delegationName,
      delegationDescription: option.description || '',
      displayAlias: option.displayAlias || '',
    })
  }
  showSubPicker.value = false
}

function updateDelegation(index: number, next: AgentDelegationDraft) {
  form.value.subagents = form.value.subagents.map((item, idx) => (idx === index ? next : item))
}

function removeDelegation(index: number) {
  form.value.subagents = form.value.subagents.filter((_, idx) => idx !== index)
}

async function save() {
  if (!form.value.agentName.trim()) {
    step.value = 'basic'
    error.value = '请先填写 Agent 名称'
    return
  }
  for (let idx = 0; idx < form.value.subagents.length; idx++) {
    const msg = delegationNameError(idx)
    if (msg) {
      step.value = 'abilities'
      error.value = `第 ${idx + 1} 项 Sub 委派：${msg}`
      return
    }
  }
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    const payload = {
      revision: detail.value?.agent.revision,
      agentName: form.value.agentName.trim(),
      displayAlias: form.value.displayAlias.trim(),
      agentType: form.value.agentType,
      description: form.value.description.trim(),
      promptSections: form.value.promptSections,
      maxIters: form.value.maxIters || null,
      toolIds: form.value.toolIds,
      subagents: isMaster.value ? form.value.subagents : [],
    }
    if (isCreate.value) {
      const created = await createAgent(payload)
      applyDetail(created)
      notice.value = `Agent 已创建（编码 ${created.agent.agentCode}，配置版本 v${created.agent.latestVersionNo}）。`
      await router.replace(`/management/agents/${created.agent.agentId}`)
    } else {
      const saved = await saveAgent(agentId.value!, payload)
      applyDetail(saved)
      notice.value = `已保存为新配置版本 v${saved.agent.latestVersionNo}（${readinessLabels[saved.agent.latestReadiness]}）。`
    }
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    busy.value = false
  }
}

async function inspectVersion(versionId: string) {
  if (!agentId.value) return
  busy.value = true
  try {
    inspectedVersion.value = await getAgentVersion(agentId.value, versionId)
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    busy.value = false
  }
}

async function inspectRelease(releaseId: string) {
  if (!agentId.value) return
  busy.value = true
  try {
    inspectedRelease.value = await getAgentRelease(agentId.value, releaseId)
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    busy.value = false
  }
}

async function openReleaseSub(subagentId: string) {
  if (!agentId.value || !inspectedRelease.value) return
  busy.value = true
  try {
    inspectedReleaseSub.value = await getAgentReleaseSubagent(agentId.value, inspectedRelease.value.releaseId, subagentId)
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    busy.value = false
  }
}

async function openReleaseTool(toolId: string) {
  if (!agentId.value || !inspectedRelease.value) return
  busy.value = true
  try {
    inspectedReleaseTool.value = await getAgentReleaseTool(agentId.value, inspectedRelease.value.releaseId, toolId)
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
    const [tools, subs] = await Promise.all([listAgentToolOptions(), listSubagentOptions()])
    toolOptions.value = tools
    subagentOptions.value = subs
    if (agentId.value) {
      applyDetail(await getAgent(agentId.value))
    } else {
      resetDirty()
    }
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    loading.value = false
  }
}

watch(agentId, () => {
  notice.value = ''
  error.value = ''
  void load()
})

onMounted(() => {
  void load()
})
</script>

<template>
  <ManagementShell>
    <SteppedEditorLayout
      v-model:step="step"
      back-path="/management/agents"
      back-label="Agent 列表"
      :title="title"
      :code="detail?.agent.agentCode"
      :subtitle="detail ? `最近更新：${formatTime(detail.agent.lastUpdateTime)}` : '编码格式：AGT-M-yyyyMMdd-001'"
      :steps="steps"
      :can-save="canEdit"
      :saving="busy || loading"
      :save-label="dirty ? '保存新版本' : '保存'"
      @save="save"
    >
      <template #status>
        <ElTag class="management-tag" :type="isMaster ? 'primary' : 'info'">{{ agentTypeLabels[form.agentType] }}</ElTag>
        <ElTag v-if="detail" class="management-tag" :class="configStatusTones[detail.agent.status]" :type="tagType(configStatusTones[detail.agent.status])">
          {{ configStatusLabels[detail.agent.status] }}
        </ElTag>
        <ElTag v-if="detail" class="management-tag" :class="readinessTones[detail.agent.latestReadiness]" :type="tagType(readinessTones[detail.agent.latestReadiness])">
          {{ versionLabel(detail.agent.latestVersionNo) }} · {{ readinessLabels[detail.agent.latestReadiness] }}
        </ElTag>
        <ElTag
          v-if="detail && isMaster && detail.agent.publishState"
          class="management-tag"
          :class="publishStateTones[detail.agent.publishState]"
          :type="tagType(publishStateTones[detail.agent.publishState])"
        >
          {{ publishStateLabels[detail.agent.publishState] }}
          <template v-if="detail.agent.currentReleaseNo != null"> #{{ detail.agent.currentReleaseNo }}</template>
        </ElTag>
      </template>

      <template #step-title>{{ stepTitle }}</template>
      <template #step-desc>
        <span v-if="isMaster">保存仅新增配置版本，不影响线上运行；发布请在 Agent 列表卡片执行。</span>
        <span v-else>Sub 聚焦单一职责，保存后供 Master 关联，不单独发布。</span>
      </template>

      <template #messages>
        <ElAlert v-if="error" type="error" :title="error" show-icon :closable="false" />
        <ElAlert v-if="notice" type="success" :title="notice" show-icon :closable="false" />
      </template>

      <!-- 步骤 1：基础信息 -->
      <div v-if="step === 'basic'" class="management-form-grid">
        <label class="management-field">
          <span class="management-field-label">Agent 名称 <span class="management-required" /></span>
          <ElInput v-model="form.agentName" :disabled="!canEdit" maxlength="120" placeholder="后台识别名称，如：智能客服总控" />
        </label>
        <label class="management-field">
          <span class="management-field-label">前端展示别称</span>
          <ElInput v-model="form.displayAlias" :disabled="!canEdit" maxlength="60" placeholder="聊天事件卡片展示，留空使用 Agent 名称" />
        </label>
        <label class="management-field">
          <span class="management-field-label">类型</span>
          <ElRadioGroup :model-value="form.agentType" :disabled="!isCreate || !canEdit" @update:model-value="switchAgentType($event as AgentType)">
            <ElRadioButton v-for="item in agentTypes" :key="item" :value="item">
              {{ agentTypeLabels[item] }}
            </ElRadioButton>
          </ElRadioGroup>
        </label>
        <label class="management-field">
          <span class="management-field-label">组件编码（自动生成）</span>
          <ElInput :model-value="detail?.agent.agentCode || '保存后自动生成（AGT-*-yyyyMMdd-001）'" disabled />
        </label>
        <label class="management-field is-full">
          <span class="management-field-label">
            职责描述
            <span v-if="!isMaster" class="management-required" />
          </span>
          <ElInput
            v-model="form.description"
            :disabled="!canEdit"
            type="textarea"
            :rows="3"
            :maxlength="DESCRIPTION_LIMIT"
            :placeholder="isMaster ? '简要说明该 Master 负责的业务场景（选填）' : '清晰说明该 Sub 的擅长领域，供 Master 决定何时委派（就绪必填）'"
          />
        </label>
      </div>

      <!-- 步骤 2：系统提示词 -->
      <div v-else-if="step === 'prompt'">
        <PromptSectionsEditor v-model="form.promptSections" :readonly="!canEdit" />
      </div>

      <!-- 步骤 3：工具与 Sub -->
      <div v-else-if="step === 'abilities'" class="studio-canvas-body">
        <div class="studio-subblock">
          <div class="studio-subblock-head">
            <span class="studio-subblock-title">工具绑定（{{ form.toolIds.length }}）</span>
            <ElButton v-if="canEdit" native-type="button" @click="showToolPicker = true">+ 添加工具</ElButton>
          </div>
          <div v-if="!toolCards.length" class="management-empty is-compact">暂未绑定工具</div>
          <div v-else class="config-binding-list">
            <ToolBindingCard
              v-for="card in toolCards"
              :key="card.toolId"
              :info="card"
              :duplicated="dupToolNames.includes(card.exposedName)"
              :readonly="!canEdit"
              @remove="removeTool(card.toolId)"
            />
          </div>
        </div>

        <div v-if="isMaster" class="studio-subblock">
          <div class="studio-subblock-head">
            <span class="studio-subblock-title">Sub 委派（{{ form.subagents.length }}）</span>
            <ElButton v-if="canEdit" native-type="button" @click="showSubPicker = true">+ 添加 Sub</ElButton>
          </div>
          <div v-if="!delegationCards.length" class="management-empty is-compact">暂未配置 Sub 委派</div>
          <div v-else class="config-binding-list">
            <DelegationCard
              v-for="(item, idx) in delegationCards"
              :key="item.draft.subagentId"
              :delegation="item.draft"
              :info="item.info"
              :name-error="delegationNameError(idx)"
              :readonly="!canEdit"
              @update="updateDelegation(idx, $event)"
              @remove="removeDelegation(idx)"
            />
          </div>
        </div>

        <ElCollapse v-model="advancedOpen">
          <ElCollapseItem title="高级设置（最大执行步数）" name="advanced">
            <label class="management-field" style="max-width: 280px">
              <span class="management-field-label">最大执行步数（1～{{ MAX_ITERS_LIMIT }}，留空使用平台默认）</span>
              <ElInputNumber v-model="form.maxIters" :disabled="!canEdit" :min="1" :max="MAX_ITERS_LIMIT" placeholder="使用平台默认" />
            </label>
          </ElCollapseItem>
        </ElCollapse>
      </div>

      <!-- 步骤 4：版本与发布 / 引用与历史 -->
      <div v-else class="studio-canvas-body">
        <div v-if="isMaster" class="studio-subblock">
          <span class="studio-subblock-title">发布定格历史（{{ detail?.releases.length ?? 0 }}）</span>
          <div class="management-table-wrap">
            <ElTable :data="detail?.releases ?? []" stripe style="width: 100%" empty-text="尚未发布过任何快照">
              <!-- @vue-generic {AgentReleaseSummary} -->
              <ElTableColumn label="发布快照" min-width="150">
                <template #default="{ row }: { row: AgentReleaseSummary }">
                  <span class="management-tags">
                    <span class="management-cell-title">Release #{{ row.releaseNo }}</span>
                    <ElTag v-if="row.current" class="management-tag is-success" type="success">当前生效</ElTag>
                    <ElTag v-else class="management-tag is-muted" type="info">历史只读</ElTag>
                  </span>
                </template>
              </ElTableColumn>
              <!-- @vue-generic {AgentReleaseSummary} -->
              <ElTableColumn label="定格配置" width="110">
                <template #default="{ row }: { row: AgentReleaseSummary }">{{ versionLabel(row.masterVersionNo) }}</template>
              </ElTableColumn>
              <!-- @vue-generic {AgentReleaseSummary} -->
              <ElTableColumn label="发布时间" min-width="165">
                <template #default="{ row }: { row: AgentReleaseSummary }">{{ formatTime(row.publishedAt) }}</template>
              </ElTableColumn>
              <!-- @vue-generic {AgentReleaseSummary} -->
              <ElTableColumn label="操作" width="110" fixed="right">
                <template #default="{ row }: { row: AgentReleaseSummary }">
                  <ElButton link type="primary" native-type="button" :disabled="busy" @click="inspectRelease(row.releaseId)">
                    查看快照
                  </ElButton>
                </template>
              </ElTableColumn>
            </ElTable>
          </div>
        </div>

        <div v-else class="studio-subblock">
          <span class="studio-subblock-title">下游引用关系（{{ detail?.usages.length ?? 0 }}）</span>
          <UsageTable
            :usages="detail?.usages ?? []"
            owner-label="引用方 Master"
            :owner-path="usage => `/management/agents/${usage.ownerId}`"
            description="展示最新配置或当前生效发布委派该 Sub 的 Master。"
          />
        </div>

        <div class="studio-subblock">
          <span class="studio-subblock-title">配置版本历史（{{ detail?.versions.length ?? 0 }}）</span>
          <VersionHistoryTable
            :versions="detail?.versions ?? []"
            :latest-version-id="detail?.agent.latestVersionId ?? null"
            :busy="busy"
            @inspect="inspectVersion"
          />
        </div>
      </div>
    </SteppedEditorLayout>

    <ToolPickerDialog
      v-if="showToolPicker"
      :options="toolOptions"
      :excluded-tool-ids="form.toolIds"
      @close="showToolPicker = false"
      @add="addTools"
    />

    <SubagentPickerDialog
      v-if="showSubPicker"
      :options="subagentOptions"
      :excluded-agent-ids="[...(agentId ? [agentId] : []), ...form.subagents.map(s => s.subagentId)]"
      @close="showSubPicker = false"
      @add="addSubagents"
    />

    <ManagementDialog
      v-if="inspectedVersion"
      :title="`只读查看配置版本 ${versionLabel(inspectedVersion.versionNo)}`"
      wide
      :busy="false"
      :dirty="false"
      @close="inspectedVersion = null"
    >
      <div class="management-form-grid">
        <div class="management-field"><span class="management-field-label">就绪状态</span><span>{{ readinessLabels[inspectedVersion.readiness] }}</span></div>
        <div class="management-field"><span class="management-field-label">保存时间</span><span>{{ formatTime(inspectedVersion.lastUpdateTime) }}</span></div>
        <div class="management-field is-full"><span class="management-field-label">职责描述</span><span>{{ inspectedVersion.description || '—' }}</span></div>
        <div class="management-field is-full">
          <span class="management-field-label">拼装后的完整系统提示词</span>
          <pre class="prompt-preview-content">{{ inspectedVersion.systemPrompt || '—' }}</pre>
        </div>
      </div>
    </ManagementDialog>

    <ManagementDialog
      v-if="inspectedRelease"
      :title="`Release #${inspectedRelease.releaseNo}（定格配置 ${versionLabel(inspectedRelease.masterVersionNo)}）`"
      wide
      :busy="false"
      :dirty="false"
      @close="inspectedRelease = null"
    >
      <p class="management-help">
        发布时间：{{ formatTime(inspectedRelease.publishedAt) }}；点击下方绑定的 Sub 或工具，将沿本次发布快照定格的版本逐层只读查看。
      </p>
      <div class="management-field is-full" style="margin-bottom: 12px">
        <span class="management-field-label">定格的系统提示词</span>
        <pre class="prompt-preview-content">{{ inspectedRelease.systemPrompt || '—' }}</pre>
      </div>
      <div class="management-field is-full" style="margin-bottom: 12px">
        <span class="management-field-label">定格的直接工具（{{ inspectedRelease.tools.length }}）</span>
        <div v-for="t in inspectedRelease.tools" :key="t.toolId" class="management-actions" style="margin-top: 6px">
          <span>{{ t.toolName }}（{{ t.displayAlias || t.toolName }} · {{ versionLabel(t.versionNo) }}）</span>
          <ElButton link type="primary" native-type="button" @click="openReleaseTool(t.toolId)">查看定格工具</ElButton>
        </div>
      </div>
      <div class="management-field is-full">
        <span class="management-field-label">定格的 Sub 委派（{{ inspectedRelease.subagents.length }}）</span>
        <div v-for="s in inspectedRelease.subagents" :key="s.subagentId" class="management-actions" style="margin-top: 6px">
          <span>{{ s.subagentName }}（委派名 {{ s.delegationName }} · {{ versionLabel(s.versionNo) }}）</span>
          <ElButton link type="primary" native-type="button" @click="openReleaseSub(s.subagentId)">查看定格 Sub</ElButton>
        </div>
      </div>
    </ManagementDialog>

    <ManagementDialog
      v-if="inspectedReleaseSub"
      :title="`定格 Sub：${inspectedReleaseSub.subagentName}（Release #${inspectedReleaseSub.releaseNo} · ${versionLabel(inspectedReleaseSub.versionNo)}）`"
      wide
      :busy="false"
      :dirty="false"
      @close="inspectedReleaseSub = null"
    >
      <div class="management-field is-full" style="margin-bottom: 10px">
        <span class="management-field-label">定格的系统提示词</span>
        <pre class="prompt-preview-content">{{ inspectedReleaseSub.systemPrompt || '—' }}</pre>
      </div>
      <div class="management-field is-full">
        <span class="management-field-label">该 Sub 定格的工具（{{ inspectedReleaseSub.tools.length }}）</span>
        <div v-for="t in inspectedReleaseSub.tools" :key="t.toolId" class="management-actions" style="margin-top: 6px">
          <span>{{ t.toolName }}（{{ versionLabel(t.versionNo) }}）</span>
          <ElButton link type="primary" native-type="button" @click="openReleaseTool(t.toolId)">查看定格工具</ElButton>
        </div>
      </div>
    </ManagementDialog>

    <ManagementDialog
      v-if="inspectedReleaseTool"
      :title="`定格工具：${inspectedReleaseTool.toolName}（Release #${inspectedReleaseTool.releaseNo} · ${versionLabel(inspectedReleaseTool.toolVersion.versionNo)}）`"
      wide
      :busy="false"
      :dirty="false"
      @close="inspectedReleaseTool = null"
    >
      <div class="management-form-grid">
        <div class="management-field"><span class="management-field-label">模型工具名</span><span class="management-code">{{ inspectedReleaseTool.toolVersion.exposedName }}</span></div>
        <div class="management-field"><span class="management-field-label">前端展示别称</span><span>{{ inspectedReleaseTool.displayAlias || '—' }}</span></div>
        <div class="management-field is-full"><span class="management-field-label">调用说明</span><span>{{ inspectedReleaseTool.toolVersion.description || '—' }}</span></div>
        <template v-if="inspectedReleaseTool.mcpVersion">
          <div class="management-field is-full">
            <span class="management-field-label">定格的 MCP 服务与连接地址</span>
            <span>{{ inspectedReleaseTool.mcpServerName }}（{{ versionLabel(inspectedReleaseTool.mcpVersion.versionNo) }}）— <span class="management-code">{{ inspectedReleaseTool.mcpVersion.endpoint }}</span></span>
          </div>
        </template>
      </div>
    </ManagementDialog>
  </ManagementShell>
</template>
