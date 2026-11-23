/**
 * 发布检查项：
 * error 未通过时同时阻止保存与发布；publish 只阻止发布（草稿可暂不满足）；advice 仅为建议。
 */
export interface CheckItem {
  label: string
  passed: boolean
  hint?: string
  level?: 'error' | 'publish' | 'advice'
}

export type CheckStage = 'save' | 'publish'

/** 返回在指定阶段阻止操作的未通过项。 */
export function blockingChecks(items: CheckItem[], stage: CheckStage): CheckItem[] {
  return items.filter(item => {
    if (item.passed || item.level === 'advice') {
      return false
    }
    return stage === 'publish' || item.level !== 'publish'
  })
}
