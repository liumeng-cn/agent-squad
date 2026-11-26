<script setup lang="ts">
import {
  vLoading,
  ElAlert,
  ElButton,
  ElInput,
  ElTable,
  ElTableColumn,
} from 'element-plus'
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useManagementStore } from '../stores/management'
import * as api from '../api/managementApi'
import { ApiError, errorMessage } from '../../../shared/http/http'
import type {
  Member,
  MemberStatus,
  Permission,
  PermissionCode,
  Query,
  Role,
} from '../model/management'
import { confirmManagement } from '../components/confirmManagement'
import AuthorizationEditor from '../components/AuthorizationEditor.vue'
import RoleEditor from '../components/RoleEditor.vue'
import MemberEditor from '../components/MemberEditor.vue'
import ManagementDialog from '../components/ManagementDialog.vue'
import ManagementShell from '../components/ManagementShell.vue'
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
    description:
      '复用已有登录账号，为其分配管理角色；只影响管理资格，不影响登录与聊天。',
    permission: 'management:user:query',
    placeholder: '用户名、昵称或手机号',
  },
  roles: {
    title: '角色管理',
    description:
      '角色组合菜单与功能权限。保存草稿后点击“应用”生效，禁用即时撤销角色授权。',
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

const statusNames: Record<MemberStatus, string> = {
  ACTIVE: '启用',
  DISABLED: '禁用',
  UNASSIGNED: '未授权',
}

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

const pageCount = computed(() =>
  Math.max(1, Math.ceil(total.value / query.value.size)),
)
let sequence = 0

watch(
  section,
  () => {
    query.value = { keyword: '', status: null, current: 1, size: 10 }
    roleEditor.value = false
    memberEditor.value = false
    notice.value = ''
    void load()
  },
  { immediate: true },
)

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
    if (!(await access.refresh())) {
      await router.replace('/chat')
    }
  } catch {
    await router.replace('/chat')
  }
}

/** 授权变化后刷新当前用户权限并重新查询；失去管理资格时离开管理平台。 */
async function refreshAccessAndReload() {
  if (!(await access.refresh())) {
    await router.replace('/chat')
    return
  }
  await load()
}

function selectStatus(status: MemberStatus | null) {
  query.value.status = status
  search()
}

