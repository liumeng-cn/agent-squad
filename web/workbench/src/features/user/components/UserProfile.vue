<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserProfile } from '../composables/useUserProfile'
import { useAuthStore } from '../../auth/stores/auth'
import { ApiError, errorMessage } from '../../../shared/http/http'
import UserAvatar from './UserAvatar.vue'
defineProps<{ compact?: boolean }>()
const auth = useAuthStore()
const profile = useUserProfile()
const router = useRouter()
const menu = ref(false)
const open = ref(false)
const displayName = ref('')
const originalName = ref('')
const error = ref('')
const notice = ref('')
const conflict = ref(false)
const busy = ref(false)
const trigger = ref<HTMLButtonElement>()
const dialog = ref<HTMLElement>()
const secondary = computed(() => auth.user?.displayName ? auth.user.userName : auth.user?.phone.replace(/^(\d{3})\d{4}(\d{4})$/, '$1 **** $2'))
async function edit() {
  menu.value = false; displayName.value = auth.user?.displayName ?? ''; originalName.value = displayName.value
  error.value = ''; notice.value = ''; conflict.value = false; open.value = true
  await nextTick(); dialog.value?.querySelector<HTMLInputElement>('input')?.focus()
}
async function close() {
  if (busy.value || (displayName.value !== originalName.value && !window.confirm('放弃尚未保存的昵称修改？'))) return
  open.value = false; await nextTick(); trigger.value?.focus()
}
async function save() {
  busy.value = true; error.value = ''; conflict.value = false
  try { await profile.update(displayName.value); originalName.value = displayName.value; open.value = false; trigger.value?.focus() }
  catch (reason) { error.value = errorMessage(reason); conflict.value = reason instanceof ApiError && reason.code === 409 }
  finally { busy.value = false }
}
async function refresh() {
  busy.value = true
  try { await auth.ensureSession(); conflict.value = false; notice.value = '已刷新版本，输入内容已保留，请确认后保存' }
  catch (reason) { error.value = errorMessage(reason) }
  finally { busy.value = false }
}
async function logout() {
  busy.value = true; error.value = ''
  try { await auth.logout(); await router.replace({ name: 'login' }) }
  catch (reason) { error.value = errorMessage(reason) }
  finally { busy.value = false }
}
async function copy(value?: string) {
  try { await navigator.clipboard.writeText(value || ''); notice.value = '已复制' }
  catch { notice.value = '复制失败，请手动选择复制' }
}
function keyboard(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    if (open.value) void close()
    else if (menu.value) { menu.value = false; trigger.value?.focus() }
  }
  if (event.key === 'Tab' && open.value && dialog.value) {
    const controls = [...dialog.value.querySelectorAll<HTMLElement>('button:not(:disabled), input')]
    const first = controls[0]; const last = controls[controls.length - 1]
    if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus() }
    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus() }
  }
}
document.addEventListener('keydown', keyboard)
onBeforeUnmount(() => document.removeEventListener('keydown', keyboard))
</script>
<template>
  <div class="user-entry">
    <button ref="trigger" class="profile-button" type="button" :aria-label="`${auth.user?.effectiveDisplayName || '用户'}，个人菜单`" :aria-expanded="menu" @click="menu = !menu; error = ''">
      <UserAvatar :avatar="auth.user?.avatar" />
      <span v-if="!compact" class="user-label"><strong>{{ auth.user?.effectiveDisplayName }}</strong><small>{{ secondary }}</small></span>
      <span v-if="!compact" class="more" aria-hidden="true">···</span>
    </button>
    <template v-if="menu"><button class="menu-dismiss" aria-label="关闭用户菜单" @click="menu = false" /><div class="user-menu">
      <button type="button" @click="edit">个人信息</button><button type="button" :disabled="busy" @click="logout">退出登录</button>
      <p v-if="error" role="alert" class="profile-error">{{ error }}</p>
    </div></template>
  </div>
  <Teleport to="body"><div v-if="open" class="profile-backdrop" @click.self="close">
    <section ref="dialog" role="dialog" aria-modal="true" aria-labelledby="profile-title" class="profile-panel">
      <div class="profile-heading"><h2 id="profile-title">个人信息</h2><button type="button" class="plain-close" aria-label="关闭个人信息" :disabled="busy" @click="close">×</button></div>
      <div class="profile-identity"><UserAvatar :avatar="auth.user?.avatar" :size="80" /><div><strong>{{ auth.user?.effectiveDisplayName }}</strong><p>专属于你的几何头像</p></div></div>
      <dl><dt>用户 ID</dt><dd>{{ auth.user?.userId }}<button type="button" class="copy" @click="copy(auth.user?.userId)">复制</button></dd><dt>用户名</dt><dd>{{ auth.user?.userName }}<button type="button" class="copy" @click="copy(auth.user?.userName)">复制</button></dd><dt>手机号</dt><dd>{{ auth.user?.phone }}</dd></dl>
      <form @submit.prevent="save"><label>昵称<input v-model="displayName" maxlength="100" placeholder="未填写时展示用户名" /></label>
        <p v-if="error" class="profile-error" role="alert">{{ error }}</p><p v-if="notice" class="profile-notice" role="status">{{ notice }}</p>
        <button v-if="conflict" type="button" :disabled="busy" class="copy" @click="refresh">刷新当前资料版本，保留输入</button>
        <div class="profile-actions"><button type="button" :disabled="busy" @click="close">取消</button><button class="primary" :disabled="busy">{{ busy ? '处理中…' : '保存' }}</button></div>
      </form>
    </section>
  </div></Teleport>
