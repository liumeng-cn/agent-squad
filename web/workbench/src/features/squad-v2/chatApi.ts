import { projectAgui, type AguiEvent } from './agui'
import { getCsrfToken } from '../../shared/api/csrf'
import type { ChatEvent, ChatRequest } from './chatTypes'

/** 按 SSE 行协议解析，兼容跨块 CRLF、中文字符及多行 data。 */
export async function consumeChatStream(
  body: ReadableStream<Uint8Array>,
  onEvent: (event: AguiEvent) => void,
) {
  const reader = body.getReader()
  const decoder = new TextDecoder()
  let pending = ''
  let data: string[] = []
  const line = (value: string) => {
    if (!value) {
      if (data.length) onEvent(JSON.parse(data.join('\n')) as AguiEvent)
      data = []
    } else if (value.startsWith('data:')) {
      data.push(value.slice(5).replace(/^ /, ''))
    }
  }
  try {
    while (true) {
      const { value, done } = await reader.read()
      pending += done ? decoder.decode() : decoder.decode(value, { stream: true })
      let boundary: number
      while ((boundary = pending.indexOf('\n')) >= 0) {
        line(pending.slice(0, boundary).replace(/\r$/, ''))
        pending = pending.slice(boundary + 1)
      }
      if (done) break
    }
    // SSE 仅在空行时提交事件，连接中途截断的帧不能当作完整事件。
  } finally {
    await reader.cancel().catch(() => undefined)
    reader.releaseLock()
  }
}

/** 一次 POST 返回当前轮次的 SSE；不自动重发用户输入。 */
export async function sendChat(request: ChatRequest, signal: AbortSignal, onEvent: (event: ChatEvent) => void) {
  const headers: Record<string, string> = { 'Content-Type': 'application/json', Accept: 'text/event-stream' }
  const csrf = getCsrfToken()
  if (csrf) headers['X-CSRF-Token'] = csrf
  const base = import.meta.env.VITE_API_BASE_URL || ''
  const response = await fetch(base + '/api/v2/chat/messages', {
    method: 'POST', credentials: base ? 'include' : 'same-origin',
    headers, body: JSON.stringify(request), signal,
  })
  if (!response.ok || !response.body) throw new Error(`连接失败（HTTP ${response.status}）`)
  if (!response.headers.get('content-type')?.includes('text/event-stream')) throw new Error('服务未返回聊天事件流')
  await consumeChatStream(response.body, event => {
    for (const projected of projectAgui(event, request)) onEvent(projected)
  })
}
