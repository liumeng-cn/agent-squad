<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useManagementStore } from '../stores/management'
import { errorMessage } from '../../../shared/http/http'
import { confirmManagement } from '../components/confirmManagement'
import * as api from '../api/agentConfigApi'
import type { AgentConfig, AgentDraft, AgentType, AgentVersion } from '../api/agentConfigApi'
import { listToolOptions, type ToolVersion } from '../api/agentCatalogApi'
import ManagementDialog from '../components/ManagementDialog.vue'
import CatalogPagination from '../components/CatalogPagination.vue'
import AgentPromptEditor from '../components/AgentPromptEditor.vue'
import ToolVersionSelect from '../components/ToolVersionSelect.vue'
import { useCatalogPage } from '../composables/useCatalogPage'
import '../management.css'
import '../agent-catalog.css'

const access = useManagementStore()
const agents = ref<AgentConfig[]>([])
const versions = ref<AgentVersion[]>([])
const selected = ref<AgentConfig>()
const version = ref<AgentVersion>()
const draft = ref<AgentDraft>()
const toolOptions = ref<ToolVersion[]>([])
const toolVersionChoices = computed(() => toolOptions.value.map(tool => ({
  value: tool.toolVersionId,
  label: `${tool.exposedName} · v${tool.versionNo}`,
})))
const creating = ref(false)
const entry = ref({ agentCode: '', agentName: '', agentType: 'MASTER' as AgentType })
const busy = ref(false)
const error = ref('')
const childOptions = ref<{ id: string; label: string }[]>([])
const initialDraft = ref('')
const { criteria, rows, total, page, size, pageCount, search, reset } = useCatalogPage(agents, agent => ({
  name: agent.agentName, code: agent.agentCode, type: agent.agentType, status: agent.status,
}))
const versionReadOnly = computed(() => version.value?.status === 'PUBLISHED' || !access.can('management:agent:edit'))
const draftDirty = computed(() => !!draft.value && !versionReadOnly.value && JSON.stringify(draft.value) !== initialDraft.value)
const dirDirty = computed(() => Boolean(entry.value.agentName || entry.value.agentCode || entry.value.agentType !== 'MASTER'))

async function loadChildOptions(catalog: AgentConfig[]) {
  const children = catalog.filter(agent => agent.agentType === 'SUB_AGENT' && agent.status === 'ENABLED')
  const lists = await Promise.all(children.map(async child =>
    (await api.versionList(child.agentId)).filter(item => item.status === 'PUBLISHED').map(item => ({
      id: item.agentVersionId, label: `${child.agentName} · v${item.versionNo}`,
    })),
  ))
  childOptions.value = lists.flat()
}


async function run(operation: () => Promise<void>) {
  busy.value = true
  error.value = ''
  try { await operation() }
  catch (reason) { error.value = errorMessage(reason) }
  finally { busy.value = false }
}
onMounted(() => {
  if (access.can('management:agent:query')) void run(async () => {
    const [catalog, options] = await Promise.all([api.agentList(), listToolOptions()])
    agents.value = catalog
    toolOptions.value = options
    await loadChildOptions(catalog)
  })
})
async function select(agent: AgentConfig) {
  await run(async () => {
    const result = await api.versionList(agent.agentId)
    selected.value = agent
    versions.value = result
    version.value = undefined
    draft.value = undefined
  })
}
async function create() {
  await run(async () => {
    const agent = await api.createAgent(entry.value)
    agents.value = await api.agentList()
    selected.value = agent
    versions.value = []
    version.value = undefined
    draft.value = undefined
    creating.value = false
    entry.value = { agentCode: '', agentName: '', agentType: 'MASTER' }
  })
}
function fill(source?: AgentVersion) {
  version.value = source
  draft.value = {
    revision: source?.revision,
    systemPrompt: source?.systemPrompt ?? '',
    description: source?.description ?? '',
    maxIters: source?.maxIters ?? null,
    toolVersionIds: [...(source?.toolVersionIds ?? [])],
    subagents: source?.subagents.map(item => ({ ...item })) ?? [],
  }
  initialDraft.value = JSON.stringify(draft.value)
}
function view(item: AgentVersion) {
  const agent = selected.value
  if (agent) void run(async () => fill(await api.versionDetail(agent.agentId, item.agentVersionId)))
}
function save() {
  const agent = selected.value
  if (!agent || !draft.value) return
  const payload = { ...draft.value, maxIters: draft.value.maxIters || null }
  void run(async () => {
    fill(await api.saveDraft(agent.agentId, payload, version.value?.agentVersionId))
    versions.value = await api.versionList(agent.agentId)
  })
}
async function changeVersion(publish: boolean) {
  const agent = selected.value, target = version.value
  if (!agent || !target || !await confirmManagement(`${publish ? '发布' : '启用'}版本 ${target.versionNo}？`)) return
  await run(async () => {
    selected.value = publish ? await api.publish(agent, target) : await api.activate(agent, target)
    agents.value = await api.agentList()
    versions.value = await api.versionList(agent.agentId)
    await loadChildOptions(agents.value)
    fill(await api.versionDetail(agent.agentId, target.agentVersionId))
  })
}
</script>

