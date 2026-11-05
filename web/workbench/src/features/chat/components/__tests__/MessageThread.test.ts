// @vitest-environment jsdom
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MessageThread from '../MessageThread.vue'
import { useConversationsStore } from '../../stores/conversations'
import type { ChatSnapshot } from '../../../../shared/api/chatApi'

describe('服务异常的用户反馈', () => {
  beforeEach(() => setActivePinia(createPinia()))
  function fixture(actions: string[] = []) {
    const store = useConversationsStore()
    store.activeConversationId = 'conversation'
    store.messages = [
      { messageId: 'user', role: 'USER', text: '原问题', executionId: 'execution', artifacts: [] },
      { messageId: 'answer', role: 'ASSISTANT', text: '本次回答暂未完成，请稍后重试。', executionId: 'execution', artifacts: [] },
    ] as never
    store.executionHistory = [{ executionId: 'execution', status: 'FAILED' }] as never
    store.snapshot = { failures: [{ failureId: 'failure', executionId: 'execution', sourceMessageId: 'user', expectedVersion: 3, category: 'ENVIRONMENT', actor: 'SYSTEM', status: 'ACTIVE', safeMessage: 'Insufficient Balance 402', diagnosticId: 'test-diagnostic', allowedActions: actions }], activeExecutions: [], interactions: [], deliverables: [], operationIds: [] } as unknown as ChatSnapshot
    return store
  }
  it('只展示一次友好提示，不把服务端原因或失败占位当成答案', () => {
    fixture()
    const wrapper = mount(MessageThread)
    expect(wrapper.text()).toContain('回答服务暂时异常')
    expect(wrapper.text()).not.toContain('Insufficient Balance')
    expect(wrapper.text()).not.toContain('本次回答暂未完成，请稍后重试。')
    expect(wrapper.findAll('.chat-service-feedback')).toHaveLength(1)
    expect(wrapper.find('.conversation-notice').exists()).toBe(false)
    expect(wrapper.findAll('.copy-answer-button')).toHaveLength(0)
    expect(wrapper.find('details').exists()).toBe(false)
  })
  it('使用后端给出的执行身份和版本重试，不伪造权限', async () => {
    const store = fixture(['RETRY'])
    const retry = vi.spyOn(store, 'retry').mockResolvedValue(undefined)
    const wrapper = mount(MessageThread)
    await wrapper.findAll('button').find(button => button.text() === '重新尝试')!.trigger('click')
    expect(retry).toHaveBeenCalledWith('execution', 3)
  })
  it('不能重试时仍可复制原问题，不复制系统异常', async () => {
    fixture()
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText } })
    const wrapper = mount(MessageThread)
    expect(wrapper.findAll('button').some(button => button.text() === '重新尝试')).toBe(false)
    await wrapper.findAll('button').find(button => button.text() === '复制原消息')!.trigger('click')
    expect(writeText).toHaveBeenCalledWith('原问题')
  })
})
