import { computed, onBeforeUnmount, ref } from 'vue'
import { sendChat } from './chatApi'
import { applyChatEvent, settleMessage } from './chatState'
import type { ChatMessage } from './chatTypes'

/** 页面级会话，仅保存当前打开页面的消息，不实现历史管理。 */
export function useChat() {
  const sessionId = crypto.randomUUID()
  const messages = ref<ChatMessage[]>([])
  const busy = computed(() => messages.value.some(message => message.status === 'RUNNING'))
  let controller: AbortController | undefined

  async function send(input: string) {
    const text = input.trim()
    if (!text || busy.value) return
    const requestId = crypto.randomUUID()
    const startedAt = Date.now()
    messages.value.push({ id: crypto.randomUUID(), role: 'user', content: text,
      status: 'COMPLETED', startedAt, activities: [] })
    messages.value.push({ id: requestId, role: 'assistant', content: '',
      status: 'RUNNING', startedAt, activities: [] })
    const reply = messages.value[messages.value.length - 1]!
    const currentController = new AbortController()
    controller = currentController
    try {
      await sendChat({ sessionId, requestId, message: text }, currentController.signal, event => {
        if (event.sessionId === sessionId && event.requestId === requestId) applyChatEvent(reply, event)
      })
      if (reply.status === 'RUNNING') throw new Error('连接已结束，但尚未收到执行结果')
    } catch (cause) {
      if (!currentController.signal.aborted) {
        settleMessage(reply, 'FAILED')
        reply.error = cause instanceof Error ? cause.message : '连接失败'
      }
    } finally {
      if (controller === currentController) controller = undefined
    }
  }

  onBeforeUnmount(() => controller?.abort())
  return { messages, busy, send }
}
