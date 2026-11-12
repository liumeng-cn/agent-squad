<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'

const props = defineProps<{ busy: boolean }>()

const emit = defineEmits<{
  (event: 'submit', message: string): void
  (event: 'disconnect'): void
}>()

const draft = ref('')
const textarea = ref<HTMLTextAreaElement | null>(null)
const canSubmit = computed(() => draft.value.trim().length > 0 && !props.busy)

function resize(): void {
  const element = textarea.value
  if (!element) return
  element.style.height = 'auto'
  const maxHeight = 180
  element.style.height = Math.min(element.scrollHeight, maxHeight) + 'px'
  element.style.overflowY = element.scrollHeight > maxHeight ? 'auto' : 'hidden'
}

function submit(): void {
  const message = draft.value.trim()
  if (!message || props.busy) return
  emit('submit', message)
  draft.value = ''
  void nextTick(resize)
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key !== 'Enter' || event.shiftKey || event.isComposing || props.busy) return
  event.preventDefault()
  submit()
}

watch(draft, () => { void nextTick(resize) })
</script>

<template>
  <footer class="composer-wrap">
    <form class="composer" @submit.prevent="submit">
      <textarea
        ref="textarea"
        v-model="draft"
        rows="1"
        maxlength="12000"
        :disabled="busy"
        :placeholder="busy ? '正在接收模拟事件…' : '输入测试说明（仅用于页面展示）…'"
        @input="resize"
        @keydown="onKeydown"
      />
      <div class="composer-toolbar">
        <div class="composer-hints">
          <span>Enter 发送</span>
          <span class="hint-separator">·</span>
          <span>Shift + Enter 换行</span>
          <span v-if="draft.length" class="character-count">
            {{ draft.length.toLocaleString() }} / 12,000
          </span>
        </div>
        <button
          v-if="busy"
          type="button"
          class="send-button disconnect-button"
          aria-label="断开事件流"
          title="只断开当前 SSE 连接，不会取消服务端 Agent 执行"
          @click="emit('disconnect')"
        >×</button>
        <button
          v-else
          type="submit"
          class="send-button"
          :disabled="!canSubmit"
          aria-label="运行模拟事件"
          title="运行模拟事件"
        >↑</button>
      </div>
    </form>
    <small>当前为 SSE 联调页面，输入内容仅用于展示；后端返回预设的并行 Agent 模拟事件。</small>
  </footer>
</template>

<style scoped>
.composer-wrap{width:min(900px,calc(100% - 40px));margin:0 auto;padding:10px 0 17px;text-align:center;flex:none}
.composer{display:flex;flex-direction:column;gap:8px;border:1px solid #d9d9d9;border-radius:19px;padding:12px 12px 10px 16px;background:var(--surface,#fff);box-shadow:0 3px 14px #00000008;transition:border-color .18s ease,box-shadow .18s ease}
.composer:focus-within{border-color:#b8b8b8;box-shadow:0 3px 16px #0000000d}
.composer textarea{display:block;width:100%;min-height:28px;max-height:180px;resize:none;overflow-y:hidden;border:0;outline:0;font:inherit;font-size:14px;line-height:1.65;color:inherit;background:transparent;padding:2px 0;box-sizing:border-box}
.composer textarea::placeholder{color:#aaa}
.composer-toolbar{display:flex;align-items:center;justify-content:space-between;gap:12px}
.composer-hints{display:flex;align-items:center;gap:6px;min-width:0;color:#a0a0a0;font-size:10px}
.hint-separator{color:#ccc}
.character-count{margin-left:8px;color:#aaa;font-variant-numeric:tabular-nums}
.send-button{width:35px;height:35px;flex:none;border:0;border-radius:50%;background:#242424;color:#fff;font-size:21px;line-height:1;cursor:pointer;display:grid;place-items:center;transition:background .16s ease,transform .16s ease}
.send-button:not(:disabled):hover{background:#444;transform:translateY(-1px)}
.send-button:focus-visible{outline:2px solid #777;outline-offset:3px}
.send-button:disabled{background:#e4e4e4;color:#fff;cursor:not-allowed}
.disconnect-button{font-size:22px;background:#555}
.composer-wrap>small{display:block;color:#aaa;font-size:10px;margin-top:8px}
@media(max-width:600px){.composer-wrap{width:calc(100% - 28px)}.composer{padding:10px 10px 9px 13px}.composer textarea{font-size:14px}.composer-hints{font-size:9px}}
</style>
