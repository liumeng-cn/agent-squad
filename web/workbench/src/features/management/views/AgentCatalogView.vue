<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { errorMessage } from '../../../shared/http/http'
import { confirmManagement } from '../components/confirmManagement'
import { useManagementStore } from '../stores/management'
import * as api from '../api/agentCatalogApi'
import type { McpEntry, McpOption, McpTransport, McpVersion, ToolEntry, ToolSource, ToolVersion } from '../api/agentCatalogApi'
import ManagementDialog from '../components/ManagementDialog.vue'
import CatalogPagination from '../components/CatalogPagination.vue'
import { useCatalogPage } from '../composables/useCatalogPage'
import '../management.css'
import '../agent-catalog.css'

const route = useRoute()
const access = useManagementStore()
const kind = computed(() => route.meta.managementSection === 'tools' ? 'tools' : 'mcp-servers')
const isTool = computed(() => kind.value === 'tools')
const title = computed(() => isTool.value ? '工具目录' : 'MCP 服务')
const canQuery = computed(() => access.can(isTool.value ? 'management:tool:query' : 'management:mcp:query'))
const canEdit = computed(() => access.can(isTool.value ? 'management:tool:edit' : 'management:mcp:edit'))
const canPublish = computed(() => access.can(isTool.value ? 'management:tool:publish' : 'management:mcp:publish'))

const entries = ref<(ToolEntry | McpEntry)[]>([])
const versions = ref<(ToolVersion | McpVersion)[]>([])
const mcpOptions = ref<McpOption[]>([])
const selected = ref<ToolEntry | McpEntry>()
const current = ref<ToolVersion | McpVersion>()
const creating = ref(false)
const entryForm = ref({ code: '', name: '', source: 'JAVA' as ToolSource })
const versionForm = ref(newForm())
const editingVersion = ref(false)
const busy = ref(false)
const error = ref('')

function newForm() {
  return { exposedName: '', implementationKey: '', mcpServerVersionId: '', remoteToolName: '',
    transportType: 'STREAMABLE_HTTP' as McpTransport, endpoint: '', credentialRef: '' }
}
const toolSource = computed(() => selected.value && 'toolType' in selected.value ? selected.value.toolType : null)
const sourceNames: Record<ToolSource, string> = { NATIVE: '原生', JAVA: 'Java', MCP: 'MCP' }
const entryId = (item: ToolEntry | McpEntry) => 'toolId' in item ? item.toolId : item.mcpServerId
const entryName = (item: ToolEntry | McpEntry) => 'toolName' in item ? item.toolName : item.serverName
const entryCode = (item: ToolEntry | McpEntry) => 'toolCode' in item ? item.toolCode : item.serverCode
const versionId = (item: ToolVersion | McpVersion) => 'toolVersionId' in item ? item.toolVersionId : item.mcpServerVersionId
const { criteria, rows, total, page, size, pageCount, search, reset } = useCatalogPage(entries, item => ({
  name: entryName(item), code: entryCode(item), type: 'toolType' in item ? item.toolType : undefined, status: item.status,
}))
const originalForm = ref('')
const entryDirty = computed(() => Boolean(entryForm.value.code || entryForm.value.name || entryForm.value.source !== 'JAVA'))
const versionDirty = computed(() => canEdit.value && current.value?.status !== 'PUBLISHED' && JSON.stringify(versionForm.value) !== originalForm.value)


async function run(action: () => Promise<void>) {
  busy.value = true
  error.value = ''
  try { await action() }
  catch (reason) { error.value = errorMessage(reason) }
  finally { busy.value = false }
}
async function load() {
  if (isTool.value) entries.value = await api.listTools()
  else entries.value = await api.listMcpServers()
}
watch(kind, () => {
  reset()
  entries.value = []
  selected.value = undefined
  versions.value = []
  creating.value = false
  editingVersion.value = false
  if (canQuery.value) void run(load)
}, { immediate: true })

