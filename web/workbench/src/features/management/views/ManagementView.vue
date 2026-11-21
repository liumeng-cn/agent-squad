<script setup lang="ts">
import { confirmManagement } from '../components/confirmManagement'
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useManagementStore } from '../stores/management'
import { useAuthStore } from '../../auth/stores/auth'
import * as api from '../api/managementApi'
import { ApiError, errorMessage } from '../../../shared/http/http'
import type { Member, Permission, PermissionCode, Query, Role } from '../model/management'
import AuthorizationEditor from '../components/AuthorizationEditor.vue'
import RoleEditor from '../components/RoleEditor.vue'
import MemberEditor from '../components/MemberEditor.vue'
import ManagementDialog from '../components/ManagementDialog.vue'
import '../management.css'
const route = useRoute(); const router = useRouter(); const access = useManagementStore(); const auth = useAuthStore()
const section = computed(() => String(route.meta.managementSection || 'roles'))
const menus: { key: string; label: string; permission: PermissionCode }[] = [
  { key: 'users', label: '用户管理', permission: 'management:user:query' },
  { key: 'roles', label: '角色管理', permission: 'management:role:query' },
  { key: 'permissions', label: '权限管理', permission: 'management:permission:query' },
]
const visibleMenus = computed(() => access.pages().map(menu => ({ key: menu.code, label: menu.name })))
const canQuery = computed(() => access.can(menus.find(item => item.key === section.value)!.permission))
const authorizationRole = ref<Role | null>(null)
const title = computed(() => menus.find(item => item.key === section.value)?.label || '管理平台')
const query = ref<Query>({ keyword: '', status: null, current: 1, size: 10 })
const roles = ref<Role[]>([]); const users = ref<Member[]>([]); const permissions = ref<Permission[]>([])
const total = ref(0); const loading = ref(false); const busy = ref(false); const error = ref(''); const notice = ref('')
const roleEditor = ref(false); const editingRole = ref<Role | null>(null)
const memberEditor = ref(false); const editingMember = ref<Member | null>(null)
const permissionDetail = ref<Permission | null>(null)
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / query.value.size)))
let sequence = 0
watch(section, () => { query.value = { keyword: '', status: null, current: 1, size: 10 }; roleEditor.value = false; memberEditor.value = false; notice.value = ''; void load() }, { immediate: true })
async function load() {
  if (!canQuery.value) { roles.value = []; users.value = []; permissions.value = []; total.value = 0; return }
  const token = ++sequence
  loading.value = true; error.value = ''; roles.value = []; users.value = []; permissions.value = []
  try {
    const body = { ...query.value }
    const result = section.value === 'users' ? await api.memberPage(body) : section.value === 'roles' ? await api.rolePage(body) : await api.permissionPage(body)
    if (token !== sequence) return
    total.value = result.total
    if (section.value === 'users') users.value = result.records as Member[]
    else if (section.value === 'roles') roles.value = result.records as Role[]
    else permissions.value = result.records as Permission[]
  } catch (reason) { if (token === sequence) { error.value = errorMessage(reason); await handleDenied(reason) } }
  finally { if (token === sequence) loading.value = false }
}
async function handleDenied(reason: unknown) {
  if (reason instanceof ApiError && reason.code === 403) {
    try { if (!await access.refresh()) await router.replace('/chat') }
    catch { await router.replace('/chat') }
  }
}
function search() { query.value.current = 1; void load() }
function reset() { query.value.keyword = ''; query.value.status = null; search() }
function page(delta: number) { query.value.current = Math.min(pageCount.value, Math.max(1, query.value.current + delta)); void load() }
function add() { if (section.value === 'roles') { editingRole.value = null; roleEditor.value = true } else { editingMember.value = null; memberEditor.value = true } }
async function editRole(role: Role) {
  busy.value = true; error.value = ''
  try { editingRole.value = await api.roleDetail(role.roleId); roleEditor.value = true }
  catch (reason) { error.value = errorMessage(reason); await handleDenied(reason) }
  finally { busy.value = false }
}
function editMember(member: Member) { editingMember.value = member; memberEditor.value = true }
async function saved() {
  authorizationRole.value = null; roleEditor.value = false; memberEditor.value = false
  notice.value = section.value === 'roles' ? '角色草稿已保存，点击“应用”后权限生效。' : '用户角色已应用。'
  try {
    if (!await access.refresh()) { await router.replace('/chat'); return }
    await load()
  } catch (reason) { error.value = errorMessage(reason) }
}
async function changeRole(role: Role, apply = false) {
  const target = role.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  const message = apply ? `应用“${role.name}”的草稿权限并启用角色？` : `${target === 'ACTIVE' ? '启用' : '禁用'}“${role.name}”？${target === 'DISABLED' ? '关联用户将不再通过此角色获得管理权限。' : '只恢复已应用的权限。'}`
  if (!await confirmManagement(message)) return
  busy.value = true; error.value = ''
  try {
    if (apply) await api.applyRole(role); else await api.setRoleStatus(role, target)
    notice.value = apply ? '角色权限已应用。' : '角色状态已更新。'
    if (!await access.refresh()) { await router.replace('/chat'); return }
    await load()
  } catch (reason) { error.value = errorMessage(reason); await handleDenied(reason) }
  finally { busy.value = false }
}
async function changeMember(member: Member) {
  const target = member.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  if (!await confirmManagement(`${target === 'ACTIVE' ? '启用' : '禁用'}该用户的管理资格？普通登录与聊天不受影响。`)) return
  busy.value = true; error.value = ''
  try {
    await api.setMemberStatus(member, target); notice.value = '管理资格状态已更新。'
    if (!await access.refresh()) { await router.replace('/chat'); return }
    await load()
  } catch (reason) { error.value = errorMessage(reason); await handleDenied(reason) }
  finally { busy.value = false }
}
function date(value: string) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—' }
const statusName = (value: string) => ({ ACTIVE: '启用', DISABLED: '禁用', UNASSIGNED: '未授权' }[value] || value)
</script>
<template>
  <div class="management-platform">
    <header class="management-top"><h1>Agent Squad 管理平台</h1><div><span>{{ auth.user?.effectiveDisplayName }}</span><RouterLink to="/chat">返回聊天</RouterLink></div></header>
    <div class="management-layout"><aside class="management-sidebar"><nav aria-label="管理菜单"><RouterLink v-for="menu in visibleMenus" :key="menu.key" :to="`/management/${menu.key}`" :aria-current="section === menu.key ? 'page' : undefined">{{ menu.label }}</RouterLink></nav></aside>
      <main class="management-main"><div class="management-breadcrumb">管理平台 / {{ title }}</div>
        <p v-if="!canQuery" class="management-panel management-empty">当前角色仅有菜单访问权限，尚未授予查询功能。</p>
        <form v-if="canQuery" class="management-panel management-search" @submit.prevent="search"><label>关键字<input v-model="query.keyword" maxlength="100" :placeholder="section === 'users' ? '用户名、昵称或手机号' : '名称或编码'" /></label>
          <label v-if="section !== 'permissions'">状态<select v-model="query.status"><option :value="null">全部</option><option value="ACTIVE">启用</option><option value="DISABLED">禁用</option><option v-if="section === 'users'" value="UNASSIGNED">未授权</option></select></label>
          <button class="management-primary" :disabled="loading || busy">查询</button><button type="button" :disabled="loading || busy" @click="reset">重置</button>
        </form>
        <section v-if="canQuery" class="management-panel" :aria-busy="loading || busy"><div class="management-toolbar">
          <button v-if="section === 'roles' && access.can('management:role:create')" class="management-primary" :disabled="busy || loading" @click="add">＋ 新增角色</button>
          <button v-if="section === 'users' && access.can('management:user:assign-role')" class="management-primary" :disabled="busy || loading" @click="add">＋ 添加管理用户</button>
          <span class="management-muted">{{ section === 'roles' ? '保存草稿后应用生效，禁用即时撤销角色授权' : section === 'users' ? '复用已有账号，只管理角色与管理资格' : '权限目录由系统维护，供角色组合使用' }}</span>
        </div>
        <p v-if="error" class="management-error" role="alert">{{ error }} <button :disabled="busy" @click="load">重新查询</button></p>
        <p v-if="notice" class="management-notice" role="status">{{ notice }}</p>
        <div class="management-table-wrap"><table v-if="section === 'roles'" class="management-table"><thead><tr><th>角色名称</th><th>编码</th><th>状态</th><th>成员</th><th>应用情况</th><th>更新时间</th><th>操作</th></tr></thead><tbody>
          <tr v-for="row in roles" :key="row.roleId"><td>{{ row.name }} <small v-if="row.builtIn">内置</small></td><td>{{ row.code }}</td><td><span class="management-tag" :class="row.status.toLowerCase()">{{ statusName(row.status) }}</span></td><td>{{ row.memberCount }}</td><td>{{ row.appliedVersion == null ? '未应用' : row.version === row.appliedVersion ? '已应用' : '有未应用修改' }}</td><td>{{ date(row.lastUpdateTime) }}</td><td class="management-actions">
            <button :disabled="busy" @click="editRole(row)">{{ row.builtIn || !access.can('management:role:update') ? '查看' : '修改' }}</button>
            <button v-if="access.can('management:role:update') || row.builtIn" :disabled="busy" @click="authorizationRole = row">{{ row.builtIn ? '查看授权' : '分配权限' }}</button>
            <template v-if="!row.builtIn"><button v-if="access.can('management:role:apply')" :disabled="busy || row.version === row.appliedVersion" @click="changeRole(row, true)">应用</button><button v-if="access.can(row.status === 'ACTIVE' ? 'management:role:disable' : 'management:role:enable')" :disabled="busy || (row.status === 'DISABLED' && row.appliedVersion == null)" :class="{ 'management-danger': row.status === 'ACTIVE' }" @click="changeRole(row)">{{ row.status === 'ACTIVE' ? '禁用' : '启用' }}</button></template>
          </td></tr></tbody></table>
          <table v-else-if="section === 'users'" class="management-table"><thead><tr><th>用户名称</th><th>用户编号</th><th>手机号</th><th>角色</th><th>管理状态</th><th>操作</th></tr></thead><tbody><tr v-for="row in users" :key="row.userId"><td>{{ row.displayName || row.username }}</td><td>{{ row.userId }}</td><td>{{ row.phone }}</td><td>{{ row.roleNames || '—' }}</td><td><span class="management-tag" :class="row.status.toLowerCase()">{{ statusName(row.status) }}</span></td><td class="management-actions"><template><button v-if="access.can('management:user:assign-role')" :disabled="busy" @click="editMember(row)">分配角色</button><button v-if="access.can(row.status === 'ACTIVE' ? 'management:user:disable' : 'management:user:enable')" :disabled="busy || row.status === 'UNASSIGNED'" :class="{ 'management-danger': row.status === 'ACTIVE' }" @click="changeMember(row)">{{ row.status === 'ACTIVE' ? '禁用' : '启用' }}</button></template></td></tr></tbody></table>
          <table v-else class="management-table"><thead><tr><th>权限名称</th><th>权限编码</th><th>所属模块</th><th>说明</th><th>操作</th></tr></thead><tbody><tr v-for="row in permissions" :key="row.code"><td>{{ row.name }}</td><td>{{ row.code }}</td><td>{{ row.module }}</td><td>{{ row.description }}</td><td class="management-actions"><button @click="permissionDetail = row">查看</button></td></tr></tbody></table>
        </div>
        <p v-if="loading" class="management-empty" role="status">正在查询…</p><p v-else-if="!error && !total" class="management-empty">暂无符合条件的数据</p>
        <div class="management-pagination"><span>共 {{ total }} 条</span><label class="management-sr-only" for="management-page-size">每页条数</label><select id="management-page-size" v-model.number="query.size" :disabled="loading || busy" @change="search"><option :value="10">10 条/页</option><option :value="20">20 条/页</option><option :value="50">50 条/页</option></select><button aria-label="上一页" :disabled="loading || busy || query.current <= 1" @click="page(-1)">‹</button><span>{{ query.current }} / {{ pageCount }}</span><button aria-label="下一页" :disabled="loading || busy || query.current >= pageCount" @click="page(1)">›</button></div>
        </section>
      </main>
    </div>
    <AuthorizationEditor v-if="authorizationRole" :role="authorizationRole" @close="authorizationRole = null" @saved="saved" />
    <RoleEditor v-if="roleEditor" :role="editingRole" @close="roleEditor = false" @saved="saved" />
    <MemberEditor v-if="memberEditor" :member="editingMember" @close="memberEditor = false" @saved="saved" />
    <ManagementDialog v-if="permissionDetail" title="权限详情" :busy="false" :dirty="false" @close="permissionDetail = null"><dl class="management-permission-detail"><dt>权限名称</dt><dd>{{ permissionDetail.name }}</dd><dt>权限编码</dt><dd>{{ permissionDetail.code }}</dd><dt>所属模块</dt><dd>{{ permissionDetail.module }}</dd><dt>说明</dt><dd>{{ permissionDetail.description }}</dd></dl></ManagementDialog>
  </div>
</template>
