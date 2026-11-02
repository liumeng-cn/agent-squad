<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import {
  adminUsersApi, createUserApi, updateUserApi, resetUserPasswordApi,
  type AdminUserSummary
} from '../../../shared/api/authApi'
import { errorMessage } from '../../../shared/api/http'

const users = ref<AdminUserSummary[]>([])
const error = ref('')
const username = ref('')
const displayName = ref('')
const role = ref<'USER' | 'ADMIN'>('USER')
const temporary = ref('')
const busy = ref(false)
const loadingMore = ref(false)
const nextCursor = ref<string | null>(null)
let active = true

async function refresh() {
  try {
    const page = await adminUsersApi()
    if (!active) return
    users.value = page.items
    nextCursor.value = page.nextCursor
    error.value = ''
  } catch (e) {
    if (active) error.value = errorMessage(e)
  }
}

async function loadMore() {
  if (loadingMore.value || busy.value || !nextCursor.value) return
  const cursor = nextCursor.value
  loadingMore.value = true
  try {
    const page = await adminUsersApi(cursor)
    if (!active) return
    const known = new Set(users.value.map(user => user.userId))
    users.value.push(...page.items.filter(user => !known.has(user.userId)))
    nextCursor.value = page.nextCursor
    error.value = ''
  } catch (e) {
    if (active) error.value = errorMessage(e)
  } finally {
    loadingMore.value = false
  }
}

async function create() {
  if (busy.value) return
  busy.value = true
  temporary.value = ''
  try {
    const result = await createUserApi(username.value, displayName.value, role.value)
    temporary.value = result.temporaryPassword
    username.value = ''
    displayName.value = ''
    await refresh()
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    busy.value = false
  }
}

async function toggle(item: AdminUserSummary) {
  if (busy.value) return
  busy.value = true
  temporary.value = ''
  try {
    await updateUserApi(item.userId, item.version, item.displayName, item.role,
      item.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE')
    await refresh()
  } catch (e) {
    error.value = errorMessage(e)
    await refresh()
  } finally {
    busy.value = false
  }
}

async function reset(item: AdminUserSummary) {
  if (busy.value) return
  busy.value = true
  temporary.value = ''
  try {
    temporary.value = (await resetUserPasswordApi(item.userId)).temporaryPassword
    await refresh()
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    busy.value = false
  }
}

onMounted(() => { void refresh() })
onBeforeUnmount(() => {
  active = false
  temporary.value = ''
})
</script>

<template>
  <main class="settings-page" style="padding:2rem;max-width:960px;margin:auto">
    <h1>用户管理</h1>
    <p>邀请制账户；临时密码仅在创建或重置时显示一次，请通过安全渠道交付。</p>
    <form @submit.prevent="create">
      <label>用户名 <input v-model="username" required minlength="3" maxlength="64" /></label>
      <label>显示名称 <input v-model="displayName" required maxlength="100" /></label>
      <label>角色 <select v-model="role"><option value="USER">普通用户</option><option value="ADMIN">管理员</option></select></label>
      <button :disabled="busy || loadingMore" type="submit">创建用户</button>
    </form>
    <section v-if="temporary" role="status" aria-label="一次性临时密码">
      <p>一次性临时密码：<code>{{ temporary }}</code></p>
      <button type="button" @click="temporary = ''">关闭并清除页面显示</button>
    </section>
    <p v-if="error" role="alert">{{ error }}</p>
    <table>
      <thead><tr><th>用户</th><th>角色</th><th>状态</th><th>操作</th></tr></thead>
      <tbody>
        <tr v-for="user in users" :key="user.userId">
          <td>{{ user.displayName }} ({{ user.username }})</td>
          <td>{{ user.role }}</td>
          <td>{{ user.status }}</td>
          <td>
            <button :disabled="busy || loadingMore" @click="toggle(user)">{{ user.status === 'ACTIVE' ? '禁用' : '启用' }}</button>
            <button :disabled="busy || loadingMore" @click="reset(user)">重置密码</button>
          </td>
        </tr>
      </tbody>
    </table>
    <button v-if="nextCursor" type="button" :disabled="busy || loadingMore" @click="loadMore">
      {{ loadingMore ? '加载中…' : '加载更多用户' }}
    </button>
  </main>
</template>
