<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ApiError, errorMessage } from '../../../shared/api/http'
import { getSkill, importSkill, listSkills, revokeSkill, setSkillGrant, type SkillDetail, type SkillFile, type SkillVersion } from '../../../shared/api/skillsApi'

const items = ref<SkillVersion[]>([])
const nextCursor = ref<string | null>(null)
const detail = ref<SkillDetail | null>(null)
const selectedPath = ref('SKILL.md')
const draftFiles = ref<SkillFile[]>([])
const pastedSkill = ref('')
const importInput = ref<HTMLInputElement | null>(null)
const loading = ref(false)
const reading = ref(false)
const saving = ref(false)
const error = ref('')
const notice = ref('')
let selectionRevision = 0
let uploadRevision = 0
const preview = computed(() => detail.value?.files.find(file => file.path === selectedPath.value)?.content ?? '')
const draftEntry = computed(() => draftFiles.value.find(file => file.path === 'SKILL.md')?.content ?? '')
const statusLabel = (status: SkillVersion['status']) => ({ ENABLED: '已启用', DISABLED: '未启用', REVOKED: '已安全撤销' })[status]

onMounted(() => { void load() })
onBeforeUnmount(() => { selectionRevision++; uploadRevision++ })

async function load(append = false) {
  if (loading.value) return
  loading.value = true
  try {
    const page = await listSkills(append ? nextCursor.value ?? undefined : undefined)
    items.value = append ? [...items.value, ...page.items] : page.items
    nextCursor.value = page.nextCursor ?? null
  } catch (cause) { error.value = errorMessage(cause) }
  finally { loading.value = false }
}
async function inspect(id: string) {
  const revision = ++selectionRevision
  error.value = ''; detail.value = null
  try {
    const result = await getSkill(id)
    if (revision !== selectionRevision) return
    detail.value = result; selectedPath.value = 'SKILL.md'
  } catch (cause) { if (revision === selectionRevision) error.value = errorMessage(cause) }
}
async function selectFiles(event: Event) {
  const files = [...((event.target as HTMLInputElement).files ?? [])]
  const revision = ++uploadRevision
  draftFiles.value = []; error.value = ''; notice.value = ''
  if (importInput.value) importInput.value.value = ''
  if (!files.length) return
  if (files.length > 64) { error.value = '技能包最多包含 64 个文件'; return }
  reading.value = true
  try {
    const result: SkillFile[] = []
    let total = 0
    for (const file of files) {
      const relative = file.webkitRelativePath || file.name
      const path = file.webkitRelativePath ? relative.split('/').slice(1).join('/') : relative
      if (file.size > 262144) throw new Error(`${path}：单文件最大 256 KiB`)
      total += file.size
      if (total > 1048576) throw new Error(`${path}：技能包总大小最大 1 MiB`)
      let content: string
      try { content = new TextDecoder('utf-8', { fatal: true }).decode(await file.arrayBuffer()) }
      catch { throw new Error(`${path}：文件必须为 UTF-8 文本`) }
      if (content.includes('\0')) throw new Error(`${path}：不能导入二进制文件`)
      result.push({ path, content })
    }
    if (!result.some(file => file.path === 'SKILL.md')) throw new Error('缺少根目录 SKILL.md，请选择技能所在文件夹')
    if (revision === uploadRevision) draftFiles.value = result
  } catch (cause) { if (revision === uploadRevision) error.value = errorMessage(cause) }
  finally { if (revision === uploadRevision) reading.value = false }
}
function previewPastedSkill() {
  error.value = ''; notice.value = ''
  if (!pastedSkill.value.trim()) { error.value = '请先粘贴 SKILL.md 内容'; return }
  if (new TextEncoder().encode(pastedSkill.value).length > 262144) { error.value = 'SKILL.md 最大 256 KiB'; return }
  draftFiles.value = [{ path: 'SKILL.md', content: pastedSkill.value }]
}
async function publish() {
  if (saving.value || !draftFiles.value.length) return
  saving.value = true; error.value = ''; notice.value = ''
  try {
    const skill = await importSkill(draftFiles.value)
    draftFiles.value = []; pastedSkill.value = ''; notice.value = '版本已导入，启用后新对话执行可按需使用。'
    await load(); await inspect(skill.skillVersionId)
  } catch (cause) { error.value = errorMessage(cause) }
  finally { saving.value = false }
}
async function changeGrant(enabled: boolean) {
  if (!detail.value || saving.value) return
  const selected = detail.value
  saving.value = true; error.value = ''; notice.value = ''
  try {
    await setSkillGrant(selected, enabled)
    notice.value = enabled ? '已加入你的可用技能目录。' : '已停用，新执行不再选择；在途执行仍使用固定版本。'
    await inspect(selected.skill.skillVersionId); await load()
  } catch (cause) {
    error.value = errorMessage(cause)
    if (cause instanceof ApiError && cause.status === 409) {
      const message = error.value; await inspect(selected.skill.skillVersionId); error.value = message
    }
  } finally { saving.value = false }
}
async function revoke() {
  if (!detail.value || saving.value) return
  if (!window.confirm('安全撤销后不能重新启用，在途执行也将停止读取此版本。确认撤销？')) return
  const selected = detail.value
  saving.value = true; error.value = ''; notice.value = ''
  try {
    detail.value = await revokeSkill(selected)
    notice.value = '已安全撤销；引用此版本的在途执行将进入受限状态。'
    await load()
  } catch (cause) { error.value = errorMessage(cause) }
  finally { saving.value = false }
}
</script>

