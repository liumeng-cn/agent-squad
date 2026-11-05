import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useConversationsStore } from '../conversations'
import type { ChatMessage, ChatSnapshot } from '../../../../shared/api/chatApi'

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
    let source: { onmessage?: (message: { data: string }) => void; close: ReturnType<typeof vi.fn>; addEventListener?: ReturnType<typeof vi.fn> }
    vi.stubGlobal('EventSource', function () { source = { close: vi.fn(), addEventListener: vi.fn() }; return source })
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

  it('连续受理、状态和新答案事件直接更新页面，不请求快照', () => {
    let source: { onmessage?: (message: { data: string }) => void; close: ReturnType<typeof vi.fn>; addEventListener?: ReturnType<typeof vi.fn> }
    vi.stubGlobal('EventSource', function () { source = { close: vi.fn(), addEventListener: vi.fn() }; return source })
    const store = useConversationsStore()
    store.activeConversationId = 'one'
    store.snapshot = { cursor: 'one:1:0', conversation: { generation: 1, titleVersion: 0 },
      messages: [], executions: [], activeExecutions: [] } as unknown as ChatSnapshot
    const refresh = vi.spyOn(store, 'refreshSnapshot')
    const queuedRefresh = vi.spyOn(store, 'queueRefresh')
    const reconnect = vi.spyOn(store, 'scheduleReconnect').mockImplementation(() => {})
    const list = vi.spyOn(store, 'refreshList')
    store.connect()
    const send = (sequence: number, type: string, payload: unknown) => source!.onmessage!({
      data: JSON.stringify({ conversationId: 'one', generation: 1, sequence, type, payload }),
    })
    const input = { messageId: 'user', sequence: 1, role: 'USER', text: '你好', revision: 0, completed: true,
      sourceMessageId: null, executionId: 'reply', taskId: null, occurredAt: '2026-11-04T00:00:00Z', artifacts: [] }
    const execution = { executionId: 'reply', sourceMessageId: 'user', status: 'QUEUED', version: 0,
      answerMessageId: null, failureId: null, allowedActions: ['STOP'] }
    send(1, 'message.accepted', { schemaVersion: 2, message: input, execution })
    send(2, 'execution.queued', { schemaVersion: 2, execution })
    send(3, 'execution.status_changed', { schemaVersion: 2, execution: { ...execution, status: 'RUNNING', version: 1 } })
    expect(store.snapshot!.activeExecutions[0]?.status).toBe('RUNNING')
    send(4, 'message.completed', { schemaVersion: 2, message: { ...input, messageId: 'answer', sequence: 2,
      role: 'ASSISTANT', sourceMessageId: 'user', text: '你好，有什么可以帮你？' } })
    send(5, 'execution.status_changed', { schemaVersion: 2, execution: { ...execution, status: 'SUCCEEDED',
      version: 2, answerMessageId: 'answer', allowedActions: [] } })
    expect(store.messages.map(item => item.messageId)).toEqual(['user', 'answer'])
    expect(store.messages[1]?.text).toBe('你好，有什么可以帮你？')
    expect(store.snapshot!.activeExecutions).toEqual([])
    expect(store.executionHistory[0]?.status).toBe('SUCCEEDED')
    expect(store.snapshot!.cursor).toBe('one:1:5')
    expect(refresh).not.toHaveBeenCalled()
    expect(queuedRefresh).not.toHaveBeenCalled()
    expect(list).not.toHaveBeenCalled()
    expect(reconnect).not.toHaveBeenCalled()
    store.dispose(); vi.unstubAllGlobals()
  })

  it('不完整的新协议事件不插入半条消息，也不推进水位', () => {
    let source: { onmessage?: (message: { data: string }) => void; close: ReturnType<typeof vi.fn>; addEventListener?: ReturnType<typeof vi.fn> }
    vi.stubGlobal('EventSource', function () { source = { close: vi.fn(), addEventListener: vi.fn() }; return source })
    const store = useConversationsStore()
    store.activeConversationId = 'one'
    store.snapshot = { cursor: 'one:1:0', conversation: { generation: 1 }, messages: [], executions: [], activeExecutions: [] } as unknown as ChatSnapshot
    const reconnect = vi.spyOn(store, 'scheduleReconnect').mockImplementation(() => {})
    store.connect()
    source!.onmessage!({ data: JSON.stringify({ conversationId: 'one', generation: 1, sequence: 1,
      type: 'message.accepted', payload: { schemaVersion: 2, message: { messageId: 'incomplete' } } }) })
    expect(store.messages).toEqual([])
    expect(store.snapshot!.cursor).toBe('one:1:0')
    expect(reconnect).toHaveBeenCalledOnce()
    store.dispose(); vi.unstubAllGlobals()
  })

})
