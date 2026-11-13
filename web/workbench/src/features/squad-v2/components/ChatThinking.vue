<script setup lang="ts">
import { computed, ref } from 'vue'
import type { ChatMessage } from '../chatTypes'
const props = defineProps<{ message: ChatMessage }>()
const preference = ref<boolean>()
const expanded = computed(() => preference.value ?? props.message.status === 'RUNNING')
</script>

<template>
  <section v-if="message.thinkingBlocks?.length" class="thinking" aria-label="模型思考内容">
    <button class="heading" :aria-expanded="expanded" @click="preference = !expanded">
      <svg class="bulb" viewBox="0 0 24 24" fill="none" aria-hidden="true">
        <path d="M9 18h6m-5 3h4M8 14a6 6 0 1 1 8 0c-1 .8-1 1.5-1 2H9c0-.5 0-1.2-1-2Z" />
      </svg>
      <span>模型思考</span>
      <span :class="['chevron', { expanded }]" aria-hidden="true">
        <svg viewBox="0 0 24 24" fill="none"><path d="m7 10 5 5 5-5" /></svg>
      </span>
    </button>
    <div v-if="expanded" class="thinking-content">
      <p v-for="block in message.thinkingBlocks" :key="block.modelCallId">{{ block.content }}</p>
    </div>
  </section>
</template>

<style scoped>
.thinking { margin: 0 0 22px; color: #85877e; font-size: 14px; }
.heading { display: flex; align-items: center; gap: 8px; min-height: 24px; padding: 0; border: 0; background: none; color: inherit; font: inherit; line-height: 20px; cursor: pointer; }
.bulb { display: block; width: 16px; height: 16px; flex: 0 0 16px; stroke: currentColor; stroke-width: 1.6; stroke-linecap: round; stroke-linejoin: round; }
.chevron { display: inline-flex; align-items: center; justify-content: center; width: 16px; height: 16px; flex: 0 0 16px; transition: transform .16s ease; }
.chevron svg { display: block; width: 14px; height: 14px; stroke: currentColor; stroke-width: 1.7; stroke-linecap: round; stroke-linejoin: round; }
.chevron.expanded { transform: rotate(180deg); }
.thinking-content { margin: 12px 0 0 7px; padding-left: 16px; border-left: 1px solid #e8e9e4; line-height: 1.8; overflow-wrap: anywhere; white-space: pre-wrap; }
p { margin: 0; }
p + p { padding-top: 16px; }
@media (prefers-reduced-motion: reduce) { .chevron { transition: none; } }
</style>
