<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { changePasswordApi } from '../../../shared/api/authApi'
import { errorMessage } from '../../../shared/api/http'
import { useAuthStore } from '../stores/auth'
const router=useRouter(),auth=useAuthStore()
const current=ref(''),next=ref(''),busy=ref(false),error=ref('')
async function submit() {
  if(busy.value)return
  busy.value=true;error.value=''
  try {
    await changePasswordApi(current.value,next.value)
    current.value='';next.value=''
    auth.clearUser()
    await router.replace({name:'login'})
  }catch(e){error.value=errorMessage(e)}
  finally{busy.value=false}
}
</script>
<template>
  <main class="login-page">
    <form class="login-card" @submit.prevent="submit">
      <h1>修改登录密码</h1>
      <p v-if="auth.user?.mustChangePassword">首次使用前请更换临时密码，至少 12 个字符。</p>
      <label class="field"><span>当前密码</span><input v-model="current" type="password" autocomplete="current-password" required /></label>
      <label class="field"><span>新密码</span><input v-model="next" type="password" autocomplete="new-password" minlength="12" required /></label>
      <p v-if="error" role="alert">{{error}}</p>
      <button class="btn btn-primary btn-block" type="submit" :disabled="busy">{{busy?'提交中…':'确认修改并重新登录'}}</button>
    </form>
  </main>
</template>
