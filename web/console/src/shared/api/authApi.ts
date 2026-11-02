import { apiGet, apiPost } from './http'
import type { AuthView } from './types'

export function loginApi(username: string, password: string): Promise<AuthView> {
  return apiPost('/api/v1/auth/login', { username, password })
}

export function meApi(): Promise<AuthView> {
  return apiGet('/api/v1/auth/me')
}

export function logoutApi(): Promise<void> {
  return apiPost('/api/v1/auth/logout')
}

/** 修改密码成功后服务端撤销全部会话，调用方必须回到登录页。 */
export function changePasswordApi(currentPassword: string, newPassword: string): Promise<void> {
  return apiPost('/api/v1/auth/password', { currentPassword, newPassword })
}
