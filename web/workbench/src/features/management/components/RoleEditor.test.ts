// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import RoleEditor from './RoleEditor.vue'
import { createRole, permissionOptions } from '../api/managementApi'
import type { Role } from '../model/management'
vi.mock('../stores/management', () => ({ useManagementStore: () => ({ can: () => true }) }))
vi.mock('../api/managementApi', () => ({ permissionOptions: vi.fn(), createRole: vi.fn(), updateRole: vi.fn() }))
beforeEach(() => vi.resetAllMocks())
it('retains the draft and reports a failed save', async () => {
  vi.mocked(permissionOptions).mockResolvedValue({ records: [{ code: 'management:access', name: '平台准入', module: '', description: '' }], total: 1, current: 1, size: 100 })
  vi.mocked(createRole).mockRejectedValue(new Error('版本冲突'))
  const view = mount(RoleEditor, { props: { role: null } })
  await flushPromises()
  await view.findAll('input')[0]!.setValue('查看角色')
  await view.findAll('input')[1]!.setValue('reader')
  await view.find('input[type=checkbox]').setValue(true)
  await view.find('footer .management-primary').trigger('click')
  await flushPromises()
  expect(view.findAll('input')[0]!.element.value).toBe('查看角色')
  expect(view.find('[role=alert]').text()).toContain('版本冲突')
  expect(view.emitted('saved')).toBeUndefined()
  view.unmount()
})
it('shows built-in roles as read-only without fetching editor-only options', async () => {
  const role = { builtIn: true, code: 'platform-admin', name: '平台管理员', description: '', draftPermissionCodes: ['management:access'] } as Role
  const view = mount(RoleEditor, { props: { role } })
  await flushPromises()
  expect(permissionOptions).not.toHaveBeenCalled()
  expect(view.find('input').attributes('disabled')).toBeDefined()
  expect(view.find('footer .management-primary').exists()).toBe(false)
  view.unmount()
})
