import { getCsrfToken, clearCsrfToken } from './csrf'

/**
 * 统一 HTTP 出口：同源 Cookie、写请求自动附 CSRF、幂等键与版本头；
 * 401 触发注册的跳转回调，错误统一收敛为 ApiError。
 */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

let unauthorizedHandler: (() => void) | null = null

export function onUnauthorized(handler: () => void): void {
  unauthorizedHandler = handler
}

export interface RequestOptions {
  body?: unknown
  idempotencyKey?: string
  ifMatch?: string
}

export function apiGet<T>(path: string): Promise<T> {
  return request<T>('GET', path)
}

export function apiPost<T>(path: string, body?: unknown, options: RequestOptions = {}): Promise<T> {
  return request<T>('POST', path, { ...options, body })
}

export function apiDelete<T>(path: string, options: RequestOptions = {}): Promise<T> {
  return request<T>('DELETE', path, options)
}

const API_BASE = import.meta.env.VITE_API_BASE_URL || ''

async function request<T>(method: string, path: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = {}
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  if (options.idempotencyKey) {
    headers['Idempotency-Key'] = options.idempotencyKey
  }
  if (options.ifMatch) {
    headers['If-Match'] = options.ifMatch
  }
  if (method !== 'GET') {
    const token = getCsrfToken()
    if (token) {
      headers['X-CSRF-Token'] = token
    }
  }

  const url = API_BASE ? `${API_BASE}${path}` : path

  let response: Response
  try {
    response = await fetch(url, {
      method,
      headers,
      credentials: API_BASE ? 'include' : 'same-origin',
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    })
  } catch {
    throw new ApiError(0, '网络异常，请检查连接后重试')
  }

  if (response.status === 401) {
    clearCsrfToken()
    unauthorizedHandler?.()
  }
  if (!response.ok) {
    throw new ApiError(response.status, await readErrorMessage(response))
  }
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

async function readErrorMessage(response: Response): Promise<string> {
  try {
    const data: unknown = await response.json()
    if (data && typeof data === 'object' && typeof (data as { message?: unknown }).message === 'string') {
      return (data as { message: string }).message
    }
  } catch {
    // 非 JSON 错误体，回退到状态码文案
  }
  return `请求失败（${response.status}）`
}

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message
  }
  if (error instanceof Error) {
    return error.message
  }
  return '操作失败，请重试'
}
