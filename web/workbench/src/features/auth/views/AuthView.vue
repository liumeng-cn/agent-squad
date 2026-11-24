<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { registerApi } from '../../user/api/userApi'
import { useAuthStore } from '../stores/auth'
import { errorMessage } from '../../../shared/http/http'
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const registering = computed(() => route.name === 'register')
const rememberedPhoneKey = 'squad.auth.rememberedPhone'
const phone = ref(readRememberedPhone())
const verificationCode = ref('')
const busy = ref(false)
const error = ref('')
const notice = ref('')

function readRememberedPhone(): string {
  try {
    return localStorage.getItem(rememberedPhoneKey) ?? ''
  } catch {
    return ''
  }
}

function rememberPhone(value: string) {
  try {
    localStorage.setItem(rememberedPhoneKey, value)
  } catch {
    // 浏览器禁用本地存储时仍允许正常登录。
  }
}

async function submit() {
  if (busy.value) return
  busy.value = true; error.value = ''; notice.value = ''
  try {
    if (registering.value) {
      await registerApi(phone.value.trim(), verificationCode.value.trim())
      await router.replace({ name: 'login' })
      notice.value = '注册成功，请使用手机号登录'
    } else {
      await auth.login(phone.value.trim(), verificationCode.value.trim())
      rememberPhone(phone.value.trim())
      await router.replace({ name: 'chat' })
    }
  } catch (reason) { error.value = errorMessage(reason) }
  finally { busy.value = false }
}
</script>
<template>
  <main class="auth-page">
    <div class="brand"><img src="/brand/squad-logo.svg" alt="Squad" width="156" height="64" /><span>一起把事情做好</span></div>
    <form class="auth-card" @submit.prevent="submit">
      <h1>{{ registering ? '创建账号' : '欢迎回来' }}</h1>
      <p class="description">{{ registering ? '使用手机号开启新的协作' : '登录后继续你的对话' }}</p>
      <label>手机号<input v-model="phone" type="tel" autocomplete="tel" inputmode="tel" pattern="1[3-9][0-9]{9}" maxlength="11" placeholder="请输入手机号" required /></label>
      <label>验证码<input v-model="verificationCode" autocomplete="one-time-code" inputmode="numeric" maxlength="4" placeholder="请输入验证码" required /></label>
      <p class="hint">当前为测试模式，验证码为 8888</p>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <p v-if="notice" class="notice" role="status">{{ notice }}</p>
      <button class="submit" :disabled="busy">{{ busy ? '正在处理…' : registering ? '注册' : '登录' }}</button>
      <p class="switch">{{ registering ? '已有账号？' : '还没有账号？' }}<RouterLink :to="{ name: registering ? 'login' : 'register' }" @click="error = ''; notice = ''">{{ registering ? '去登录' : '注册账号' }}</RouterLink></p>
    </form>
  </main>
</template>
<style scoped>
.auth-page{min-height:100dvh;background:#fff;color:#20251e;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','PingFang SC',sans-serif;padding:0 24px;box-sizing:border-box}.brand{height:72px;display:flex;align-items:center;gap:16px;font-size:19px;font-weight:600}.brand img{object-fit:contain}.brand span{font-size:12px;color:#626b5c;font-weight:400}.auth-card{max-width:360px;margin:12vh auto 0}h1{font-size:26px;font-weight:500;margin-bottom:10px}.description{color:#56614f;font-size:14px;margin-bottom:32px}label{display:block;font-size:13px;margin-top:20px}input{display:block;width:100%;box-sizing:border-box;margin-top:9px;border:1px solid #b6bfaf;border-radius:12px;padding:13px 15px;font:inherit;background:#fafbf9;color:#20251e}input::placeholder{color:#68715f}input:focus{outline:2px solid #17477c;outline-offset:2px}.hint,.switch{font-size:12px;color:#56614f}.submit{width:100%;padding:13px;border:0;border-radius:14px;background:#17477c;color:#fff;font:inherit;margin-top:18px;cursor:pointer}.submit:disabled{opacity:.5;cursor:default}.switch{text-align:center;margin-top:24px}.switch a{color:#17477c;margin-left:6px}.error{color:#963f35;font-size:13px}.notice{color:#43543a;font-size:13px}
</style>
