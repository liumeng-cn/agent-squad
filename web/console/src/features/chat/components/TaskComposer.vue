<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRuntimeStore } from '../stores/runtime'
import { uploadArtifact, type Artifact } from '../../../shared/api/taskDetailsApi'
import { errorMessage } from '../../../shared/api/http'
import { newIdempotencyKey } from '../../../shared/lib/idempotency'

const props = defineProps<{
  conversationId: string
  /** 当前会话已有未完成目标时禁止发送。 */
  blocked: boolean
  blockHint: string
}>()

const runtime = useRuntimeStore()
const draft = ref('')
const artifacts = ref<Artifact[]>([])
const uploading = ref(false)
const uploadError = ref('')
const fileInput = ref<HTMLInputElement | null>(null)
let uploadAttempt: { file: File; key: string } | null = null

watch(() => props.conversationId, () => {
  draft.value = ''; artifacts.value = []; uploadError.value = ''; uploadAttempt = null
  runtime.dropInflightKey()
})

async function upload(file?: File): Promise<void> {
  if (!file || uploading.value) return
  if (file.size > 10 * 1024 * 1024 || artifacts.value.length >= 20) {
    uploadError.value = '最多 20 个附件，每个文件最大 10 MiB'; return
  }
  uploading.value = true; uploadError.value = ''
  const conversationId = props.conversationId
  if (uploadAttempt?.file !== file) uploadAttempt = { file, key: newIdempotencyKey() }
  try {
    const artifact = await uploadArtifact(file, uploadAttempt.key)
    if (conversationId !== props.conversationId) return
    artifacts.value.push(artifact)
    uploadAttempt = null; runtime.dropInflightKey()
    if (fileInput.value) fileInput.value.value = ''
  } catch (cause) { if (conversationId === props.conversationId) uploadError.value = errorMessage(cause) }
  finally { uploading.value = false }
}

watch(draft, () => {
  runtime.dropInflightKey()
})

async function send(): Promise<void> {
  const text = draft.value.trim()
  if (!text || props.blocked || uploading.value || uploadError.value) {
    return
  }
  const ok = await runtime.send(props.conversationId, text, artifacts.value.map(file => file.artifactId))
  if (ok) {
    draft.value = ''
    artifacts.value = []
  }
}

function onEnter(event: KeyboardEvent): void {
  if (!event.shiftKey) {
    event.preventDefault()
    void send()
  }
}
</script>

<template>
  <div class="composer-area">
    <p v-if="blocked" class="composer-hint">{{ blockHint }}</p>
    <p v-if="runtime.submitError" class="form-error" role="alert">{{ runtime.submitError }}</p>
    <p v-if="uploadError" class="form-error" role="alert">{{ uploadError }}
      <button class="btn btn-sm" @click="upload(uploadAttempt?.file)">重试上传</button>
      <button class="btn btn-sm" @click="uploadError = ''; uploadAttempt = null">取消附件</button>
    </p>
    <ul v-if="artifacts.length" class="attachment-list">
      <li v-for="file in artifacts" :key="file.artifactId">{{ file.fileName }} · {{ file.sizeBytes }} 字节
        <button class="btn btn-sm" :disabled="runtime.submitting" @click="artifacts = artifacts.filter(item => item.artifactId !== file.artifactId); runtime.dropInflightKey()">移除</button>
      </li>
    </ul>
    <div class="composer">
      <textarea
        v-model="draft"
        class="input"
        rows="3"
        placeholder="输入目标…"
        :disabled="blocked || runtime.submitting"
        @keydown.enter="onEnter"
      ></textarea>
      <div class="composer-actions">
        <label class="btn btn-sm">{{ uploading ? '上传中…' : '添加附件' }}
          <input ref="fileInput" type="file" accept=".txt,.md,.json,.csv" :disabled="blocked || uploading || runtime.submitting"
            @change="upload(($event.target as HTMLInputElement).files?.[0])" />
        </label>
        <button
          v-if="runtime.resumable && !runtime.snapshot?.pending.length"
          class="btn btn-primary"
          :disabled="runtime.resuming"
          @click="runtime.resume()"
        >
          {{ runtime.resuming ? '恢复中…' : '恢复执行' }}
        </button>
        <button
          v-if="runtime.stoppable"
          class="btn btn-danger"
          :disabled="runtime.stopping"
          @click="runtime.stop()"
        >
          {{ runtime.stopping ? '停止中…' : '停止' }}
        </button>
        <button
          class="btn btn-primary"
          :disabled="blocked || runtime.submitting || uploading || !!uploadError || !draft.trim()"
          @click="send"
        >
          {{ runtime.submitting ? '发送中…' : '发送' }}
        </button>
      </div>
    </div>
  </div>
</template>
