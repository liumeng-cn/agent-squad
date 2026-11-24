<script setup lang="ts">
import { ElPagination } from 'element-plus'

/** 本地与服务端列表共用分页，调整每页条数时回到第一页并且只触发一次查询。 */
defineProps<{ total: number; pageCount: number; busy: boolean }>()
const page = defineModel<number>('page', { required: true })
const size = defineModel<number>('size', { required: true })
const emit = defineEmits<{ change: [] }>()

function changePage(value: number) {
  if (page.value === value) return
  page.value = value
  emit('change')
}

function changeSize(value: number) {
  if (size.value === value) return
  size.value = value
  page.value = 1
  emit('change')
}
</script>

<template>
  <nav class="management-pagination" aria-label="列表分页">
    <ElPagination
      :current-page="page"
      :page-size="size"
      :total="total"
      :page-sizes="[10, 20, 50]"
      :disabled="busy"
      layout="total, sizes, prev, pager, next, jumper"
      background
      @update:current-page="changePage"
      @update:page-size="changeSize"
    />
  </nav>
</template>
