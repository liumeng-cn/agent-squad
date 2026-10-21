<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const username = ref('')
const password = ref('')
const submitting = ref(false)

async function submit(): Promise<void> {
  if (submitting.value) {
    return
  }
  submitting.value = true
  const ok = await auth.login(username.value, password.value)
  submitting.value = false
  if (!ok) {
    return
  }
  password.value = ''
  const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : null
  if (redirect && redirect.startsWith('/')) {
    await router.replace(redirect)
  } else {
    await router.replace({ name: 'workspace' })
  }
}
</script>

<template>
  <main class="login-page">
    <form class="login-card" @submit.prevent="submit">
      <h1>小队工作台</h1>
      <p class="login-subtitle">登录后继续你的任务</p>
      <label class="field">
        <span>账号</span>
        <input v-model="username" class="input" type="text" autocomplete="username" required />
      </label>
      <label class="field">
        <span>密码</span>
        <input
          v-model="password"
          class="input"
          type="password"
          autocomplete="current-password"
          required
        />
      </label>
      <p v-if="auth.loginError" class="form-error" role="alert">{{ auth.loginError }}</p>
      <button class="btn btn-primary btn-block" type="submit" :disabled="submitting">
        {{ submitting ? '登录中…' : '登录' }}
      </button>
    </form>
  </main>
</template>
