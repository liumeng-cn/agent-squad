<script setup lang="ts">
/**
 * 列表分页：总数、每页条数与上一页下一页，供本地分页和服务端分页的列表共用。
 */
defineProps<{ total: number; pageCount: number; busy: boolean }>()
const page = defineModel<number>('page', { required: true })
const size = defineModel<number>('size', { required: true })
const emit = defineEmits<{ change: [] }>()

function go(delta: number) {
  page.value += delta
  emit('change')
}

function resize(event: Event) {
  size.value = Number((event.target as HTMLSelectElement).value)
  page.value = 1
  emit('change')
}
</script>

<template>
  <nav class="management-pagination" aria-label="列表分页">
    <span class="management-pagination-total">共 {{ total }} 条</span>
    <label>
      每页
      <select :value="size" :disabled="busy" aria-label="每页条数" @change="resize">
        <option :value="10">10 条</option>
        <option :value="20">20 条</option>
        <option :value="50">50 条</option>
      </select>
    </label>
    <button type="button" aria-label="上一页" :disabled="busy || page <= 1" @click="go(-1)">上一页</button>
    <span>{{ page }} / {{ pageCount }}</span>
    <button type="button" aria-label="下一页" :disabled="busy || page >= pageCount" @click="go(1)">下一页</button>
  </nav>
</template>
