// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import ManagementDialog from './ManagementDialog.vue'
import { confirmManagement } from './confirmManagement'

vi.mock('./confirmManagement', () => ({ confirmManagement: vi.fn() }))
beforeEach(() => vi.clearAllMocks())

it('keeps unsaved content when closing is cancelled and closes after confirmation', async () => {
  vi.mocked(confirmManagement).mockResolvedValueOnce(false).mockResolvedValueOnce(true)
  const view = mount(ManagementDialog, {
    props: { title: '编辑配置', dirty: true, busy: false },
    global: { stubs: { teleport: true } },
    slots: { default: '<p>未保存内容</p>' },
  })
  await flushPromises()
  await view.find('.el-dialog__headerbtn').trigger('click')
  await flushPromises()
  expect(view.emitted('close')).toBeUndefined()
  expect(view.text()).toContain('未保存内容')
  await view.find('.el-dialog__headerbtn').trigger('click')
  await flushPromises()
  expect(view.emitted('close')).toHaveLength(1)
  view.unmount()
})

it('prevents cancellation while saving', async () => {
  const view = mount(ManagementDialog, {
    props: { title: '编辑配置', dirty: true, busy: true },
    global: { stubs: { teleport: true } },
  })
  await flushPromises()
  expect(view.find('.el-dialog__headerbtn').exists()).toBe(false)
  expect(view.find('.el-dialog__footer button').attributes('disabled')).toBeDefined()
  expect(confirmManagement).not.toHaveBeenCalled()
  view.unmount()
})
