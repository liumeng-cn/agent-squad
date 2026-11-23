<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { confirmManagement } from './confirmManagement'

/**
 * 管理平台通用弹窗：遮罩点击、Esc 与关闭按钮统一走未保存确认，并把焦点限制在弹窗内。
 * 遮罩层带 management-platform，单独挂载时也能继承设计令牌和控件样式。
 */
const props = defineProps<{ title: string; busy: boolean; dirty: boolean; wide?: boolean }>()
const emit = defineEmits<{ close: [] }>()

const panel = ref<HTMLElement>()
let previous: HTMLElement | null = null

async function close() {
  if (props.busy) {
    return
  }
  if (props.dirty && !await confirmManagement('放弃尚未保存的修改？')) {
    return
  }
  emit('close')
}

function keyboard(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    event.preventDefault()
    void close()
    return
  }
  if (event.key !== 'Tab' || !panel.value) {
    return
  }
  const controls = [...panel.value.querySelectorAll<HTMLElement>(
    'button:not(:disabled),input:not(:disabled),textarea:not(:disabled),select:not(:disabled)',
  )]
  const first = controls[0]
  const last = controls[controls.length - 1]
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last?.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first?.focus()
  }
}

onMounted(async () => {
  previous = document.activeElement as HTMLElement | null
  document.addEventListener('keydown', keyboard)
  await nextTick()
  panel.value?.querySelector<HTMLElement>('input:not(:disabled),textarea:not(:disabled),select:not(:disabled),button')?.focus()
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', keyboard)
  previous?.focus()
})
</script>

<template>
  <div class="management-platform management-overlay" @click.self="close">
    <section
      ref="panel"
      class="management-dialog"
      :class="{ 'management-dialog-wide': wide }"
      role="dialog"
      aria-modal="true"
      :aria-label="title"
      :aria-busy="busy"
    >
      <header>
        <h2>{{ title }}</h2>
        <button type="button" aria-label="关闭" :disabled="busy" @click="close">×</button>
      </header>
      <div class="management-dialog-content">
        <slot />
      </div>
      <footer>
        <button type="button" :disabled="busy" @click="close">取消</button>
        <slot name="actions" />
      </footer>
    </section>
  </div>
</template>
