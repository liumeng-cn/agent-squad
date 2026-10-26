<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  changeWorkerStatusApi,
  listWorkerDefinitionsApi,
  publishWorkerDefinitionApi,
  type WorkerDefinitionDraft,
} from '../../../shared/api/workersApi'
import { errorMessage } from '../../../shared/api/http'
import type { WorkerDefinition } from '../../../shared/api/types'
import { newIdempotencyKey } from '../../../shared/lib/idempotency'

const router = useRouter()
const definitions = ref<WorkerDefinition[]>([])
const nextCursor = ref<string | null>(null)
const loading = ref(false)
const saving = ref(false)
const error = ref<string | null>(null)
const notice = ref<string | null>(null)
const workerKey = ref('')
const workerVersion = ref('1.0.0')
const description = ref('')
const inputSchema = ref('{"type":"object"}')
const outputSchema = ref('{"type":"object"}')
const toolAllowlist = ref('')
const modelProfileKey = ref('default')
const runtimeProfileKey = ref('default')

onMounted(() => { void load() })

async function load(cursor?: string): Promise<void> {
  if (loading.value) return
  loading.value = true
  error.value = null
  try {
    const page = await listWorkerDefinitionsApi(cursor)
    definitions.value = cursor ? [...definitions.value, ...page.items] : page.items
    nextCursor.value = page.nextCursor
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    loading.value = false
  }
}

async function publish(): Promise<void> {
  if (saving.value) return
  error.value = null
  notice.value = null
  let draft: WorkerDefinitionDraft
  try {
    draft = {
      workerKey: workerKey.value.trim(),
      workerVersion: workerVersion.value.trim(),
      description: description.value.trim(),
      inputSchema: JSON.parse(inputSchema.value) as unknown,
      outputSchema: JSON.parse(outputSchema.value) as unknown,
      toolAllowlist: toolAllowlist.value.split(',').map((item) => item.trim()).filter(Boolean),
      modelProfileKey: modelProfileKey.value.trim(),
      runtimeProfileKey: runtimeProfileKey.value.trim(),
    }
  } catch {
    error.value = '输入或输出 Schema 不是有效 JSON'
    return
  }
  saving.value = true
  try {
    await publishWorkerDefinitionApi(draft, newIdempotencyKey())
    notice.value = '能力版本已发布'
    await load()
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    saving.value = false
  }
}

async function changeStatus(definition: WorkerDefinition, status: WorkerDefinition['status']): Promise<void> {
  if (saving.value) return
  saving.value = true
  error.value = null
  notice.value = null
  try {
    const updated = await changeWorkerStatusApi(definition, status, newIdempotencyKey())
    definitions.value = definitions.value.map((item) =>
      item.definitionId === updated.definitionId ? updated : item,
    )
    notice.value = `${updated.workerKey}@${updated.workerVersion} 已更新为 ${updated.status}`
  } catch (cause) {
    await load()
    error.value = errorMessage(cause)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <main class="detail-page">
    <div class="detail-card">
      <button class="btn btn-sm" @click="router.push({ name: 'workspace' })">返回工作台</button>
      <h1>Worker 能力管理</h1>
      <p>发布后的能力正文不可修改；停用会阻止新任务选择，撤销后不能恢复。</p>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <p v-if="notice" role="status">{{ notice }}</p>
    </div>

    <form class="detail-card worker-form" @submit.prevent="publish">
      <h2>发布能力版本</h2>
      <label>能力键 <input v-model="workerKey" class="input" required maxlength="80" /></label>
      <label>版本 <input v-model="workerVersion" class="input" required maxlength="40" /></label>
      <label>描述 <textarea v-model="description" class="input" required maxlength="4000"></textarea></label>
      <label>输入 Schema <textarea v-model="inputSchema" class="input" required spellcheck="false"></textarea></label>
      <label>输出 Schema <textarea v-model="outputSchema" class="input" required spellcheck="false"></textarea></label>
      <label>允许工具（逗号分隔） <input v-model="toolAllowlist" class="input" /></label>
      <label>模型配置键 <input v-model="modelProfileKey" class="input" required /></label>
      <label>运行配置键 <input v-model="runtimeProfileKey" class="input" required /></label>
      <button class="btn btn-primary" type="submit" :disabled="saving">{{ saving ? '提交中…' : '发布' }}</button>
    </form>

    <div class="detail-card">
      <h2>已发布能力</h2>
      <p v-if="!loading && definitions.length === 0">暂无能力版本</p>
      <ul v-else class="event-log">
        <li v-for="item in definitions" :key="item.definitionId">
          <strong>{{ item.workerKey }}@{{ item.workerVersion }}</strong> · {{ item.status }}
          <p>{{ item.description }}</p>
          <button v-if="item.status === 'ENABLED'" class="btn btn-sm" :disabled="saving" @click="changeStatus(item, 'DISABLED')">停用</button>
          <button v-if="item.status === 'DISABLED'" class="btn btn-sm" :disabled="saving" @click="changeStatus(item, 'ENABLED')">启用</button>
          <button v-if="item.status !== 'REVOKED'" class="btn btn-sm btn-danger" :disabled="saving" @click="changeStatus(item, 'REVOKED')">撤销</button>
        </li>
      </ul>
      <button v-if="nextCursor" class="btn btn-sm" :disabled="loading" @click="load(nextCursor)">加载更多</button>
    </div>
  </main>
</template>

<style scoped>
.worker-form {
  display: grid;
  gap: 12px;
}

.worker-form label {
  display: grid;
  gap: 6px;
}

.worker-form textarea {
  min-height: 92px;
}
</style>
