import type { TaskEvent } from '../api/types'

export interface TaskEventStreamHandlers {
  onEvent: (event: TaskEvent) => void
  onError: () => void
}

/**
 * 单个目标的 SSE 订阅封装。
 *
 * <p>首次连接带 ?after=seq；连接内浏览器自动重连并以 Last-Event-ID 续传。
 * 一旦出现 error 立即关闭连接，交由业务层拉快照判定登录/删除/游标后再按新游标重建。</p>
 */
export class TaskEventStream {
  private source: EventSource | null = null

  constructor(
    private readonly taskId: string,
    private readonly handlers: TaskEventStreamHandlers,
  ) {}

  start(afterSeq: number): void {
    this.close()
    const source = new EventSource(`/api/v1/tasks/${this.taskId}/events?after=${afterSeq}`)
    source.onmessage = (message) => {
      try {
        this.handlers.onEvent(JSON.parse(message.data) as TaskEvent)
      } catch {
        // 无法解析的事件不推进水位，等待下次重连补齐
      }
    }
    source.onerror = () => {
      this.close()
      this.handlers.onError()
    }
    this.source = source
  }

  close(): void {
    this.source?.close()
    this.source = null
  }
}
