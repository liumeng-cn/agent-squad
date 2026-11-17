import type { AguiEvent } from '../model/agui'
import type { ChatReceipt, ChatSession, SubmitChatInput } from '../model/chatTypes'
import { apiGet, apiPost } from '../../../shared/http/http'

/** 按 SSE 行协议解析，兼容跨块 CRLF、中文字符及多行 data。 */
export async function consumeChatStream<T = AguiEvent>(
  body: ReadableStream<Uint8Array>,
  onEvent: (event: T) => void,
) {
  const reader = body.getReader()
  const decoder = new TextDecoder()
  let pending = ''
  let data: string[] = []
  const line = (value: string) => {
    if (!value) {
      if (data.length) onEvent(JSON.parse(data.join('\n')) as T)
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

/** 提交与观察分离，订阅重连不会重新执行用户输入。 */
export function submitChat(input: SubmitChatInput): Promise<ChatReceipt> {
  return apiPost('/api/v2/chat/messages', input)
}
export function readSession(sessionId: string): Promise<ChatSession> {
  return apiGet(`/api/v2/chat/sessions/${encodeURIComponent(sessionId)}`)
}
export function interruptChat(sessionId: string, runId: string): Promise<boolean> {
  return apiPost('/api/v2/chat/interruptions', { sessionId, runId })
}
export async function subscribeSession(sessionId: string, signal: AbortSignal, receive: (state: ChatSession) => void) {
  const base = import.meta.env.VITE_API_BASE_URL || ''
  const response = await fetch(`${base}/api/v2/chat/sessions/${encodeURIComponent(sessionId)}/events`, {
    credentials: base ? 'include' : 'same-origin', signal, headers: { Accept: 'text/event-stream' },
  })
  if (!response.ok || !response.body || !response.headers.get('content-type')?.includes('text/event-stream')) {
    throw new Error('会话订阅暂不可用')
  }
  await consumeChatStream<ChatSession>(response.body, receive)
}

export function resumeChat(sessionId: string, turnId: string): Promise<string> {
  return apiPost('/api/v2/chat/resumptions', { sessionId, turnId })
}
