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
function blocks(text: string): VNode[] {
  const lines = text.replace(/\r\n/g, '\n').split('\n')
  const result: VNode[] = []
  let index = 0
  while (index < lines.length) {
    const line = lines[index]!
    if (!line.trim()) { index++; continue }
    if (/^\s*```/.test(line)) {
      const code: string[] = []; index++
      while (index < lines.length && !/^\s*```/.test(lines[index]!)) code.push(lines[index++]!)
      result.push(h('pre', [h('code', code.join('\n'))])); index++; continue
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
    while (index < lines.length && lines[index]!.trim() && !/^\s*(?:```|#{1,4}\s|[-*]\s|\d+\.\s)/.test(lines[index]!)) paragraph.push(lines[index++]!)
    result.push(h('p', inline(paragraph.join('\n'))))
  }
  return result
}
export default defineComponent({
  props: { text: { type: String, required: true } },
  setup: props => () => h('div', { class: 'answer-markdown' }, blocks(props.text)),
})
</script>
