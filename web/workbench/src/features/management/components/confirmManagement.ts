/** 确认框选项：标题、确认按钮文字，以及停用、移除等危险操作的强调样式。 */
export interface ConfirmOptions {
  title?: string
  confirmText?: string
  danger?: boolean
}

/** 管理操作统一使用页面内确认框，避免浏览器原生提示。 */
export function confirmManagement(message: string, options: ConfirmOptions = {}): Promise<boolean> {
  return new Promise(resolve => {
    const title = options.title ?? '操作确认'
    const overlay = document.createElement('div')
    overlay.className = 'management-confirm-overlay'
    const panel = document.createElement('section')
    panel.className = 'management-confirm'
    panel.setAttribute('role', 'alertdialog')
    panel.setAttribute('aria-modal', 'true')
    panel.setAttribute('aria-label', title)
    const heading = document.createElement('h2')
    heading.textContent = title
    const content = document.createElement('p')
    content.textContent = message
    const actions = document.createElement('footer')
    const cancel = document.createElement('button')
    cancel.type = 'button'
    cancel.textContent = '取消'
    const accept = document.createElement('button')
    accept.type = 'button'
    accept.textContent = options.confirmText ?? '确定'
    accept.className = options.danger ? 'confirm-primary confirm-danger' : 'confirm-primary'
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
      if (event.key === 'Escape') {
        event.preventDefault()
        event.stopImmediatePropagation()
        finish(false)
      }
      if (event.key === 'Tab') {
        event.preventDefault()
        event.stopImmediatePropagation()
        if (document.activeElement === cancel) {
          accept.focus()
        } else {
          cancel.focus()
        }
      }
    }

    cancel.onclick = () => finish(false)
    accept.onclick = () => finish(true)
    overlay.onclick = event => {
      if (event.target === overlay) {
        finish(false)
      }
    }
    document.addEventListener('keydown', keyboard, true)
    document.body.append(overlay)
    cancel.focus()
  })
}
