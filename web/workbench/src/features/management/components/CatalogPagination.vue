<script setup lang="ts">
defineProps<{ total: number; pageCount: number; busy: boolean }>()
const page = defineModel<number>('page', { required: true })
const size = defineModel<number>('size', { required: true })
</script>

<template>
  <nav class="management-pagination catalog-pagination" aria-label="列表分页">
    <span class="catalog-total">共 {{ total }} 条</span>
    <label>每页
      <select v-model.number="size" :disabled="busy" aria-label="每页条数">
        <option :value="10">10 条</option><option :value="20">20 条</option><option :value="50">50 条</option>
      </select>
    </label>
    <button :disabled="busy || page <= 1" @click="page--">上一页</button>
    <span>{{ page }} / {{ pageCount }}</span>
    <button :disabled="busy || page >= pageCount" @click="page++">下一页</button>
  </nav>
</template>
