import { apiGet, apiPost } from '../../../shared/http/http'
import type { Login } from '../model/auth'

export const loginApi = (phone: string, verificationCode: string) => apiPost<Login>('/api/v2/auth/login', { phone, verificationCode })
export const meApi = () => apiGet<Login>('/api/v2/auth/me')
export const logoutApi = () => apiPost<void>('/api/v2/auth/logout')
