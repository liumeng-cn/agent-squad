import { apiGet, apiPost, apiPut } from '../../../shared/http/http'

export type ToolSource = 'NATIVE' | 'JAVA' | 'MCP'
export type McpTransport = 'STREAMABLE_HTTP' | 'SSE'
export type CatalogKind = 'tools' | 'mcp-servers'

export interface ToolEntry {
  status: 'ENABLED' | 'DISABLED'
  toolId: string
  toolCode: string
  toolName: string
  toolType: ToolSource
}
export interface McpEntry {
  status: 'ENABLED' | 'DISABLED'
  mcpServerId: string
  serverCode: string
  serverName: string
}
export interface VersionMetadata {
  versionNo: number
  revision: number
  status: 'DRAFT' | 'PUBLISHED'
}
export interface ToolVersion extends VersionMetadata {
  toolVersionId: string
  exposedName: string
  implementationKey: string | null
  mcpServerVersionId: string | null
  remoteToolName: string | null
}
export interface McpVersion extends VersionMetadata {
  mcpServerVersionId: string
  transportType: McpTransport
  endpoint: string
  credentialRef: string | null
}
export interface McpOption {
  mcpServerVersionId: string
  serverName: string
  versionNo: number
}

const base = '/v2-server/api/v2/agents/management'
const catalog = (kind: CatalogKind) => `${base}/${kind}`
const versions = (kind: CatalogKind, id: string) => `${catalog(kind)}/${id}/versions`

export const listTools = () => apiGet<ToolEntry[]>(catalog('tools'))
export const listMcpServers = () => apiGet<McpEntry[]>(catalog('mcp-servers'))
export const listToolVersions = (id: string) => apiGet<ToolVersion[]>(versions('tools', id))
export const listMcpVersions = (id: string) => apiGet<McpVersion[]>(versions('mcp-servers', id))
export const listMcpOptions = () => apiGet<McpOption[]>(`${catalog('tools')}/mcp-options`)
export const listToolOptions = () => apiGet<ToolVersion[]>(`${base}/agents/tool-options`)

export const createTool = (body: Omit<ToolEntry, 'toolId' | 'status'>) => apiPost<ToolEntry>(catalog('tools'), body)
export const createMcp = (body: Omit<McpEntry, 'mcpServerId' | 'status'>) => apiPost<McpEntry>(catalog('mcp-servers'), body)

export function saveVersion<T extends ToolVersion | McpVersion>(
  kind: CatalogKind, resourceId: string, body: object, versionId?: string,
) {
  const path = versions(kind, resourceId)
  return versionId ? apiPut<T>(`${path}/${versionId}`, body) : apiPost<T>(path, body)
}

export const publishVersion = (kind: CatalogKind, id: string, versionId: string, revision: number) =>
  apiPost<ToolVersion | McpVersion>(`${versions(kind, id)}/${versionId}/publish`, { revision })
