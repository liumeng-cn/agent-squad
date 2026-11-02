import { apiGet, apiPost, apiPatch } from './http'
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

export interface AdminUserSummary {
  userId: string
  username: string
  displayName: string
  role: 'USER' | 'ADMIN'
  status: 'ACTIVE' | 'DISABLED'
  mustChangePassword: boolean
  version: number
}

export interface AdminUserPage {
  items: AdminUserSummary[]
  nextCursor: string | null
}

export interface CreatedUser {
  userId: string
  temporaryPassword: string
}

export function changePasswordApi(currentPassword: string, newPassword: string): Promise<void> {
  return apiPost('/api/v1/auth/password', { currentPassword, newPassword })
}

export function adminUsersApi(): Promise<AdminUserPage> {
  return apiGet('/api/v1/admin/users?limit=100')
}

export function createUserApi(username: string, displayName: string, role: 'USER' | 'ADMIN'): Promise<CreatedUser> {
  return apiPost('/api/v1/admin/users', { username, displayName, role })
}

export function updateUserApi(userId: string, expectedVersion: number, displayName: string, role: string,
                              status: string): Promise<void> {
  return apiPatch('/api/v1/admin/users/' + encodeURIComponent(userId), { expectedVersion, displayName, role, status })
}

export function resetUserPasswordApi(userId: string): Promise<CreatedUser> {
  return apiPost('/api/v1/admin/users/' + encodeURIComponent(userId) + '/reset-password')
}
