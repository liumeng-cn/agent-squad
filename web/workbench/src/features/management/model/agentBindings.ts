import type {
  AgentDelegation,
  AgentDelegationDraft,
  AgentToolBinding,
  SubagentOption,
  ToolOption,
  ToolType,
} from './agentConfig'

/**
 * Agent 编辑页的关联展示：按资源业务 ID 合并最新可选项与已保存绑定详情。
 */
export interface ToolCardInfo {
  toolId: string
  toolName: string
  displayAlias: string
  toolCode: string
  toolType: ToolType
  exposedName: string
  description: string
  versionNo: number | null
  usable: boolean
  unusableReason: string | null
}

export interface DelegationCardInfo {
  subagentId: string
  agentName: string
  agentDisplayAlias: string
  agentCode: string
  description: string
  versionNo: number | null
  toolCount: number
  usable: boolean
  unusableReason: string | null
}

export function toolCard(toolId: string, options: ToolOption[], bindings: AgentToolBinding[]): ToolCardInfo {
  const option = options.find(item => item.toolId === toolId)
  const binding = bindings.find(item => item.toolId === toolId)

  return {
    toolId,
    toolName: option?.toolName ?? binding?.toolName ?? '未知工具',
    displayAlias: option?.displayAlias ?? binding?.displayAlias ?? '',
    toolCode: option?.toolCode ?? binding?.toolCode ?? '',
    toolType: option?.toolType ?? binding?.toolType ?? 'HTTP',
    exposedName: option?.exposedName ?? binding?.exposedName ?? '',
    description: option?.description ?? binding?.description ?? '',
    versionNo: option?.latestVersionNo ?? binding?.versionNo ?? null,
    usable: Boolean(option) || Boolean(binding?.usable),
    unusableReason: option ? null : (binding?.unusableReason ?? '工具当前不可用'),
  }
}

export function delegationCard(
  delegation: AgentDelegationDraft,
  options: SubagentOption[],
  bindings: AgentDelegation[],
): DelegationCardInfo {
  const option = options.find(item => item.agentId === delegation.subagentId)
  const binding = bindings.find(item => item.subagentId === delegation.subagentId)

  return {
    subagentId: delegation.subagentId,
    agentName: option?.agentName ?? binding?.subagentName ?? '未知 Sub',
    agentDisplayAlias: option?.displayAlias ?? binding?.subagentDisplayAlias ?? '',
    agentCode: option?.agentCode ?? binding?.subagentCode ?? '',
    description: option?.description ?? binding?.subagentDescription ?? '',
    versionNo: option?.latestVersionNo ?? binding?.versionNo ?? null,
    toolCount: option?.toolCount ?? binding?.toolCount ?? 0,
    usable: Boolean(option) || Boolean(binding?.usable),
    unusableReason: option ? null : (binding?.unusableReason ?? '子 Agent 当前不可用'),
  }
}

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
