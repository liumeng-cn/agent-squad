// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils'
import { reactive } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, expect, it, vi } from 'vitest'
import UserProfile from './UserProfile.vue'
import { managementAccess } from '../../management/api/managementApi'
const state = vi.hoisted(() => ({ auth: null as any }))
vi.mock('../../auth/stores/auth', () => ({ useAuthStore: () => state.auth }))
vi.mock('../../management/api/managementApi', () => ({ managementAccess: vi.fn() }))
beforeEach(() => {
  vi.resetAllMocks()
  state.auth = reactive({ user: { userId: '123', userName: 'reader', phone: '13800000000', displayName: '', effectiveDisplayName: 'reader' } })
})
async function profile() {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/management', component: { template: '<div />' } }] })
  const view = mount(UserProfile, { global: { plugins: [router], stubs: { UserAvatar: true } } })
  await flushPromises()
  await view.find('.profile-button').trigger('click')
  await flushPromises()
  return view
}
it('shows the management link for an authorized role', async () => {
  vi.mocked(managementAccess).mockResolvedValue({ canAccess: true, menus: [], permissionCodes: ['management:role:query'] })
  const view = await profile()
  expect(view.find('a[href="/management"]').text()).toBe('管理平台')
  await view.find('a[href="/management"]').trigger('click')
  expect(view.find('.user-menu').exists()).toBe(false)
  view.unmount()
})
it('hides the management link for ordinary users and after switching accounts', async () => {
  vi.mocked(managementAccess).mockResolvedValue({ canAccess: true, menus: [], permissionCodes: ['management:role:query'] })
  const view = await profile()
  expect(view.find('a[href="/management"]').exists()).toBe(true)
  vi.mocked(managementAccess).mockResolvedValue({ canAccess: false, menus: [], permissionCodes: [] })
  state.auth.user = { ...state.auth.user, userId: '456' }
  await flushPromises()
  expect(view.find('a[href="/management"]').exists()).toBe(false)
  view.unmount()
})
it('hides a previously visible entry when permission refresh fails', async () => {
  vi.mocked(managementAccess).mockResolvedValue({ canAccess: true, menus: [], permissionCodes: ['management:role:query'] })
  const view = await profile()
  await view.find('.profile-button').trigger('click')
  vi.mocked(managementAccess).mockRejectedValue(new Error('unavailable'))
  await view.find('.profile-button').trigger('click')
  await flushPromises()
  expect(view.find('a[href="/management"]').exists()).toBe(false)
  view.unmount()
})
