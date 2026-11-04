<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ApiError, errorMessage } from '../../../shared/api/http'
import { createConnection, listConnections, updateConnection, type Connection, type ConnectionUpdate } from '../../../shared/api/connectionsApi'
import { newIdempotencyKey } from '../../../shared/lib/idempotency'

const items = ref<Connection[]>([])
const nextCursor = ref<string | null>(null)
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const notice = ref('')

const name = ref('')
const provider = ref('')
const environment = ref('')
const policy = ref('{"resources":[]}')
const secret = ref('')

const editing = ref<Connection | null>(null)
const editPolicy = ref('')
const editSecret = ref('')

onMounted(() => { void load() })

function parsePolicy(text: string): Record<string, unknown> {
  let parsed: unknown
  try { parsed = JSON.parse(text) } catch { throw new Error('资源策略必须是有效的 JSON 对象') }
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed) || !Object.keys(parsed).length) {
    throw new Error('资源策略必须是至少含一个字段的 JSON 对象')
  }
  return parsed as Record<string, unknown>
}

async function load(cursor?: string) {
  if (loading.value) return
  loading.value = true
  try {
    const page = await listConnections(cursor)
    items.value = cursor ? [...items.value, ...page.items] : page.items
    nextCursor.value = page.nextCursor ?? null
  } catch (cause) { error.value = errorMessage(cause) }
  finally { loading.value = false }
}

function select(connection: Connection) {
  editing.value = connection
  editPolicy.value = JSON.stringify(connection.resourcePolicy, null, 2)
  editSecret.value = ''
  error.value = ''
  notice.value = ''
}

async function create() {
  if (saving.value || !name.value.trim() || !provider.value.trim() || !environment.value.trim() || !secret.value) return
  saving.value = true; error.value = ''; notice.value = ''
  try {
    const created = await createConnection({
      name: name.value.trim(),
      provider: provider.value.trim(),
      environment: environment.value.trim(),
      resourcePolicy: parsePolicy(policy.value),
      secret: secret.value,
    }, newIdempotencyKey())
    notice.value = `已创建连接 ${created.name}；密钥加密保存，页面不会再显示明文。`
    name.value = ''; provider.value = ''; environment.value = ''
    policy.value = '{"resources":[]}'; secret.value = ''
    await load()
  } catch (cause) { error.value = errorMessage(cause) }
  finally { saving.value = false }
}

async function apply(update: Omit<ConnectionUpdate, 'expectedVersion'>) {
  if (!editing.value || saving.value) return
  const connectionId = editing.value.connectionId
  saving.value = true; error.value = ''; notice.value = ''
  try {
    const updated = await updateConnection(connectionId, { ...update, expectedVersion: editing.value.revision }, newIdempotencyKey())
    editing.value = updated
    notice.value = update.secret ? '密钥已轮换；旧密钥不再可用。' : update.enabled === undefined ? '资源策略已更新。' : updated.enabled ? '连接已启用。' : '连接已停用，新执行不会再使用它。'
    await load()
  } catch (cause) {
    error.value = errorMessage(cause)
    if (cause instanceof ApiError && cause.code === 409) {
      error.value = '连接版本已变化，已刷新到最新版本；你的草稿仍在，请核对后重新提交。'
      await load()
      const latest = items.value.find(item => item.connectionId === connectionId)
      if (latest) editing.value = latest
    }
  } finally { saving.value = false }
}

async function savePolicy() {
  try { await apply({ resourcePolicy: parsePolicy(editPolicy.value) }) }
  catch (cause) { error.value = errorMessage(cause) }
}

async function rotateSecret() {
  if (!editSecret.value) { error.value = '请输入新密钥；留空不会覆盖旧密钥。'; return }
  await apply({ secret: editSecret.value })
  if (!error.value) editSecret.value = ''
}

async function toggleEnabled() {
  if (editing.value) await apply({ enabled: !editing.value.enabled })
}
</script>