<template>
  <div class="management-platform">
    <header class="management-top"><h1>Agent Squad 管理平台</h1><RouterLink to="/chat">返回聊天</RouterLink></header>
    <div class="management-layout">
      <aside class="management-sidebar"><nav aria-label="管理菜单">
        <RouterLink v-for="menu in access.pages()" :key="menu.menuId" :to="menu.routePath || '/management'"
                    :aria-current="menu.routePath === '/management/agents' ? 'page' : undefined">{{ menu.name }}</RouterLink>
      </nav></aside>
      <main class="management-main catalog-main">
        <div class="catalog-heading">
          <h2>Agent 配置</h2>
          <button v-if="access.can('management:agent:edit')" class="management-primary" :disabled="busy" @click="creating = true">新增 Agent</button>
        </div>
        <p v-if="error && !creating && !draft" class="management-error" role="alert">{{ error }}</p>
        <p v-if="!access.can('management:agent:query')" class="management-panel management-empty">当前角色缺少查询权限。</p>
        <template v-else>
          <form class="management-panel management-search catalog-filter" @submit.prevent="search">
            <label>关键字<input v-model="criteria.keyword" placeholder="搜索名称或编码" maxlength="120" /></label>
            <label>类型<select v-model="criteria.type"><option value="">全部</option><option value="MASTER">主 Agent</option><option value="SUB_AGENT">子 Agent</option></select></label>
            <label>状态<select v-model="criteria.status"><option value="">全部</option><option value="ENABLED">启用</option><option value="DISABLED">停用</option></select></label>
            <button type="submit" class="management-primary" :disabled="busy">查询</button>
            <button type="button" :disabled="busy" @click="reset">重置</button>
          </form>
          <section class="management-panel catalog-table-panel" :aria-busy="busy">
            <div class="management-table-wrap">
              <table class="management-table">
                <thead><tr><th>名称</th><th>编码</th><th>类型</th><th>状态</th><th>发布状态</th><th>操作</th></tr></thead>
                <tbody>
                  <tr v-for="agent in rows" :key="agent.agentId">
                    <td><strong>{{ agent.agentName }}</strong></td><td>{{ agent.agentCode }}</td>
                    <td>{{ agent.agentType === 'MASTER' ? '主 Agent' : '子 Agent' }}</td>
                    <td><span class="management-tag" :class="{ active: agent.status === 'ENABLED' }">{{ agent.status === 'ENABLED' ? '启用' : '停用' }}</span></td>
                    <td>{{ agent.currentVersionId ? '已发布' : '未发布' }}</td>
                    <td class="management-actions"><button :disabled="busy" @click="select(agent)">管理版本</button></td>
                  </tr>
                  <tr v-if="!rows.length"><td colspan="6" class="management-empty">{{ busy ? '正在加载…' : '没有符合条件的 Agent' }}</td></tr>
                </tbody>
              </table>
            </div>
            <CatalogPagination v-model:page="page" v-model:size="size" :total="total" :page-count="pageCount" :busy="busy" />
          </section>
          <section v-if="selected" class="management-panel">
            <div class="catalog-section-title">
              <h3>{{ selected.agentName }}的版本</h3>
              <button v-if="access.can('management:agent:edit')" :disabled="busy" @click="fill()">新增草稿</button>
            </div>
            <table class="management-table">
              <thead><tr><th>版本</th><th>状态</th><th>操作</th></tr></thead>
              <tbody>
                <tr v-for="item in versions" :key="item.agentVersionId">
                  <td>v{{ item.versionNo }}</td>
                  <td><span class="management-tag" :class="{ active: selected.currentVersionId === item.agentVersionId }">
                    {{ selected.currentVersionId === item.agentVersionId ? '当前生效' : item.status === 'DRAFT' ? '草稿' : '已发布' }}
                  </span></td>
                  <td class="management-actions"><button :disabled="busy" @click="view(item)">{{ item.status === 'DRAFT' && access.can('management:agent:edit') ? '编辑草稿' : '查看版本' }}</button></td>
                </tr>
                <tr v-if="!versions.length"><td colspan="3" class="management-empty">暂无版本</td></tr>
              </tbody>
            </table>
          </section>
        </template>
      </main>
    </div>
    <ManagementDialog v-if="creating" title="新增 Agent" :busy="busy" :dirty="dirDirty" @close="creating = false">
      <form id="agent-create-form" class="catalog-form" @submit.prevent="create">
        <p v-if="error" class="management-error" role="alert">{{ error }}</p>
        <label>名称<input v-model.trim="entry.agentName" required maxlength="120" /></label>
        <label>编码<input v-model.trim="entry.agentCode" required maxlength="80" pattern="[A-Za-z0-9_\-]+" /></label>
        <label>类型<select v-model="entry.agentType"><option value="MASTER">主 Agent</option><option value="SUB_AGENT">子 Agent</option></select></label>
      </form>
      <template #actions><button type="submit" form="agent-create-form" class="management-primary" :disabled="busy">创建</button></template>
    </ManagementDialog>
    <ManagementDialog v-if="draft && selected" :title="version ? `${selected.agentName} · v${version.versionNo}` : '新增 Agent 草稿'"
                      :busy="busy" :dirty="draftDirty" wide @close="draft = undefined">
      <form id="agent-draft-form" class="catalog-form" @submit.prevent="save">
        <p v-if="error" class="management-error" role="alert">{{ error }}</p>
        <AgentPromptEditor v-model="draft.systemPrompt" :read-only="versionReadOnly || busy" />
        <div class="management-form-row">
          <label>职责描述<input v-model="draft.description" maxlength="500" :disabled="versionReadOnly || busy" /></label>
          <label>最大步数<input v-model.number="draft.maxIters" type="number" min="1" :disabled="versionReadOnly || busy" /></label>
        </div>
        <ToolVersionSelect v-model="draft.toolVersionIds" :options="toolVersionChoices" :read-only="versionReadOnly || busy" />
        <fieldset v-if="selected.agentType === 'MASTER'" class="catalog-bindings" :disabled="versionReadOnly || busy">
          <legend>子 Agent 委派</legend>
          <div v-for="(child, index) in draft.subagents" :key="index" class="catalog-child-row">
            <label>子 Agent 版本<select v-model="child.subagentVersionId" required>
              <option value="" disabled>选择已发布版本</option>
              <option v-if="child.subagentVersionId && !childOptions.some(option => option.id === child.subagentVersionId)"
                      :value="child.subagentVersionId">原绑定已不可用 · {{ child.subagentVersionId }}</option>
              <option v-for="option in childOptions" :key="option.id" :value="option.id">{{ option.label }}</option>
            </select></label>
            <label>委派名称<input v-model.trim="child.delegationName" required maxlength="80" pattern="[A-Za-z0-9_\-]+" /></label>
            <label>委派描述<input v-model="child.delegationDescription" maxlength="500" /></label>
            <button type="button" @click="draft.subagents.splice(index, 1)">移除</button>
          </div>
          <div v-if="!versionReadOnly"><button type="button" :disabled="!childOptions.length"
              @click="draft.subagents.push({ subagentVersionId: '', delegationName: '' })">添加子 Agent</button></div>
          <p v-if="!childOptions.length" class="management-muted">发布子 Agent 后可在这里选择版本。</p>
        </fieldset>
      </form>
      <template #actions>
        <button v-if="!versionReadOnly" type="submit" form="agent-draft-form" class="management-primary" :disabled="busy">保存草稿</button>
        <template v-if="version && access.can('management:agent:publish')">
          <button v-if="version.status === 'DRAFT'" :disabled="busy || draftDirty" @click="changeVersion(true)">发布</button>
          <button v-else-if="selected.currentVersionId !== version.agentVersionId" :disabled="busy" @click="changeVersion(false)">切换到此版本</button>
        </template>
      </template>
    </ManagementDialog>
  </div>
</template>
