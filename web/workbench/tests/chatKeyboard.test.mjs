import assert from 'node:assert/strict'
import { test } from 'node:test'
import { shouldSendOnEnter } from '../src/shared/lib/chatKeyboard.ts'

test('候选确认、旧浏览器 229 与 composition 期间不发送', () => {
  assert.equal(shouldSendOnEnter({ isComposing: true, keyCode: 13, shiftKey: false }, false), false)
  assert.equal(shouldSendOnEnter({ isComposing: false, keyCode: 229, shiftKey: false }, false), false)
  assert.equal(shouldSendOnEnter({ isComposing: false, keyCode: 13, shiftKey: false }, true), false)
})
test('Shift Enter 换行，普通 Enter 发送', () => {
  assert.equal(shouldSendOnEnter({ isComposing: false, keyCode: 13, shiftKey: true }, false), false)
  assert.equal(shouldSendOnEnter({ isComposing: false, keyCode: 13, shiftKey: false }, false), true)
})
