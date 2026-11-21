import { computed, ref, watch, type Ref } from 'vue'

type Filters = { keyword: string; type: string; status: string }

export function useCatalogPage<T>(
  items: Ref<T[]>,
  describe: (item: T) => { name: string; code: string; type?: string; status: string },
) {
  const empty = (): Filters => ({ keyword: '', type: '', status: '' })
  const criteria = ref(empty())
  const applied = ref(empty())
  const page = ref(1)
  const size = ref(10)
  const filtered = computed(() => items.value.filter(item => {
    const entry = describe(item)
    const filters = applied.value
    return (!filters.keyword || [entry.code, entry.name].some(text => text.toLowerCase().includes(filters.keyword)))
      && (!filters.type || filters.type === entry.type)
      && (!filters.status || filters.status === entry.status)
  }))
  const total = computed(() => filtered.value.length)
  const pageCount = computed(() => Math.max(1, Math.ceil(total.value / size.value)))
  const rows = computed(() => filtered.value.slice((page.value - 1) * size.value, page.value * size.value))
  watch(pageCount, count => { page.value = Math.min(page.value, count) })
  watch(size, () => { page.value = 1 })

  function search() {
    applied.value = { ...criteria.value, keyword: criteria.value.keyword.trim().toLowerCase() }
    page.value = 1
  }

  function reset() {
    criteria.value = empty()
    search()
  }

  return { criteria, rows, total, page, size, pageCount, search, reset }
}
