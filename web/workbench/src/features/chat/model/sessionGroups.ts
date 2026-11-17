import type { SessionSummary } from './session'
/** 按当地日历天分组，采用本地日期的UTC序号，避免夏令时的23/25小时误差。 */
export function groupSessions(items: SessionSummary[], now = new Date()) {
  const day = (date: Date) => Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()) / 86400000
  const groups = new Map<string, SessionSummary[]>()
  const ordered = [...items].sort((a, b) => b.lastActivityAt.localeCompare(a.lastActivityAt))
  for (const item of ordered) {
    const age = day(now) - day(new Date(item.lastActivityAt))
    const label = age <= 0 ? '今天' : age === 1 ? '昨天' : age < 7 ? '近 7 天' : age < 30 ? '近 30 天' : '更早'
    if (!groups.has(label)) groups.set(label, [])
    groups.get(label)!.push(item)
  }
  return [...groups].map(([label, sessions]) => ({ label, sessions }))
}
