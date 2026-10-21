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
