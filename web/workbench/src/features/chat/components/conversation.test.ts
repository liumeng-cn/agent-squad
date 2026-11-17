// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ChatComposer from './ChatComposer.vue'

afterEach(() => vi.restoreAllMocks())

describe('运行中的补充输入', () => {
  it('运行时可发送补充；确认接收前保留草稿', async () => {
    let accept!: (value: boolean) => void
    const sendMessage = vi.fn(() => new Promise<boolean>(resolve => { accept = resolve }))
    const wrapper = mount(ChatComposer, { props: { busy: true, stopping: false, disabled: false, sendMessage } })
    await wrapper.get('textarea').setValue('只统计本月')
    await wrapper.get('form').trigger('submit')
    expect(sendMessage).toHaveBeenCalledWith('只统计本月')
    expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('只统计本月')
    accept(true)
    await flushPromises()
    expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('')
    wrapper.unmount()
  })

  it('拒绝接收时保留草稿；停止按钮独立于发送按钮', async () => {
    const wrapper = mount(ChatComposer, { props: { busy: true, stopping: false, disabled: false,
      sendMessage: vi.fn(async () => false) } })
    await wrapper.get('textarea').setValue('补充条件')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('补充条件')
    await wrapper.get('[aria-label="停止执行"]').trigger('click')
    expect(wrapper.emitted('stop')).toHaveLength(1)
    wrapper.unmount()
  })
})
