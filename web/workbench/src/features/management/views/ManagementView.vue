<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useManagementStore } from '../stores/management'
import * as api from '../api/managementApi'
import { ApiError, errorMessage } from '../../../shared/http/http'
import type { Member, MemberStatus, Permission, PermissionCode, Query, Role } from '../model/management'
import { confirmManagement } from '../components/confirmManagement'
import AuthorizationEditor from '../components/AuthorizationEditor.vue'
import RoleEditor from '../components/RoleEditor.vue'
import MemberEditor from '../components/MemberEditor.vue'
import ManagementDialog from '../components/ManagementDialog.vue'
import ManagementShell from '../components/ManagementShell.vue'
import ManagementPageHeader from '../components/ManagementPageHeader.vue'
import CatalogPagination from '../components/CatalogPagination.vue'

/**
 * 用户、角色与权限管理页：三个页面共用查询、分页和授权刷新流程，按路由 meta.managementSection 切换内容。
 */
type Section = 'users' | 'roles' | 'permissions'

interface SectionConfig {
  title: string
  description: string
  permission: PermissionCode
  placeholder: string
}

const sections: Record<Section, SectionConfig> = {
  users: {
    title: '用户管理',
    description: '复用已有登录账号，为其分配管理角色；只影响管理资格，不影响登录与聊天。',
    permission: 'management:user:query',
    placeholder: '用户名、昵称或手机号',
  },
  roles: {
    title: '角色管理',
    description: '角色组合菜单与功能权限。保存草稿后点击“应用”生效，禁用即时撤销角色授权。',
    permission: 'management:role:query',
    placeholder: '名称或编码',
  },
  permissions: {
    title: '权限管理',
    description: '权限目录由系统维护，供角色组合使用。',
    permission: 'management:permission:query',
    placeholder: '名称或编码',
  },
}

const statusNames: Record<MemberStatus, string> = { ACTIVE: '启用', DISABLED: '禁用', UNASSIGNED: '未授权' }
const statusTones: Record<MemberStatus, string> = { ACTIVE: 'is-success', DISABLED: 'is-muted', UNASSIGNED: 'is-warning' }

const route = useRoute()
const router = useRouter()
const access = useManagementStore()

const section = computed<Section>(() => {
  const value = String(route.meta.managementSection || 'roles')
  return value === 'users' || value === 'permissions' ? value : 'roles'
})
const config = computed(() => sections[section.value])
const canQuery = computed(() => access.can(config.value.permission))

const query = ref<Query>({ keyword: '', status: null, current: 1, size: 10 })
const roles = ref<Role[]>([])
const users = ref<Member[]>([])
const permissions = ref<Permission[]>([])
const total = ref(0)
const loading = ref(false)
const busy = ref(false)
const error = ref('')
const notice = ref('')

const authorizationRole = ref<Role | null>(null)
const roleEditor = ref(false)
const editingRole = ref<Role | null>(null)
const memberEditor = ref(false)
const editingMember = ref<Member | null>(null)
const permissionDetail = ref<Permission | null>(null)

const pageCount = computed(() => Math.max(1, Math.ceil(total.value / query.value.size)))
const columnCount = computed(() => ({ users: 6, roles: 7, permissions: 4 })[section.value])
const rowCount = computed(() => {
  if (section.value === 'users') {
    return users.value.length
  }
  return section.value === 'roles' ? roles.value.length : permissions.value.length
})
let sequence = 0

watch(section, () => {
  query.value = { keyword: '', status: null, current: 1, size: 10 }
  roleEditor.value = false
  memberEditor.value = false
  notice.value = ''
  void load()
}, { immediate: true })

