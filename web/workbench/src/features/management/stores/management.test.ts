import { beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useManagementStore } from './management'
import { managementAccess } from '../api/managementApi'
const auth = vi.hoisted(() => ({ user: { userId: '123' } }))
vi.mock('../../auth/stores/auth', () => ({ useAuthStore: () => auth }))
vi.mock('../api/managementApi', () => ({ managementAccess: vi.fn() }))
beforeEach(() => { setActivePinia(createPinia()); auth.user = { userId: '123' }; vi.resetAllMocks() })
it('revokes previous permissions when refresh fails', async () => {
  const store = useManagementStore()
  vi.mocked(managementAccess).mockResolvedValue({ canAccess: true, menus: [], permissionCodes: ['management:role:update'] })
  await store.refresh()
  expect(store.can('management:role:update')).toBe(true)
  vi.mocked(managementAccess).mockRejectedValue(new Error('unavailable'))
  await expect(store.refresh()).rejects.toThrow('unavailable')
  expect(store.can('management:role:update')).toBe(false)
})
it('does not apply permissions returned after an account switch', async () => {
  const store = useManagementStore()
  let resolve!: (value: Awaited<ReturnType<typeof managementAccess>>) => void
  vi.mocked(managementAccess).mockReturnValue(new Promise(done => { resolve = done }))
  const pending = store.refresh()
  auth.user = { userId: '456' }
  resolve({ canAccess: true, menus: [], permissionCodes: ['management:user:assign-role'] })
  expect(await pending).toBe(false)
  expect(store.permissionCodes).toEqual([])
})
