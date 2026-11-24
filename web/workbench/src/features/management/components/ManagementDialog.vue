<script setup lang="ts">
import { ElButton, ElConfigProvider, ElDialog } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { confirmManagement } from './confirmManagement'

/** 管理弹窗统一使用 Element Plus，关闭前保留未保存确认与提交期间保护。 */
const props = defineProps<{
  title: string
  busy: boolean
  dirty: boolean
  wide?: boolean
}>()
const emit = defineEmits<{ close: [] }>()

async function close() {
  if (props.busy) return
  if (props.dirty && !(await confirmManagement('放弃尚未保存的修改？'))) return
  emit('close')
}
</script>

<template>
  <ElDialog
    :model-value="true"
    :title="title"
    :width="wide ? '1000px' : '600px'"
    class="management-platform management-element-dialog"
    :before-close="close"
    :show-close="!busy"
    :close-on-click-modal="!busy"
    :close-on-press-escape="!busy"
    append-to-body
    align-center
    destroy-on-close
  >
    <ElConfigProvider :locale="zhCn"
      ><div class="management-dialog-content" :aria-busy="busy"><slot /></div
    ></ElConfigProvider>
    <template #footer>
      <ElButton :disabled="busy" @click="close">取消</ElButton>
      <slot name="actions" />
    </template>
  </ElDialog>
</template>
