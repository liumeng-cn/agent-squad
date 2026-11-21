<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import ManagementDialog from './ManagementDialog.vue'
import { permissionOptions, createRole, updateRole } from '../api/managementApi'
import { useManagementStore } from '../stores/management'
import { errorMessage } from '../../../shared/http/http'
import type { Permission, Role, RoleDraft } from '../model/management'
const props = defineProps<{ role: Role | null }>()
const emit = defineEmits<{ close: []; saved: [] }>()
const access = useManagementStore()
const readonly = computed(() => !!props.role?.builtIn || !access.can('management:role:manage'))
const draft = reactive<RoleDraft>({ code: props.role?.code ?? '', name: props.role?.name ?? '', description: props.role?.description ?? '', permissionCodes: [...(props.role?.draftPermissionCodes ?? [])] })
const initial = JSON.stringify(draft)
const dirty = computed(() => JSON.stringify(draft) !== initial)
const permissions = ref<Permission[]>([])
const loading = ref(true); const busy = ref(false); const error = ref('')
onMounted(load)
async function load() {
  loading.value = true; error.value = ''
  try {
    permissions.value = readonly.value
      ? draft.permissionCodes.map(code => ({ code, name: code, module: '', description: '' }))
      : (await permissionOptions()).records
  }
  catch (reason) { error.value = errorMessage(reason) }
  finally { loading.value = false }
}
async function save() {
  if (!draft.name.trim() || !/^[a-z][a-z0-9-]{1,63}$/.test(draft.code)) { error.value = '请填写名称及合法编码（小写字母、数字、连字符）'; return }
  busy.value = true; error.value = ''
  try { if (props.role) await updateRole(props.role, draft); else await createRole(draft); emit('saved') }
  catch (reason) { error.value = errorMessage(reason) }
  finally { busy.value = false }
}
</script>
<template>
  <ManagementDialog :title="readonly ? '角色详情' : role ? '修改角色' : '新增角色'" :busy="busy" :dirty="dirty" @close="emit('close')">
    <div class="management-form-row">
    <label>角色名称 <input v-model="draft.name" maxlength="64" :disabled="busy || readonly" /></label>
    <label>角色编码 <input v-model="draft.code" maxlength="64" :readonly="!!role" :disabled="busy || readonly" /></label>
    </div>
    <label>说明 <textarea v-model="draft.description" maxlength="500" rows="3" :disabled="busy || readonly" /></label>
    <fieldset :disabled="busy || loading || readonly"><legend>权限范围</legend>
      <span v-if="loading" class="management-muted">正在加载权限…</span>
      <label v-for="item in permissions" :key="item.code" class="management-check"><input v-model="draft.permissionCodes" type="checkbox" :value="item.code" :disabled="!access.can(item.code)" /> <span>{{ item.name }}<small>{{ item.code }}</small></span></label>
    </fieldset>
    <p class="management-help">保存只更新草稿；在列表点击“应用”后权限生效。不能授予自己不具备的权限。</p>
    <p v-if="error" class="management-error" role="alert">{{ error }} <button v-if="!permissions.length" @click="load">重试</button></p>
    <template #actions><button v-if="!readonly" class="management-primary" :disabled="busy || loading || !permissions.length" @click="save">{{ busy ? '保存中…' : '保存草稿' }}</button></template>
  </ManagementDialog>
</template>
