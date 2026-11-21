import { apiGet, apiPost } from '../../../shared/http/http'
import type { Login } from '../model/auth'

export const loginApi = (phone: string, verificationCode: string) => apiPost<Login>('/gateway/api/v2/auth/login', { phone, verificationCode })
export const meApi = () => apiGet<Login>('/gateway/api/v2/auth/me')
export const logoutApi = () => apiPost<void>('/gateway/api/v2/auth/logout')
