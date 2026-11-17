import { describe, expect, it } from 'vitest'
import { presentMessages } from './messagePresentation'
import type { ChatMessage } from './chatTypes'

const message = (id: string, content = '', extra: Partial<ChatMessage> = {}): ChatMessage => ({
  id, turnId: 'turn', role: 'assistant', content, status: 'COMPLETED', startedAt: 100,
  finishedAt: 200, activities: [], thinkingBlocks: [], ...extra,
})

describe('回复过程片段展示', () => {
  it('合并连续空白过程和最终正文并保留工具思考及总耗时', () => {
    const source = [message('first', '\n\n', {
      activities: [{ id: 'tool', kind: 'TOOL', title: '读取资料', status: 'COMPLETED', startedAt: 100 }],
      thinkingBlocks: [{ modelCallId: 'model', content: '过程记录' }],
    }), message('second', '   '), message('final', '最终正文', { startedAt: 500, finishedAt: 900 })]
    const result = presentMessages(source)
    expect(result).toHaveLength(1)
    expect(result[0]).toMatchObject({ id: 'first', content: '最终正文', startedAt: 100, finishedAt: 900 })
    expect(result[0]!.activities).toHaveLength(1)
    expect(result[0]!.thinkingBlocks).toHaveLength(1)
    expect(source[0]!.content).toBe('\n\n')
  })

  it('流式回复保留首片段标识且展示最新状态', () => {
    const result = presentMessages([message('first'), message('next', '正在回复', { status: 'RUNNING' })])
    expect(result).toHaveLength(1)
    expect(result[0]).toMatchObject({ id: 'first', status: 'RUNNING', content: '正在回复' })
  })

  it('不合并已有正文或失败中断片段', () => {
    for (const first of [message('first', '中间说明'), message('first', '', { status: 'FAILED' }),
      message('first', '', { status: 'INTERRUPTED' })]) {
      expect(presentMessages([first, message('next', '下一段')])).toHaveLength(2)
    }
  })

  it('不跨用户消息或不同轮次合并', () => {
    expect(presentMessages([message('first'), message('next', '', { turnId: 'other' })])).toHaveLength(2)
    expect(presentMessages([message('first'), message('user', '补充', { role: 'user' }), message('next')])).toHaveLength(3)
  })
})
