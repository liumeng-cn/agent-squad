/**
 * Agent 配置管理的传输模型，字段与 api/openapi.json 及 v2-server 的 DTO/VO 保持一致。
 * 依赖顺序：MCP 服务 → 工具 → 子 Agent → 主 Agent；下游只能绑定上游已发布且启用的版本。
 */
export type ConfigStatus = 'ENABLED' | 'DISABLED'
export type VersionStatus = 'DRAFT' | 'PUBLISHED'
export type AgentType = 'MASTER' | 'SUB_AGENT'
export type ToolType = 'BUILTIN' | 'HTTP' | 'MCP'
export type McpTransport = 'STREAMABLE_HTTP' | 'SSE'
export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
export type JsonSchema = Record<string, unknown>

export interface KeyValue { name: string; value: string }
export interface RevisionRequest { revision: number }

/** 版本记录；current 对 Agent 为当前生效版本，对工具和 MCP 为最新发布版本。 */
export interface VersionSummary {
  versionId: string
  versionNo: number
  status: VersionStatus
  revision: number
  publishedAt: string | null
  lastUpdateTime: string
  current: boolean
}

/**
 * 引用关系：哪个下游资源的哪个版本引用了当前资源的哪个版本。
 * active 表示生效引用（启用的下游正在使用），存在生效引用时不能停用当前资源。
 */
export interface ConfigUsage {
  ownerId: string
  ownerCode: string
  ownerName: string
  ownerStatus: ConfigStatus
  versionNo: number
  versionStatus: VersionStatus
  referencedVersionNo: number
  active: boolean
}

export interface McpServerDraft {
  revision?: number
  transportType: McpTransport
  endpoint: string
  credentialRef: string | null
  headers: KeyValue[]
  timeoutMs: number
}
export interface McpServer {
  mcpServerId: string
  serverCode: string
  serverName: string
  description: string
  status: ConfigStatus
  revision: number
  publishedVersionId: string | null
  publishedVersionNo: number | null
  draftVersionId: string | null
  usageCount: number
  lastUpdateTime: string
}
export interface McpServerVersion extends McpServerDraft {
  mcpServerVersionId: string
  mcpServerId: string
  versionNo: number
  status: VersionStatus
  revision: number
  publishedAt: string | null
  lastUpdateTime: string
}
export interface McpServerDetail { server: McpServer; versions: VersionSummary[]; usages: ConfigUsage[] }
export interface McpServerCreate { serverCode: string; serverName: string; description: string; draft: McpServerDraft }
export interface McpServerProfile { serverName: string; description: string; revision: number }
export interface McpRemoteTool { name: string; title: string | null; description: string; inputSchema: JsonSchema; readOnly: boolean }
export interface McpProbe { durationMs: number; tools: McpRemoteTool[] }

export interface HttpToolSpec {
  method: HttpMethod
  url: string
  headers: KeyValue[]
  bodyTemplate: string | null
  credentialRef: string | null
  timeoutMs: number
  resultPointer: string | null
}
/** 工具草稿：按类型只填写对应实现字段，其余为 null。内置工具的名称、描述、参数以实现为准。 */
export interface ToolDraft {
  revision?: number
  exposedName: string
  description: string
  inputSchema: JsonSchema
  readOnly: boolean
  implementationKey: string | null
  mcpServerVersionId: string | null
  remoteToolName: string | null
  httpSpec: HttpToolSpec | null
}
export interface Tool {
  toolId: string
  toolCode: string
  toolName: string
  toolType: ToolType
  status: ConfigStatus
  revision: number
  exposedName: string | null
  description: string
  publishedVersionId: string | null
  publishedVersionNo: number | null
  draftVersionId: string | null
  usageCount: number
  lastUpdateTime: string
}
export interface ToolVersion extends ToolDraft {
  toolVersionId: string
  toolId: string
  toolType: ToolType
  versionNo: number
  status: VersionStatus
  revision: number
  publishedAt: string | null
  lastUpdateTime: string
  mcpServerName: string | null
  mcpServerVersionNo: number | null
}
export interface ToolDetail { tool: Tool; versions: VersionSummary[]; usages: ConfigUsage[] }
export interface ToolCreate { toolCode: string; toolName: string; toolType: ToolType; draft: ToolDraft }
export interface ToolProfile { toolName: string; revision: number }
export interface ToolTestRunRequest { toolType: ToolType; draft: ToolDraft; arguments: Record<string, unknown> }
export interface ToolTestRun { success: boolean; durationMs: number; request: string; output: string }
export interface BuiltinToolOption { implementationKey: string; exposedName: string; description: string; inputSchema: JsonSchema; readOnly: boolean }
export interface McpServerOption { mcpServerVersionId: string; mcpServerId: string; serverCode: string; serverName: string; versionNo: number; latest: boolean }

export interface AgentDelegationDraft { subagentVersionId: string; delegationName: string; delegationDescription: string }
export interface AgentDraft {
  revision?: number
  description: string
  systemPrompt: string
  maxIters: number | null
  toolVersionIds: string[]
  subagents: AgentDelegationDraft[]
}
export interface Agent {
  agentId: string
  agentCode: string
  agentName: string
  agentType: AgentType
  status: ConfigStatus
  revision: number
  description: string
  currentVersionId: string | null
  currentVersionNo: number | null
  draftVersionId: string | null
  toolCount: number
  subagentCount: number
  usageCount: number
  lastUpdateTime: string
}
export interface AgentToolBinding {
  toolVersionId: string
  toolId: string
  toolCode: string
  toolName: string
  toolType: ToolType
  exposedName: string
  description: string
  versionNo: number
  latestVersionNo: number | null
  usable: boolean
}
export interface AgentDelegation {
  subagentVersionId: string
  subagentId: string
  subagentCode: string
  subagentName: string
  description: string
  versionNo: number
  currentVersionNo: number | null
  delegationName: string
  delegationDescription: string
  usable: boolean
}
export interface AgentVersion {
  agentVersionId: string
  agentId: string
  versionNo: number
  status: VersionStatus
  revision: number
  publishedAt: string | null
  lastUpdateTime: string
  description: string
  systemPrompt: string
  maxIters: number | null
  tools: AgentToolBinding[]
  subagents: AgentDelegation[]
}
export interface AgentDetail { agent: Agent; versions: VersionSummary[]; usages: ConfigUsage[] }
export interface AgentCreate { agentCode: string; agentName: string; agentType: AgentType; draft: AgentDraft }
export interface AgentProfile { agentName: string; revision: number }
export interface ToolOption {
  toolVersionId: string
  toolId: string
  toolCode: string
  toolName: string
  toolType: ToolType
  exposedName: string
  description: string
  versionNo: number
  latest: boolean
}
export interface SubagentOption {
  subagentVersionId: string
  agentId: string
  agentCode: string
  agentName: string
  description: string
  versionNo: number
  current: boolean
}
