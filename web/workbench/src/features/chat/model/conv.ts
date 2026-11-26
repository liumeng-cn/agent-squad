export type TitleSource = 'AUTO' | 'MANUAL'
export interface ConvSummary {
  convId: string
  sessionId: string
  agentCode?: string
  agentDisplayName?: string
  title: string
  titleSource: TitleSource
  version: number
  createdAt: string
  remark: string | null
  archived: boolean
}