/** 查询当前页面数据；并发请求只采用最后一次结果，避免切换页面后旧结果覆盖新结果。 */
async function load() {
  if (!canQuery.value) {
    roles.value = []
    users.value = []
    permissions.value = []
    total.value = 0
    return
  }

  const token = ++sequence
  loading.value = true
  error.value = ''
  roles.value = []
  users.value = []
  permissions.value = []
  try {
    const body = { ...query.value }
    if (section.value === 'users') {
      const result = await api.memberPage(body)
      if (token !== sequence) return
      users.value = result.records
      total.value = result.total
    } else if (section.value === 'roles') {
      const result = await api.rolePage(body)
      if (token !== sequence) return
      roles.value = result.records
      total.value = result.total
    } else {
      const result = await api.permissionPage(body)
      if (token !== sequence) return
      permissions.value = result.records
      total.value = result.total
    }
  } catch (reason) {
    if (token === sequence) {
      error.value = errorMessage(reason)
      await handleDenied(reason)
    }
  } finally {
    if (token === sequence) {
      loading.value = false
    }
  }
}

/** 接口拒绝访问时刷新授权；已失去全部管理资格则返回聊天页。 */
async function handleDenied(reason: unknown) {
  if (!(reason instanceof ApiError) || reason.code !== 403) {
    return
  }
  try {
    if (!await access.refresh()) {
      await router.replace('/chat')
    }
  } catch {
    await router.replace('/chat')
  }
}

/** 授权变化后刷新当前用户权限并重新查询；失去管理资格时离开管理平台。 */
async function refreshAccessAndReload() {
  if (!await access.refresh()) {
    await router.replace('/chat')
    return
  }
  await load()
}

function search() {
  query.value.current = 1
  void load()
}

function reset() {
  query.value.keyword = ''
  query.value.status = null
  search()
}

function add() {
  if (section.value === 'roles') {
    editingRole.value = null
    roleEditor.value = true
  } else {
    editingMember.value = null
    memberEditor.value = true
  }
}

async function editRole(role: Role) {
  busy.value = true
  error.value = ''
  try {
    editingRole.value = await api.roleDetail(role.roleId)
    roleEditor.value = true
  } catch (reason) {
    error.value = errorMessage(reason)
    await handleDenied(reason)
  } finally {
    busy.value = false
  }
}

function editMember(member: Member) {
  editingMember.value = member
  memberEditor.value = true
}

async function saved() {
  authorizationRole.value = null
  roleEditor.value = false
  memberEditor.value = false
  notice.value = section.value === 'roles' ? '角色草稿已保存，点击“应用”后权限生效。' : '用户角色已应用。'
  try {
    await refreshAccessAndReload()
  } catch (reason) {
    error.value = errorMessage(reason)
  }
}

function roleApplyState(role: Role): string {
  if (role.appliedVersion == null) {
    return '未应用'
  }
  return role.version === role.appliedVersion ? '已应用' : '有未应用修改'
}

function roleChangeMessage(role: Role, apply: boolean): string {
  if (apply) {
    return `应用“${role.name}”的草稿权限并启用角色？`
  }
  if (role.status === 'ACTIVE') {
    return `禁用“${role.name}”？关联用户将不再通过此角色获得管理权限。`
  }
  return `启用“${role.name}”？只恢复已应用的权限。`
}

async function changeRole(role: Role, apply = false) {
  const disabling = !apply && role.status === 'ACTIVE'
  if (!await confirmManagement(roleChangeMessage(role, apply), { danger: disabling })) {
    return
  }

  busy.value = true
  error.value = ''
  try {
    if (apply) {
      await api.applyRole(role)
    } else {
      await api.setRoleStatus(role, disabling ? 'DISABLED' : 'ACTIVE')
    }
    notice.value = apply ? '角色权限已应用。' : '角色状态已更新。'
    await refreshAccessAndReload()
  } catch (reason) {
    error.value = errorMessage(reason)
    await handleDenied(reason)
  } finally {
    busy.value = false
  }
}

async function changeMember(member: Member) {
  const disabling = member.status === 'ACTIVE'
  const message = `${disabling ? '禁用' : '启用'}该用户的管理资格？普通登录与聊天不受影响。`
  if (!await confirmManagement(message, { danger: disabling })) {
    return
  }

  busy.value = true
  error.value = ''
  try {
    await api.setMemberStatus(member, disabling ? 'DISABLED' : 'ACTIVE')
    notice.value = '管理资格状态已更新。'
    await refreshAccessAndReload()
  } catch (reason) {
    error.value = errorMessage(reason)
    await handleDenied(reason)
  } finally {
    busy.value = false
  }
}

