import type {
  AgentDelegation,
  AgentDelegationDraft,
  AgentToolBinding,
  SubagentOption,
  ToolOption,
  ToolType,
} from './agentConfig'

/**
 * Agent 编辑页的绑定展示：草稿只保存版本 ID 与委派名称，展示信息合并已保存的绑定详情与当前可选项。
 */
export interface ToolCardInfo {
  toolVersionId: string
  toolId: string
  toolName: string
  toolCode: string
  toolType: ToolType
  exposedName: string
  description: string
  versionNo: number | null
  /** 同一工具可选择的已发布版本（版本号倒序） */
  versions: ToolOption[]
  /** 最新发布版本号，大于当前绑定版本时提示可升级 */
  latestVersionNo: number | null
  /** 当前绑定是否可用：版本已发布、工具启用且依赖可用 */
  usable: boolean
}

export interface DelegationCardInfo {
  subagentVersionId: string
  agentId: string
  agentName: string
  agentCode: string
  description: string
  versionNo: number | null
  versions: SubagentOption[]
  currentVersionNo: number | null
  usable: boolean
}

function byVersionDesc<T extends { versionNo: number }>(items: T[]): T[] {
  return [...items].sort((left, right) => right.versionNo - left.versionNo)
}

/** 合并绑定版本的展示信息：优先使用可选项中的最新数据，选项中没有时回退到已保存的绑定详情。 */
export function toolCard(toolVersionId: string, options: ToolOption[], bindings: AgentToolBinding[]): ToolCardInfo {
  const option = options.find(item => item.toolVersionId === toolVersionId)
  const binding = bindings.find(item => item.toolVersionId === toolVersionId)
  const toolId = option?.toolId ?? binding?.toolId ?? ''
  const versions = byVersionDesc(options.filter(item => item.toolId === toolId))
  const latest = versions.find(item => item.latest) ?? versions[0]

  return {
    toolVersionId,
    toolId,
    toolName: option?.toolName ?? binding?.toolName ?? '未知工具',
    toolCode: option?.toolCode ?? binding?.toolCode ?? '',
    toolType: option?.toolType ?? binding?.toolType ?? 'HTTP',
    exposedName: option?.exposedName ?? binding?.exposedName ?? '',
    description: option?.description ?? binding?.description ?? '',
    versionNo: option?.versionNo ?? binding?.versionNo ?? null,
    versions,
    latestVersionNo: latest?.versionNo ?? binding?.latestVersionNo ?? null,
    usable: Boolean(option) || Boolean(binding?.usable),
  }
}

export function delegationCard(
  delegation: AgentDelegationDraft,
  options: SubagentOption[],
  bindings: AgentDelegation[],
): DelegationCardInfo {
  const option = options.find(item => item.subagentVersionId === delegation.subagentVersionId)
  const binding = bindings.find(item => item.subagentVersionId === delegation.subagentVersionId)
  const agentId = option?.agentId ?? binding?.subagentId ?? ''
  const versions = byVersionDesc(options.filter(item => item.agentId === agentId))
  const current = versions.find(item => item.current)

  return {
    subagentVersionId: delegation.subagentVersionId,
    agentId,
    agentName: option?.agentName ?? binding?.subagentName ?? '未知子 Agent',
    agentCode: option?.agentCode ?? binding?.subagentCode ?? '',
    description: option?.description ?? binding?.description ?? '',
    versionNo: option?.versionNo ?? binding?.versionNo ?? null,
    versions,
    currentVersionNo: current?.versionNo ?? binding?.currentVersionNo ?? null,
    usable: Boolean(option) || Boolean(binding?.usable),
  }
}

/** 模型工具名在同一 Agent 内必须唯一，返回重复的名称。 */
export function duplicatedNames(names: string[]): string[] {
  const seen = new Set<string>()
  const duplicated = new Set<string>()
  for (const name of names) {
    if (!name) {
      continue
    }
    if (seen.has(name)) {
      duplicated.add(name)
    }
    seen.add(name)
  }
  return [...duplicated]
}