function search() {
  query.value.current = 1
  void load()
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
  notice.value =
    section.value === 'roles'
      ? '角色草稿已保存，点击“应用”后权限生效。'
      : '用户角色已应用。'
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
  if (
    !(await confirmManagement(roleChangeMessage(role, apply), {
      danger: disabling,
    }))
  ) {
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
  if (!(await confirmManagement(message, { danger: disabling }))) {
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
  return value
    ? new Date(value).toLocaleString('zh-CN', { hour12: false })
    : '—'
}
</script>

<template>
  <ManagementShell>


    <section v-if="!canQuery" class="management-card">
      <div class="management-empty">
        <p class="management-empty-title">暂无查询权限</p>
        <p class="management-empty-desc">
          当前角色仅有菜单访问权限，尚未授予查询功能。
        </p>
      </div>
    </section>

    <template v-else>
      <div class="studio-toolbar">
        <div class="studio-pills" role="group" aria-label="状态筛选">
          <button
            type="button"
            class="studio-pill"
            :class="{ 'is-active': query.status === null }"
            @click="selectStatus(null)"
          >
            全部 {{ total }}
          </button>
          <template v-if="section !== 'permissions'">
            <button
              type="button"
              class="studio-pill"
              :class="{ 'is-active': query.status === 'ACTIVE' }"
              @click="selectStatus('ACTIVE')"
            >
              启用
            </button>
            <button
              type="button"
              class="studio-pill"
              :class="{ 'is-active': query.status === 'DISABLED' }"
              @click="selectStatus('DISABLED')"
            >
              禁用
            </button>
            <button
              v-if="section === 'users'"
              type="button"
              class="studio-pill"
              :class="{ 'is-active': query.status === 'UNASSIGNED' }"
              @click="selectStatus('UNASSIGNED')"
            >
              未授权
            </button>
          </template>
        </div>

        <div class="studio-toolbar-right">
          <ElInput
            v-model="query.keyword"
            clearable
            maxlength="100"
            class="studio-search"
            :placeholder="`搜索${config.placeholder}`"
            @keyup.enter="search"
            @clear="search"
          />
          <ElButton
            native-type="button"
            :disabled="loading || busy"
            @click="search"
          >
            查询
          </ElButton>
          <ElButton
            type="primary"
            v-if="section === 'roles' && access.can('management:role:create')"
            native-type="button"
            class="management-primary"
            :disabled="busy || loading"
            @click="add"
          >
            + 新增角色
          </ElButton>
          <ElButton
            type="primary"
            v-if="section === 'users' && access.can('management:user:assign-role')"
            native-type="button"
            class="management-primary"
            :disabled="busy || loading"
            @click="add"
          >
            + 添加管理用户
          </ElButton>
        </div>
      </div>

      <ElAlert
        v-if="error"
        class="management-error"
        role="alert"
        type="error"
        :closable="false"
        show-icon
      >
        {{ error }}
        <ElButton
          link
          native-type="button"
          class="management-link"
          :disabled="busy"
          @click="load"
          >重新查询</ElButton
        >
      </ElAlert>
      <ElAlert
        v-if="notice"
        class="management-notice"
        role="status"
        type="success"
        :closable="false"
        show-icon
        >{{ notice }}</ElAlert
      >

      <section
        class="management-card management-table-card studio-table-card"
        :aria-busy="loading || busy"
      >
        <div class="management-table-wrap">
          <ElTable
            v-loading="loading || busy"
            v-if="section === 'roles'"
            :data="roles" :stripe="false" class="management-business-table"
            style="width: 100%"
            empty-text="暂无数据"
          >
<ElTableColumn type="index" label="序号" width="72" align="center" :index="(index: number) => (query.current - 1) * query.size + index + 1" />
            <!-- @vue-generic {Role} -->
            <ElTableColumn label="角色" min-width="180">
              <template #default="{ row }: { row: Role }"
                ><div>
                  <div class="management-cell-main">
                    <span class="management-cell-title">
                      {{ row.name }}
                      <span v-if="row.builtIn">内置</span>
                    </span>

                  </div>
                </div></template
              >
            </ElTableColumn>
<ElTableColumn prop="code" label="编码" min-width="200" show-overflow-tooltip />
            <!-- @vue-generic {Role} -->
            <ElTableColumn label="状态" min-width="100">
              <template #default="{ row }: { row: Role }"
                ><div>
                  <span >{{ statusNames[row.status] }}</span>
                </div></template
              >
            </ElTableColumn>
            <!-- @vue-generic {Role} -->
            <ElTableColumn label="成员" min-width="150">
              <template #default="{ row }: { row: Role }"
                ><div>{{ row.memberCount }}</div></template
              >
            </ElTableColumn>
            <!-- @vue-generic {Role} -->
            <ElTableColumn label="应用情况" min-width="150">
              <template #default="{ row }: { row: Role }"
                ><div>
                  <span >
                    {{ roleApplyState(row) }}
                  </span>
                </div></template
              >
            </ElTableColumn>
            <!-- @vue-generic {Role} -->
            <ElTableColumn label="说明" min-width="150">
              <template #default="{ row }: { row: Role }"
                ><div>
                  <div
                    class="management-cell-ellipsis"
                    :title="row.description"
                  >
                    {{ row.description || '—' }}
                  </div>
                </div></template
              >
            </ElTableColumn>
            <!-- @vue-generic {Role} -->
            <ElTableColumn label="更新时间" min-width="175">
              <template #default="{ row }: { row: Role }"
                ><div class="management-cell-nowrap">
                  {{ date(row.lastUpdateTime) }}
                </div></template
              >
            </ElTableColumn>
            <!-- @vue-generic {Role} -->
            <ElTableColumn label="操作" min-width="180" fixed="right">
              <template #default="{ row }: { row: Role }"
                ><div class="management-actions">
                  <ElButton link type="primary"
                    native-type="button"
                    :disabled="busy"
                    @click="editRole(row)"
                  >
                    {{
                      row.builtIn || !access.can('management:role:update')
                        ? '查看'
                        : '修改'
                    }}
                  </ElButton>
                  <ElButton link type="primary"
                    v-if="access.can('management:role:update') || row.builtIn"
                    native-type="button"
                    :disabled="busy"
                    @click="authorizationRole = row"
                  >
                    {{ row.builtIn ? '查看授权' : '分配权限' }}
                  </ElButton>
                  <template v-if="!row.builtIn">
                    <ElButton link type="primary"
                      v-if="access.can('management:role:apply')"
                      native-type="button"
                      :disabled="busy || row.version === row.appliedVersion"
                      @click="changeRole(row, true)"
                    >
                      应用
                    </ElButton>
                    <ElButton link type="primary"
                      v-if="
                        access.can(
                          row.status === 'ACTIVE'
                            ? 'management:role:disable'
                            : 'management:role:enable',
                        )
                      "
                      native-type="button"
                      :disabled="
                        busy ||
                        (row.status === 'DISABLED' &&
                          row.appliedVersion == null)
                      "
                      @click="changeRole(row)"
                    >
                      {{ row.status === 'ACTIVE' ? '禁用' : '启用' }}
                    </ElButton>
                  </template>
                </div></template
              >
            </ElTableColumn>
          </ElTable>
          <ElTable
            v-loading="loading || busy"
            v-else-if="section === 'users'"
            :data="users" :stripe="false" class="management-business-table"
            style="width: 100%"
            empty-text="暂无数据"
          >
<ElTableColumn type="index" label="序号" width="72" align="center" :index="(index: number) => (query.current - 1) * query.size + index + 1" />
            <!-- @vue-generic {Member} -->
            <ElTableColumn label="用户" min-width="180">
              <template #default="{ row }: { row: Member }"
                ><div>
                  <div class="management-cell-main">
                    <span class="management-cell-title">{{
                      row.displayName || row.username
                    }}</span>

                  </div>
                </div></template
              >
            </ElTableColumn>
<ElTableColumn prop="userId" label="用户编号" min-width="200" show-overflow-tooltip />
            <!-- @vue-generic {Member} -->
            <ElTableColumn label="手机号" min-width="150">
              <template #default="{ row }: { row: Member }"
                ><div class="management-cell-nowrap">
                  {{ row.phone }}
                </div></template
              >
            </ElTableColumn>
            <!-- @vue-generic {Member} -->
            <ElTableColumn label="角色" min-width="150">
              <template #default="{ row }: { row: Member }"
                ><div>
                  <div class="management-cell-ellipsis" :title="row.roleNames">
                    {{ row.roleNames || '—' }}
                  </div>
                </div></template
              >
            </ElTableColumn>
            <!-- @vue-generic {Member} -->
            <ElTableColumn label="管理状态" min-width="150">
              <template #default="{ row }: { row: Member }"
                ><div>
                  <span >{{ statusNames[row.status] }}</span>
                </div></template
              >
            </ElTableColumn>
            <!-- @vue-generic {Member} -->
            <ElTableColumn label="更新时间" min-width="175">
              <template #default="{ row }: { row: Member }"
                ><div class="management-cell-nowrap">
                  {{ date(row.lastUpdateTime) }}
                </div></template
              >
            </ElTableColumn>
            <!-- @vue-generic {Member} -->
            <ElTableColumn label="操作" min-width="180" fixed="right">
              <template #default="{ row }: { row: Member }"
                ><div class="management-actions">
                  <ElButton link type="primary"
                    v-if="access.can('management:user:assign-role')"
                    native-type="button"
                    :disabled="busy"
                    @click="editMember(row)"
                  >
                    分配角色
                  </ElButton>
                  <ElButton link type="primary"
                    v-if="
                      access.can(
                        row.status === 'ACTIVE'
                          ? 'management:user:disable'
                          : 'management:user:enable',
                      )
                    "
                    native-type="button"
                    :disabled="busy || row.status === 'UNASSIGNED'"
                    @click="changeMember(row)"
                  >
                    {{ row.status === 'ACTIVE' ? '禁用' : '启用' }}
                  </ElButton>
                </div></template
              >
            </ElTableColumn>
          </ElTable>
          <ElTable
            v-loading="loading || busy"
            v-else
            :data="permissions" :stripe="false" class="management-business-table"
            style="width: 100%"
            empty-text="暂无数据"
          >
<ElTableColumn type="index" label="序号" width="72" align="center" :index="(index: number) => (query.current - 1) * query.size + index + 1" />
            <!-- @vue-generic {Permission} -->
            <ElTableColumn label="权限" min-width="180">
              <template #default="{ row }: { row: Permission }"
                ><div>
                  <div class="management-cell-main">
                    <span class="management-cell-title">{{ row.name }}</span>

                  </div>
                </div></template
              >
            </ElTableColumn>
<ElTableColumn prop="code" label="编码" min-width="200" show-overflow-tooltip />
            <!-- @vue-generic {Permission} -->
            <ElTableColumn label="所属模块" min-width="150">
              <template #default="{ row }: { row: Permission }"
                ><div>{{ row.module }}</div></template
              >
            </ElTableColumn>
            <!-- @vue-generic {Permission} -->
            <ElTableColumn label="说明" min-width="150">
              <template #default="{ row }: { row: Permission }"
                ><div>
                  <div
                    class="management-cell-ellipsis"
                    :title="row.description"
                  >
                    {{ row.description }}
                  </div>
                </div></template
              >
            </ElTableColumn>
            <!-- @vue-generic {Permission} -->
            <ElTableColumn label="操作" min-width="180" fixed="right">
              <template #default="{ row }: { row: Permission }"
                ><div class="management-actions">
                  <ElButton link type="primary" native-type="button" @click="permissionDetail = row"
                    >查看</ElButton
                  >
                </div></template
              >
            </ElTableColumn>
          </ElTable>
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

    <AuthorizationEditor
      v-if="authorizationRole"
      :role="authorizationRole"
      @close="authorizationRole = null"
      @saved="saved"
    />
    <RoleEditor
      v-if="roleEditor"
      :role="editingRole"
      @close="roleEditor = false"
      @saved="saved"
    />
    <MemberEditor
      v-if="memberEditor"
      :member="editingMember"
      @close="memberEditor = false"
      @saved="saved"
    />
    <ManagementDialog
      v-if="permissionDetail"
      title="权限详情"
      :busy="false"
      :dirty="false"
      @close="permissionDetail = null"
    >
      <dl class="management-permission-detail">
        <dt>权限名称</dt>
        <dd>{{ permissionDetail.name }}</dd>
        <dt>权限编码</dt>
        <dd>
          <span class="management-code">{{ permissionDetail.code }}</span>
        </dd>
        <dt>所属模块</dt>
        <dd>{{ permissionDetail.module }}</dd>
        <dt>说明</dt>
        <dd>{{ permissionDetail.description }}</dd>
      </dl>
    </ManagementDialog>
  </ManagementShell>
</template>
