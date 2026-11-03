<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { adminUsersApi,createUserApi,updateUserApi,resetUserPasswordApi,
         type AdminUserSummary } from '../../../shared/api/authApi'
import { errorMessage } from '../../../shared/api/http'
const users=ref<AdminUserSummary[]>([]),error=ref(''),username=ref(''),displayName=ref('')
const role=ref<'USER'|'ADMIN'>('USER'),temporary=ref(''),busy=ref(false)
async function refresh() {
  try{users.value=(await adminUsersApi()).items;error.value=''}
  catch(e){error.value=errorMessage(e)}
}
async function create() {
  if(busy.value)return
  busy.value=true;temporary.value=''
  try{
    const result=await createUserApi(username.value,displayName.value,role.value)
    temporary.value=result.temporaryPassword
    username.value='';displayName.value=''
    await refresh()
  }catch(e){error.value=errorMessage(e)}
  finally{busy.value=false}
}
async function toggle(item:AdminUserSummary) {
  if(busy.value)return
  busy.value=true;temporary.value=''
  try{
    await updateUserApi(item.userId,item.version,item.displayName,item.role,
      item.status==='ACTIVE'?'DISABLED':'ACTIVE')
    await refresh()
  }catch(e){error.value=errorMessage(e)}
  finally{busy.value=false}
}
async function reset(item:AdminUserSummary) {
  if(busy.value)return
  busy.value=true;temporary.value=''
  try{
    temporary.value=(await resetUserPasswordApi(item.userId)).temporaryPassword
    await refresh()
  }catch(e){error.value=errorMessage(e)}
  finally{busy.value=false}
}
onMounted(()=>{void refresh()})
</script>
<template>
  <main class="settings-page" style="padding:2rem;max-width:960px;margin:auto">
    <h1>用户管理</h1>
    <p>邀请制账户；临时密码仅在创建或重置时显示一次，请安全交付。</p>
    <form @submit.prevent="create">
      <label>用户名 <input v-model="username" required minlength="3" maxlength="64" /></label>
      <label>显示名称 <input v-model="displayName" required /></label>
      <label>角色 <select v-model="role"><option value="USER">普通用户</option><option value="ADMIN">管理员</option></select></label>
      <button :disabled="busy" type="submit">创建用户</button>
    </form>
    <p v-if="temporary" role="status">一次性临时密码：<code>{{temporary}}</code></p>
    <p v-if="error" role="alert">{{error}}</p>
    <table><thead><tr><th>用户</th><th>角色</th><th>状态</th><th>操作</th></tr></thead>
      <tbody><tr v-for="user in users" :key="user.userId">
        <td>{{user.displayName}} ({{user.username}})</td><td>{{user.role}}</td><td>{{user.status}}</td>
        <td><button :disabled="busy" @click="toggle(user)">{{user.status==='ACTIVE'?'禁用':'启用'}}</button>
        <button :disabled="busy" @click="reset(user)">重置密码</button></td>
      </tr></tbody></table>
  </main>
</template>
