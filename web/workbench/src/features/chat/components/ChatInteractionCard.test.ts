// @vitest-environment jsdom
import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import ChatInteractionCard from './ChatInteractionCard.vue'

describe('用户问题卡片', () => {
  it('展示问题和选项，选择后需提交才发送答案', async () => {
    const submitAnswer = vi.fn(async () => true)
    const wrapper = mount(ChatInteractionCard, { props: {
      interaction: { requestId: 'q1', turnId: 'turn1', question: '是否继续？', options: ['确认', '取消'] },
      modelValue: '', submitting: false, disabled: false, locked: false, submitAnswer,
    } })
    expect(wrapper.text()).toContain('是否继续？')
    await wrapper.get('button[type="button"]').trigger('click')
    expect(wrapper.emitted('update:modelValue')).toEqual([['确认']])
    expect(submitAnswer).not.toHaveBeenCalled()
    await wrapper.setProps({ modelValue: '取消' })
    await wrapper.get('form').trigger('submit')
    expect(submitAnswer).toHaveBeenCalledWith('q1', '取消')
    wrapper.unmount()
  })

  it('不确定提交结果时锁定答案并允许重试，提交中禁用按钮', async () => {
    const submitAnswer = vi.fn(async () => true)
    const wrapper = mount(ChatInteractionCard, { props: {
      interaction: { requestId: 'q1', turnId: 'turn1', question: '是否继续？', options: ['确认'] },
      modelValue: '确认', submitting: false, disabled: false, locked: true, submitAnswer,
    } })
    expect(wrapper.get('textarea').attributes('disabled')).toBeDefined()
    expect(wrapper.get('button[type="submit"]').text()).toBe('重试提交')
    await wrapper.get('form').trigger('submit')
    expect(submitAnswer).toHaveBeenCalledOnce()
    await wrapper.setProps({ submitting: true })
    await wrapper.get('form').trigger('submit')
    expect(submitAnswer).toHaveBeenCalledOnce()
    wrapper.unmount()
  })
})
