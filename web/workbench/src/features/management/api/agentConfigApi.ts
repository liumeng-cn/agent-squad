import { apiGet, apiPost, apiPut } from '../../../shared/http/http'

export type AgentType = 'MASTER' | 'SUB_AGENT'
export interface AgentConfig {
  agentId: string
  agentCode: string
  agentName: string
  agentType: AgentType
  status: 'ENABLED' | 'DISABLED'
  currentVersionId: string | null
  revision: number
}
export interface AgentDelegation {
  subagentVersionId: string
  delegationName: string
  delegationDescription?: string
}
export interface AgentDraft {
  revision?: number
  systemPrompt: string
  description: string
  maxIters: number | null
  toolVersionIds: string[]
  subagents: AgentDelegation[]
}
export interface AgentVersion extends AgentDraft {
  agentVersionId: string
  versionNo: number
  revision: number
  status: 'DRAFT' | 'PUBLISHED'
}
const base = '/v2-server/api/v2/agents/management/agents'
const versions = (agentId: string) => `${base}/${agentId}/versions`
export const agentList = () => apiGet<AgentConfig[]>(base)
export const createAgent = (body: Pick<AgentConfig, 'agentCode' | 'agentName' | 'agentType'>) =>
  apiPost<AgentConfig>(base, body)
export const versionList = (agentId: string) => apiGet<AgentVersion[]>(versions(agentId))
export const versionDetail = (agentId: string, versionId: string) =>
  apiGet<AgentVersion>(`${versions(agentId)}/${versionId}`)
export const saveDraft = (agentId: string, body: AgentDraft, versionId?: string) =>
  versionId ? apiPut<AgentVersion>(`${versions(agentId)}/${versionId}`, body) : apiPost<AgentVersion>(versions(agentId), body)
export const publish = (agent: AgentConfig, version: AgentVersion) =>
  apiPost<AgentConfig>(`${versions(agent.agentId)}/${version.agentVersionId}/publish`, { revision: version.revision })
export const activate = (agent: AgentConfig, version: AgentVersion) =>
  apiPost<AgentConfig>(`${versions(agent.agentId)}/${version.agentVersionId}/activate`, { revision: agent.revision })
