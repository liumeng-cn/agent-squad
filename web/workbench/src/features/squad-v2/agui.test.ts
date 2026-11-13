import { describe, expect, it } from 'vitest'
import { projectAgui } from './agui'
const request = { sessionId: 's', requestId: 'r', message: '你好' }
describe('AG-UI 页面投影', () => {
  it('区分工具参数结束与工具返回', () => {
    expect(projectAgui({ type: 'TOOL_CALL_END', toolCallId: 't' }, request)).toEqual([])
    expect(projectAgui({ type: 'TOOL_CALL_RESULT', toolCallId: 't' }, request)[0])
      .toMatchObject({ type: 'ACTIVITY_UPDATED', status: 'COMPLETED', activityId: 't' })
  })
  it('隔离运行并区分等待与成功', () => {
    expect(projectAgui({ type: 'RUN_FINISHED', runId: 'other' }, request)).toEqual([])
    expect(projectAgui({ type: 'RUN_FINISHED', outcome: { type: 'interrupt' } }, request)[0])
      .toMatchObject({ status: 'WAITING' })
    expect(projectAgui({ type: 'RUN_ERROR', message: '失败' }, request)[0])
      .toMatchObject({ type: 'MESSAGE_FAILED', status: 'FAILED' })
  })
})