async function select(item: ToolEntry | McpEntry) {
  await run(async () => {
    versions.value = 'toolId' in item ? await api.listToolVersions(item.toolId) : await api.listMcpVersions(item.mcpServerId)
    selected.value = item
    editingVersion.value = false
    if ('toolType' in item && item.toolType === 'MCP') mcpOptions.value = await api.listMcpOptions()
  })
}
async function create() {
  await run(async () => {
    const { code, name, source } = entryForm.value
    if (isTool.value) await api.createTool({ toolCode: code, toolName: name, toolType: source })
    else await api.createMcp({ serverCode: code, serverName: name })
    await load()
    creating.value = false
    entryForm.value = { code: '', name: '', source: 'JAVA' }
  })
}
function openVersion(item?: ToolVersion | McpVersion) {
  current.value = item
  const form = newForm()
  if (item && 'toolVersionId' in item) {
    form.exposedName = item.exposedName
    form.implementationKey = item.implementationKey ?? ''
    form.mcpServerVersionId = item.mcpServerVersionId ?? ''
    form.remoteToolName = item.remoteToolName ?? ''
  } else if (item) {
    form.transportType = item.transportType
    form.endpoint = item.endpoint
    form.credentialRef = item.credentialRef ?? ''
  }
  versionForm.value = form
  originalForm.value = JSON.stringify(form)
  editingVersion.value = true
}
function save() {
  const item = selected.value
  if (!item) return
  const form = versionForm.value
  const revision = current.value?.revision
  const id = current.value ? versionId(current.value) : undefined
  void run(async () => {
    if ('toolId' in item) {
      const implementation = item.toolType === 'MCP'
        ? { mcpServerVersionId: form.mcpServerVersionId, remoteToolName: form.remoteToolName }
        : { implementationKey: form.implementationKey }
      openVersion(await api.saveVersion<ToolVersion>('tools', item.toolId, {
        revision, exposedName: form.exposedName, ...implementation,
      }, id))
      versions.value = await api.listToolVersions(item.toolId)
    } else {
      openVersion(await api.saveVersion<McpVersion>('mcp-servers', item.mcpServerId, {
        revision, transportType: form.transportType, endpoint: form.endpoint, credentialRef: form.credentialRef || null,
      }, id))
      versions.value = await api.listMcpVersions(item.mcpServerId)
    }
  })
}
async function publish() {
  const item = selected.value
  const target = current.value
  if (!item || !target || !await confirmManagement(`发布版本 ${target.versionNo}？发布后该版本将冻结。`)) return
  await run(async () => {
    openVersion(await api.publishVersion(kind.value, entryId(item), versionId(target), target.revision))
    versions.value = 'toolId' in item ? await api.listToolVersions(item.toolId) : await api.listMcpVersions(item.mcpServerId)
  })
}
</script>

