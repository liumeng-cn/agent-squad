/** 临时模型正文无持久游标，全文替换；最终消息和快照始终是权威。 */
export interface ReplyPreview {
  schemaVersion: 2
  executionId: string
  sourceMessageId: string
  runId: string
  streamId: string
  revision: number
  offset: 0
  text: string
  replace: true
  preview: true
}
export function decodeReplyPreview(value: unknown): ReplyPreview | null {
  if (!value || typeof value !== 'object') return null
  const item = value as Record<string, unknown>
  if (item.schemaVersion !== 2 || item.offset !== 0 || item.replace !== true || item.preview !== true
      || !Number.isSafeInteger(item.revision) || (item.revision as number) < 1
      || typeof item.text !== 'string' || item.text.length > 65536
      || ['executionId', 'sourceMessageId', 'runId', 'streamId'].some(key => typeof item[key] !== 'string' || !(item[key] as string).length)) return null
  return item as unknown as ReplyPreview
}
