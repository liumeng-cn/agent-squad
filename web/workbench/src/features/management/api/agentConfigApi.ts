import { apiGet, apiPost, apiPut } from '../../../shared/http/http'
import type {
  Agent,
  AgentCreate,
  AgentDetail,
  AgentDraft,
  AgentProfile,
  AgentVersion,
  BuiltinToolOption,
  McpProbe,
  McpRemoteTool,
  McpServer,
  McpServerCreate,
  McpServerDetail,
  McpServerDraft,
  McpServerOption,
  McpServerProfile,
  McpServerVersion,
  SubagentOption,
  Tool,
  ToolCreate,
  ToolDetail,
  ToolDraft,
  ToolOption,
  ToolProfile,
  ToolTestRun,
  ToolTestRunRequest,
  ToolVersion,
} from '../model/agentConfig'

/**
 * Agent 配置管理接口：MCP 服务、工具与 Agent 的目录、版本、选项和试运行。
 * 路径与 docs/management/agent-config-design.md 的接口表一一对应。
 */
const base = '/v2-server/api/v2/agents/management'

const segment = (value: string) => encodeURIComponent(value)
const mcpServerPath = (mcpServerId: string) => `${base}/mcp-servers/${segment(mcpServerId)}`
const toolPath = (toolId: string) => `${base}/tools/${segment(toolId)}`
const agentPath = (agentId: string) => `${base}/agents/${segment(agentId)}`

// MCP 服务
export const listMcpServers = () => apiGet<McpServer[]>(`${base}/mcp-servers`)
export const createMcpServer = (body: McpServerCreate) => apiPost<McpServerDetail>(`${base}/mcp-servers`, body)
export const probeMcpServer = (draft: McpServerDraft) => apiPost<McpProbe>(`${base}/mcp-servers/probe`, draft)
export const listMcpCredentialOptions = () => apiGet<string[]>(`${base}/mcp-servers/credential-options`)
export const getMcpServer = (mcpServerId: string) => apiGet<McpServerDetail>(mcpServerPath(mcpServerId))
export const updateMcpServer = (mcpServerId: string, body: McpServerProfile) =>
  apiPut<McpServer>(mcpServerPath(mcpServerId), body)
export const enableMcpServer = (mcpServerId: string, revision: number) =>
  apiPost<McpServer>(`${mcpServerPath(mcpServerId)}/enable`, { revision })
export const disableMcpServer = (mcpServerId: string, revision: number) =>
  apiPost<McpServer>(`${mcpServerPath(mcpServerId)}/disable`, { revision })
export const getMcpServerVersion = (mcpServerId: string, versionId: string) =>
  apiGet<McpServerVersion>(`${mcpServerPath(mcpServerId)}/versions/${segment(versionId)}`)
export const createMcpServerVersion = (mcpServerId: string, draft: McpServerDraft) =>
  apiPost<McpServerVersion>(`${mcpServerPath(mcpServerId)}/versions`, draft)
export const saveMcpServerVersion = (mcpServerId: string, versionId: string, draft: McpServerDraft) =>
  apiPut<McpServerVersion>(`${mcpServerPath(mcpServerId)}/versions/${segment(versionId)}`, draft)
export const publishMcpServerVersion = (mcpServerId: string, versionId: string, revision: number) =>
  apiPost<McpServerVersion>(`${mcpServerPath(mcpServerId)}/versions/${segment(versionId)}/publish`, { revision })

// 工具
export const listTools = () => apiGet<Tool[]>(`${base}/tools`)
export const createTool = (body: ToolCreate) => apiPost<ToolDetail>(`${base}/tools`, body)
export const testRunTool = (body: ToolTestRunRequest) => apiPost<ToolTestRun>(`${base}/tools/test-run`, body)
export const listBuiltinToolOptions = () => apiGet<BuiltinToolOption[]>(`${base}/tools/builtin-options`)
export const listMcpServerOptions = () => apiGet<McpServerOption[]>(`${base}/tools/mcp-options`)
export const listRemoteTools = (mcpServerVersionId: string) =>
  apiGet<McpRemoteTool[]>(`${base}/tools/mcp-options/${segment(mcpServerVersionId)}/remote-tools`)
export const listToolCredentialOptions = () => apiGet<string[]>(`${base}/tools/credential-options`)
export const getTool = (toolId: string) => apiGet<ToolDetail>(toolPath(toolId))
export const updateTool = (toolId: string, body: ToolProfile) => apiPut<Tool>(toolPath(toolId), body)
export const enableTool = (toolId: string, revision: number) => apiPost<Tool>(`${toolPath(toolId)}/enable`, { revision })
export const disableTool = (toolId: string, revision: number) => apiPost<Tool>(`${toolPath(toolId)}/disable`, { revision })
export const getToolVersion = (toolId: string, versionId: string) =>
  apiGet<ToolVersion>(`${toolPath(toolId)}/versions/${segment(versionId)}`)
export const createToolVersion = (toolId: string, draft: ToolDraft) =>
  apiPost<ToolVersion>(`${toolPath(toolId)}/versions`, draft)
export const saveToolVersion = (toolId: string, versionId: string, draft: ToolDraft) =>
  apiPut<ToolVersion>(`${toolPath(toolId)}/versions/${segment(versionId)}`, draft)
export const publishToolVersion = (toolId: string, versionId: string, revision: number) =>
  apiPost<ToolVersion>(`${toolPath(toolId)}/versions/${segment(versionId)}/publish`, { revision })

// Agent
export const listAgents = () => apiGet<Agent[]>(`${base}/agents`)
export const createAgent = (body: AgentCreate) => apiPost<AgentDetail>(`${base}/agents`, body)
export const listAgentToolOptions = () => apiGet<ToolOption[]>(`${base}/agents/tool-options`)
export const listSubagentOptions = () => apiGet<SubagentOption[]>(`${base}/agents/subagent-options`)
export const getAgent = (agentId: string) => apiGet<AgentDetail>(agentPath(agentId))
export const updateAgent = (agentId: string, body: AgentProfile) => apiPut<Agent>(agentPath(agentId), body)
export const enableAgent = (agentId: string, revision: number) => apiPost<Agent>(`${agentPath(agentId)}/enable`, { revision })
export const disableAgent = (agentId: string, revision: number) => apiPost<Agent>(`${agentPath(agentId)}/disable`, { revision })
export const getAgentVersion = (agentId: string, versionId: string) =>
  apiGet<AgentVersion>(`${agentPath(agentId)}/versions/${segment(versionId)}`)
export const createAgentVersion = (agentId: string, draft: AgentDraft) =>
  apiPost<AgentVersion>(`${agentPath(agentId)}/versions`, draft)
export const saveAgentVersion = (agentId: string, versionId: string, draft: AgentDraft) =>
  apiPut<AgentVersion>(`${agentPath(agentId)}/versions/${segment(versionId)}`, draft)
/** 发布草稿并切换为当前生效版本，携带版本修订号。 */
export const publishAgentVersion = (agentId: string, versionId: string, versionRevision: number) =>
  apiPost<Agent>(`${agentPath(agentId)}/versions/${segment(versionId)}/publish`, { revision: versionRevision })
/** 切换到已发布版本，携带 Agent 修订号。 */
export const activateAgentVersion = (agentId: string, versionId: string, agentRevision: number) =>
  apiPost<Agent>(`${agentPath(agentId)}/versions/${segment(versionId)}/activate`, { revision: agentRevision })
