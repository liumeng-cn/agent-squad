<script lang="ts">
import { defineComponent, h, type VNode } from 'vue'

/** 只构造文本和受限元素；原始 HTML、脚本及非 HTTP 链接始终作为文本处理。 */
function inline(text: string): (string | VNode)[] {
  const pieces: (string | VNode)[] = []
  const pattern = /\*\*([^*]+)\*\*|`([^`]+)`|\[([^\]]+)\]\(([^\s)]+)\)/g
  let previous = 0
  for (const match of text.matchAll(pattern)) {
    const index = match.index!
    if (index > previous) pieces.push(text.slice(previous, index))
    if (match[1]) pieces.push(h('strong', match[1]))
    else if (match[2]) pieces.push(h('code', match[2]))
    else if (/^https?:\/\//i.test(match[4] ?? '')) pieces.push(h('a', { href: match[4], target: '_blank', rel: 'noopener noreferrer' }, match[3]))
    else pieces.push(match[0])
    previous = index + match[0].length
  }
  if (previous < text.length) pieces.push(text.slice(previous))
  return pieces
}
/**
 * 仅解析结构化文本为 Vue VNode，不使用 innerHTML/v-html。
 * 表格、引用和代码围栏的内容最终仍由 Vue 做文本转义。
 */
function tableCells(line: string): string[] {
  return line.trim().replace(/^\|/, '').replace(/\|$/, '').split('|').map(cell => cell.trim())
}
function isTableSeparator(line: string): boolean {
  return /^\s*\|?\s*:?-{3,}:?\s*(?:\|\s*:?-{3,}:?\s*)+\|?\s*$/.test(line)
}
function blocks(text: string, depth = 0): VNode[] {
  // 嵌套引用设定上限，恶意深层输入降级为转义文本。
  if (depth >= 32) return [h('p', text)]
  const lines = text.replace(/\r\n/g, '\n').split('\n')
  const result: VNode[] = []
  let index = 0
  while (index < lines.length) {
    const line = lines[index]!
    if (!line.trim()) { index++; continue }
    if (/^\s*```/.test(line)) {
      const language = line.trim().slice(3).trim().slice(0, 40)
      const code: string[] = []; index++
      while (index < lines.length && !/^\s*```/.test(lines[index]!)) code.push(lines[index++]!)
      const content = code.join('\n')
      result.push(h('div', { class: 'markdown-code-block' }, [
        h('span', { class: 'markdown-code-language' }, language || '代码'),
        h('button', { type: 'button', onClick: async (event: MouseEvent) => {
          const button = event.currentTarget as HTMLButtonElement
          try { await navigator.clipboard.writeText(content); button.textContent = '已复制' }
          catch { button.textContent = '复制失败，请手动选择' }
        } }, '复制代码'),
        h('pre', [h('code', content)]),
      ])); index++; continue
    }
    if (line.trim().startsWith('>')) {
      const quote: string[] = []
      while (index < lines.length && /^\s*>/.test(lines[index]!)) {
        quote.push(lines[index++]!.replace(/^\s*> ?/, ''))
      }
      result.push(h('blockquote', blocks(quote.join('\n'), depth + 1)))
      continue
    }
    if (index + 1 < lines.length && line.includes('|') && isTableSeparator(lines[index + 1]!)) {
      const headers = tableCells(line)
      index += 2
      const rows: VNode[] = []
      while (index < lines.length && lines[index]!.includes('|') && lines[index]!.trim()) {
        const values = tableCells(lines[index++]!)
        rows.push(h('tr', headers.map((_, i) => h('td', inline(values[i] ?? '')))))
      }
      result.push(h('div', { class: 'markdown-table-scroll', style: 'overflow-x:auto' }, [
        h('table', [h('thead', [h('tr', headers.map(cell => h('th', inline(cell))))]), h('tbody', rows)]),
      ]))
      continue
    }
    const heading = /^(#{1,4})\s+(.+)$/.exec(line)
    if (heading) { result.push(h(`h${heading[1]!.length + 1}`, inline(heading[2]!))); index++; continue }
    if (/^\s*(?:[-*]|\d+\.)\s+\S/.test(line)) {
      const ordered = /^\s*\d+\./.test(line); const children: VNode[] = []
      const pattern = ordered ? /^\s*\d+\.\s+(.+)$/ : /^\s*[-*]\s+(.+)$/
      let item: RegExpExecArray | null
      while (index < lines.length && (item = pattern.exec(lines[index]!))) { children.push(h('li', inline(item[1]!))); index++ }
      result.push(h(ordered ? 'ol' : 'ul', children)); continue
    }
    const paragraph = [line]; index++
    while (index < lines.length && lines[index]!.trim() && !/^\s*(?:```|>|#{1,4}\s|[-*]\s|\d+\.\s)/.test(lines[index]!)) paragraph.push(lines[index++]!)
    result.push(h('p', inline(paragraph.join('\n'))))
  }
  return result
}
export default defineComponent({
  props: { text: { type: String, required: true } },
  setup: props => () => h('div', { class: 'answer-markdown' }, blocks(props.text)),
})
</script>
