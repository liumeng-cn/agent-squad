import { computed, ref } from 'vue'
import type { SquadMsg } from '../../../shared/api/squadTypes'
import { openSquadTestStream, type SquadTestStreamRequest } from '../api/squadTestApi'
import { SseDecoder } from '../model/sseDecoder'

export type SquadStreamStatus =
  | 'IDLE'
  | 'CONNECTING'
  | 'STREAMING'
  | 'COMPLETED'
  | 'DISCONNECTED'
  | 'ERROR'

export function useSquadEventStream(onEvent: (event: SquadMsg) => void) {
  const status = ref<SquadStreamStatus>('IDLE')
  const error = ref<string | null>(null)
  const isBusy = computed(() => status.value === 'CONNECTING' || status.value === 'STREAMING')
  let controller: AbortController | null = null

  async function start(request: SquadTestStreamRequest): Promise<void> {
    if (isBusy.value) return

    const activeController = new AbortController()
    controller = activeController
    status.value = 'CONNECTING'
    error.value = null

    let reader: ReadableStreamDefaultReader<Uint8Array> | null = null
    const parser = new SseDecoder()
    const textDecoder = new TextDecoder()

    const consumeFrames = (frames: ReturnType<SseDecoder['push']>) => {
      for (const frame of frames) {
        if (!frame.data) continue
        try {
          const value: unknown = JSON.parse(frame.data)
          if (!isSquadMsg(value)) throw new Error('事件信封缺少必要字段')
          const event = value as SquadMsg
          if (event.sequence === null && /^\d+$/.test(frame.id)) {
            event.sequence = Number(frame.id)
          }
          // SSE event 名称只是传输层标签；业务类型始终以 SquadMsg.eventType 为准。
          onEvent(event)
        } catch (cause) {
          error.value = cause instanceof Error
            ? '事件解析失败：' + cause.message
            : '收到无法解析的 SSE 事件'
        }
      }
    }

    try {
      const response = await openSquadTestStream(request, activeController.signal)
      if (activeController.signal.aborted) {
        status.value = 'DISCONNECTED'
        return
      }

      reader = response.body!.getReader()
      status.value = 'STREAMING'

      while (true) {
        const result = await reader.read()
        if (result.done) break
        consumeFrames(parser.push(textDecoder.decode(result.value, { stream: true })))
      }

      consumeFrames(parser.push(textDecoder.decode()))
      consumeFrames(parser.flush())
      status.value = activeController.signal.aborted ? 'DISCONNECTED' : 'COMPLETED'
    } catch (cause) {
      if (activeController.signal.aborted) {
        status.value = 'DISCONNECTED'
      } else {
        status.value = 'ERROR'
        error.value = cause instanceof Error ? cause.message : '事件流连接失败'
      }
    } finally {
      reader?.releaseLock()
      if (controller === activeController) controller = null
    }
  }

  function disconnect(): void {
    controller?.abort()
  }

  function reset(): void {
    disconnect()
    status.value = 'IDLE'
    error.value = null
  }

  return {
    status,
    error,
    isBusy,
    start,
    disconnect,
    reset,
  }
}

function isSquadMsg(value: unknown): value is SquadMsg {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false
  const event = value as Record<string, unknown>
  return typeof event.eventType === 'string'
    && typeof event.eventId === 'string'
    && event.payload !== null
    && typeof event.payload === 'object'
    && !Array.isArray(event.payload)
}
