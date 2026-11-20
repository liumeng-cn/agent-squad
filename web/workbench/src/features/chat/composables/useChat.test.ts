// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ChatSession } from '../model/chatTypes'
import { resetChatSessions, useChat } from './useChat'

const api = vi.hoisted(() => ({ submitChat: vi.fn(), subscribeSession: vi.fn(), readSession: vi.fn(),
  interruptChat: vi.fn(), resumeChat: vi.fn() }))
vi.mock('../api/chatApi', () => api)
const convApi = vi.hoisted(() => ({ readConv: vi.fn() }))
vi.mock('../api/convApi', () => ({ readConv: convApi.readConv }))
const convsStore = vi.hoisted(() => ({ upsert: vi.fn() }))
vi.mock('../stores/convs', () => ({ useConvsStore: () => convsStore }))
vi.mock('../../auth/stores/auth', () => ({ useAuthStore: () => ({ user: { userId: 'test-user' } }) }))

let chat: ReturnType<typeof useChat>
let receive: (snapshot: ChatSession) => void
let observedSignal: AbortSignal
const snapshot: ChatSession = { sessionId: 'native-session', cursor: 1, inputCursor: 1,
  turnId: 'turn-one', runId: 'run-one', status: 'RUNNING', messages: [], inputs: [],
  summary: { convId: 'conversation', sessionId: 'native-session', title: '新对话',
    titleSource: 'AUTO', version: 0, createdAt: '2026-11-20T00:00:00Z', remark: null, archived: false } }

function mountChat(convId?: string) {
  return mount(defineComponent({ setup() { chat = useChat('test-user', convId); return () => h('div') } }))
}

beforeEach(() => {
  vi.resetAllMocks()
  sessionStorage.clear()
  resetChatSessions()
  api.readSession.mockResolvedValue(snapshot)
  convApi.readConv.mockResolvedValue({ convId: 'conversation', sessionId: 'native-session', title: '新对话',
    titleSource: 'AUTO', version: 0, createdAt: '2026-11-20T00:00:00Z', remark: null, archived: false })
  api.subscribeSession.mockImplementation((_id: string, signal: AbortSignal, callback: typeof receive) => {
    receive = callback
    observedSignal = signal
    return new Promise<void>(resolve => signal.addEventListener('abort', () => resolve()))
  })
})
afterEach(() => vi.useRealTimers())

