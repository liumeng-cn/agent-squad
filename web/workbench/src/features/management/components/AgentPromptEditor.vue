<script setup lang="ts">
const prompt = defineModel<string>({ required: true })
defineProps<{ readOnly: boolean }>()
</script>

<template>
  <section class="catalog-prompt">
    <div class="catalog-prompt-heading">
      <label for="agent-system-prompt">系统提示词</label>
      <span class="management-muted">{{ prompt.length }} / 20000</span>
    </div>
    <textarea id="agent-system-prompt" v-model="prompt" :readonly="readOnly"
              required maxlength="20000" rows="10" placeholder="编写 Agent 的角色、任务和回答要求" />
    <slot name="validation" :prompt="prompt" :read-only="readOnly" />
  </section>
</template>
