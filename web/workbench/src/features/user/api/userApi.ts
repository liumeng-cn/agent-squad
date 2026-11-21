import { apiPost, apiPatch } from '../../../shared/http/http'
import type { User } from '../model/user'

export const registerApi = (phone: string, verificationCode: string) => apiPost<User>('/user-server/api/v2/users/register', { phone, verificationCode })
export const updateUserApi = (displayName: string, version: number) => apiPatch<User>('/user-server/api/v2/users/me', { displayName, version })
