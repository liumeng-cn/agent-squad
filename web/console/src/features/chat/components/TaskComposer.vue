<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRuntimeStore } from '../stores/runtime'

const props = defineProps<{
  conversationId: string
  /** 当前会话已有未完成目标时禁止发送。 */
  blocked: boolean
  blockHint: string
}>()

const runtime = useRuntimeStore()
const draft = ref('')

watch(draft, () => {
  runtime.dropInflightKey()
})

async function send(): Promise<void> {
  const text = draft.value.trim()
  if (!text || props.blocked) {
    return
  }
  const ok = await runtime.send(props.conversationId, text)
  if (ok) {
    draft.value = ''
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
        <button
          v-if="runtime.resumable"
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
          :disabled="blocked || runtime.submitting || !draft.trim()"
          @click="send"
        >
          {{ runtime.submitting ? '发送中…' : '发送' }}
        </button>
      </div>
    </div>
  </div>
</template>