</template>
<style scoped>
.user-entry{position:relative}.profile-button{display:flex;align-items:center;gap:11px;width:100%;min-height:52px;border:0;border-radius:12px;padding:8px;background:transparent;color:var(--text);text-align:left}.profile-button:hover{background:var(--hover)}.user-label{min-width:0;flex:1}.user-label strong,.user-label small{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.user-label strong{font-size:14px;font-weight:500}.user-label small{font-size:12px;color:var(--text-secondary)}.more{font-size:20px}.menu-dismiss{position:fixed;inset:0;opacity:0;z-index:20;cursor:default}.user-menu{position:absolute;bottom:calc(100% + 10px);left:0;width:240px;padding:7px;background:#fff;border:1px solid var(--border);border-radius:14px;box-shadow:0 10px 28px #20263014;z-index:21}.user-menu>button{display:block;width:100%;background:transparent;border:0;padding:11px 14px;border-radius:9px;text-align:left;color:var(--text);font-size:14px}.user-menu>button:hover{background:var(--hover)}.profile-backdrop{position:fixed;inset:0;background:#20263045;z-index:100;display:flex;align-items:center;justify-content:center;padding:16px}.profile-panel{width:480px;max-width:100%;max-height:calc(100dvh - 32px);overflow:auto;padding:28px;border-radius:22px;background:#fff;color:var(--text)}.profile-heading{display:flex;align-items:center;justify-content:space-between}h2{font-size:20px;font-weight:500;margin:0}.plain-close{border:0;background:none;font-size:26px;color:var(--text-secondary);width:40px;height:40px}.profile-identity{display:flex;align-items:center;gap:18px;margin:22px 0}.profile-identity strong{font-size:18px;overflow-wrap:anywhere}.profile-identity p{font-size:12px;color:var(--text-secondary);margin:5px 0}.profile-panel dt{font-size:12px;color:var(--text-secondary);margin-top:16px}.profile-panel dd{margin:5px 0;font-size:14px;overflow-wrap:anywhere}.copy{border:0;background:none;color:var(--accent);font-size:12px;padding:4px 8px;white-space:nowrap}label{font-size:13px}input{width:100%;border:1px solid var(--input-border);border-radius:12px;padding:12px;margin-top:8px;font:inherit;color:var(--text)}input::placeholder{color:var(--text-placeholder)}.profile-actions{display:flex;gap:12px;justify-content:flex-end;margin-top:25px}.profile-actions button{border:1px solid var(--input-border);background:#fff;border-radius:12px;padding:9px 18px;color:var(--text);font-size:14px}.profile-actions .primary{background:var(--accent);border-color:var(--accent);color:#fff}.profile-error{font-size:13px;color:var(--danger);padding:0 8px}.profile-notice{font-size:13px;color:var(--text-secondary)}
</style>
