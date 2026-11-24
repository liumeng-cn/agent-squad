/**
 * Agent 配置与发布定格的前端传输模型，字段与 api/openapi.json 及 v2-server 保持一致。
 * 规则：每次保存生成新配置版本；仅 Master 可发布并定格完整依赖版本图。
 */
export type ConfigStatus = 'ENABLED' | 'DISABLED'
export type ConfigReadiness = 'READY' | 'INCOMPLETE'
export type AgentPublishState = 'UNPUBLISHED' | 'PUBLISHED' | 'MODIFIED'
export type AgentType = 'MASTER' | 'SUB_AGENT'
export type ToolType = 'BUILTIN' | 'HTTP' | 'MCP'
export type McpTransport = 'STREAMABLE_HTTP' | 'SSE'
export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
export type JsonSchema = Record<string, unknown>

export interface KeyValue { name: string; value: string }
export interface RevisionRequest { revision: number }

export interface PromptSections {
  role: string
  goal: string
  workflow: string
  constraints: string
  outputFormat: string
}

/** 历史配置版本摘要（MCP / Tool / Sub / Master 通用）。 */
export interface VersionSummary {
  versionId: string
  versionNo: number
  readiness: ConfigReadiness
  lastUpdateTime: string
}

/** 下游引用关系，用于停用或删除前保护。 */
export interface ConfigUsage {
  ownerId: string
  ownerCode: string
  ownerName: string
  ownerStatus: ConfigStatus
  versionNo: number
  released: boolean
  active: boolean
}

// MCP
export interface McpServerSave {
  revision?: number
  serverName: string
  description: string
  transportType: McpTransport
  endpoint: string
  credentialRef: string | null
  headers: KeyValue[]
  timeoutMs: number
}
export interface McpServerProbe {
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
  latestVersionId: string | null
  latestVersionNo: number
  latestReadiness: ConfigReadiness
  transportType: McpTransport | null
  endpoint: string
  usageCount: number
  lastUpdateTime: string
}
export interface McpServerVersion {
  mcpServerVersionId: string
  mcpServerId: string
  versionNo: number
  readiness: ConfigReadiness
  transportType: McpTransport | null
  endpoint: string
  credentialRef: string | null
  headers: KeyValue[]
  timeoutMs: number
  lastUpdateTime: string
}
export interface McpServerDetail {
  server: McpServer
  latestVersion: McpServerVersion | null
  versions: VersionSummary[]
  usages: ConfigUsage[]
}
export interface McpRemoteTool { name: string; title: string | null; description: string; inputSchema: JsonSchema; readOnly: boolean }
export interface McpProbe { durationMs: number; tools: McpRemoteTool[] }
export interface McpServerOption {
  mcpServerId: string
  serverCode: string
  serverName: string
  description: string
  latestVersionId: string
  latestVersionNo: number
  transportType: McpTransport
  endpoint: string
}

// 工具
export interface HttpToolSpec {
  method: HttpMethod
  url: string
  headers: KeyValue[]
  bodyTemplate: string | null
  credentialRef: string | null
  timeoutMs: number
  resultPointer: string | null
}
export interface ToolSave {
  revision?: number
  toolName: string
  displayAlias: string
  toolType?: ToolType
  exposedName: string
  description: string
  inputSchema: JsonSchema
  readOnly: boolean
  implementationKey: string | null
  mcpServerId: string | null
  remoteToolName: string | null
  httpSpec: HttpToolSpec | null
}
export interface Tool {
  toolId: string
  toolCode: string
  toolName: string
  displayAlias: string
  toolType: ToolType
  status: ConfigStatus
  revision: number
  latestVersionId: string | null
  latestVersionNo: number
  latestReadiness: ConfigReadiness
  exposedName: string
  description: string
  readOnly: boolean
  usageCount: number
  lastUpdateTime: string
}
export interface ToolVersion {
  toolVersionId: string
  toolId: string
  toolType: ToolType
  versionNo: number
  readiness: ConfigReadiness
  exposedName: string
  description: string
  inputSchema: JsonSchema
  readOnly: boolean
  implementationKey: string | null
  mcpServerId: string | null
  mcpServerName: string | null
  remoteToolName: string | null
  httpSpec: HttpToolSpec | null
  lastUpdateTime: string
}
export interface ToolDetail {
  tool: Tool
  latestVersion: ToolVersion | null
  versions: VersionSummary[]
  usages: ConfigUsage[]
}
export interface ToolTestRunRequest {
  toolType: ToolType
  definition: ToolSave
  arguments: Record<string, unknown>
}
export interface ToolTestRun { success: boolean; durationMs: number; request: string; output: string }
export interface BuiltinToolOption { implementationKey: string; exposedName: string; description: string; inputSchema: JsonSchema; readOnly: boolean }
export interface ToolOption {
  toolId: string
  toolCode: string
  toolName: string
  displayAlias: string
  toolType: ToolType
  latestVersionId: string
  latestVersionNo: number
  exposedName: string
  description: string
  readOnly: boolean
}

