import type { User } from '../../user/model/user'
import type { Login } from '../model/auth'
import { loginApi, meApi, logoutApi } from '../api/authApi'
import { defineStore } from 'pinia'
import { ApiError } from '../../../shared/http/http'
import { clearCsrfToken, setCsrfToken } from '../../../shared/http/csrf'

export const useAuthStore = defineStore('auth', {
  state: () => ({ user: null as User | null }),
  actions: {
    apply(result: Login) { this.user = result.user; setCsrfToken(result.csrfToken) },
    async ensureSession() {
      try { this.apply(await meApi()); return true }
      catch (error) {
        if (error instanceof ApiError && error.code === 401) { this.user = null; clearCsrfToken(); return false }
        throw error
      }
    },
    async login(phone: string, verificationCode: string) {
      this.apply(await loginApi(phone, verificationCode))
    },
    clearUser() { this.user = null; clearCsrfToken() },
    async logout() {
      await logoutApi()
      this.user = null; clearCsrfToken()
    },
  },
})
