<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useV2Auth } from '../auth'
import { errorMessage } from '../../../shared/api/http'
const auth = useV2Auth()
const router = useRouter()
const open = ref(false)
const displayName = ref('')
const error = ref('')
const busy = ref(false)
function edit() { displayName.value = auth.user?.displayName ?? ''; error.value = ''; open.value = true }
async function save() {
  busy.value = true; error.value = ''
  try { await auth.update(displayName.value); open.value = false }
  catch (reason) { error.value = errorMessage(reason) }
  finally { busy.value = false }
}
async function logout() {
  busy.value = true; error.value = ''
  try { await auth.logout(); await router.replace({ name: 'v2-login' }) }
  catch (reason) { error.value = errorMessage(reason) }
  finally { busy.value = false }
}
</script>
<template>
  <button class="profile-button" type="button" @click="edit">{{ auth.user?.effectiveDisplayName || '个人信息' }}</button>
  <Teleport to="body"><div v-if="open" class="profile-backdrop" @click.self="!busy && (open = false)">
    <section role="dialog" aria-modal="true" aria-labelledby="profile-title" class="profile-panel">
      <h2 id="profile-title">个人信息</h2>
      <dl><dt>用户 ID</dt><dd>{{ auth.user?.userId }}</dd><dt>用户名</dt><dd>{{ auth.user?.userName }}</dd><dt>手机号</dt><dd>{{ auth.user?.phone }}</dd></dl>
      <form @submit.prevent="save"><label>昵称<input v-model="displayName" maxlength="100" placeholder="未填写时展示用户名" /></label>
        <p v-if="error" class="profile-error" role="alert">{{ error }}</p>
        <div class="profile-actions"><button type="button" :disabled="busy" @click="logout">退出登录</button><button type="button" :disabled="busy" @click="open = false">取消</button><button :disabled="busy">{{ busy ? '处理中…' : '保存' }}</button></div>
      </form>
    </section>
  </div></Teleport>
</template>
<style scoped>
.profile-button{border:0;background:transparent;color:#74796b;font:inherit;font-size:12px;cursor:pointer;max-width:140px;text-overflow:ellipsis;overflow:hidden;white-space:nowrap}.profile-backdrop{position:fixed;inset:0;background:#292c2533;z-index:100;display:flex;align-items:center;justify-content:center;padding:20px}.profile-panel{width:360px;max-width:100%;padding:28px;border-radius:20px;background:white;color:#292c25;font-family:inherit}h2{font-size:20px;font-weight:500;margin-top:0}dt{font-size:12px;color:#858a7d;margin-top:14px}dd{margin:5px 0;font-size:14px;overflow-wrap:anywhere}label{font-size:13px}input{width:100%;box-sizing:border-box;border:1px solid #e5e7e1;border-radius:12px;padding:12px;margin-top:8px;font:inherit}.profile-actions{display:flex;gap:12px;justify-content:flex-end;margin-top:25px}.profile-actions button{border:1px solid #e5e7e1;background:#f7f8f5;border-radius:12px;padding:8px 12px;color:#626c58;cursor:pointer}.profile-actions button:first-child{margin-right:auto}.profile-error{font-size:13px;color:#a6564d}
</style>
