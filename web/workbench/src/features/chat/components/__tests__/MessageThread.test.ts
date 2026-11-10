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
  it.each([
    ['MODEL_BILLING_UNAVAILABLE', '模型余额不足'],
    ['BUDGET_EXHAUSTED', '本次处理额度已用完'],
    ['MODEL_CREDENTIALS_INVALID', '当前模型的调用授权不可用'],
    ['MODEL_UNAVAILABLE', '当前模型不可用'],
    ['MODEL_ACCESS_DENIED', '当前模型未开通或试用已失效'],
  ])('用红字显示 %s 对应的处理建议', (code, expectedText) => {
    const store = fixture()
    store.snapshot!.failures[0].code = code
    const wrapper = mount(MessageThread)
    expect(wrapper.find('.chat-model-error').text()).toContain(expectedText)
    expect(wrapper.text()).not.toContain('Insufficient Balance')
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
  it('失败提示跟随来源消息，目标失败与回答失败使用不同说明', () => {
    const store = fixture()
    store.snapshot = {
      failures: [
        { failureId: 'reply-failure', executionId: 'execution', sourceMessageId: 'user', category: 'INTERNAL', actor: 'SYSTEM', status: 'ACTIVE', safeMessage: '内部原因', allowedActions: [] },
        { failureId: 'task-failure', executionId: null, taskId: 'task', sourceMessageId: 'user', category: 'LOGIC_INVALID', actor: 'SYSTEM', status: 'ACTIVE', safeMessage: '内部原因', allowedActions: [] },
      ],
      activeExecutions: [], interactions: [], deliverables: [], operationIds: [],
    } as unknown as ChatSnapshot
    const wrapper = mount(MessageThread)
    const notes = wrapper.findAll('.chat-service-feedback')
    expect(notes).toHaveLength(2)
    expect(wrapper.findAll('.from-user .chat-service-feedback')).toHaveLength(2)
    expect(wrapper.find('.from-user').text()).toContain('这项工作未能完成')
    expect(wrapper.find('.from-user').text()).toContain('回答服务暂时异常')
    expect(wrapper.text()).not.toContain('内部原因')
  })
  it('步骤跟随各自来源消息，始终位于对应最终回答前', () => {
    const store = fixture()
    store.snapshot = { failures: [], activeExecutions: [], interactions: [], deliverables: [], operationIds: [] } as unknown as ChatSnapshot
    store.executionHistory = []
    store.messages = [
      { messageId: 'user', role: 'USER', text: '第一个要求', artifacts: [] },
      { messageId: 'answer', role: 'ASSISTANT', text: '第一份结果', taskId: 'task', completed: true, artifacts: [] },
      { messageId: 'user2', role: 'USER', text: '第二个要求', artifacts: [] },
      { messageId: 'answer2', role: 'ASSISTANT', text: '第二份结果', taskId: 'task2', completed: true, artifacts: [] },
    ] as never
    store.taskHistory = [
      { taskId: 'task', sourceMessageId: 'user', status: 'SUCCEEDED', version: 2 },
      { taskId: 'task2', sourceMessageId: 'user2', status: 'SUCCEEDED', version: 2 },
    ]
    const wrapper = mount(MessageThread, { global: { stubs: { TaskSteps: { props: ['taskId'], template: '<section class="steps-marker">{{ taskId }}</section>' } } } })
    expect(wrapper.findAll('.chat-messages > article, .chat-messages > .steps-marker').map(node => node.text())).toEqual([
      '第一个要求', 'task', expect.stringContaining('第一份结果'), '第二个要求', 'task2', expect.stringContaining('第二份结果'),
    ])
  })
  it('预览切换终稿复用回答及Markdown节点', async () => {
    const store = fixture()
    store.snapshot = { messages: [], failures: [], activeExecutions: [{ executionId: 'execution', sourceMessageId: 'user', status: 'RUNNING' }], interactions: [], deliverables: [], operationIds: [] } as unknown as ChatSnapshot
    store.messages = [store.messages[0]!]
    store.executionHistory = []
    store.applyPreview({ schemaVersion: 2, executionId: 'execution', sourceMessageId: 'user', runId: 'run', streamId: 'stream', revision: 1, offset: 0, text: '正在生成的正文', phase: 'ANSWERING', replace: true, preview: true })
    const wrapper = mount(MessageThread)
    const article = wrapper.find('.from-assistant').element
    const markdown = wrapper.find('.answer-markdown').element
    store.applyEventPayload({ type: 'message.completed', payload: { schemaVersion: 2, message: { messageId: 'answer', sequence: 2, role: 'ASSISTANT', text: '正式正文', revision: 0, completed: true, sourceMessageId: 'user', executionId: 'execution', taskId: null, occurredAt: '2026-11-06T13:00:00Z', artifacts: [] } } } as never)
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.from-assistant').element).toBe(article)
    expect(wrapper.find('.answer-markdown').element).toBe(markdown)
    expect(wrapper.text()).toContain('正式正文')
    expect(wrapper.text()).not.toContain('正在生成…')
  })
  it('回答进行中显示计时和加载图标，完成后停在最终用时', async () => {
    const store = fixture()
    store.snapshot = { messages: [], failures: [], activeExecutions: [{ executionId: 'execution', sourceMessageId: 'user', status: 'RUNNING' }], interactions: [], deliverables: [], operationIds: [] } as unknown as ChatSnapshot
    store.messages = [{ messageId: 'user', role: 'USER', text: '问题', occurredAt: new Date(Date.now() - 3000).toISOString(), artifacts: [] }] as never
    const wrapper = mount(MessageThread)
    try {
      expect(wrapper.find('.answer-state .answer-spinner').exists()).toBe(true)
      expect(wrapper.find('.answer-elapsed').text()).toMatch(/\d+ 秒/)
      store.snapshot.activeExecutions = []
      store.messages = [
        { messageId: 'user', role: 'USER', text: '问题', occurredAt: '2026-11-06T12:48:46Z', artifacts: [] },
        { messageId: 'answer', role: 'ASSISTANT', text: '回答', completed: true, sourceMessageId: 'user', executionId: 'execution', occurredAt: '2026-11-06T12:51:25Z', artifacts: [] },
      ] as never
      await wrapper.vm.$nextTick()
      expect(wrapper.find('.answer-spinner').exists()).toBe(false)
      expect(wrapper.find('.answer-duration').text()).toBe('用时 2 分 39 秒')
    } finally { wrapper.unmount() }
  })
  it('实际内容增长时合并自动滚动，用户向上查看后不抢回位置', async () => {
    fixture()
    let notify!: () => void
    const frames: FrameRequestCallback[] = []
    vi.stubGlobal('ResizeObserver', class {
      constructor(callback: () => void) { notify = callback }
      observe() {}
      disconnect() {}
    })
    vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => { frames.push(callback); return frames.length })
    vi.stubGlobal('cancelAnimationFrame', vi.fn())
    const wrapper = mount(MessageThread)
    try {
      const scroller = wrapper.find('.chat-scroll').element as HTMLElement
      let height = 1000
      Object.defineProperty(scroller, 'scrollHeight', { get: () => height })
      Object.defineProperty(scroller, 'clientHeight', { get: () => 200 })
      notify(); notify()
      expect(frames).toHaveLength(1)
      frames.shift()!(0)
      expect(scroller.scrollTop).toBe(1000)
      scroller.scrollTop = 100
      await wrapper.find('.chat-scroll').trigger('scroll')
      height = 1500; notify()
      expect(frames).toHaveLength(0)
      expect(scroller.scrollTop).toBe(100)
    } finally { wrapper.unmount(); vi.unstubAllGlobals() }
  })
})