<template>
  <main class="skills-page">
    <header class="skills-header"><div><router-link :to="{ name: 'workspace' }">← 返回对话</router-link><h1>我的技能</h1><p>为你的回复和后台工作提供可复用的知识与流程。技能不会自动取得工具或脚本权限。</p></div></header>
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <p v-if="notice" class="skills-notice" role="status">{{ notice }}</p>
    <section class="skills-import" aria-labelledby="import-heading">
      <h2 id="import-heading">导入技能</h2>
      <p>选择含 SKILL.md 的文本文件夹。最多 64 个文件，单文件 256 KiB、总量 1 MiB；导入后需明确启用。</p>
      <input id="skill-package-files" ref="importInput" type="file" webkitdirectory multiple :disabled="saving || reading" @change="selectFiles" />
      <label for="skill-package-files" class="skills-file-label">选择技能文件夹</label>
      <span v-if="reading" role="status">正在检查文件…</span>
      <details class="skills-paste"><summary>或直接粘贴 SKILL.md</summary>
        <label class="field"><span>技能 Markdown 内容</span><textarea v-model="pastedSkill" class="input" rows="8" aria-label="技能 Markdown 内容" placeholder="---&#10;name: my-skill&#10;description: 说明何时使用这个技能&#10;version: 1.0.0&#10;---&#10;写下具体步骤与要求…" :disabled="saving || reading"></textarea></label>
        <button class="btn" :disabled="saving || reading || !pastedSkill.trim()" @click="previewPastedSkill">预览粘贴内容</button>
      </details>
      <div v-if="draftFiles.length" class="skills-preview">
        <h3>导入预览 · {{ draftFiles.length }} 个文件</h3>
        <ul><li v-for="file in draftFiles" :key="file.path">{{ file.path }}</li></ul>
        <pre aria-label="待导入技能入口正文">{{ draftEntry }}</pre>
        <button class="btn btn-primary" :disabled="saving" @click="publish">{{ saving ? '正在导入…' : '导入不可变版本' }}</button>
        <button class="btn" :disabled="saving" @click="draftFiles = []">取消导入</button>
      </div>
    </section>
    <div class="skills-columns">
      <section class="skills-list" aria-labelledby="list-heading">
        <h2 id="list-heading">个人版本目录</h2>
        <p v-if="loading && !items.length" role="status">正在加载…</p>
        <p v-else-if="!items.length">还没有导入技能。</p>
        <button v-for="item in items" :key="item.skillVersionId" class="skill-item" :class="{ selected: detail?.skill.skillVersionId === item.skillVersionId }" @click="inspect(item.skillVersionId)"><strong>{{ item.name }} <small>{{ item.version }}</small></strong><span>{{ statusLabel(item.status) }}</span><p>{{ item.description }}</p></button>
        <button v-if="nextCursor" class="btn" :disabled="loading" @click="load(true)">{{ loading ? '加载中…' : '加载更多版本' }}</button>
      </section>
      <section class="skills-detail" aria-labelledby="detail-heading">
        <h2 id="detail-heading">{{ detail ? detail.skill.name : '选择一个技能查看详情' }}</h2>
        <template v-if="detail">
          <p>{{ detail.skill.description }}</p><p>版本 {{ detail.skill.version }} · {{ statusLabel(detail.skill.status) }}</p>
          <p v-if="detail.skill.status === 'REVOKED'">此版本已永久撤销，旧执行也不能再读取。</p>
          <div v-else class="skills-actions"><button class="btn btn-primary" :disabled="saving" @click="changeGrant(!detail.grant.enabled)">{{ detail.grant.enabled ? '停用此版本' : '启用此版本' }}</button><button class="btn btn-danger" :disabled="saving" @click="revoke">安全撤销</button></div>
          <p class="skills-hint">同名技能启用多个版本时，新执行选择最后导入的启用版本；旧执行的固定版本不会随之改变。</p>
          <label class="field"><span>查看包内文件</span><select v-model="selectedPath" class="input"><option v-for="file in detail.files" :key="file.path" :value="file.path">{{ file.path }}</option></select></label>
          <pre aria-label="技能文件正文">{{ preview }}</pre>
          <details><summary>内容摘要</summary><code>{{ detail.skill.contentHash }}</code></details>
        </template>
      </section>
    </div>
  </main>
