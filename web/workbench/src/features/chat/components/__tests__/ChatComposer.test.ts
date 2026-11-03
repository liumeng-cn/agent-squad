// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ChatComposer from '../ChatComposer.vue'
import { useAuthStore } from '../../../auth/stores/auth'
import { useConversationsStore } from '../../stores/conversations'
import type { AuthView } from '../../../../shared/api/types'
import { ApiError } from '../../../../shared/api/http'

beforeEach(() => { setActivePinia(createPinia()); sessionStorage.clear(); useAuthStore().user = { userId: 'A' } as AuthView })
describe('会话草稿与发送身份', () => {
  it('切换会话后恢复各自草稿', async () => {
    const wrapper = mount(ChatComposer, { props: { conversationId: 'one', draftRevision: 0 } })
    await wrapper.find('textarea').setValue('会话一')
    await wrapper.setProps({ conversationId: 'two' })
    expect((wrapper.find('textarea').element as HTMLTextAreaElement).value).toBe('')
    await wrapper.find('textarea').setValue('会话二')
    await wrapper.setProps({ conversationId: 'one' })
    expect((wrapper.find('textarea').element as HTMLTextAreaElement).value).toBe('会话一')
  })
  it('不确定发送重试复用同一幂等键', async () => {
    const send = vi.spyOn(useConversationsStore(), 'send').mockRejectedValue(new ApiError(500, '失败'))
    const wrapper = mount(ChatComposer, { props: { conversationId: 'one', draftRevision: 0 } })
    await wrapper.find('textarea').setValue('内容')
    await wrapper.find('.send-message-button').trigger('click')
    await wrapper.find('.send-message-button').trigger('click')
    expect(send).toHaveBeenCalledTimes(2)
    expect(send.mock.calls[0]?.[2]).toBe(send.mock.calls[1]?.[2])
  })
  it('登出清理用户草稿且另一用户看不到旧文本', async () => {
    const wrapper = mount(ChatComposer, { props: { conversationId: 'one', draftRevision: 0 } })
    await wrapper.find('textarea').setValue('A的草稿')
    useAuthStore().clearUser()
    await wrapper.vm.$nextTick()
    useAuthStore().user = { userId: 'B' } as AuthView
    await wrapper.vm.$nextTick()
    expect((wrapper.find('textarea').element as HTMLTextAreaElement).value).toBe('')
    expect(sessionStorage.getItem('agentsquad:draft:A:one')).toBeNull()
  })
})
