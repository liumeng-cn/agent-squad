// @vitest-environment jsdom
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import ActivityTimeline from '../ActivityTimeline.vue'
import { useConversationsStore } from '../../stores/conversations'
import type { ExecutionActivity } from '../../../../shared/api/activities'

const activity = (id: string, version = 1): ExecutionActivity => ({
  activityId: id, sourceMessageId: 'message', executionId: 'execution', taskId: null, jobId: null,
  planVersion: 0, type: 'MODEL_CALL', group: 'EXECUTION', status: 'RUNNING', waitReason: null,
  summary: '模型处理', startedAt: '2026-11-07T01:00:00Z', endedAt: null, version,
})

describe('实际处理过程', () => {
  it('完成后折叠，手动展开不被后续活动刷新收回', async () => {
    const wrapper = mount(ActivityTimeline, { props: { activities: [activity('one')], completed: false } })
    expect(wrapper.find('details').element.open).toBe(true)
    await wrapper.setProps({ completed: true })
    expect(wrapper.find('details').element.open).toBe(false)
    wrapper.find('details').element.open = true
    await wrapper.find('details').trigger('toggle')
    await wrapper.setProps({ activities: [{ ...activity('one', 2), status: 'SUCCEEDED' }] })
    expect(wrapper.find('details').element.open).toBe(true)
    expect(wrapper.text()).toContain('已完成')
  })

  it('审批等待说明用户下一步动作，摘要按文本显示', () => {
    const wrapper = mount(ActivityTimeline, { props: { activities: [{ ...activity('one'),
      status: 'WAITING', waitReason: 'APPROVAL', summary: '<script>private</script>' }], completed: false } })
    expect(wrapper.text()).toContain('等待你确认')
    expect(wrapper.find('script').exists()).toBe(false)
  })

  it('活动版本防止回退，并行活动独立更新且不触发步骤重读', () => {
    setActivePinia(createPinia())
    const store = useConversationsStore()
    const push = (value: ExecutionActivity) => store.applyEventPayload({ type: 'activity.updated',
      payload: { schemaVersion: 2, activity: value } } as never)
    expect(push(activity('one'))).toBe(true)
    push(activity('two'))
    push({ ...activity('one', 3), status: 'SUCCEEDED' })
    push(activity('one', 2))
    expect(store.activities).toHaveLength(2)
    expect(store.activities.find(a => a.activityId === 'one')?.status).toBe('SUCCEEDED')
    expect(store.activities.find(a => a.activityId === 'two')?.status).toBe('RUNNING')
    expect(store.stepsVersion).toBe(0)
    store.startDraft()
    expect(store.activities).toEqual([])
  })
})
