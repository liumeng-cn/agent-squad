// @vitest-environment jsdom
import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { mount } from '@vue/test-utils'
import MessageThread from '../MessageThread.vue'
import { useConversationsStore } from '../../stores/conversations'

describe('原消息下的要求修改反馈', () => {
  beforeEach(() => setActivePinia(createPinia()))
  it('等待不冒充生效，应用与拒绝均显示在来源消息下', async () => {
    const store = useConversationsStore()
    store.messages = [{ messageId: 'user', sequence: 1, role: 'USER', text: '改成活泼风格', revision: 0,
      completed: true, sourceMessageId: null, executionId: 'reply', taskId: null, occurredAt: '', artifacts: [] }]
    store.directiveHistory = [{ directiveId: 'change', taskId: 'task', sourceMessageId: 'user', status: 'ACCEPTED',
      waitReason: 'WAITING_OPERATION', appliedVersion: null, goalRevision: null, reasonCode: null, reasonMessage: null }]
    const wrapper = mount(MessageThread)
    expect(wrapper.find('.from-user').text()).toContain('正在等待原操作结果，尚未应用')
    expect(wrapper.text()).not.toContain('已应用你的修改')
    store.directiveHistory[0] = { ...store.directiveHistory[0]!, status: 'APPLIED', waitReason: 'NONE', appliedVersion: 2, goalRevision: 1 }
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.from-user').text()).toContain('已应用你的修改')
    store.directiveHistory[0] = { ...store.directiveHistory[0]!, status: 'REJECTED', appliedVersion: null,
      goalRevision: null, reasonCode: 'GOAL_CHANGED', reasonMessage: '要求已经更新，请重新确认' }
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.from-user').text()).toContain('这次修改未应用：要求已经更新，请重新确认')
    expect(wrapper.text()).not.toContain('GOAL_CHANGED')
    wrapper.unmount()
  })
})
