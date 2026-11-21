/** 管理操作统一使用页面内确认框，避免浏览器原生提示。 */
export function confirmManagement(message: string): Promise<boolean> {
  return new Promise(resolve => {
    const overlay = document.createElement('div')
    overlay.className = 'management-confirm-overlay'
    const panel = document.createElement('section')
    panel.className = 'management-confirm'
    panel.setAttribute('role', 'alertdialog')
    panel.setAttribute('aria-modal', 'true')
    panel.setAttribute('aria-label', '操作确认')
    const heading = document.createElement('h2')
    heading.textContent = '操作确认'
    const content = document.createElement('p')
    content.textContent = message
    const actions = document.createElement('footer')
    const cancel = document.createElement('button')
    cancel.textContent = '取消'
    const accept = document.createElement('button')
    accept.textContent = '确定'
    accept.className = 'confirm-primary'
    actions.append(cancel, accept)
    panel.append(heading, content, actions)
    overlay.append(panel)
    const previous = document.activeElement as HTMLElement | null
    function finish(value: boolean) {
      document.removeEventListener('keydown', keyboard, true)
      overlay.remove()
      previous?.focus()
      resolve(value)
    }
    function keyboard(event: KeyboardEvent) {
      if (event.key === 'Escape') { event.preventDefault(); event.stopImmediatePropagation(); finish(false) }
      if (event.key === 'Tab') {
        event.preventDefault()
        event.stopImmediatePropagation()
        if (document.activeElement === cancel) accept.focus()
        else cancel.focus()
      }
    }
    cancel.onclick = () => finish(false)
    accept.onclick = () => finish(true)
    overlay.onclick = event => { if (event.target === overlay) finish(false) }
    document.addEventListener('keydown', keyboard, true)
    document.body.append(overlay)
    cancel.focus()
  })
}
