// @vitest-environment jsdom
import { mount } from '@vue/test-utils'
import { expect, it } from 'vitest'
import AuthorizationTree from './AuthorizationTree.vue'
import type { Menu } from '../model/management'
const page: Menu = { menuId: 'roles', parentMenuId: null, code: 'roles', name: '角色管理', type: 'PAGE', routeName: 'roles', routePath: '/management/roles', status: 'ACTIVE', selectable: true, disabledReason: null, children: [], functions: [
  { code: 'management:role:query', name: '查询角色', menuId: 'roles', status: 'ACTIVE', selectable: true, disabledReason: null },
  { code: 'management:role:update', name: '修改角色', menuId: 'roles', status: 'ACTIVE', selectable: false, disabledReason: '不可授予' },
] }
it('selects assignable descendants without granting disabled functions', async () => {
  const view = mount(AuthorizationTree, { props: { nodes: [page], modelValue: [], keyword: '', expanded: true, readonly: false } })
  await view.find('[aria-label=角色管理] input').setValue(true)
  expect(view.emitted('update:modelValue')?.[0]?.[0]).toEqual(['m:roles', 'f:management:role:query'])
  view.unmount()
})
it('selecting one assigned function for removal does not select its menu', async () => {
  const view = mount(AuthorizationTree, { props: { nodes: [page], modelValue: [], keyword: '', expanded: true, readonly: false, assignedMenus: ['roles'], assignedCodes: ['management:role:query'] } })
  await view.find('[aria-label=查询角色] input').setValue(true)
  expect(view.emitted('update:modelValue')?.[0]?.[0]).toEqual(['f:management:role:query'])
  view.unmount()
})
