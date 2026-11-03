// @vitest-environment jsdom
import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import SafeMarkdown from '../SafeMarkdown.vue'

describe('受限 Markdown', () => {
  it('原始HTML与危险链接不产生可执行元素', () => {
    const wrapper = mount(SafeMarkdown, { props: { text: '<script>alert(1)</script>\n[危险](javascript:alert)\n![追踪](https://example.com/pixel)\n<svg onload=alert(1)>' } })
    expect(wrapper.find('script').exists()).toBe(false)
    expect(wrapper.find('svg').exists()).toBe(false)
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.findAll('a').every(link => /^https?:/.test(link.attributes('href') ?? ''))).toBe(true)
  })
  it('代码围栏、表格、引用保持文本转义', () => {
    const wrapper = mount(SafeMarkdown, { props: { text: '```html\n<img onerror=alert(1)>\n```\n\n| A | B |\n| --- | --- |\n| 一 | 二 |\n\n> 引用' } })
    expect(wrapper.find('pre code').text()).toContain('<img')
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.findAll('td')).toHaveLength(2)
    expect(wrapper.find('blockquote').text()).toBe('引用')
  })
  it('代码复制只写入代码正文', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText } })
    const wrapper = mount(SafeMarkdown, { props: { text: '```typescript\nconst x = 1\n```' } })
    await wrapper.find('button').trigger('click')
    expect(writeText).toHaveBeenCalledWith('const x = 1')
    expect(wrapper.find('.markdown-code-language').text()).toBe('typescript')
  })
  it('超深引用有界降级而不递归溢出', () => {
    const wrapper = mount(SafeMarkdown, { props: { text: '> '.repeat(1000) + '正文' } })
    expect(wrapper.findAll('blockquote').length).toBeLessThanOrEqual(32)
    expect(wrapper.text()).toContain('正文')
  })

})