// Agent 与 Master 发布定格
export interface AgentDelegationDraft {
  subagentId: string
  delegationName: string
  delegationDescription: string
  displayAlias: string
}
export interface AgentSave {
  revision?: number
  agentName: string
  displayAlias: string
  agentType?: AgentType
  description: string
  promptSections: PromptSections
  maxIters: number | null
  toolIds: string[]
  subagents: AgentDelegationDraft[]
}
export interface Agent {
  agentId: string
  agentCode: string
  agentName: string
  displayAlias: string
  agentType: AgentType
  status: ConfigStatus
  revision: number
  latestVersionId: string | null
  latestVersionNo: number
  latestReadiness: ConfigReadiness
  description: string
  currentReleaseId: string | null
  currentReleaseNo: number | null
  currentReleaseAt: string | null
  publishState: AgentPublishState | null
  toolCount: number
  subagentCount: number
  usageCount: number
  lastUpdateTime: string
}
export interface AgentToolBinding {
  toolId: string
  toolCode: string
  toolName: string
  displayAlias: string
  toolType: ToolType
  toolStatus: ConfigStatus
  toolVersionId: string
  versionNo: number
  readiness: ConfigReadiness
  exposedName: string
  description: string
  readOnly: boolean
  mcpServerId: string | null
  mcpServerName: string | null
  usable: boolean
  unusableReason: string | null
}
export interface AgentDelegation {
  subagentId: string
  subagentCode: string
  subagentName: string
  subagentDisplayAlias: string
  subagentStatus: ConfigStatus
  subagentVersionId: string
  versionNo: number
  readiness: ConfigReadiness
  delegationName: string
  delegationDescription: string | null
  displayAlias: string
  subagentDescription: string
  toolCount: number
  usable: boolean
  unusableReason: string | null
}
export interface AgentVersion {
  agentVersionId: string
  agentId: string
  versionNo: number
  readiness: ConfigReadiness
  description: string
  promptSections: PromptSections
  systemPrompt: string
  maxIters: number | null
  lastUpdateTime: string
  tools: AgentToolBinding[]
  subagents: AgentDelegation[]
}
export interface AgentReleaseSummary {
  releaseId: string
  releaseNo: number
  masterVersionId: string
  masterVersionNo: number
  masterName: string
  masterDisplayAlias: string
  dependencyDigest: string
  publishedAt: string
  current: boolean
}
export interface AgentDetail {
  agent: Agent
  latestVersion: AgentVersion | null
  versions: VersionSummary[]
  releases: AgentReleaseSummary[]
  usages: ConfigUsage[]
}
export interface SubagentOption {
  agentId: string
  agentCode: string
  agentName: string
  displayAlias: string
  latestVersionId: string
  latestVersionNo: number
  description: string
  toolCount: number
}
export interface AgentPublishDependency {
  resourceKind: 'MASTER' | 'SUB_AGENT' | 'TOOL' | 'MCP'
  resourceId: string
  resourceName: string
  versionNo: number | null
  displayAlias: string
  scope: string
}
export interface AgentPublishPreview {
  agentId: string
  agentName: string
  revision: number
  masterVersionNo: number | null
  currentReleaseNo: number | null
  dependencyDigest: string
  publishable: boolean
  errors: string[]
  dependencies: AgentPublishDependency[]
}
export interface AgentPublishRequest {
  revision: number
  dependencyDigest: string
  publishOperationId: string
}
export interface AgentReleaseDetail {
  releaseId: string
  releaseNo: number
  masterAgentId: string
  masterName: string
  masterDisplayAlias: string
  masterVersionId: string
  masterVersionNo: number
  description: string
  promptSections: PromptSections
  systemPrompt: string
  maxIters: number | null
  dependencyDigest: string
  publishedAt: string
  current: boolean
  tools: AgentToolBinding[]
  subagents: AgentDelegation[]
}
export interface AgentReleaseSubagentDetail {
  releaseId: string
  releaseNo: number
  publishedAt: string
  subagentId: string
  subagentCode: string
  subagentName: string
  displayAlias: string
  delegationName: string
  delegationDescription: string | null
  subagentVersionId: string
  versionNo: number
  description: string
  promptSections: PromptSections
  systemPrompt: string
  maxIters: number | null
  tools: AgentToolBinding[]
}
export interface AgentReleaseToolDetail {
  releaseId: string
  releaseNo: number
  publishedAt: string
  toolId: string
  toolName: string
  displayAlias: string
  toolVersion: ToolVersion
  mcpVersion: McpServerVersion | null
  mcpServerName: string | null
}
