import type {
  AgentPublishState,
  AgentType,
  ConfigReadiness,
  ConfigStatus,
  HttpMethod,
  McpTransport,
  PromptSections,
  ToolType,
} from './agentConfig'

/**
 * Agent 配置页面共用的中文标签、展示语气与格式化方法；有限取值统一在这里映射。
 */
export type TagTone = 'is-success' | 'is-primary' | 'is-warning' | 'is-danger' | 'is-muted'

export const configStatusLabels: Record<ConfigStatus, string> = { ENABLED: '启用', DISABLED: '停用' }
export const configStatusTones: Record<ConfigStatus, TagTone> = { ENABLED: 'is-success', DISABLED: 'is-muted' }
export const readinessLabels: Record<ConfigReadiness, string> = { READY: '已就绪', INCOMPLETE: '未就绪' }
export const readinessTones: Record<ConfigReadiness, TagTone> = { READY: 'is-success', INCOMPLETE: 'is-warning' }
export const publishStateLabels: Record<AgentPublishState, string> = {
  UNPUBLISHED: '未发布',
  PUBLISHED: '已生效',
  MODIFIED: '有未发布修改',
}
export const publishStateTones: Record<AgentPublishState, TagTone> = {
  UNPUBLISHED: 'is-muted',
  PUBLISHED: 'is-success',
  MODIFIED: 'is-warning',
}
export const agentTypeLabels: Record<AgentType, string> = { MASTER: 'Master', SUB_AGENT: 'Sub' }
export const toolTypeLabels: Record<ToolType, string> = { BUILTIN: '内置工具', HTTP: 'HTTP 接口', MCP: 'MCP 工具' }
export const mcpTransportLabels: Record<McpTransport, string> = { STREAMABLE_HTTP: 'Streamable HTTP', SSE: 'SSE' }

export const toolTypes: ToolType[] = ['BUILTIN', 'HTTP', 'MCP']
export const agentTypes: AgentType[] = ['MASTER', 'SUB_AGENT']
export const mcpTransports: McpTransport[] = ['STREAMABLE_HTTP', 'SSE']
export const httpMethods: HttpMethod[] = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE']
export const bodyMethods: HttpMethod[] = ['POST', 'PUT', 'PATCH']

export const toolTypeDescriptions: Record<ToolType, string> = {
  BUILTIN: '选择平台代码中登记的实现，参数与行为由实现决定。',
  HTTP: '按参数定义和请求模板自动生成接口调用，适合对接已有业务接口。',
  MCP: '从已就绪的 MCP 发现远程工具，自动带出名称、描述和参数。',
}

export const agentTypeDescriptions: Record<AgentType, string> = {
  MASTER: '面向用户的入口，可绑定工具并委派 Sub 协作，支持发布定格。',
  SUB_AGENT: '专注单一职责，由 Master 按职责描述委派，不单独发布。',
}

export const mcpTransportDescriptions: Record<McpTransport, string> = {
  STREAMABLE_HTTP: 'MCP 推荐的单端点 HTTP 传输，支持流式响应。',
  SSE: '旧版服务端事件流传输，用于尚未升级的服务。',
}

export const EXPOSED_NAME_PATTERN = /^[A-Za-z0-9_-]{1,64}$/
export const DELEGATION_NAME_PATTERN = /^[A-Za-z0-9_-]{1,80}$/
export const DESCRIPTION_LIMIT = 500
export const TOOL_DESCRIPTION_LIMIT = 2000
export const MAX_ITERS_LIMIT = 200

export function emptyPromptSections(): PromptSections {
  return { role: '', goal: '', workflow: '', constraints: '', outputFormat: '' }
}

/** 与后端 PromptSectionRules.assemble 保持完全一致的 Markdown 拼装逻辑。 */
export function assemblePromptSections(sections: PromptSections): string {
  const blocks: string[] = []
  const entries: Array<[string, string]> = [
    ['角色与定位', sections.role],
    ['核心目标', sections.goal],
    ['工作流程与策略', sections.workflow],
    ['约束与边界', sections.constraints],
    ['输出规范', sections.outputFormat],
  ]
  for (const [title, content] of entries) {
    if (content && content.trim()) {
      blocks.push(`## ${title}\n${content.trim()}`)
    }
  }
  return blocks.join('\n\n')
}

export function formatTime(value: string | null | undefined): string {
  if (!value) {
    return '—'
  }
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

export function versionLabel(versionNo: number | null | undefined): string {
  return versionNo == null ? '—' : `v${versionNo}`
}

export function sanitizeIdentifier(value: string, maxLength: number): string {
  const replaced = value.trim().replace(/[^A-Za-z0-9_-]/g, '_')
  return replaced.slice(0, maxLength)
}

export function uniqueIdentifier(base: string, taken: Iterable<string>, maxLength: number): string {
  const used = new Set(taken)
  const seed = base || 'item'
  if (!used.has(seed)) {
    return seed
  }
  for (let index = 2; ; index++) {
    const suffix = `_${index}`
    const candidate = `${seed.slice(0, maxLength - suffix.length)}${suffix}`
    if (!used.has(candidate)) {
      return candidate
    }
  }
}

export type EditorTab = 'config' | 'versions' | 'releases' | 'usages'

export interface ChoiceOption<V extends string> {
  value: V
  title: string
  description: string
  disabled?: boolean
}
