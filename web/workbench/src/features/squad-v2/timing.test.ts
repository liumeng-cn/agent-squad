// @vitest-environment jsdom
import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import ChatExecution from './components/ChatExecution.vue'
import ChatMessageList from './components/ChatMessageList.vue'
import type { ChatMessage, ChatStatus } from './chatTypes'

const message: ChatMessage = { id: 'reply', role: 'assistant', content: '完成', status: 'RUNNING',
  startedAt: 1000, activities: [] }

describe('执行耗时', () => {
  it.each<ChatStatus>(['COMPLETED', 'FAILED', 'INTERRUPTED', 'WAITING'])('进入 %s 后耗时保持不变', async status => {
    const wrapper = mount(ChatExecution, { props: { message: { ...message }, now: 4000 } })
    expect(wrapper.get('.duration').text()).toBe('3.0 秒')
    await wrapper.setProps({ message: { ...message, status, finishedAt: 6000 }, now: 8000 })
    expect(wrapper.get('.duration').text()).toBe('5.0 秒')
    await wrapper.setProps({ now: 20000 })
    expect(wrapper.get('.duration').text()).toBe('5.0 秒')
    wrapper.unmount()
  })

  it('历史结束时间未知时不使用当前时间继续计时', () => {
    const wrapper = mount(ChatExecution, { props: { message: { ...message, status: 'COMPLETED' }, now: 20000 } })
    expect(wrapper.get('.duration').text()).toBe('— 秒')
    wrapper.unmount()
  })

  it('没有运行中的回复时释放刷新计时器，恢复运行时重新启动', async () => {
    vi.useFakeTimers()
    vi.stubGlobal('ResizeObserver', class { observe() {} disconnect() {} })
    const wrapper = mount(ChatMessageList, { props: { messages: [{ ...message }] },
      global: { stubs: { ChatMessageItem: true } } })
    try {
      expect(vi.getTimerCount()).toBe(1)
      await wrapper.setProps({ messages: [{ ...message, status: 'COMPLETED', finishedAt: 6000 }] })
      expect(vi.getTimerCount()).toBe(0)
      await wrapper.setProps({ messages: [{ ...message }] })
      expect(vi.getTimerCount()).toBe(1)
      wrapper.unmount()
      expect(vi.getTimerCount()).toBe(0)
    } finally {
      wrapper.unmount()
      vi.unstubAllGlobals()
      vi.useRealTimers()
    }
  })
})