describe('会话执行与观察分离', () => {
  it('停止后允许发送新问题，接收后旧继续按钮不被迟到快照恢复', async () => {
    const wrapper = mountChat('conversation')
    await flushPromises()
    const stopped: ChatSession = { ...snapshot, status: 'INTERRUPTED' }
    receive(stopped)
    expect(chat.canResume.value).toBe(true)
    api.submitChat.mockResolvedValue({ disposition: 'NEW_TURN', turnId: 'turn-two' })
    api.readSession.mockResolvedValue(stopped)
    expect(await chat.send('你好')).toBe(true)
    expect(api.submitChat.mock.calls[0]![0].observedTurnId).toBeUndefined()
    expect(chat.canResume.value).toBe(false)
    receive(stopped)
    expect(chat.canResume.value).toBe(false)
    receive({ ...snapshot, cursor: 3, turnId: 'turn-two', status: 'INTERRUPTED' })
    expect(chat.canResume.value).toBe(true)
    wrapper.unmount()
  })

  it('取消订阅不发停止或重提请求', async () => {
    const wrapper = mountChat('conversation')
    await flushPromises()
    receive(snapshot)
    expect(chat.busy.value).toBe(true)
    wrapper.unmount()
    expect(observedSignal.aborted).toBe(true)
    expect(api.submitChat).not.toHaveBeenCalled()
    expect(api.interruptChat).not.toHaveBeenCalled()
  })

  it('快照的结束时间传给页面，重复快照不会重新计时', async () => {
    const wrapper = mountChat('conversation')
    await flushPromises()
    const completed: ChatSession = { ...snapshot, status: 'COMPLETED', messages: [{
      id: 'reply', turnId: 'turn-one', role: 'ASSISTANT', content: '完成', status: 'COMPLETED',
      startedAt: 1000, finishedAt: 6000, activities: [], thinkingBlocks: [],
    }] }
    receive(completed)
    expect(chat.messages.value[0]?.finishedAt).toBe(6000)
    receive({ ...completed, inputCursor: 2 })
    expect(chat.messages.value[0]?.finishedAt).toBe(6000)
    wrapper.unmount()
  })

  it('插话快照保留已有消息、思考块和工具对象，不重建整条回复', async () => {
    const wrapper = mountChat('conversation')
    await flushPromises()
    const message = { id: 'reply-one', turnId: 'turn-one', role: 'ASSISTANT' as const,
      content: '已有正文', status: 'RUNNING' as const, startedAt: 1000,
      activities: [{ id: 'tool-one', title: '测试工具', status: 'RUNNING' as const, startedAt: 1000 }],
      thinkingBlocks: [{ modelCallId: 'model-one', content: '已有思考' }],
    }
    receive({ ...snapshot, messages: [message] })
    const original = chat.messages.value[0]!
    const thought = original.thinkingBlocks![0]
    const activity = original.activities[0]
    receive({ ...snapshot, cursor: 2, messages: [{ ...message, content: '已有正文，继续输出' }, {
      id: 'supplement', turnId: 'turn-one', role: 'USER', content: '补充要求', status: 'COMPLETED',
      startedAt: 1500, activities: [], thinkingBlocks: [],
    }] })
    expect(chat.messages.value[0]).toBe(original)
    expect(chat.messages.value[0]!.thinkingBlocks![0]).toBe(thought)
    expect(chat.messages.value[0]!.activities[0]).toBe(activity)
    expect(chat.messages.value.map(item => item.content)).toEqual(['已有正文，继续输出', '补充要求'])
    wrapper.unmount()
  })

  it('运行时发送明确目标轮次，接收失败不会清掉输入', async () => {
    const wrapper = mountChat('conversation')
    await flushPromises()
    receive(snapshot)
    api.submitChat.mockRejectedValue(new TypeError('网络中断'))
    expect(await chat.send('只看华东')).toBe(false)
    expect(api.submitChat).toHaveBeenCalledWith(expect.objectContaining({ convId: 'conversation',
      observedTurnId: 'turn-one', message: '只看华东', interactionId: expect.any(String) }))
    expect(chat.unresolvedInput.value).toBe(true)
    // 同内容重试沿用原交互标识，是幂等重发；新消息被未确认输入阻止
    expect(await chat.send('只看华东')).toBe(false)
    expect(api.submitChat).toHaveBeenCalledTimes(2)
    expect(api.submitChat.mock.calls[0]![0].interactionId).toBe(api.submitChat.mock.calls[1]![0].interactionId)
    expect(await chat.send('换一个问题')).toBe(false)
    expect(api.submitChat).toHaveBeenCalledTimes(2)
    expect(chat.busy.value).toBe(true)
    wrapper.unmount()
  })

  it('新对话首发不监听会话，建档后才建立订阅', async () => {
    const wrapper = mountChat()
    await flushPromises()
    expect(api.subscribeSession).not.toHaveBeenCalled()
    api.submitChat.mockResolvedValue({ disposition: 'NEW_TURN', turnId: 'turn-one',
      convId: 'any', sessionId: 'native-session', interactionId: 'any', inputId: 'any' })
    expect(await chat.send('你好')).toBe(true)
    expect(api.subscribeSession).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('首发切换路由后才订阅，后续发送不重复触发建档回调', async () => {
    const created = vi.fn()
    const wrapper = mount(defineComponent({ setup() {
      chat = useChat('test-user', undefined, { onCreated: created })
      return () => h('div')
    } }))
    api.submitChat.mockResolvedValue({ disposition: 'NEW_TURN', turnId: 'turn-one' })
    expect(await chat.send('你好')).toBe(true)
    expect(created).toHaveBeenCalledTimes(1)
    expect(api.subscribeSession).not.toHaveBeenCalled()
    expect(api.readSession).not.toHaveBeenCalled()
    const id = created.mock.calls[0]![0]
    wrapper.unmount()

    const routed = mountChat(id)
    await flushPromises()
    expect(api.subscribeSession).toHaveBeenCalledTimes(1)
    expect(await chat.send('继续')).toBe(true)
    expect(created).toHaveBeenCalledTimes(1)
    expect(api.subscribeSession).toHaveBeenCalledTimes(1)
    routed.unmount()
  })

  it('首发响应丢失后可重试，重试沿用同一对话标识和交互标识', async () => {
    const wrapper = mountChat()
    await flushPromises()
    api.submitChat.mockRejectedValueOnce(new TypeError('网络中断'))
    expect(await chat.send('你好')).toBe(false)
    expect(chat.unresolvedInput.value).toBe(true)
    const first = api.submitChat.mock.calls[0]![0]
    api.submitChat.mockResolvedValue({ disposition: 'NEW_TURN', turnId: 'turn-one' })
    expect(await chat.send('你好')).toBe(true)
    const second = api.submitChat.mock.calls[1]![0]
    expect(second.convId).toBe(first.convId)
    expect(second.interactionId).toBe(first.interactionId)
    wrapper.unmount()
  })
})
