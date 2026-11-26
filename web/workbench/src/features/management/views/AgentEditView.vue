<script setup lang="ts">
import {
  ElAlert,
  ElButton,
  ElCollapse,
  ElCollapseItem,
  ElDrawer,
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
  getAgentVersion,
  listAgentToolOptions,
  listSubagentOptions,
  saveAgent,
} from '../api/agentConfigApi'
import type {
  AgentDelegationDraft,
  AgentDetail,
  AgentReleaseSummary,
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
import SectionedEditorLayout, { type AsideMetaItem, type EditorSectionAnchor } from '../components/agent-config/SectionedEditorLayout.vue'
import ConfigReadonlyCards from '../components/agent-config/ConfigReadonlyCards.vue'
import ReleaseInspectorDrawer from '../components/agent-config/ReleaseInspectorDrawer.vue'
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

const readonlyView = ref(false)
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
const inspectingReleaseId = ref<string | null>(null)

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

const sections = computed<EditorSectionAnchor[]>(() => [
  { key: 'basic', no: '01', label: '基础信息' },
  { key: 'prompt', no: '02', label: '结构化系统提示词' },
  { key: 'abilities', no: '03', label: isMaster.value ? '工具绑定与 Sub 委派' : '工具绑定' },
  { key: 'history', no: '04', label: isMaster.value ? '发布快照与配置历史' : '引用关系与配置历史', hidden: isCreate.value },
])

const readinessChecks = computed(() => {
  const checks = [
    { label: 'Agent 名称', ok: Boolean(form.value.agentName.trim()) },
    { label: '角色与定位（Role）', ok: Boolean(form.value.promptSections.role.trim()) },
    { label: '核心目标（Goal）', ok: Boolean(form.value.promptSections.goal.trim()) },
  ]
  if (!isMaster.value) {
    checks.push({ label: '职责描述（供 Master 委派）', ok: Boolean(form.value.description.trim()) })
  }
  return checks
})
const readinessDone = computed(() => readinessChecks.value.filter(c => c.ok).length)
const missingItems = computed(() => readinessChecks.value.filter(c => !c.ok).map(c => c.label))

const isStandalone = computed(() => isMaster.value && !form.value.toolIds.length && !form.value.subagents.length)

const asideMeta = computed<AsideMetaItem[]>(() => {
  const items: AsideMetaItem[] = [
    { label: '组件编码', value: detail.value?.agent.agentCode || '保存后自动生成', code: Boolean(detail.value?.agent.agentCode) },
    { label: '当前版本', value: detail.value ? versionLabel(detail.value.agent.latestVersionNo) : 'v1（待创建）' },
  ]
  if (isMaster.value) {
    items.push({
      label: '运行模式',
      value: isStandalone.value ? '独立对话模式（无组件）' : `协同编排（${form.value.toolIds.length} 工具 · ${form.value.subagents.length} Sub）`,
    })
    items.push({
      label: '线上快照',
      value: detail.value?.agent.currentReleaseNo != null ? `Release #${detail.value.agent.currentReleaseNo}` : '尚未发布',
    })
  }
  return items
})

const agentTips = computed(() =>
  isMaster.value
    ? [
        'Master 不挂载任何工具与 Sub 也能独立运行，只需完成“角色与定位”和“核心目标”即可发布。',
        '保存只产生新配置版本，不会影响已发布的线上快照；发布请在列表页卡片执行。',
        '委派名称（delegation_name）不可与已绑定的模型工具名或 ask_user 重名。',
      ]
    : [
        'Sub 必须填写“职责描述”，Master 会据此判断何时委派该子智能体。',
        'Sub 保存即产生新版本，不可独立发布，需被 Master 关联后随 Master 一并定格。',
      ],
)

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
    error.value = '请先填写 Agent 名称'
    document.getElementById('sec-basic')?.scrollIntoView?.({ behavior: 'smooth', block: 'start' })
    return
  }
  for (let idx = 0; idx < form.value.subagents.length; idx++) {
    const msg = delegationNameError(idx)
    if (msg) {
      error.value = `第 ${idx + 1} 项 Sub 委派：${msg}`
      document.getElementById('sec-abilities')?.scrollIntoView?.({ behavior: 'smooth', block: 'start' })
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

function inspectRelease(releaseId: string) {
  inspectingReleaseId.value = releaseId
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
  <ManagementShell :current="title">
    <SectionedEditorLayout
      v-model:readonly-view="readonlyView"
      back-path="/management/agents"
      back-label="Agent 列表"
      :eyebrow="isMaster ? 'Agent 配置 · 主协调智能体（Master）' : 'Agent 配置 · 专职子智能体（Sub）'"
      :title="title"
      :code="detail?.agent.agentCode"
      :sections="sections"
      :readiness-done="readinessDone"
      :readiness-total="readinessChecks.length"
      :missing-items="missingItems"
      :meta-items="asideMeta"
      :tips="agentTips"
      :dirty="dirty"
      :can-save="canEdit"
      :can-toggle-readonly="!isCreate && Boolean(detail?.latestVersion)"
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

      <template #aside-extra>
        <ElButton
          v-if="isMaster && detail?.agent.currentReleaseId"
          native-type="button"
          style="width: 100%"
          @click="inspectRelease(detail.agent.currentReleaseId)"
        >
          查看生效快照 #{{ detail.agent.currentReleaseNo }}
        </ElButton>
      </template>

      <template #readonly>
        <ConfigReadonlyCards
          v-if="detail?.latestVersion"
          :agent="{ summary: detail.agent, version: detail.latestVersion }"
        />
      </template>

      <template #messages>
        <ElAlert v-if="error" type="error" :title="error" show-icon :closable="false" />
        <ElAlert v-if="notice" type="success" :title="notice" show-icon :closable="false" />
      </template>

      <!-- 01 基础信息 -->
      <section id="sec-basic" class="mp-sec-card">
        <div class="mp-sec-head">
          <div class="mp-sec-title-wrap">
            <span class="mp-sec-num">01</span>
            <h2 class="mp-sec-title">基础信息</h2>
          </div>
          <span class="mp-sec-sub">{{ isMaster ? '保存仅新增配置版本，发布在列表卡片执行' : 'Sub 聚焦单一职责，随 Master 一并定格' }}</span>
        </div>
        <div class="mp-sec-body">
          <div class="management-form-grid">
            <label class="management-field">
              <span class="management-field-label">Agent 名称 <span class="management-required" /></span>
              <ElInput v-model="form.agentName" :disabled="!canEdit" maxlength="120" placeholder="后台识别名称，如：智能客服总控" />
            </label>
            <label class="management-field">
              <span class="management-field-label">前端展示别称 <span class="mp-optional-tag">选填</span></span>
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
                <span v-else class="mp-optional-tag">选填</span>
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
        </div>
      </section>

      <!-- 02 系统提示词 -->
      <section id="sec-prompt" class="mp-sec-card">
        <div class="mp-sec-head">
          <div class="mp-sec-title-wrap">
            <span class="mp-sec-num">02</span>
            <h2 class="mp-sec-title">结构化系统提示词</h2>
          </div>
          <span class="mp-sec-sub">角色与核心目标必填，其余按需展开</span>
        </div>
        <div class="mp-sec-body">
          <PromptSectionsEditor v-model="form.promptSections" :readonly="!canEdit" />
        </div>
      </section>

      <!-- 03 工具与 Sub -->
      <section id="sec-abilities" class="mp-sec-card">
        <div class="mp-sec-head">
          <div class="mp-sec-title-wrap">
            <span class="mp-sec-num">03</span>
            <h2 class="mp-sec-title">{{ isMaster ? '工具绑定与 Sub 委派' : '工具绑定' }}</h2>
          </div>
          <span class="mp-sec-sub">{{ isMaster ? '均为选配，支持零组件独立对话' : '仅列出启用且就绪的工具' }}</span>
        </div>
        <div class="mp-sec-body">
          <div v-if="isStandalone" class="ro-standalone-banner">
            <strong>支持零组件独立对话运行</strong>
            <span>工具与子 Agent 均为可选项。不挂载任何组件时，Master 只需完成核心提示词即可保存就绪并直接发布运行。</span>
          </div>

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
      </section>

      <!-- 04 版本与发布 / 引用与历史 -->
      <section v-if="!isCreate" id="sec-history" class="mp-sec-card">
        <div class="mp-sec-head">
          <div class="mp-sec-title-wrap">
            <span class="mp-sec-num">04</span>
            <h2 class="mp-sec-title">{{ isMaster ? '发布快照与配置历史' : '引用关系与配置历史' }}</h2>
          </div>
          <span class="mp-sec-sub">{{ detail?.versions.length ?? 0 }} 个历史版本</span>
        </div>
        <div class="mp-sec-body">
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
      </section>
    </SectionedEditorLayout>

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

    <ElDrawer
      :model-value="Boolean(inspectedVersion)"
      :title="inspectedVersion ? `配置版本详情 · ${versionLabel(inspectedVersion.versionNo)}` : ''"
      size="min(780px, 92vw)"
      append-to-body
      destroy-on-close
      @close="inspectedVersion = null"
    >
      <ConfigReadonlyCards
        v-if="inspectedVersion"
        :agent="{ summary: detail?.agent, version: inspectedVersion, agentType: form.agentType }"
      />
    </ElDrawer>

    <ReleaseInspectorDrawer
      v-if="agentId && inspectingReleaseId"
      :agent-id="agentId"
      :release-id="inspectingReleaseId"
      @close="inspectingReleaseId = null"
    />
  </ManagementShell>
</template>
