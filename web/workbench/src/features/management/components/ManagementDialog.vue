<script setup lang="ts">
import { confirmManagement } from './confirmManagement'
import { nextTick, onMounted, onBeforeUnmount, ref } from 'vue'
const props = defineProps<{ title: string; busy: boolean; dirty: boolean }>()
const emit = defineEmits<{ close: [] }>()
const panel = ref<HTMLElement>()
let previous: HTMLElement | null = null
async function close() {
  if (props.busy || (props.dirty && !await confirmManagement('放弃尚未保存的修改？'))) return
  emit('close')
}
function keyboard(event: KeyboardEvent) {
  if (event.key === 'Escape') { event.preventDefault(); close() }
  if (event.key !== 'Tab' || !panel.value) return
  const controls = [...panel.value.querySelectorAll<HTMLElement>('button:not(:disabled),input:not(:disabled),textarea:not(:disabled),select:not(:disabled)')]
  const first = controls[0]; const last = controls[controls.length - 1]
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus() }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus() }
}
onMounted(async () => { previous = document.activeElement as HTMLElement; document.addEventListener('keydown', keyboard); await nextTick(); panel.value?.querySelector<HTMLElement>('input,button')?.focus() })
onBeforeUnmount(() => { document.removeEventListener('keydown', keyboard); previous?.focus() })
</script>
<template>
  <div class="management-overlay" @click.self="close"><section ref="panel" class="management-dialog" role="dialog" aria-modal="true" :aria-label="title" :aria-busy="busy">
    <header><h2>{{ title }}</h2><button aria-label="关闭" :disabled="busy" @click="close">×</button></header>
    <div class="management-dialog-content"><slot /></div>
    <footer><button :disabled="busy" @click="close">取消</button><slot name="actions" /></footer>
  </section></div>
</template>
