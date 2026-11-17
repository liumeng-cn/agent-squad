import type { ChatMessage } from './chatTypes'

/** 合并同轮仅含过程的中间片段，保留正文和中断边界，不修改接口快照。 */
export function presentMessages(messages: ChatMessage[]): ChatMessage[] {
  const result: ChatMessage[] = []
  for (const message of messages) {
    const previous = result.at(-1)
    if (previous?.role === 'assistant' && message.role === 'assistant'
      && previous.turnId && previous.turnId === message.turnId
      && previous.status === 'COMPLETED' && !previous.content.trim() && !previous.error) {
      result[result.length - 1] = {
        ...message,
        id: previous.id,
        startedAt: previous.startedAt,
        activities: [...previous.activities, ...message.activities],
        thinkingBlocks: [...(previous.thinkingBlocks ?? []), ...(message.thinkingBlocks ?? [])],
      }
    } else {
      result.push(message)
    }
  }
  return result
}
