import type { AguiEvent } from '../model/agui'
import type { ChatAgentOption, ChatAnswer, ChatReceipt, ChatSession, SubmitChatInput } from '../model/chatTypes'
import { ApiError, apiGet, apiPost } from '../../../shared/http/http'

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

/** 查询可供新对话选择的已发布 Master 智能体列表。 */
export function listChatAgents(): Promise<ChatAgentOption[]> {
  return apiGet('/v2-server/api/v2/chat/agents')
}

/** 提交与观察分离，订阅重连不会重新执行用户输入。 */
export function submitChat(input: SubmitChatInput): Promise<ChatReceipt> {
  return apiPost('/v2-server/api/v2/chat/messages', input)
}
export function readSession(convId: string): Promise<ChatSession> {
  return apiGet(`/v2-server/api/v2/chat/convs/${encodeURIComponent(convId)}/session`)
}
export function interruptChat(convId: string, runId: string): Promise<boolean> {
  return apiPost('/v2-server/api/v2/chat/interruptions', { convId, runId })
}
export async function subscribeSession(convId: string, signal: AbortSignal, receive: (state: ChatSession) => void) {
  const base = import.meta.env.VITE_API_BASE_URL || ''
  const response = await fetch(`${base}/v2-server/api/v2/chat/convs/${encodeURIComponent(convId)}/events`, {
    credentials: base ? 'include' : 'same-origin', signal, headers: { Accept: 'text/event-stream' },
  })
  const contentType = response.headers.get('content-type') ?? ''
  if (contentType.includes('application/json')) {
    const result = await response.json() as { code: number; msg: string }
    throw new ApiError(result.code, result.msg, response.status)
  }
  if (!response.ok || !response.body || !contentType.includes('text/event-stream')) {
    throw new ApiError(response.status, '对话订阅暂不可用', response.status)
  }
  await consumeChatStream<ChatSession>(response.body, receive)
}

export function resumeChat(convId: string, turnId: string): Promise<string> {
  return apiPost('/v2-server/api/v2/chat/resumptions', { convId, turnId })
}

export function answerChat(input: ChatAnswer): Promise<string> {
  return apiPost('/v2-server/api/v2/chat/answers', input)
}
