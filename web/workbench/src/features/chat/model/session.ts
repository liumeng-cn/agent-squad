import type { ExecutionStatus } from './chatTypes'
export type TitleSource = 'AUTO' | 'MANUAL'
export type SessionStatus = ExecutionStatus | 'UNKNOWN'
export interface SessionSummary {
  sessionId: string
  title: string
  titleSource: TitleSource
  version: number
  hasMessages: boolean
  createdAt: string
  lastActivityAt: string
  execution: { status: SessionStatus; observedAt: string | null; stale: boolean }
}
export interface SessionPage { items: SessionSummary[]; nextCursor: string | null; hasMore: boolean; serverTime: string }
