// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ChatSession } from '../model/chatTypes'
import { useChat } from './useChat'

const api = vi.hoisted(() => ({ submitChat: vi.fn(), subscribeSession: vi.fn(), readSession: vi.fn(),
  interruptChat: vi.fn(), resumeChat: vi.fn() }))
vi.mock('../api/chatApi', () => api)

let chat: ReturnType<typeof useChat>
let receive: (snapshot: ChatSession) => void
let observedSignal: AbortSignal
const snapshot: ChatSession = { sessionId: 'stable-session', cursor: 1, inputCursor: 1,
  turnId: 'turn-one', runId: 'run-one', status: 'RUNNING', messages: [], inputs: [] }

function mountChat() {
  return mount(defineComponent({ setup() { chat = useChat(); return () => h('div') } }))
}

beforeEach(() => {
  vi.resetAllMocks()
  sessionStorage.clear()
  sessionStorage.setItem('squad-v2.sessionId', 'stable-session')
  api.readSession.mockResolvedValue(snapshot)
  api.subscribeSession.mockImplementation((_id: string, signal: AbortSignal, callback: typeof receive) => {
    receive = callback
    observedSignal = signal
    return new Promise<void>(resolve => signal.addEventListener('abort', () => resolve()))
  })
})
afterEach(() => vi.useRealTimers())

describe('会话执行与观察分离', () => {
  it('停止后允许发送新问题，接收后旧继续按钮不被迟到快照恢复', async () => {
    const wrapper = mountChat()
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

  it('刷新复用会话，取消订阅不发停止或重提请求', async () => {
    const wrapper = mountChat()
    await flushPromises()
    receive(snapshot)
    expect(chat.busy.value).toBe(true)
    wrapper.unmount()
    expect(observedSignal.aborted).toBe(true)
    expect(api.submitChat).not.toHaveBeenCalled()
    expect(api.interruptChat).not.toHaveBeenCalled()
    expect(sessionStorage.getItem('squad-v2.sessionId')).toBe('stable-session')
  })

  it('快照的结束时间传给页面，重复快照不会重新计时', async () => {
    const wrapper = mountChat()
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
    const wrapper = mountChat()
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

  it('清空后使用新会话，旧订阅迟到的快照不能恢复旧消息', async () => {
    const wrapper = mountChat()
    await flushPromises()
    const oldReceive = receive
    const oldSignal = observedSignal
    receive({ ...snapshot, status: 'COMPLETED' })
    chat.clear()
    expect(sessionStorage.getItem('squad-v2.sessionId')).not.toBe('stable-session')
    expect(oldSignal.aborted).toBe(true)
    oldReceive({ ...snapshot, messages: [{ id: 'old', turnId: 'turn-one', role: 'ASSISTANT',
      content: '旧内容', status: 'COMPLETED', startedAt: 1000, activities: [], thinkingBlocks: [] }] })
    expect(chat.messages.value).toEqual([])
    expect(api.submitChat).not.toHaveBeenCalled()
    expect(api.interruptChat).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('执行中不能清空并丢失正在运行的任务入口', async () => {
    const wrapper = mountChat()
    await flushPromises()
    receive(snapshot)
    chat.clear()
    expect(sessionStorage.getItem('squad-v2.sessionId')).toBe('stable-session')
    wrapper.unmount()
  })

  it('运行时发送明确目标轮次，接收失败不会清掉输入', async () => {
    const wrapper = mountChat()
    await flushPromises()
    receive(snapshot)
    api.submitChat.mockRejectedValue(new TypeError('网络中断'))
    expect(await chat.send('只看华东')).toBe(false)
    expect(api.submitChat).toHaveBeenCalledWith(expect.objectContaining({ sessionId: 'stable-session',
      observedTurnId: 'turn-one', message: '只看华东', interactionId: expect.any(String) }))
    expect(chat.unresolvedInput.value).toBe(true)
    expect(await chat.send('只看华东')).toBe(false)
    expect(api.submitChat).toHaveBeenCalledTimes(1)
    expect(chat.busy.value).toBe(true)
    wrapper.unmount()
  })
})
