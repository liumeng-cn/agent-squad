// @vitest-environment jsdom
import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import TaskSteps from '../TaskSteps.vue'
import type { WorkerJob, WorkerJobStatus } from '../../../../shared/api/types'
const { getJobs } = vi.hoisted(() => ({ getJobs: vi.fn() }))
vi.mock('../../../../shared/api/tasksApi', () => ({ getTaskJobs: getJobs }))
function job(nodeKey: string, status: WorkerJobStatus, title: string): WorkerJob {
  return { jobId: nodeKey, taskId: 'task', nodeKey, planVersion: 1, workerKey: 'writer', workerVersion: '1', status, version: 1, title, dependsOn: [] }
}
beforeEach(() => getJobs.mockReset())
describe('真实工作步骤', () => {
  it('最终回答完成后折叠，手动展开后不会被后续进度刷新收回', async () => {
    getJobs.mockResolvedValue({ items: [job('one', 'SUCCEEDED', '整理结果')] })
    const wrapper = mount(TaskSteps, { props: { taskId: 'task', status: 'RUNNING', revision: '1', answerCompleted: false } })
    await flushPromises()
    expect(wrapper.find('details').element.open).toBe(true)
    await wrapper.setProps({ status: 'SUCCEEDED' })
    expect(wrapper.find('details').element.open).toBe(true)
    await wrapper.setProps({ answerCompleted: true })
    expect(wrapper.find('details').element.open).toBe(false)
    wrapper.find('details').element.open = true
    await wrapper.find('details').trigger('toggle')
    await wrapper.setProps({ revision: '2' })
    await flushPromises()
    expect(wrapper.find('details').element.open).toBe(true)
  })

  it('旧版步骤单独展示，能返回当前安排并保留复用说明', async () => {
    getJobs.mockResolvedValueOnce({ items: [{ ...job('current', 'SUCCEEDED', '当前结果'), planVersion: 2, required: false, reusedFromJobId: 'previous' }],
      planVersion: 2, currentPlanVersion: 2, acceptanceChecks: [] })
    const wrapper = mount(TaskSteps, { props: { taskId: 'task', status: 'RUNNING', revision: '1' } })
    await flushPromises()
    expect(wrapper.text()).toContain('沿用已有结果')
    expect(wrapper.text()).toContain('可选工作')
    getJobs.mockResolvedValueOnce({ items: [job('old', 'SUPERSEDED', '旧版步骤')], planVersion: 1, currentPlanVersion: 2, acceptanceChecks: [] })
    await wrapper.findAll('button').find(button => button.text() === '查看上一版')!.trigger('click')
    await flushPromises()
    expect(getJobs).toHaveBeenLastCalledWith('task', 1)
    expect(wrapper.text()).toContain('过去的安排 · 第 1 版')
    expect(wrapper.text()).toContain('0/1 项已完成')
    expect(wrapper.text()).not.toContain('当前结果')
    getJobs.mockResolvedValueOnce({ items: [job('current', 'SUCCEEDED', '当前结果')], planVersion: 2, currentPlanVersion: 2, acceptanceChecks: [] })
    await wrapper.findAll('button').find(button => button.text() === '返回当前安排')!.trigger('click')
    await flushPromises()
    expect(getJobs).toHaveBeenLastCalledWith('task', undefined)
    expect(wrapper.text()).toContain('当前安排 · 第 2 版')
  })
  it('分别显示程序、模型和用户检查，不把未知写成通过', async () => {
    getJobs.mockResolvedValue({ items: [job('one', 'SUCCEEDED', '生成短文')], nextCursor: null, acceptanceChecks: [
      { criterionId: 'saved', text: '结果已保存', required: true, verificationType: 'DETERMINISTIC', verdict: 'PASS' },
      { criterionId: 'quality', text: '文字质量', required: true, verificationType: 'MODEL_REVIEW', verdict: 'UNKNOWN' },
      { criterionId: 'confirm', text: '用户认可结果', required: true, verificationType: 'USER_REVIEW', verdict: 'FAIL' },
    ] })
    const wrapper = mount(TaskSteps, { props: { taskId: 'task', status: 'PARTIAL', revision: '3' } })
    await flushPromises()
    expect(wrapper.text()).toContain('程序检查 · 符合要求')
    expect(wrapper.text()).toContain('模型检查 · 尚未确认')
    expect(wrapper.text()).toContain('用户确认 · 未达到要求')
    expect(wrapper.text()).toContain('1/1 项已完成')
    await wrapper.setProps({ taskId: 'other' })
    expect(wrapper.text()).not.toContain('结果已保存')
  })
  it('显示真实说明和失败原因，不把未执行算成完成', async () => {
    getJobs.mockResolvedValue({ items: [job('one', 'FAILED', '读取资料'), job('two', 'SKIPPED', '整理结果')], nextCursor: null })
    const wrapper = mount(TaskSteps, { props: { taskId: 'task', status: 'PARTIAL', revision: '1' } })
    await flushPromises()
    expect(wrapper.text()).toContain('读取资料')
    expect(wrapper.text()).toContain('前一步失败，这一步未执行')
    expect(wrapper.text()).toContain('0/2 项已完成')
  })
  it('新进度先返回后，迟到旧请求不能覆盖它', async () => {
    let resolveOld!: (value: unknown) => void
    getJobs.mockReturnValueOnce(new Promise(resolve => { resolveOld = resolve }))
    const wrapper = mount(TaskSteps, { props: { taskId: 'task', status: 'RUNNING', revision: '1' } })
    getJobs.mockResolvedValueOnce({ items: [job('new', 'SUCCEEDED', '新的安排')], nextCursor: null })
    await wrapper.setProps({ revision: '2' })
    await flushPromises()
    resolveOld({ items: [job('old', 'RUNNING', '旧安排')], nextCursor: null })
    await flushPromises()
    expect(wrapper.text()).toContain('新的安排')
    expect(wrapper.text()).not.toContain('旧安排')
    expect(wrapper.text()).toContain('1/1 项已完成')
  })
  it('终态且没有任何步骤与检查时不渲染步骤区块', async () => {
    getJobs.mockResolvedValue({ items: [], nextCursor: null, acceptanceChecks: [] })
    const wrapper = mount(TaskSteps, { props: { taskId: 'task', status: 'SUCCEEDED', revision: '1' } })
    await flushPromises()
    expect(wrapper.find('details').exists()).toBe(false)
    expect(getJobs).toHaveBeenCalledWith('task', undefined)
  })
  it('终态但有步骤时保留区块，便于回看历史安排', async () => {
    getJobs.mockResolvedValue({ items: [job('one', 'SUCCEEDED', '整理结果')], nextCursor: null })
    const wrapper = mount(TaskSteps, { props: { taskId: 'task', status: 'SUCCEEDED', revision: '1' } })
    await flushPromises()
    expect(wrapper.find('details').exists()).toBe(true)
    expect(wrapper.text()).toContain('1/1 项已完成')
  })
  it('读取失败可重试，不编造步骤', async () => {
    getJobs.mockRejectedValueOnce(new Error('offline'))
    const wrapper = mount(TaskSteps, { props: { taskId: 'task', status: 'RUNNING', revision: '1' } })
    await flushPromises()
    expect(wrapper.findAll('li')).toHaveLength(0)
    expect(wrapper.text()).toContain('暂时无法读取进度')
    getJobs.mockResolvedValueOnce({ items: [job('one', 'RUNNING', '开始整理')], nextCursor: null })
    await wrapper.find('button').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('开始整理')
  })
})
