import type { ExecutionStatus } from './chatTypes'
export type TitleSource = 'AUTO' | 'MANUAL'
export type SessionStatus = ExecutionStatus | 'UNKNOWN'
export interface ConvSummary {
  convId: string
  sessionId: string
  title: string
  titleSource: TitleSource
  version: number
  createdAt: string
  remark: string | null
  archived: boolean
}
