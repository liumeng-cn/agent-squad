import { describe, expect, it } from 'vitest'
import { applyChatEvent } from './chatState'
import { consumeChatStream } from './chatApi'
import type { ChatMessage } from './chatTypes'
const envelope = { sessionId: 's', requestId: 'r', messageId: 'r', timestamp: 100 }
function reply(): ChatMessage {
  return { id: 'r', role: 'assistant', content: '', status: 'RUNNING', startedAt: 0, activities: [] }
}
describe('chat event projection', () => {
  it('merges calls and keeps final content without duplicating deltas', () => {
    const message = reply()
    applyChatEvent(message, { ...envelope, type: 'MESSAGE_DELTA', text: '你好' })
    applyChatEvent(message, { ...envelope, type: 'MESSAGE_CONTENT', text: '你好，世界' })
    for (const status of ['RUNNING', 'COMPLETED'] as const) {
      applyChatEvent(message, { ...envelope, type: 'ACTIVITY_UPDATED', activityId: 'call',
        kind: 'TOOL', title: '查询', status })
    }
    applyChatEvent(message, { ...envelope, type: 'MESSAGE_COMPLETED', status: 'COMPLETED' })
    expect(message.content).toBe('你好，世界')
    expect(message.activities).toHaveLength(1)
    expect(message.activities[0]?.status).toBe('COMPLETED')
    expect(message.status).toBe('COMPLETED')
  })
  it('does not apply another message or fabricate success for unfinished activities', () => {
    const message = reply()
    applyChatEvent(message, { ...envelope, messageId: 'other', type: 'MESSAGE_DELTA', text: '忽略' })
    applyChatEvent(message, { ...envelope, type: 'ACTIVITY_UPDATED', activityId: 'call',
      kind: 'TOOL', title: '查询', status: 'RUNNING' })
    applyChatEvent(message, { ...envelope, type: 'MESSAGE_FAILED', status: 'FAILED', message: '失败' })
    expect(message.content).toBe('')
    expect(message.activities[0]?.status).toBe('INTERRUPTED')
    expect(message.error).toBe('失败')
  })
  it('keeps provider thinking separate and groups it by model call', () => {
    const message = reply()
    for (const text of ['测试思考', '增量']) {
      applyChatEvent(message, { ...envelope, type: 'THINKING_DELTA', modelCallId: 'call-1', text })
    }
    applyChatEvent(message, { ...envelope, type: 'THINKING_DELTA', modelCallId: 'call-2', text: '第二次调用' })
    applyChatEvent(message, { ...envelope, type: 'MESSAGE_DELTA', text: '最终答案' })
    expect(message.content).toBe('最终答案')
    expect(message.thinkingBlocks).toEqual([
      { modelCallId: 'call-1', content: '测试思考增量' },
      { modelCallId: 'call-2', content: '第二次调用' },
    ])
  })
  it('decodes UTF-8 and SSE frames split at every byte including CRLF', async () => {
    const event = { type: 'TEXT_MESSAGE_CONTENT', delta: '中文回复' }
    const bytes = new TextEncoder().encode(`: ping\r\nevent: chat\r\ndata: ${JSON.stringify(event)}\r\n\r\n`)
    const stream = new ReadableStream<Uint8Array>({ start(controller) {
      for (const byte of bytes) controller.enqueue(new Uint8Array([byte]))
      controller.close()
    } })
    const events: import('./agui').AguiEvent[] = []
    await consumeChatStream(stream, event => events.push(event))
    expect(events).toEqual([event])
  })
})