function date(value: string) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—'
}
</script>

<template>
  <ManagementShell>
    <ManagementPageHeader :title="config.title" :description="config.description">
      <template #actions>
        <button
          v-if="section === 'roles' && access.can('management:role:create')"
          type="button"
          class="management-primary"
          :disabled="busy || loading"
          @click="add"
        >
          新增角色
        </button>
        <button
          v-if="section === 'users' && access.can('management:user:assign-role')"
          type="button"
          class="management-primary"
          :disabled="busy || loading"
          @click="add"
        >
          添加管理用户
        </button>
      </template>
    </ManagementPageHeader>

    <section v-if="!canQuery" class="management-card">
      <div class="management-empty">
        <p class="management-empty-title">暂无查询权限</p>
        <p class="management-empty-desc">当前角色仅有菜单访问权限，尚未授予查询功能。</p>
      </div>
    </section>

    <template v-else>
      <form class="management-card management-filter" @submit.prevent="search">
        <label class="management-filter-field is-wide">
          关键字
          <input v-model="query.keyword" maxlength="100" :placeholder="config.placeholder" />
        </label>
        <label v-if="section !== 'permissions'" class="management-filter-field">
          状态
          <select v-model="query.status">
            <option :value="null">全部</option>
            <option value="ACTIVE">启用</option>
            <option value="DISABLED">禁用</option>
            <option v-if="section === 'users'" value="UNASSIGNED">未授权</option>
          </select>
        </label>
        <div class="management-filter-actions">
          <button type="submit" class="management-primary" :disabled="loading || busy">查询</button>
          <button type="button" :disabled="loading || busy" @click="reset">重置</button>
        </div>
      </form>

      <section class="management-card management-table-card" :aria-busy="loading || busy">
        <p v-if="error" class="management-error" role="alert">
          {{ error }}
          <button type="button" class="management-link" :disabled="busy" @click="load">重新查询</button>
        </p>
        <p v-if="notice" class="management-notice" role="status">{{ notice }}</p>

        <div class="management-table-wrap">
          <table class="management-table">
            <thead v-if="section === 'roles'">
              <tr>
                <th>角色</th>
                <th>状态</th>
                <th>成员</th>
                <th>应用情况</th>
                <th>说明</th>
                <th>更新时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <thead v-else-if="section === 'users'">
              <tr>
                <th>用户</th>
                <th>手机号</th>
                <th>角色</th>
                <th>管理状态</th>
                <th>更新时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <thead v-else>
              <tr>
                <th>权限</th>
                <th>所属模块</th>
                <th>说明</th>
                <th>操作</th>
              </tr>
            </thead>

            <tbody v-if="section === 'roles'">
              <tr v-for="row in roles" :key="row.roleId">
                <td>
                  <div class="management-cell-main">
                    <span class="management-cell-title">
                      {{ row.name }}
                      <span v-if="row.builtIn" class="management-tag is-primary">内置</span>
                    </span>
                    <span class="management-cell-sub">{{ row.code }}</span>
                  </div>
                </td>
                <td>
                  <span class="management-tag is-dot" :class="statusTones[row.status]">{{ statusNames[row.status] }}</span>
                </td>
                <td>{{ row.memberCount }}</td>
                <td>
                  <span class="management-tag" :class="roleApplyState(row) === '已应用' ? 'is-success' : 'is-warning'">
                    {{ roleApplyState(row) }}
                  </span>
                </td>
                <td><div class="management-cell-ellipsis" :title="row.description">{{ row.description || '—' }}</div></td>
                <td class="management-cell-nowrap">{{ date(row.lastUpdateTime) }}</td>
                <td class="management-actions">
                  <button type="button" :disabled="busy" @click="editRole(row)">
                    {{ row.builtIn || !access.can('management:role:update') ? '查看' : '修改' }}
                  </button>
                  <button
                    v-if="access.can('management:role:update') || row.builtIn"
                    type="button"
                    :disabled="busy"
                    @click="authorizationRole = row"
                  >
                    {{ row.builtIn ? '查看授权' : '分配权限' }}
                  </button>
                  <template v-if="!row.builtIn">
                    <button
                      v-if="access.can('management:role:apply')"
                      type="button"
                      :disabled="busy || row.version === row.appliedVersion"
                      @click="changeRole(row, true)"
                    >
                      应用
                    </button>
                    <button
                      v-if="access.can(row.status === 'ACTIVE' ? 'management:role:disable' : 'management:role:enable')"
                      type="button"
                      :class="{ 'management-danger': row.status === 'ACTIVE' }"
                      :disabled="busy || (row.status === 'DISABLED' && row.appliedVersion == null)"
                      @click="changeRole(row)"
                    >
                      {{ row.status === 'ACTIVE' ? '禁用' : '启用' }}
                    </button>
                  </template>
                </td>
              </tr>
            </tbody>

            <tbody v-else-if="section === 'users'">
              <tr v-for="row in users" :key="row.userId">
                <td>
                  <div class="management-cell-main">
                    <span class="management-cell-title">{{ row.displayName || row.username }}</span>
                    <span class="management-cell-sub">{{ row.userId }}</span>
                  </div>
                </td>
                <td class="management-cell-nowrap">{{ row.phone }}</td>
                <td><div class="management-cell-ellipsis" :title="row.roleNames">{{ row.roleNames || '—' }}</div></td>
                <td>
                  <span class="management-tag is-dot" :class="statusTones[row.status]">{{ statusNames[row.status] }}</span>
                </td>
                <td class="management-cell-nowrap">{{ date(row.lastUpdateTime) }}</td>
                <td class="management-actions">
                  <button v-if="access.can('management:user:assign-role')" type="button" :disabled="busy" @click="editMember(row)">
                    分配角色
                  </button>
                  <button
                    v-if="access.can(row.status === 'ACTIVE' ? 'management:user:disable' : 'management:user:enable')"
                    type="button"
                    :class="{ 'management-danger': row.status === 'ACTIVE' }"
                    :disabled="busy || row.status === 'UNASSIGNED'"
                    @click="changeMember(row)"
                  >
                    {{ row.status === 'ACTIVE' ? '禁用' : '启用' }}
                  </button>
                </td>
              </tr>
            </tbody>

            <tbody v-else>
              <tr v-for="row in permissions" :key="row.code">
                <td>
                  <div class="management-cell-main">
                    <span class="management-cell-title">{{ row.name }}</span>
                    <span class="management-cell-sub">{{ row.code }}</span>
                  </div>
                </td>
                <td>{{ row.module }}</td>
                <td><div class="management-cell-ellipsis" :title="row.description">{{ row.description }}</div></td>
                <td class="management-actions">
                  <button type="button" @click="permissionDetail = row">查看</button>
                </td>
              </tr>
            </tbody>

            <tbody v-if="loading || (!error && !rowCount)">
              <tr>
                <td :colspan="columnCount">
                  <div v-if="loading" class="management-loading" role="status">正在查询…</div>
                  <div v-else class="management-empty is-compact">暂无符合条件的数据</div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <CatalogPagination
          v-model:page="query.current"
          v-model:size="query.size"
          :total="total"
          :page-count="pageCount"
          :busy="loading || busy"
          @change="load"
        />
      </section>
    </template>

    <AuthorizationEditor v-if="authorizationRole" :role="authorizationRole" @close="authorizationRole = null" @saved="saved" />
    <RoleEditor v-if="roleEditor" :role="editingRole" @close="roleEditor = false" @saved="saved" />
    <MemberEditor v-if="memberEditor" :member="editingMember" @close="memberEditor = false" @saved="saved" />
    <ManagementDialog v-if="permissionDetail" title="权限详情" :busy="false" :dirty="false" @close="permissionDetail = null">
      <dl class="management-permission-detail">
        <dt>权限名称</dt>
        <dd>{{ permissionDetail.name }}</dd>
        <dt>权限编码</dt>
        <dd><span class="management-code">{{ permissionDetail.code }}</span></dd>
        <dt>所属模块</dt>
        <dd>{{ permissionDetail.module }}</dd>
        <dt>说明</dt>
        <dd>{{ permissionDetail.description }}</dd>
      </dl>
    </ManagementDialog>
  </ManagementShell>
</template>
