import { defineStore } from 'pinia'
import { loginApi, logoutApi, meApi } from '../../../shared/api/authApi'
import { clearCsrfToken, setCsrfToken } from '../../../shared/api/csrf'
import { errorMessage } from '../../../shared/api/http'
import type { AuthView } from '../../../shared/api/types'

interface AuthState {
  user: AuthView | null
  /** 是否已做过首次会话探测；守卫据此避免重复 /me。 */
  ready: boolean
  loginError: string | null
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    user: null,
    ready: false,
    loginError: null,
  }),

  getters: {
    loggedIn: (state) => state.user !== null,
  },

  actions: {
    /** 首次进入应用时探测会话；返回是否已登录。 */
    async ensureSession(): Promise<boolean> {
      if (this.ready) {
        return this.loggedIn
      }
      try {
        this.applyUser(await meApi())
      } catch {
        this.clearUser()
      } finally {
        this.ready = true
      }
      return this.loggedIn
    },

    async login(username: string, password: string): Promise<boolean> {
      this.loginError = null
      try {
        this.applyUser(await loginApi(username, password))
        this.ready = true
        return true
      } catch (error) {
        this.loginError = errorMessage(error)
        return false
      }
    },

    async logout(): Promise<void> {
      try {
        await logoutApi()
      } catch {
        // 会话可能已失效，本地状态照常清理
      }
      this.clearUser()
    },

    applyUser(user: AuthView): void {
      this.user = user
      setCsrfToken(user.csrfToken)
    },

    clearUser(): void {
      this.user = null
      clearCsrfToken()
    },
  },
})