<template>
  <main class="connections-page">
    <header class="connections-header"><div><router-link :to="{ name: 'workspace' }">← 返回对话</router-link><h1>连接设置</h1><p>登记外部服务凭据供你授权的工具使用。密钥只写不读，提交后页面只显示是否已保存。</p></div></header>
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <p v-if="notice" class="connections-notice" role="status">{{ notice }}</p>
    <section class="connections-create" aria-labelledby="create-heading">
      <h2 id="create-heading">新建连接</h2>
      <form @submit.prevent="create">
        <label class="field"><span>名称</span><input v-model="name" class="input" required maxlength="100" :disabled="saving" placeholder="例如：生产环境数据源" /></label>
        <label class="field"><span>提供方</span><input v-model="provider" class="input" required maxlength="80" :disabled="saving" pattern="[A-Za-z0-9][A-Za-z0-9._-]*" placeholder="例如：http-api" /></label>
        <label class="field"><span>环境</span><input v-model="environment" class="input" required maxlength="80" :disabled="saving" pattern="[A-Za-z0-9][A-Za-z0-9._-]*" placeholder="例如：staging" /></label>
        <label class="field"><span>资源策略（JSON 对象）</span><textarea v-model="policy" class="input" rows="5" spellcheck="false" :disabled="saving"></textarea></label>
        <label class="field"><span>密钥（只写不读）</span><input v-model="secret" class="input" type="password" required maxlength="16384" autocomplete="off" :disabled="saving" /></label>
        <button class="btn btn-primary" :disabled="saving || !name.trim() || !provider.trim() || !environment.trim() || !secret">{{ saving ? '提交中…' : '创建连接' }}</button>
      </form>
    </section>
    <div class="connections-columns">
      <section class="connections-list" aria-labelledby="list-heading">
        <h2 id="list-heading">我的连接</h2>
        <p v-if="loading && !items.length" role="status">正在加载…</p>
        <p v-else-if="!items.length">还没有连接。创建后可在授权工具时引用。</p>
        <button v-for="item in items" :key="item.connectionId" class="connection-item" :class="{ selected: editing?.connectionId === item.connectionId }" @click="select(item)">
          <strong>{{ item.name }}</strong>
          <span>{{ item.provider }} · {{ item.environment }} · {{ item.enabled ? '已启用' : '已停用' }}</span>
          <small>版本 {{ item.revision }} · 密钥{{ item.secretPresent ? '已保存' : '缺失' }}</small>
        </button>
        <button v-if="nextCursor" class="btn" :disabled="loading" @click="load(nextCursor)">{{ loading ? '加载中…' : '加载更多连接' }}</button>
      </section>
      <section class="connections-detail" aria-labelledby="detail-heading">
        <h2 id="detail-heading">{{ editing ? editing.name : '选择一个连接管理' }}</h2>
        <template v-if="editing">
          <p>{{ editing.provider }} · {{ editing.environment }} · 当前版本 {{ editing.revision }}</p>
          <p class="connections-hint">密钥只写不读：轮换时留空不会覆盖，页面不会回显任何明文。</p>
          <div class="connections-actions">
            <button class="btn btn-primary" :disabled="saving" @click="toggleEnabled">{{ editing.enabled ? '停用连接' : '启用连接' }}</button>
          </div>
          <label class="field"><span>资源策略（JSON 对象）</span><textarea v-model="editPolicy" class="input" rows="6" spellcheck="false" :disabled="saving"></textarea></label>
          <button class="btn" :disabled="saving" @click="savePolicy">{{ saving ? '保存中…' : '保存资源策略' }}</button>
          <label class="field"><span>轮换密钥（留空不改动）</span><input v-model="editSecret" class="input" type="password" maxlength="16384" autocomplete="off" :disabled="saving" /></label>
          <button class="btn" :disabled="saving || !editSecret" @click="rotateSecret">{{ saving ? '轮换中…' : '轮换密钥' }}</button>
        </template>
      </section>
    </div>
  </main>
</template>

<style scoped>
.connections-page { max-width: 1160px; margin: 0 auto; padding: 28px; }
.connections-header h1 { margin: 14px 0 6px; font-size: 26px; }
.connections-header p, .connections-create > p, .connections-hint { color: var(--text-secondary); font-size: 14px; }
.connections-header a { color: var(--accent); }
.connections-create, .connections-list, .connections-detail { background: var(--surface); border: 1px solid var(--border); padding: 22px; border-radius: 14px; margin-top: 20px; min-width: 0; }
.connections-page h2 { font-size: 18px; margin: 0 0 12px; }
.connections-create form { display: grid; gap: 12px; max-width: 520px; }
.connections-create .btn { justify-self: start; }
.connections-columns { display: grid; grid-template-columns: minmax(220px, 1fr) minmax(0, 2fr); gap: 20px; }
.connection-item { display: block; width: 100%; border: 1px solid var(--border); background: white; border-radius: 9px; padding: 14px; text-align: left; margin-bottom: 10px; overflow-wrap: anywhere; }
.connection-item.selected { border-color: var(--accent); background: var(--accent-soft); }
.connection-item strong { display: block; }
.connection-item span, .connection-item small { display: block; color: var(--text-secondary); font-size: 12px; margin-top: 3px; }
.connections-detail .field { margin-top: 14px; }
.connections-detail .btn + .field { margin-top: 16px; }
.connections-detail .btn { margin-right: 8px; }
.connections-actions { display: flex; flex-wrap: wrap; gap: 9px; }
.connections-notice { padding: 12px; color: #245a39; border: 1px solid #b5d2c0; border-radius: 8px; }
@media (max-width: 760px) { .connections-page { padding: 18px 12px; }.connections-columns { grid-template-columns: 1fr; gap: 0; }.connections-create, .connections-list, .connections-detail { padding: 16px; } }
</style>
