import { describe, expect, it } from 'vitest'
import { SseDecoder } from '../sseDecoder'

describe('SSE 解码器', () => {
  it('解析单条事件、多行 data 和事件名称', () => {
    const decoder = new SseDecoder()
    const frames = decoder.push(
      'id: 12\r\nevent: squad\r\ndata: {"eventType":\r\ndata: "AGENT_START"}\r\n\r\n',
    )

    expect(frames).toEqual([{
      id: '12',
      event: 'squad',
      data: '{"eventType":\n"AGENT_START"}',
      retry: null,
    }])
  })

  it('支持事件内容和 CRLF 被拆分到不同网络分片', () => {
    const decoder = new SseDecoder()
    expect(decoder.push('id: 1\r')).toEqual([])
    expect(decoder.push('\nevent: squad\ndata: {"value":1}\r')).toEqual([])
    expect(decoder.push('\n\r')).toEqual([])
    expect(decoder.push('\n')).toEqual([{
      id: '1',
      event: 'squad',
      data: '{"value":1}',
      retry: null,
    }])
  })

  it('一个分片内可以连续解析多条事件，并保留最近的 SSE id', () => {
    const decoder = new SseDecoder()
    const frames = decoder.push(
      'id: 2\ndata: first\n\n' + 'data: second\n\n',
    )

    expect(frames.map(frame => frame.data)).toEqual(['first', 'second'])
    expect(frames.map(frame => frame.id)).toEqual(['2', '2'])
  })

  it('忽略注释和未知字段，解析 retry', () => {
    const decoder = new SseDecoder()
    const frames = decoder.push(': heartbeat\nunknown: ignored\nretry: 5000\ndata: ping\n\n')

    expect(frames[0]).toMatchObject({ data: 'ping', retry: 5000 })
  })

  it('连接结束时补发没有空行结尾的最后一条事件', () => {
    const decoder = new SseDecoder()
    expect(decoder.push('data: last event')).toEqual([])
    expect(decoder.flush()).toEqual([{
      id: '',
      event: 'message',
      data: 'last event',
      retry: null,
    }])
  })
})
