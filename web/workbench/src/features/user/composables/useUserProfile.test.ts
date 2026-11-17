import { beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '../../auth/stores/auth'
import { useUserProfile } from './useUserProfile'
import { updateUserApi } from '../api/userApi'
vi.mock('../api/userApi', () => ({ updateUserApi: vi.fn() }))
beforeEach(() => { setActivePinia(createPinia()); vi.resetAllMocks() })
it('按读取版本编辑昵称，成功刷新本人展示', async () => {
  const auth = useAuthStore()
  const user = { userId: '100000', userName: 'user_100000', phone: '13800138000', displayName: '', effectiveDisplayName: 'user_100000', status: 'ACTIVE' as const, version: 0 }
  auth.user = user
  vi.mocked(updateUserApi).mockResolvedValue({ ...user, displayName: '昵称', effectiveDisplayName: '昵称', version: 1 })
  await useUserProfile().update('昵称')
  expect(updateUserApi).toHaveBeenCalledWith('昵称', 0)
  expect(auth.user?.effectiveDisplayName).toBe('昵称')
})
it('编辑期间退出，迟到资料不能重新建立登录身份', async () => {
  const auth = useAuthStore()
  const user = { userId: '100000', userName: 'user_100000', phone: '13800138000', displayName: '', effectiveDisplayName: 'user_100000', status: 'ACTIVE' as const, version: 0 }
  auth.user = user
  let finish!: (value: typeof user) => void
  vi.mocked(updateUserApi).mockImplementation(() => new Promise(resolve => { finish = resolve }))
  const pending = useUserProfile().update('昵称')
  auth.clearUser()
  finish(user)
  await pending
  expect(auth.user).toBeNull()
})
