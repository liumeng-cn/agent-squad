<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  createUserApi,
  listUsersApi,
  resetUserPasswordApi,
  updateUserApi,
} from '../../../shared/api/adminUsersApi'
import { errorMessage } from '../../../shared/api/http'
import type { AccountRole, AccountStatus, AdminUserView } from '../../../shared/api/types'

const router = useRouter()
const users = ref<AdminUserView[]>([])
const nextCursor = ref<string | null>(null)
const loading = ref(false)
const saving = ref(false)
const error = ref<string | null>(null)
const notice = ref<string | null>(null)
const temporaryPassword = ref<string | null>(null)
const temporaryPasswordOwner = ref<string | null>(null)

const username = ref('')
const displayName = ref('')
const role = ref<AccountRole>('USER')

onMounted(() => { void load() })

async function load(cursor?: string): Promise<void> {
  if (loading.value) return
  loading.value = true
  error.value = null
  try {
    const page = await listUsersApi(cursor)
    users.value = cursor ? [...users.value, ...page.items] : page.items
    nextCursor.value = page.nextCursor
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    loading.value = false
  }
}

async function create(): Promise<void> {
  if (saving.value) return
  saving.value = true
  error.value = null
  notice.value = null
  temporaryPassword.value = null
  try {
    const result = await createUserApi({
      username: username.value.trim(),
      displayName: displayName.value.trim() || undefined,
      role: role.value,
    })
    temporaryPassword.value = result.temporaryPassword
    temporaryPasswordOwner.value = username.value.trim()
    notice.value = `账号 ${username.value.trim()} 已创建`
    username.value = ''
    displayName.value = ''
    role.value = 'USER'
    await load()
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    saving.value = false
  }
}

async function changeStatus(user: AdminUserView, status: AccountStatus): Promise<void> {
  if (saving.value) return
  saving.value = true
  error.value = null
  notice.value = null
  try {
    const updated = await updateUserApi(user.userId, {
      expectedVersion: user.version,
      status,
    })
    replaceUser(updated)
    notice.value = `${updated.username} 已${status === 'ACTIVE' ? '启用' : '停用'}`
  } catch (cause) {
    error.value = errorMessage(cause)
    await load()
  } finally {
    saving.value = false
  }
}

async function resetPassword(user: AdminUserView): Promise<void> {
  if (saving.value) return
  if (!window.confirm(`重置 ${user.username} 的密码？该账号的所有登录状态将立即失效。`)) return
  saving.value = true
  error.value = null
  notice.value = null
  temporaryPassword.value = null
  try {
    const result = await resetUserPasswordApi(user.userId)
    temporaryPassword.value = result.temporaryPassword
    temporaryPasswordOwner.value = user.username
    notice.value = `${user.username} 的密码已重置，需要重新登录并修改临时密码`
    // 服务端同时提升 password 与 version，直接重新读取保证行版本准确。
    await load()
  } catch (cause) {
    error.value = errorMessage(cause)
    await load()
  } finally {
    saving.value = false
  }
}

function replaceUser(updated: AdminUserView): void {
  users.value = users.value.map((item) => (item.userId === updated.userId ? updated : item))
}

function dismissTemporaryPassword(): void {
  temporaryPassword.value = null
  temporaryPasswordOwner.value = null
}

function formatTime(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}
</script>

<template>
  <main class="detail-page">
    <div class="detail-card">
      <button class="btn btn-sm" @click="router.push({ name: 'workspace' })">返回工作台</button>
      <h1>账号管理</h1>
      <p>账号只能由管理员创建，没有公开注册入口。停用或重置密码会立即撤销该账号的全部登录状态。</p>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <p v-if="notice" role="status">{{ notice }}</p>
      <div v-if="temporaryPassword" class="temp-password" role="status">
        <strong>临时密码（仅显示一次）</strong>
        <p>{{ temporaryPasswordOwner }}：<code>{{ temporaryPassword }}</code></p>
        <p>请通过安全渠道交给使用者；对方首次登录后必须先修改密码。</p>
        <button class="btn btn-sm" type="button" @click="dismissTemporaryPassword">我已保存</button>
      </div>
    </div>

    <form class="detail-card user-form" @submit.prevent="create">
      <h2>创建账号</h2>
      <label>
        用户名
        <input
          v-model="username"
          class="input"
          required
          minlength="3"
          maxlength="64"
          pattern="[a-z0-9][a-z0-9._-]{2,63}"
          autocomplete="off"
        />
      </label>
      <label>显示名称 <input v-model="displayName" class="input" maxlength="100" /></label>
      <label>
        角色
        <select v-model="role" class="input">
          <option value="USER">普通用户</option>
          <option value="ADMIN">管理员</option>
        </select>
      </label>
      <button class="btn btn-primary" type="submit" :disabled="saving || !username.trim()">
        {{ saving ? '提交中…' : '创建并生成临时密码' }}
      </button>
    </form>

    <div class="detail-card">
      <h2>全部账号</h2>
      <p v-if="!loading && users.length === 0">暂无账号</p>
      <div v-else class="user-table-wrap">
        <table class="user-table">
          <thead>
            <tr>
              <th>账号</th>
              <th>角色</th>
              <th>状态</th>
              <th>密码状态</th>
              <th>创建时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="user in users" :key="user.userId">
              <td>
                <strong>{{ user.username }}</strong>
                <span v-if="user.displayName" class="user-display-name">{{ user.displayName }}</span>
              </td>
              <td>{{ user.role === 'ADMIN' ? '管理员' : '用户' }}</td>
              <td>{{ user.status === 'ACTIVE' ? '正常' : '已停用' }}</td>
              <td>{{ user.mustChangePassword ? '需修改临时密码' : '已设置' }}</td>
              <td>{{ formatTime(user.createTime) }}</td>
              <td class="user-actions">
                <button
                  v-if="user.status === 'ACTIVE'"
                  class="btn btn-sm"
                  type="button"
                  :disabled="saving"
                  @click="changeStatus(user, 'DISABLED')"
                >停用</button>
                <button
                  v-else
                  class="btn btn-sm"
                  type="button"
                  :disabled="saving"
                  @click="changeStatus(user, 'ACTIVE')"
                >启用</button>
                <button
                  class="btn btn-sm btn-danger"
                  type="button"
                  :disabled="saving"
                  @click="resetPassword(user)"
                >重置密码</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <button v-if="nextCursor" class="btn btn-sm" type="button" :disabled="loading" @click="load(nextCursor)">
        加载更多
      </button>
    </div>
  </main>
</template>

<style scoped>
.user-form {
  display: grid;
  gap: 12px;
}

.user-form label {
  display: grid;
  gap: 6px;
}

.temp-password {
  margin-top: 12px;
  padding: 12px 14px;
  border: 1px solid #d9a441;
  border-radius: 8px;
  background: #fff8e6;
}

.temp-password code {
  font-size: 15px;
  word-break: break-all;
}

.user-table-wrap {
  overflow-x: auto;
}

.user-table {
  width: 100%;
  border-collapse: collapse;
}

.user-table th,
.user-table td {
  padding: 8px 10px;
  text-align: left;
  border-bottom: 1px solid rgba(0, 0, 0, 0.08);
  white-space: nowrap;
}

.user-display-name {
  display: block;
  font-size: 12px;
  opacity: 0.7;
}

.user-actions {
  display: flex;
  gap: 8px;
}
</style>
