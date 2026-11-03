import { apiGet, apiPatch, apiPost } from './http'
import type {
  AdminUserCreateRequest,
  AdminUserCreateResult,
  AdminUserPage,
  AdminUserUpdateRequest,
  AdminUserView,
  ResetPasswordResult,
} from './types'

/** 管理员账号管理接口；临时密码只在创建/重置响应中出现一次。 */
export function listUsersApi(cursor?: string, limit?: number): Promise<AdminUserPage> {
  const params = new URLSearchParams()
  if (cursor) params.set('cursor', cursor)
  if (limit) params.set('limit', String(limit))
  const query = params.toString()
  return apiGet(`/api/v1/admin/users${query ? `?${query}` : ''}`)
}

export function createUserApi(request: AdminUserCreateRequest): Promise<AdminUserCreateResult> {
  return apiPost('/api/v1/admin/users', request)
}

export function updateUserApi(userId: string, request: AdminUserUpdateRequest): Promise<AdminUserView> {
  return apiPatch(`/api/v1/admin/users/${encodeURIComponent(userId)}`, request)
}

export function resetUserPasswordApi(userId: string): Promise<ResetPasswordResult> {
  return apiPost(`/api/v1/admin/users/${encodeURIComponent(userId)}/reset-password`)
}
