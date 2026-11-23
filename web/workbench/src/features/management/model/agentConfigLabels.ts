import type { AgentType, ConfigStatus, HttpMethod, McpTransport, ToolType, VersionStatus } from './agentConfig'

/**
 * Agent 配置页面共用的中文标签、展示语气与格式化方法；有限取值统一在这里映射，页面不直接比较或拼写文案。
 */
export type TagTone = 'is-success' | 'is-primary' | 'is-warning' | 'is-danger' | 'is-muted'

export const configStatusLabels: Record<ConfigStatus, string> = { ENABLED: '启用', DISABLED: '停用' }
export const configStatusTones: Record<ConfigStatus, TagTone> = { ENABLED: 'is-success', DISABLED: 'is-muted' }
export const versionStatusLabels: Record<VersionStatus, string> = { DRAFT: '草稿', PUBLISHED: '已发布' }
export const agentTypeLabels: Record<AgentType, string> = { MASTER: '主 Agent', SUB_AGENT: '子 Agent' }
export const toolTypeLabels: Record<ToolType, string> = { BUILTIN: '内置工具', HTTP: 'HTTP 接口', MCP: 'MCP 工具' }
export const mcpTransportLabels: Record<McpTransport, string> = { STREAMABLE_HTTP: 'Streamable HTTP', SSE: 'SSE' }

export const toolTypes: ToolType[] = ['BUILTIN', 'HTTP', 'MCP']
export const agentTypes: AgentType[] = ['MASTER', 'SUB_AGENT']
export const mcpTransports: McpTransport[] = ['STREAMABLE_HTTP', 'SSE']
export const httpMethods: HttpMethod[] = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE']
/** 允许携带请求体模板的方法 */
export const bodyMethods: HttpMethod[] = ['POST', 'PUT', 'PATCH']

export const toolTypeDescriptions: Record<ToolType, string> = {
  BUILTIN: '选择平台代码中登记的实现，参数与行为由实现决定。',
  HTTP: '按参数定义和请求模板自动生成接口调用，适合对接已有业务接口。',
  MCP: '从已发布的 MCP 服务发现远程工具，直接带出名称、描述和参数。',
}

export const agentTypeDescriptions: Record<AgentType, string> = {
  MASTER: '面向用户的入口，可绑定工具并委派子 Agent 协作完成任务。',
  SUB_AGENT: '专注单一职责，由主 Agent 按职责描述判断是否委派。',
}

export const mcpTransportDescriptions: Record<McpTransport, string> = {
  STREAMABLE_HTTP: 'MCP 推荐的单端点 HTTP 传输，支持流式响应。',
  SSE: '旧版服务端事件流传输，用于尚未升级的服务。',
}

/** 编码、模型工具名、委派名称与参数名的格式，和服务端校验保持一致。 */
export const CODE_PATTERN = /^[A-Za-z0-9_-]{1,80}$/
export const EXPOSED_NAME_PATTERN = /^[A-Za-z0-9_-]{1,64}$/
export const DELEGATION_NAME_PATTERN = /^[A-Za-z0-9_-]{1,80}$/
export const SYSTEM_PROMPT_LIMIT = 20000
export const DESCRIPTION_LIMIT = 500
export const TOOL_DESCRIPTION_LIMIT = 2000
export const MAX_ITERS_LIMIT = 200

export function formatTime(value: string | null | undefined): string {
  if (!value) {
    return '—'
  }
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

export function versionLabel(versionNo: number | null | undefined): string {
  return versionNo == null ? '—' : `v${versionNo}`
}

/** 把任意文本转为合法的标识：非法字符替换为下划线，并截断到指定长度。 */
export function sanitizeIdentifier(value: string, maxLength: number): string {
  const replaced = value.trim().replace(/[^A-Za-z0-9_-]/g, '_')
  return replaced.slice(0, maxLength)
}

/** 在已有名称集合中生成不重复的标识，冲突时追加数字后缀。 */
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

/** 配置流程中的环节：MCP 服务 → 工具 → 子 Agent → 主 Agent */
export type FlowStep = 'mcp' | 'tools' | 'subagents' | 'masters'

/**
 * 编辑页当前所处的版本状态：
 * create 新建资源；draft 编辑已保存的草稿；newDraft 基于已发布版本（或空白）准备新草稿，尚未保存；published 查看已发布版本。
 */
export type EditorMode = 'create' | 'draft' | 'newDraft' | 'published'

/** 编辑页页签 */
export type EditorTab = 'config' | 'versions' | 'usages'

/** 选择卡片的一个选项 */
export interface ChoiceOption<V extends string> {
  value: V
  title: string
  description: string
  disabled?: boolean
}
