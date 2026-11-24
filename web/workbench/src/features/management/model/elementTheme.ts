/** 将业务状态映射到 Element Plus 标签颜色。 */
export function tagType(
  tone: string | Record<string, boolean>,
): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  const value =
    typeof tone === 'string'
      ? tone
      : Object.keys(tone)
          .filter((key) => tone[key])
          .join(' ')
  if (value.includes('success')) return 'success'
  if (value.includes('warning')) return 'warning'
  if (value.includes('danger')) return 'danger'
  if (value.includes('primary')) return 'primary'
  return 'info'
}
