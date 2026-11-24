// @vitest-environment jsdom
import { mount } from '@vue/test-utils'
import { ElPagination } from 'element-plus'
import { expect, it } from 'vitest'
import CatalogPagination from './CatalogPagination.vue'

it('resets to page one when changing page size and avoids a duplicate query', async () => {
  const view = mount(CatalogPagination, { props: { total: 80, pageCount: 8, page: 4, size: 10, busy: false } })
  view.findComponent(ElPagination).vm.$emit('update:page-size', 50)
  expect(view.emitted('update:size')?.[0]).toEqual([50])
  expect(view.emitted('update:page')?.[0]).toEqual([1])
  await view.setProps({ size: 50, page: 1, pageCount: 2 })
  view.findComponent(ElPagination).vm.$emit('update:current-page', 1)
  expect(view.emitted('change')).toHaveLength(1)
  view.unmount()
})
