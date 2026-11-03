import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useConversationsStore } from '../conversations'
import type { ChatMessage } from '../../../../shared/api/chatApi'

const message = (messageId: string, revision: number, sequence = 1) => ({ messageId, revision, sequence, text: `revision-${revision}` }) as ChatMessage

describe('权威消息回放', () => {
  beforeEach(() => setActivePinia(createPinia()))
  it('重复最终事件与快照只保留同一 messageId 一份', () => {
    const store = useConversationsStore()
    store.mergeMessages([message('final', 1)])
    store.mergeMessages([message('final', 1), message('final', 1)])
    expect(store.messages).toHaveLength(1)
  })
  it('迟到旧修订不能覆盖新终稿', () => {
    const store = useConversationsStore()
    store.mergeMessages([message('final', 2)])
    store.mergeMessages([message('final', 1)])
    expect(store.messages[0]?.revision).toBe(2)
  })
  it('历史翻页与最新快照按消息序号合并', () => {
    const store = useConversationsStore()
    store.mergeMessages([message('new', 1, 20)])
    store.mergeMessages([message('old', 1, 1), message('new', 1, 20)])
    expect(store.messages.map(item => item.messageId)).toEqual(['old', 'new'])
  })
  it('新会话清除旧会话显示状态', () => {
    const store = useConversationsStore()
    store.mergeMessages([message('old', 1)])
    store.startDraft()
    expect(store.messages).toEqual([])
    expect(store.activeConversationId).toBeNull()
  })
  it('完成事件即时替换已知正文，未知身份请求快照', () => {
    const store = useConversationsStore()
    store.mergeMessages([message('final', 1)])
    expect(store.applyEventPayload({ type: 'message.completed', payload: { messageId: 'final', revision: 2, text: '终稿' } } as never)).toBe(true)
    expect(store.messages[0]?.text).toBe('终稿')
    expect(store.applyEventPayload({ type: 'message.completed', payload: { messageId: 'unknown', revision: 1, text: '未知' } } as never)).toBe(false)
    expect(store.messages).toHaveLength(1)
  })

  it('SSE缺口和代际变化关闭旧流并请求恢复', () => {
    let source: { onmessage?: (message: { data: string }) => void; close: ReturnType<typeof vi.fn> }
    vi.stubGlobal('EventSource', function () { source = { close: vi.fn() }; return source })
    const store = useConversationsStore()
    store.activeConversationId = 'one'
    store.snapshot = { cursor: 'one:0', conversation: { generation: 1 } } as never
    const reconnect = vi.spyOn(store, 'scheduleReconnect').mockImplementation(() => {})
    store.connect()
    source!.onmessage!({ data: JSON.stringify({ conversationId: 'one', generation: 1, sequence: 2 }) })
    expect(source!.close).toHaveBeenCalled()
    expect(reconnect).toHaveBeenCalledTimes(1)
    store.connect()
    source!.onmessage!({ data: JSON.stringify({ conversationId: 'one', generation: 2, sequence: 1 }) })
    expect(reconnect).toHaveBeenCalledTimes(2)
    store.dispose()
    vi.unstubAllGlobals()
  })

})