</template>

<style scoped>
.skills-page { max-width: 1160px; margin: 0 auto; padding: 28px; }
.skills-header h1 { margin: 14px 0 6px; font-size: 26px; }
.skills-header p, .skills-import > p, .skills-hint { color: var(--text-secondary); font-size: 14px; }
.skills-header a { color: var(--accent); }
.skills-import, .skills-list, .skills-detail { background: var(--surface); border: 1px solid var(--border); padding: 22px; border-radius: 14px; margin-top: 20px; min-width: 0; }
.skills-page h2 { font-size: 18px; margin: 0 0 12px; }
.skills-columns { display: grid; grid-template-columns: minmax(220px, 1fr) minmax(0, 2fr); gap: 20px; }
.skill-item { width: 100%; border: 1px solid var(--border); background: white; border-radius: 9px; padding: 14px; text-align: left; margin-bottom: 10px; overflow-wrap: anywhere; }
.skill-item.selected { border-color: var(--accent); background: var(--accent-soft); }
.skill-item strong { display: block; }.skill-item small, .skill-item span { color: var(--text-secondary); font-size: 12px; }.skill-item p { font-size: 13px; margin-bottom: 0; }
.skills-actions { display: flex; flex-wrap: wrap; gap: 9px; }
.skills-page pre { max-height: 350px; overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; padding: 16px; background: var(--bg); border-radius: 8px; font-size: 13px; }
.skills-page code { overflow-wrap: anywhere; font-size: 12px; }
.skills-notice { padding: 12px; color: #245a39; border: 1px solid #b5d2c0; border-radius: 8px; }
.skills-paste { margin-top: 14px; }.skills-paste summary { cursor: pointer; margin-bottom: 10px; }.skills-paste textarea { resize: vertical; }
.skills-file-label { display: block; font-size: 13px; margin-top: 5px; }.skills-preview .btn { margin-right: 8px; }
@media (max-width: 760px) { .skills-page { padding: 18px 12px; }.skills-columns { grid-template-columns: 1fr; gap: 0; }.skills-import, .skills-list, .skills-detail { padding: 16px; } }
</style>
