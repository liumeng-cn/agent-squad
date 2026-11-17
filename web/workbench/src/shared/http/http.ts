import { getCsrfToken, clearCsrfToken } from './csrf'
import type { ApiResult } from './types'

/**
 * 统一 HTTP 出口：同源 Cookie、写请求自动附 CSRF、幂等键与版本头；
 * 业务码 401 触发注册的跳转回调，错误统一收敛为 ApiError。
 *
 * 后端业务接口统一返回 {@link ApiResult}，本层负责解包 data 并在业务码非成功时抛出。
 */
export class ApiError extends Error {
  constructor(
    readonly code: number,
    message: string,
    readonly status: number = 0,
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
  blob?: boolean
}

export function apiDownload(path: string): Promise<Blob> {
  return request<Blob>('GET', path, { blob: true })
}

export function apiGet<T>(path: string): Promise<T> {
  return request<T>('GET', path)
}

export function apiPost<T>(path: string, body?: unknown, options: RequestOptions = {}): Promise<T> {
  return request<T>('POST', path, { ...options, body })
}

export function apiPut<T>(path: string, body: unknown, options: RequestOptions = {}): Promise<T> {
  return request<T>('PUT', path, { ...options, body })
}

export function apiPatch<T>(path: string, body: unknown, options: RequestOptions = {}): Promise<T> {
  return request<T>('PATCH', path, { ...options, body })
}

export function apiDelete<T>(path: string, options: RequestOptions = {}): Promise<T> {
  return request<T>('DELETE', path, options)
}

const API_BASE = import.meta.env.VITE_API_BASE_URL || ''

async function request<T>(method: string, path: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = {}
  if (options.body !== undefined && !(options.body instanceof FormData)) {
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
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), 30000)
  try {
    response = await fetch(url, {
      method,
      signal: controller.signal,
      headers,
      credentials: API_BASE ? 'include' : 'same-origin',
      body: options.body instanceof FormData ? options.body : options.body === undefined ? undefined : JSON.stringify(options.body),
    })
  } catch {
    throw new ApiError(0, '暂时无法确认请求结果，请检查连接后重试')
  } finally { clearTimeout(timeout) }

  // 下载成功时保留二进制响应；业务拒绝仍按 JSON 中的 code 处理。
  const jsonResponse = response.headers.get('content-type')?.includes('json')
  if (options.blob && response.ok && !jsonResponse) return (await response.blob()) as T
  let result: unknown
  try {
    result = await response.json()
  } catch {
    throw new ApiError(0, `响应格式错误（HTTP ${response.status}）`, response.status)
  }
  if (isApiResult(result)) {
    if (result.code !== 0) {
      if (result.code === 401) {
        clearCsrfToken()
        unauthorizedHandler?.()
      }
      throw new ApiError(result.code, result.msg || `操作失败（${result.code}）`, response.status)
    }
    if (response.ok) return result.data as T
  }
  throw new ApiError(0, `请求失败（HTTP ${response.status}）`, response.status)
}

function isApiResult(value: unknown): value is ApiResult<unknown> {
  return value !== null && typeof value === 'object'
      && 'code' in value && typeof (value as ApiResult<unknown>).code === 'number'
      && 'data' in value
      && 'msg' in value
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
