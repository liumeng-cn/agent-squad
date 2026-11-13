import type { ChatEvent, ChatMessage, ChatStatus } from './chatTypes'

/** 事件只更新所属回复；工具与模型调用按稳定标识归并。 */
export function applyChatEvent(message: ChatMessage, event: ChatEvent) {
  if (message.id !== event.messageId || message.status !== 'RUNNING') return
  switch (event.type) {
    case 'THINKING_DELTA': {
      const blocks = message.thinkingBlocks ??= []
      let block = blocks.find(item => item.modelCallId === event.modelCallId)
      if (!block) {
        block = { modelCallId: event.modelCallId, content: '' }
        blocks.push(block)
      }
      block.content += event.text
      break
    }
    case 'MESSAGE_DELTA': message.content += event.text; break
    case 'MESSAGE_CONTENT': message.content = event.text; break
    case 'ACTIVITY_UPDATED': {
      let activity = message.activities.find(item => item.id === event.activityId)
      if (!activity) {
        activity = { id: event.activityId, kind: event.kind, title: event.title,
          status: event.status, startedAt: event.timestamp }
        message.activities.push(activity)
      }
      activity.title = event.title
      activity.status = event.status
      if (event.status !== 'RUNNING') activity.finishedAt = event.timestamp
      break
    }
    case 'MESSAGE_COMPLETED':
    case 'MESSAGE_FAILED':
      settleMessage(message, event.status, event.timestamp)
      message.error = event.message || undefined
      break
  }
}

/** 终态时收敛未结束过程项，不将未知调用伪装为成功。 */
export function settleMessage(message: ChatMessage, status: ChatStatus, time = Date.now()) {
  message.status = status
  message.finishedAt = time
  for (const activity of message.activities) {
    if (activity.status === 'RUNNING') {
      activity.status = status === 'WAITING' ? 'WAITING' : 'INTERRUPTED'
      activity.finishedAt = time
    }
  }
}
