import { apiGet, apiPost, apiPut } from '../../../shared/http/http'
import type {
  Agent,
  AgentDetail,
  AgentPublishPreview,
  AgentPublishRequest,
  AgentReleaseDetail,
  AgentReleaseSubagentDetail,
  AgentReleaseToolDetail,
  AgentSave,
  AgentVersion,
  BuiltinToolOption,
  McpProbe,
  McpRemoteTool,
  McpServer,
  McpServerDetail,
  McpServerOption,
  McpServerProbe,
  McpServerSave,
  McpServerVersion,
  SubagentOption,
  Tool,
  ToolDetail,
  ToolOption,
  ToolSave,
  ToolTestRun,
  ToolTestRunRequest,
  ToolVersion,
} from '../model/agentConfig'

/**
 * Agent 配置与 Master 发布定格接口客户端。
 */
const base = '/v2-server/api/v2/agents/management'

const segment = (value: string) => encodeURIComponent(value)
const mcpServerPath = (mcpServerId: string) => `${base}/mcp-servers/${segment(mcpServerId)}`
const toolPath = (toolId: string) => `${base}/tools/${segment(toolId)}`
const agentPath = (agentId: string) => `${base}/agents/${segment(agentId)}`

// MCP
export const listMcpServers = () => apiGet<McpServer[]>(`${base}/mcp-servers`)
export const createMcpServer = (body: McpServerSave) => apiPost<McpServerDetail>(`${base}/mcp-servers`, body)
export const saveMcpServer = (mcpServerId: string, body: McpServerSave) =>
  apiPut<McpServerDetail>(mcpServerPath(mcpServerId), body)
export const probeMcpServer = (body: McpServerProbe) => apiPost<McpProbe>(`${base}/mcp-servers/probe`, body)
export const listMcpCredentialOptions = () => apiGet<string[]>(`${base}/mcp-servers/credential-options`)
export const getMcpServer = (mcpServerId: string) => apiGet<McpServerDetail>(mcpServerPath(mcpServerId))
export const enableMcpServer = (mcpServerId: string, revision: number) =>
  apiPost<McpServer>(`${mcpServerPath(mcpServerId)}/enable`, { revision })
export const disableMcpServer = (mcpServerId: string, revision: number) =>
  apiPost<McpServer>(`${mcpServerPath(mcpServerId)}/disable`, { revision })
export const deleteMcpServer = (mcpServerId: string, revision: number) =>
  apiPost<null>(`${mcpServerPath(mcpServerId)}/delete`, { revision })
export const getMcpServerVersion = (mcpServerId: string, versionId: string) =>
  apiGet<McpServerVersion>(`${mcpServerPath(mcpServerId)}/versions/${segment(versionId)}`)

// 工具
export const listTools = () => apiGet<Tool[]>(`${base}/tools`)
export const createTool = (body: ToolSave) => apiPost<ToolDetail>(`${base}/tools`, body)
export const saveTool = (toolId: string, body: ToolSave) => apiPut<ToolDetail>(toolPath(toolId), body)
export const testRunTool = (body: ToolTestRunRequest) => apiPost<ToolTestRun>(`${base}/tools/test-run`, body)
export const listBuiltinToolOptions = () => apiGet<BuiltinToolOption[]>(`${base}/tools/builtin-options`)
export const listMcpServerOptions = () => apiGet<McpServerOption[]>(`${base}/tools/mcp-options`)
export const listRemoteTools = (mcpServerId: string) =>
  apiGet<McpRemoteTool[]>(`${base}/tools/mcp-options/${segment(mcpServerId)}/remote-tools`)
export const listToolCredentialOptions = () => apiGet<string[]>(`${base}/tools/credential-options`)
export const getTool = (toolId: string) => apiGet<ToolDetail>(toolPath(toolId))
export const enableTool = (toolId: string, revision: number) => apiPost<Tool>(`${toolPath(toolId)}/enable`, { revision })
export const disableTool = (toolId: string, revision: number) => apiPost<Tool>(`${toolPath(toolId)}/disable`, { revision })
export const deleteTool = (toolId: string, revision: number) => apiPost<null>(`${toolPath(toolId)}/delete`, { revision })
export const getToolVersion = (toolId: string, versionId: string) =>
  apiGet<ToolVersion>(`${toolPath(toolId)}/versions/${segment(versionId)}`)

// Agent 与 Master 发布
export const listAgents = () => apiGet<Agent[]>(`${base}/agents`)
export const createAgent = (body: AgentSave) => apiPost<AgentDetail>(`${base}/agents`, body)
export const saveAgent = (agentId: string, body: AgentSave) => apiPut<AgentDetail>(agentPath(agentId), body)
export const listAgentToolOptions = () => apiGet<ToolOption[]>(`${base}/agents/tool-options`)
export const listSubagentOptions = () => apiGet<SubagentOption[]>(`${base}/agents/subagent-options`)
export const getAgent = (agentId: string) => apiGet<AgentDetail>(agentPath(agentId))
export const enableAgent = (agentId: string, revision: number) => apiPost<Agent>(`${agentPath(agentId)}/enable`, { revision })
export const disableAgent = (agentId: string, revision: number) => apiPost<Agent>(`${agentPath(agentId)}/disable`, { revision })
export const deleteAgent = (agentId: string, revision: number) => apiPost<null>(`${agentPath(agentId)}/delete`, { revision })
export const getAgentVersion = (agentId: string, versionId: string) =>
  apiGet<AgentVersion>(`${agentPath(agentId)}/versions/${segment(versionId)}`)
export const previewPublishAgent = (agentId: string) =>
  apiGet<AgentPublishPreview>(`${agentPath(agentId)}/publish-preview`)
export const publishAgent = (agentId: string, body: AgentPublishRequest) =>
  apiPost<Agent>(`${agentPath(agentId)}/publish`, body)
export const getAgentRelease = (agentId: string, releaseId: string) =>
  apiGet<AgentReleaseDetail>(`${agentPath(agentId)}/releases/${segment(releaseId)}`)
export const getAgentReleaseSubagent = (agentId: string, releaseId: string, subagentId: string) =>
  apiGet<AgentReleaseSubagentDetail>(`${agentPath(agentId)}/releases/${segment(releaseId)}/subagents/${segment(subagentId)}`)
export const getAgentReleaseTool = (agentId: string, releaseId: string, toolId: string) =>
  apiGet<AgentReleaseToolDetail>(`${agentPath(agentId)}/releases/${segment(releaseId)}/tools/${segment(toolId)}`)
