/** 输入法候选确认与换行保留给输入框，只有普通 Enter 发送。 */
export function shouldSendOnEnter(event: Pick<KeyboardEvent, 'isComposing' | 'keyCode' | 'shiftKey'>, composing: boolean): boolean {
  return !event.isComposing && event.keyCode !== 229 && !composing && !event.shiftKey
}
