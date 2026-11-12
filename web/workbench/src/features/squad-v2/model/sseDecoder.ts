export interface SseFrame {
  data: string
  event: string
  id: string
  retry: number | null
}

/**
 * 按 SSE 规范解析文本分片。
 *
 * push() 可以接收任意大小的网络分片；一条事件可以跨多个分片，
 * 一个分片也可以包含多条事件。flush() 用于处理连接结束时没有空行
 * 结束的最后一条事件。
 */
export class SseDecoder {
  private buffer = ''
  private dataLines: string[] = []
  private eventName = ''
  private lastEventId = ''
  private retry: number | null = null

  push(chunk: string): SseFrame[] {
    if (chunk) this.buffer += chunk
    const frames: SseFrame[] = []
    let lineStart = 0

    for (let index = 0; index < this.buffer.length; index += 1) {
      const character = this.buffer[index]
      if (character !== '\n' && character !== '\r') continue

      // CR 可能是下一个网络分片中的 CRLF 前半部分，先保留。
      if (character === '\r' && index === this.buffer.length - 1) break

      const line = this.buffer.slice(lineStart, index)
      if (character === '\r' && this.buffer[index + 1] === '\n') index += 1
      this.consumeLine(line, frames)
      lineStart = index + 1
    }

    this.buffer = this.buffer.slice(lineStart)
    return frames
  }

  flush(): SseFrame[] {
    const frames: SseFrame[] = []
    if (this.buffer.endsWith('\r')) {
      this.consumeLine(this.buffer.slice(0, -1), frames)
    } else if (this.buffer.length > 0) {
      this.consumeLine(this.buffer, frames)
    }
    this.buffer = ''
    this.dispatch(frames)
    return frames
  }

  private consumeLine(line: string, frames: SseFrame[]): void {
    if (line.length === 0) {
      this.dispatch(frames)
      return
    }
    if (line.startsWith(':')) return

    const separator = line.indexOf(':')
    const field = separator < 0 ? line : line.slice(0, separator)
    let value = separator < 0 ? '' : line.slice(separator + 1)
    if (value.startsWith(' ')) value = value.slice(1)

    switch (field) {
      case 'data':
        this.dataLines.push(value)
        break
      case 'event':
        this.eventName = value
        break
      case 'id':
        if (!value.includes('\0')) this.lastEventId = value
        break
      case 'retry':
        if (/^\d+$/.test(value)) this.retry = Number(value)
        break
      default:
        // SSE 允许扩展字段；未知字段不影响当前事件。
        break
    }
  }

  private dispatch(frames: SseFrame[]): void {
    if (this.dataLines.length === 0) return
    frames.push({
      data: this.dataLines.join('\n'),
      event: this.eventName || 'message',
      id: this.lastEventId,
      retry: this.retry,
    })
    this.dataLines = []
    this.eventName = ''
  }
}
