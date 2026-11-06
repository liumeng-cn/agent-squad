import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useConversationsStore } from '../conversations'
import { decodeReplyPreview } from '../replyPreviews'
import type { ChatSnapshot } from '../../../../shared/api/chatApi'

const preview = (streamId = 'stream-a', revision = 1, text = '第一段') => ({ schemaVersion: 2, executionId: 'reply', sourceMessageId: 'user', runId: 'run', streamId, revision, offset: 0, text, phase: 'ANSWERING', replace: true, preview: true })
describe('临时回答预览', () => {
  beforeEach(() => setActivePinia(createPinia()))
  function active() {
    const store = useConversationsStore()
    store.snapshot = { cursor: 'one:1:7', activeExecutions: [{ executionId: 'reply', sourceMessageId: 'user', status: 'RUNNING', version: 1, allowedActions: ['STOP'] }], messages: [], executions: [] } as unknown as ChatSnapshot
    return store
  }
  it('全文替换并拒绝重复和迟到修订，不推进持久游标', () => {
    const store = active()
    store.applyPreview(preview())
    store.applyPreview(preview('stream-a', 2, '第一段第二段'))
    store.applyPreview(preview('stream-a', 1, '旧内容'))
    expect(store.previews.reply?.text).toBe('第一段第二段')
    expect(store.snapshot?.cursor).toBe('one:1:7')
    expect(store.messages).toEqual([])
  })
  it('新模型调用替换旧草稿，旧调用迟到块不串入新正文', () => {
    const store = active()
    store.applyPreview(preview())
    store.applyPreview(preview('stream-b', 1, '新调用'))
    store.applyPreview(preview('stream-a', 3, '旧调用迟到'))
    expect(store.previews.reply?.text).toBe('新调用')
  })
  it('未知执行、错误来源和终态均不接受临时正文', () => {
    const store = active()
    store.applyPreview({ ...preview(), sourceMessageId: 'other' })
    store.applyPreview({ ...preview(), executionId: 'unknown' })
    expect(store.previews).toEqual({})
    store.snapshot!.activeExecutions = []
    store.applyPreview(preview())
    expect(store.previews).toEqual({})
  })
  it('正式答案替换预览，切换会话清空所有临时内容', () => {
    const store = active()
    store.applyPreview(preview())
    expect(store.applyEventPayload({ type: 'message.completed', payload: { schemaVersion: 2, message: { messageId: 'answer', sequence: 2, role: 'ASSISTANT', text: '正式答案', revision: 0, completed: true, sourceMessageId: 'user', executionId: 'reply', taskId: null, occurredAt: '2026-11-05T01:00:00Z', artifacts: [] } } } as never)).toBe(true)
    expect(store.previews).toEqual({})
    store.applyPreview(preview('late'))
    expect(store.previews).toEqual({})
    store.startDraft()
    expect(store.previews).toEqual({})
  })
  it('不接受越界文本和非替换协议', () => {
    expect(decodeReplyPreview({ ...preview(), text: 'a'.repeat(65537) })).toBeNull()
    expect(decodeReplyPreview({ ...preview(), offset: 1 })).toBeNull()
    expect(decodeReplyPreview({ ...preview(), preview: false })).toBeNull()
    expect(decodeReplyPreview({ ...preview(), phase: 'DONE' })).toBeNull()
  })
  it('推理阶段只保留阶段心跳，正文到达后按同一流替换', () => {
    const store = active()
    store.applyPreview({ ...preview(undefined, 1, ''), phase: 'THINKING' })
    expect(store.previews.reply?.phase).toBe('THINKING')
    expect(store.previews.reply?.text).toBe('')
    store.applyPreview(preview('stream-a', 2, '正文开始'))
    expect(store.previews.reply?.phase).toBe('ANSWERING')
    expect(store.previews.reply?.text).toBe('正文开始')
  })
})
