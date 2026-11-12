import { getCsrfToken } from '../../../shared/api/csrf'

export interface SquadTestStreamRequest {
  intervalMillis: number
}

/**
 * 打开本地联调使用的 SSE 测试接口。
 *
 * 这里只负责 HTTP 请求，不解析事件、不维护执行状态。
 * 页面输入的测试提示词不会发送到这个模拟接口。
 */
export async function openSquadTestStream(
  request: SquadTestStreamRequest,
  signal: AbortSignal,
): Promise<Response> {
  const baseUrl = import.meta.env.VITE_API_BASE_URL || ''
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'text/event-stream',
  }
  const csrfToken = getCsrfToken()
  if (csrfToken) headers['X-CSRF-Token'] = csrfToken

  const response = await fetch(baseUrl + '/api/v2/test/events/stream', {
    method: 'POST',
    credentials: baseUrl ? 'include' : 'same-origin',
    headers,
    body: JSON.stringify(request),
    signal,
  })

  if (!response.ok) {
    throw new Error('事件流请求失败（HTTP ' + response.status + '）')
  }
  if (!response.body) {
    throw new Error('服务器没有返回可读取的事件流')
  }

  const contentType = response.headers.get('content-type') || ''
  if (contentType && !contentType.toLowerCase().includes('text/event-stream')) {
    throw new Error('服务器返回的内容不是 SSE 事件流')
  }

  return response
}
