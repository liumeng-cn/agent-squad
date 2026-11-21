<script setup lang="ts">
import { confirmManagement } from './confirmManagement'
import { computed, onMounted, ref } from 'vue'
import ManagementDialog from './ManagementDialog.vue'
import { findMember, roleOptions, saveMemberRoles } from '../api/managementApi'
import { errorMessage } from '../../../shared/http/http'
import type { Member, Role } from '../model/management'
const props = defineProps<{ member: Member | null }>()
const emit = defineEmits<{ close: []; saved: [] }>()
const candidate = ref<Member | null>(props.member)
const phone = ref(''); const roles = ref<Role[]>([])
const selected = ref<string[]>([...(props.member?.roleIds ?? [])])
const initial = JSON.stringify(selected.value)
const dirty = computed(() => JSON.stringify(selected.value) !== initial)
const optionsReady = ref(false)
const busy = ref(false); const loading = ref(true); const error = ref('')
onMounted(load)
async function load() {
  loading.value = true; error.value = ''
  try { roles.value = await roleOptions(); optionsReady.value = true }
  catch (reason) { error.value = errorMessage(reason) }
  finally { loading.value = false }
}
async function lookup() {
  if (!/^1\d{10}$/.test(phone.value)) { error.value = '请输入有效手机号'; return }
  busy.value = true; error.value = ''; candidate.value = null
  try { candidate.value = await findMember(phone.value); selected.value = [...candidate.value.roleIds] }
  catch (reason) { error.value = errorMessage(reason) }
  finally { busy.value = false }
}
async function save() {
  if (!candidate.value) return
  if (!selected.value.length && !await confirmManagement('不选择角色将撤销该账号的管理资格，确认继续？')) return
  busy.value = true; error.value = ''
  try { await saveMemberRoles(candidate.value, selected.value); emit('saved') }
  catch (reason) { error.value = errorMessage(reason) }
  finally { busy.value = false }
}
</script>
<template>
  <ManagementDialog :title="member ? '修改用户角色' : '添加管理用户'" :busy="busy" :dirty="dirty" @close="emit('close')">
    <div v-if="!member" class="management-lookup"><label>已有账号手机号<input v-model="phone" maxlength="11" :disabled="busy" @input="candidate = null" @keydown.enter.prevent="lookup" /></label><button :disabled="busy" @click="lookup">查找账号</button></div>
    <div v-if="candidate" class="management-identity"><strong>{{ candidate.displayName || candidate.username }}</strong><span>{{ candidate.phone }}</span><small>用户编号 {{ candidate.userId }}</small></div>
    <fieldset :disabled="busy || loading || !optionsReady || !candidate"><legend>分配角色</legend>
      <span v-if="loading" class="management-muted">正在加载角色…</span>
      <label v-for="role in roles" :key="role.roleId" class="management-check"><input v-model="selected" type="checkbox" :value="role.roleId" /><span>{{ role.name }}<small>{{ role.code }} · {{ role.status === 'ACTIVE' ? '启用' : '禁用' }}</small></span></label>
      <p v-if="!loading && !roles.length" class="management-muted">暂无已应用角色，请先创建并应用角色。</p>
    </fieldset>
    <p class="management-help">只修改管理资格，不影响该账号登录和聊天。已经禁用的管理用户重新分配角色后仍保持禁用。</p>
    <p v-if="error" class="management-error" role="alert">{{ error }} <button v-if="!roles.length" @click="load">重试</button></p>
    <template #actions><button class="management-primary" :disabled="busy || loading || !optionsReady || !candidate" @click="save">{{ busy ? '保存中…' : '应用授权' }}</button></template>
  </ManagementDialog>
</template>
