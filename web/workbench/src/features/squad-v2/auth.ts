import { defineStore } from 'pinia'
import { apiGet, apiPost, apiPatch, ApiError } from '../../shared/api/http'
import { clearCsrfToken, setCsrfToken } from '../../shared/api/csrf'

export interface User {
  userId: string
  userName: string
  phone: string
  displayName: string
  effectiveDisplayName: string
  status: 'ACTIVE' | 'DISABLED'
  version: number
}
interface Login { user: User; csrfToken: string; expiresAt: string }
export const useV2Auth = defineStore('v2-auth', {
  state: () => ({ user: null as User | null }),
  actions: {
    apply(result: Login) { this.user = result.user; setCsrfToken(result.csrfToken) },
    async ensureSession() {
      try { this.apply(await apiGet<Login>('/api/v2/auth/me')); return true }
      catch (error) {
        if (error instanceof ApiError && error.code === 401) { this.user = null; clearCsrfToken(); return false }
        throw error
      }
    },
    async login(phone: string, verificationCode: string) {
      this.apply(await apiPost<Login>('/api/v2/auth/login', { phone, verificationCode }))
    },
    async register(phone: string, verificationCode: string) {
      return apiPost<User>('/api/v2/users/register', { phone, verificationCode })
    },
    async update(displayName: string) {
      const user = this.user
      if (!user) throw new Error('请先登录')
      this.user = await apiPatch<User>('/api/v2/users/me', { displayName, version: user.version })
    },
    async logout() {
      await apiPost('/api/v2/auth/logout')
      this.user = null; clearCsrfToken()
    },
  },
})
