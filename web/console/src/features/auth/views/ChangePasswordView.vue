<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useConversationsStore } from '../../chat/stores/conversations'
import { errorMessage } from '../../../shared/api/http'

const auth = useAuthStore()
const conversations = useConversationsStore()
const router = useRouter()

const currentPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const submitting = ref(false)
const error = ref<string | null>(null)

const forced = computed(() => auth.mustChangePassword)

/** 与网关 PasswordPolicy 保持一致：12–128 个字符且不超过 72 个 UTF-8 字节。 */
const policyMessage = computed(() => {
  if (!newPassword.value) return ''
  const chars = [...newPassword.value].length
  const bytes = new TextEncoder().encode(newPassword.value).length
  if (chars < 12 || chars > 128) return '新密码需为 12–128 个字符'
  if (bytes > 72) return '新密码不能超过 72 个字节（中文等字符按多字节计算）'
  if (confirmPassword.value && confirmPassword.value !== newPassword.value) return '两次输入的新密码不一致'
  return ''
})

const canSubmit = computed(() =>
  !submitting.value &&
  currentPassword.value.length > 0 &&
  newPassword.value.length > 0 &&
  confirmPassword.value.length > 0 &&
  policyMessage.value === '',
)

async function submit(): Promise<void> {
  if (!canSubmit.value) return
  error.value = null
  submitting.value = true
  try {
    await auth.changePassword(currentPassword.value, newPassword.value)
    await conversations.resetAll()
    currentPassword.value = ''
    newPassword.value = ''
    confirmPassword.value = ''
    await router.replace({ name: 'login', query: { passwordChanged: '1' } })
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    submitting.value = false
  }
}

async function logout(): Promise<void> {
  await auth.logout()
  conversations.resetAll()
  await router.replace({ name: 'login' })
}
</script>

<template>
  <main class="login-page">
    <form class="login-card" @submit.prevent="submit">
      <h1>修改密码</h1>
      <p class="login-subtitle">
        <template v-if="forced">当前使用的是临时密码，请先设置新密码再继续使用。</template>
        <template v-else>修改密码后所有登录状态将失效，需要使用新密码重新登录。</template>
      </p>
      <label class="field">
        <span>当前密码</span>
        <input
          v-model="currentPassword"
          class="input"
          type="password"
          autocomplete="current-password"
          required
        />
      </label>
      <label class="field">
        <span>新密码</span>
        <input
          v-model="newPassword"
          class="input"
          type="password"
          autocomplete="new-password"
          minlength="12"
          maxlength="128"
          required
        />
      </label>
      <label class="field">
        <span>确认新密码</span>
        <input
          v-model="confirmPassword"
          class="input"
          type="password"
          autocomplete="new-password"
          minlength="12"
          maxlength="128"
          required
        />
      </label>
      <p v-if="policyMessage" class="form-error" role="alert">{{ policyMessage }}</p>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <button class="btn btn-primary btn-block" type="submit" :disabled="!canSubmit">
        {{ submitting ? '提交中…' : '更新密码' }}
      </button>
      <button class="btn btn-block" type="button" :disabled="submitting" @click="logout">
        退出登录
      </button>
    </form>
  </main>
</template>
