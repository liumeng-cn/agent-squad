import { afterEach, expect, it, vi } from 'vitest'
import { apiGet, ApiError, onUnauthorized } from '../http'

afterEach(() => { vi.unstubAllGlobals(); onUnauthorized(() => {}) })

it('HTTP 200 的业务冲突按响应 code 抛出', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(Response.json({ code: 409, data: null, msg: '状态已变化' })))
  await expect(apiGet('/test')).rejects.toMatchObject({ code: 409, status: 200, message: '状态已变化' })
})

it('HTTP 200 的未登录业务码触发登录处理', async () => {
  const handler = vi.fn()
  onUnauthorized(handler)
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(Response.json({ code: 401, data: null, msg: '请登录' })))
  await expect(apiGet('/test')).rejects.toBeInstanceOf(ApiError)
  expect(handler).toHaveBeenCalledOnce()
})

it('网络异常没有业务码', async () => {
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))
  await expect(apiGet('/test')).rejects.toMatchObject({ code: 0, status: 0 })
})
