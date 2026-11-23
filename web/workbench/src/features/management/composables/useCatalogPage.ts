import { computed, ref, watch, type Ref } from 'vue'

/**
 * 配置目录的本地筛选与分页：目录数量有限，一次加载后在页面内按关键字、类型和状态过滤。
 * 查询条件点击“查询”后才生效，避免输入过程中列表频繁跳动。
 */
export interface CatalogFilters {
  keyword: string
  type: string
  status: string
}

export interface CatalogEntry {
  /** 参与关键字匹配的文本，如名称、编码、模型工具名 */
  texts: (string | null | undefined)[]
  type?: string
  status: string
}

export function useCatalogPage<T>(items: Ref<T[]>, describe: (item: T) => CatalogEntry) {
  const empty = (): CatalogFilters => ({ keyword: '', type: '', status: '' })
  const criteria = ref(empty())
  const applied = ref(empty())
  const page = ref(1)
  const size = ref(10)

  const filtered = computed(() => items.value.filter(item => {
    const entry = describe(item)
    const filters = applied.value
    const keywordMatched = !filters.keyword || entry.texts.some(text => text?.toLowerCase().includes(filters.keyword))
    const typeMatched = !filters.type || filters.type === entry.type
    const statusMatched = !filters.status || filters.status === entry.status
    return keywordMatched && typeMatched && statusMatched
  }))
  const total = computed(() => filtered.value.length)
  const pageCount = computed(() => Math.max(1, Math.ceil(total.value / size.value)))
  const rows = computed(() => filtered.value.slice((page.value - 1) * size.value, page.value * size.value))
  const filtering = computed(() => Boolean(applied.value.keyword || applied.value.type || applied.value.status))

  watch(pageCount, count => {
    page.value = Math.min(page.value, count)
  })

  function search() {
    applied.value = { ...criteria.value, keyword: criteria.value.keyword.trim().toLowerCase() }
    page.value = 1
  }

  function reset() {
    criteria.value = empty()
    search()
  }

  /** 类型页签等即时筛选：同时更新输入条件和生效条件。 */
  function applyType(type: string) {
    criteria.value.type = type
    applied.value = { ...applied.value, type }
    page.value = 1
  }

  return { criteria, applied, rows, total, page, size, pageCount, filtering, search, reset, applyType }
}
