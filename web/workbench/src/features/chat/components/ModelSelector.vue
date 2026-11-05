<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { modelOptions, type ModelOption, type ModelSelection, type ReasoningMode } from '../../../shared/api/modelsApi'
const props = defineProps<{ modelValue: ModelSelection | null; disabled?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [selection: ModelSelection] }>()
const root = ref<HTMLElement | null>(null)
const open = ref(false)
const loading = ref(false)
const error = ref(false)
const models = ref<ModelOption[]>([])
const current = computed(() => models.value.find(model => model.modelId === props.modelValue?.modelId))
const modeText: Record<ReasoningMode, string> = { DEFAULT: '默认', OFF: '快速', AUTO: '自动', ON: '深入', LOW: '轻度', MEDIUM: '标准', HIGH: '深入' }
const capabilityText = { REASONING: '推理', TEXT_GENERATION: '生成', CODING: '编程', TOOL_USE: '工具', VISION: '视觉' }
const modeIndex = computed(() => Math.max(0, current.value?.reasoningModes.indexOf(props.modelValue?.reasoningMode ?? 'DEFAULT') ?? 0))
function select(model: ModelOption) {
  emit('update:modelValue', { modelId: model.modelId, reasoningMode: model.defaultReasoningMode })
}
function mode(event: Event) {
  const model = current.value
  const selected = model?.reasoningModes[Number((event.target as HTMLInputElement).value)]
  if (model && selected) emit('update:modelValue', { modelId: model.modelId, reasoningMode: selected })
}
async function load() {
  loading.value = true; error.value = false
  try {
    models.value = await modelOptions()
    if (!models.value.some(model => model.modelId === props.modelValue?.modelId && model.reasoningModes.includes(props.modelValue.reasoningMode))) {
      const initial = models.value.find(model => model.modelId === 'default') ?? models.value[0]
      if (initial) select(initial)
    }
  } catch { error.value = true }
  finally { loading.value = false }
}
function outside(event: PointerEvent) { if (!root.value?.contains(event.target as Node)) open.value = false }
onMounted(() => { void load(); document.addEventListener('pointerdown', outside) })
onBeforeUnmount(() => document.removeEventListener('pointerdown', outside))
</script>
<template>
  <div ref="root" class="model-selector" @keydown.esc="open = false">
    <button type="button" class="model-selector-trigger" aria-label="选择模型与推理强度" :aria-expanded="open" :disabled="disabled" @click="open = !open">
      {{ current?.displayName ?? (loading ? '读取模型…' : '选择模型') }}<span v-if="modelValue && current && current.reasoningModes.length > 1"> · {{ modeText[modelValue.reasoningMode] }}</span><svg class="model-selector-chevron" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m5 6.5 3 3 3-3" /></svg>
    </button>
    <div v-if="open" class="model-selector-popup" role="dialog" aria-label="模型与推理强度">
      <p v-if="error" class="model-selector-help">暂时无法读取模型 <button type="button" class="text-button" @click="load">重试</button></p>
      <p v-else-if="!loading && !models.length" class="model-selector-help">暂无可用模型</p>
      <div class="model-choice-list" role="group" aria-label="选择模型">
        <button v-for="model in models" :key="model.modelId" type="button" :aria-pressed="model.modelId === modelValue?.modelId" @click="select(model)">{{ model.displayName }}<small v-if="model.capabilities.length">{{ model.capabilities.map(item => capabilityText[item]).join(' · ') }}</small><span v-if="model.modelId === modelValue?.modelId" aria-hidden="true">✓</span></button>
      </div>
      <div v-if="current && current.reasoningModes.length > 1" class="model-reasoning-control">
        <div><span>推理强度</span><strong>{{ modeText[modelValue?.reasoningMode ?? current.defaultReasoningMode] }}</strong></div>
        <input type="range" aria-label="推理强度" min="0" :max="current.reasoningModes.length - 1" step="1" :value="modeIndex" @input="mode" />
        <div class="model-reasoning-labels"><span v-for="item in current.reasoningModes" :key="item">{{ modeText[item] }}</span></div>
      </div>
      <p v-if="current" class="model-selector-help">下一条消息使用此设置</p>
    </div>
  </div>
</template>
