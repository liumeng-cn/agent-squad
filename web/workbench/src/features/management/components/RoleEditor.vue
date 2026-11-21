<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import ManagementDialog from './ManagementDialog.vue'
import { createRole, updateRole } from '../api/managementApi'
import { useManagementStore } from '../stores/management'
import { errorMessage } from '../../../shared/http/http'
import type { Role, RoleDraft } from '../model/management'
const props = defineProps<{ role: Role | null }>()
const emit = defineEmits<{ close: []; saved: [] }>()
const access = useManagementStore()
const readonly = computed(() => !!props.role?.builtIn || !access.can(props.role ? 'management:role:update' : 'management:role:create'))
const draft = reactive<RoleDraft>({ code: props.role?.code ?? '', name: props.role?.name ?? '', description: props.role?.description ?? '' })
const initial = JSON.stringify(draft)
const dirty = computed(() => JSON.stringify(draft) !== initial)
const busy = ref(false); const error = ref('')
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
    <p class="management-help">基本信息保存后，可在角色列表中分配菜单与功能，再应用生效。</p>
    <p v-if="error" class="management-error" role="alert">{{ error }}</p>
    <template #actions><button v-if="!readonly" class="management-primary" :disabled="busy" @click="save">{{ busy ? '保存中…' : '保存草稿' }}</button></template>
  </ManagementDialog>
</template>
