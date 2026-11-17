import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from './auth'
import { apiGet, apiPost, ApiError } from '../../../shared/http/http'
import { getCsrfToken } from '../../../shared/http/csrf'
vi.mock('../../../shared/http/http', async (load) => ({
  ...await load<typeof import('../../../shared/http/http')>(),
  apiGet: vi.fn(), apiPatch: vi.fn(), apiPost: vi.fn(),
}))
const user = { userId: '100000', userName: 'user_100000', phone: '13800138000', displayName: '', effectiveDisplayName: 'user_100000', status: 'ACTIVE' as const, version: 0 }
describe('v2独立用户登录状态', () => {
  beforeEach(() => { setActivePinia(createPinia()); vi.clearAllMocks() })
  it('登录后持有CSRF，退出清理身份', async () => {
    vi.mocked(apiPost).mockResolvedValue({ user, csrfToken: 'csrf', expiresAt: '2026-11-24T00:00:00Z' })
    const auth = useAuthStore()
    await auth.login(user.phone, '8888')
    expect(getCsrfToken()).toBe('csrf')
    await auth.logout()
    expect(auth.user).toBeNull()
    expect(getCsrfToken()).toBeNull()
  })
  it('服务故障不伪装为未登录，401清除本地身份', async () => {
    const auth = useAuthStore()
    vi.mocked(apiGet).mockRejectedValue(new ApiError(503, '服务暂不可用', 503))
    await expect(auth.ensureSession()).rejects.toThrow('服务暂不可用')
    vi.mocked(apiGet).mockRejectedValue(new ApiError(401, '请登录', 401))
    expect(await auth.ensureSession()).toBe(false)
  })
})