<template>
  <div class="management-platform">
    <header class="management-top"><h1>Agent Squad 管理平台</h1><RouterLink to="/chat">返回聊天</RouterLink></header>
    <div class="management-layout">
      <aside class="management-sidebar">
        <nav aria-label="管理菜单">
          <RouterLink v-for="menu in access.pages()" :key="menu.menuId" :to="menu.routePath || '/management'"
                      :aria-current="menu.routePath === route.path ? 'page' : undefined">{{ menu.name }}</RouterLink>
        </nav>
      </aside>
      <main class="management-main catalog-main">
        <div class="catalog-heading">
          <h2>{{ title }}</h2>
          <button v-if="canEdit" class="management-primary" :disabled="busy" @click="creating = true">新增{{ isTool ? '工具' : '连接' }}</button>
        </div>
        <p v-if="error && !creating && !editingVersion" class="management-error" role="alert">{{ error }}</p>
        <p v-if="!canQuery" class="management-panel">当前角色缺少查询权限。</p>
        <template v-else>
          <form class="management-panel management-search catalog-filter" @submit.prevent="search">
            <label>关键字<input v-model="criteria.keyword" placeholder="搜索名称或编码" maxlength="120" /></label>
            <label v-if="isTool">来源<select v-model="criteria.type"><option value="">全部</option>
              <option v-for="(name, source) in sourceNames" :key="source" :value="source">{{ name }}</option>
            </select></label>
            <label>状态<select v-model="criteria.status"><option value="">全部</option><option value="ENABLED">启用</option><option value="DISABLED">停用</option></select></label>
            <button class="management-primary" :disabled="busy">查询</button>
            <button type="button" :disabled="busy" @click="reset">重置</button>
          </form>
          <section class="management-panel catalog-table-panel" :aria-busy="busy">
            <div class="management-table-wrap"><table class="management-table">
              <thead><tr><th>名称</th><th>编码</th><th v-if="isTool">来源</th><th>状态</th><th>操作</th></tr></thead>
              <tbody>
                <tr v-for="item in rows" :key="entryId(item)">
                  <td><strong>{{ entryName(item) }}</strong></td><td>{{ entryCode(item) }}</td>
                  <td v-if="'toolType' in item">{{ sourceNames[item.toolType] }}</td>
                  <td><span class="management-tag" :class="{ active: item.status === 'ENABLED' }">{{ item.status === 'ENABLED' ? '启用' : '停用' }}</span></td>
                  <td class="management-actions"><button :disabled="busy" @click="select(item)">管理版本</button></td>
                </tr>
                <tr v-if="!rows.length"><td :colspan="isTool ? 5 : 4" class="management-empty">{{ busy ? '正在加载…' : '没有符合条件的记录' }}</td></tr>
              </tbody>
            </table></div>
            <CatalogPagination v-model:page="page" v-model:size="size" :total="total" :page-count="pageCount" :busy="busy" />
          </section>
          <section v-if="selected" class="management-panel">
            <div class="catalog-section-title">
              <h3>{{ entryName(selected) }}的版本</h3>
              <button v-if="canEdit" :disabled="busy" @click="openVersion()">新增草稿</button>
            </div>
            <table class="management-table">
              <thead><tr><th>版本</th><th>状态</th><th>操作</th></tr></thead>
              <tbody><tr v-for="item in versions" :key="versionId(item)">
                <td>{{ item.versionNo }}</td><td>{{ item.status === 'DRAFT' ? '草稿' : '已发布' }}</td>
                <td class="management-actions"><button :disabled="busy" @click="openVersion(item)">{{ item.status === 'DRAFT' && canEdit ? '编辑草稿' : '查看版本' }}</button></td>
              </tr></tbody>
            </table>

          </section>
        </template>
      </main>
    </div>
    <ManagementDialog v-if="creating" :title="isTool ? '新增工具' : '新增 MCP 连接'"
                      :busy="busy" :dirty="entryDirty" @close="creating = false">
      <form id="catalog-create-form" class="catalog-form" @submit.prevent="create">
        <p v-if="error" class="management-error" role="alert">{{ error }}</p>
        <label>名称<input v-model.trim="entryForm.name" required maxlength="120" /></label>
        <label>编码<input v-model.trim="entryForm.code" required maxlength="80" pattern="[A-Za-z0-9_\-]+" /></label>
        <label v-if="isTool">来源<select v-model="entryForm.source">
          <option v-for="(name, source) in sourceNames" :key="source" :value="source">{{ name }}</option>
        </select></label>
      </form>
      <template #actions><button type="submit" form="catalog-create-form" class="management-primary" :disabled="busy">创建</button></template>
    </ManagementDialog>
    <ManagementDialog v-if="editingVersion && selected" :title="current ? `${entryName(selected)} · v${current.versionNo}` : '新增版本草稿'"
                      :busy="busy" :dirty="versionDirty" @close="editingVersion = false">
      <form id="catalog-version-form" class="catalog-form" @submit.prevent="save">
        <p v-if="error" class="management-error" role="alert">{{ error }}</p>
        <fieldset class="catalog-bindings" :disabled="busy || current?.status === 'PUBLISHED' || !canEdit">
          <template v-if="isTool">
            <label>模型工具名<input v-model.trim="versionForm.exposedName" required maxlength="64" pattern="[A-Za-z0-9_\-]+" /></label>
            <template v-if="toolSource === 'MCP'">
              <label>连接版本<select v-model="versionForm.mcpServerVersionId" required>
                <option value="" disabled>选择已发布的连接版本</option>
                <option v-if="versionForm.mcpServerVersionId && !mcpOptions.some(it => it.mcpServerVersionId === versionForm.mcpServerVersionId)"
                        :value="versionForm.mcpServerVersionId">原绑定已不可用</option>
                <option v-for="it in mcpOptions" :key="it.mcpServerVersionId" :value="it.mcpServerVersionId">{{ it.serverName }} · v{{ it.versionNo }}</option>
              </select></label>
              <label>远端工具名<input v-model.trim="versionForm.remoteToolName" required maxlength="255" /></label>
            </template>
            <label v-else>实现版本标识<input v-model.trim="versionForm.implementationKey" required maxlength="160" /></label>
          </template>
          <template v-else>
            <label>传输方式<select v-model="versionForm.transportType"><option value="STREAMABLE_HTTP">Streamable HTTP</option><option value="SSE">SSE</option></select></label>
            <label>连接地址<input v-model.trim="versionForm.endpoint" type="url" required maxlength="2048" /></label>
            <label>凭据引用<input v-model.trim="versionForm.credentialRef" maxlength="255" /></label>
          </template>
        </fieldset>
      </form>
      <template #actions>
        <button v-if="canEdit && current?.status !== 'PUBLISHED'" type="submit" form="catalog-version-form"
                class="management-primary" :disabled="busy">保存草稿</button>
        <button v-if="canPublish && current?.status === 'DRAFT'" :disabled="busy || versionDirty" @click="publish">发布</button>
      </template>
    </ManagementDialog>
  </div>
</template>
