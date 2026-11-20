<script setup lang="ts">
import type { ChatInteraction } from '../model/chatTypes'

const props = defineProps<{
  interaction: ChatInteraction
  modelValue: string
  submitting: boolean
  disabled: boolean
  locked: boolean
  submitAnswer: (requestId: string, answer: string) => Promise<boolean>
}>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
function submit() {
  if (!props.disabled && !props.submitting && props.modelValue.trim()) {
    void props.submitAnswer(props.interaction.requestId, props.modelValue)
  }
}
</script>

<template>
  <form class="interaction-card" aria-label="待答问题" @submit.prevent="submit">
    <span class="interaction-label">需要你的回答</span>
    <p class="question">{{ interaction.question }}</p>
    <div v-if="interaction.options.length" class="options" role="group" aria-label="建议答案">
      <button v-for="(option, index) in interaction.options" :key="index" type="button"
        :aria-pressed="modelValue === option" :disabled="disabled || submitting || locked"
        @click="emit('update:modelValue', option)">{{ option }}</button>
    </div>
    <label :for="`answer-${interaction.requestId}`">你的回答</label>
    <textarea :id="`answer-${interaction.requestId}`" :value="modelValue" rows="2" maxlength="4000"
      placeholder="选择上方答案，或输入你的想法" :disabled="disabled || submitting || locked"
      @input="emit('update:modelValue', ($event.target as HTMLTextAreaElement).value)" />
    <div class="interaction-footer">
      <span>{{ locked ? '原答案已保留，可重试提交' : '提交后将继续本轮任务' }}</span>
      <button type="submit" :disabled="disabled || submitting || !modelValue.trim()">
        {{ submitting ? '提交中…' : locked ? '重试提交' : '提交回答' }}
      </button>
    </div>
  </form>
</template>

<style scoped>
.interaction-card{padding:18px 20px;margin:0 0 12px;border:1px solid var(--input-border,#d9ded5);border-radius:16px;background:var(--surface,#fff)}.interaction-label{font-size:12px;color:var(--text-secondary,#68715f)}.question{white-space:pre-wrap;overflow-wrap:anywhere;margin:8px 0 14px;line-height:1.7;color:var(--text,#20251e)}.options{display:flex;flex-wrap:wrap;gap:8px;margin-bottom:14px}.options button{padding:7px 12px;border:1px solid var(--input-border,#d9ded5);border-radius:12px;background:transparent;color:var(--text,#20251e);font:inherit;overflow-wrap:anywhere}.options button[aria-pressed=true]{background:#eaf0e5;border-color:#8c9c80}.interaction-card label{display:block;font-size:12px;margin-bottom:6px;color:var(--text-secondary,#68715f)}textarea{display:block;box-sizing:border-box;width:100%;padding:10px 12px;resize:vertical;border:1px solid var(--input-border,#d9ded5);border-radius:10px;background:var(--surface,#fff);color:var(--text,#20251e);font:inherit;line-height:1.6}.interaction-footer{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-top:12px}.interaction-footer span{font-size:12px;color:var(--text-secondary,#68715f)}.interaction-footer button{flex-shrink:0;padding:8px 14px;border:0;border-radius:10px;background:#35472c;color:#fff;font:inherit}button{cursor:pointer}button:disabled,textarea:disabled{opacity:.6;cursor:default}
</style>
